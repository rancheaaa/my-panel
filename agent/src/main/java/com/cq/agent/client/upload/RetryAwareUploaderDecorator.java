package com.cq.agent.client.upload;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.config.AgentConfig;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 重试感知的上传装饰者（基于接口）
 * 将 RetryManager 的功能集成到装饰者中，提供：
 * - 失败队列扫描与自动重试
 * - 指数退避策略
 * - 最大重试次数控制
 * - 任务配置管理
 * 设计原则：
 * - 实现 UploadService 接口（与核心类相同的契约）
 * - 通过组合包装原始 UploadService
 * - 单一职责：只关注重试逻辑
 */
public class RetryAwareUploaderDecorator implements UploadService {

    private static final Logger logger = LoggerFactory.getLogger(RetryAwareUploaderDecorator.class);

    /** 委托对象（必须实现 UploadService 接口） */
    @Getter
    private final UploadService delegate;
    
    /** 全局ProgressReporter实例 */
    @Getter
    private ProgressReporter globalProgressReporter;

    /**
     * -- GETTER --
     *  获取最大重试次数
     */
    // ===== 重试配置 =====
    @Getter
    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;

    // ===== 依赖项 =====
    private TransferMetaStore<UploadTask> uploadMetaStore;
    private Path uploadFailQueueDir;

    // ===== 状态跟踪 =====
    private final Map<Long, AtomicInteger> retryCountMap = new ConcurrentHashMap<>();
    private final Map<Long, AgentTaskConfig> taskConfigMap = new ConcurrentHashMap<>();

    // ===== 重试执行器（可选）=====
    @SuppressWarnings("all")
    private Function<Long, Long> retryExecutor;

    /**
     * 构造函数
     * @param delegate 被装饰的上传服务实例
     * @param maxRetries 最大重试次数
     * @param intervalMin 首次重试等待时间（分钟）
     * @param maxDelayHours 最大退避时间（小时）
     */
    public RetryAwareUploaderDecorator(UploadService delegate, int maxRetries, 
            long intervalMin, long maxDelayHours) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
        this.initialDelayMs = TimeUnit.MINUTES.toMillis(intervalMin);
        this.maxDelayMs = TimeUnit.HOURS.toMillis(maxDelayHours);

        logger.info("✅ 创建RetryAwareUploaderDecorator: maxRetries={}, initialDelay={}min, maxDelay={}h",
            maxRetries, intervalMin, maxDelayHours);
    }

    // ==================== UploadService 接口实现 ====================

    @Override
    public boolean uploadFile(String localFilePath, String remoteTargetInfo, UploadListener listener) {
        return delegate.uploadFile(localFilePath, remoteTargetInfo, listener);
    }

    @Override
    public AgentConfig getAgentConfig() {
        return delegate.getAgentConfig();
    }

    // ==================== 重试管理初始化 ====================

    /**
     * 使用配置自动初始化重试依赖（推荐方式）
     * 内部创建 TransferMetaStore 和失败队列路径，简化外部调用
     */
    public void initWithConfig(AgentConfig config) {
        try {
            TransferMetaStore<UploadTask> uploadMetaStore = new TransferMetaStore<>(
                    Path.of(config.getUploadSendingQueueDir()), UploadTask.class);
            Path uploadFailQueueDir = Path.of(config.getUploadFailRetryQueueDir());
            
            initRetryDependencies(uploadMetaStore, uploadFailQueueDir);
            
            logger.info("✅ 重试装饰者初始化完成（使用配置自动创建依赖）");
        } catch (Exception e) {
            logger.warn("⚠️ 重试装饰者初始化失败，将使用无持久化模式: {}", e.getMessage());
        }
    }

    /**
     * 初始化重试相关的依赖项（手动传入依赖）
     */
    public void initRetryDependencies(TransferMetaStore<UploadTask> metaStore, Path failQueueDir) {
        this.uploadMetaStore = metaStore;
        this.uploadFailQueueDir = failQueueDir;
        
        logger.info("✅ 重试依赖初始化完成: failQueueDir={}", failQueueDir);
    }

    // ==================== 核心重试方法（从RetryManager移植）====================

    /**
     * 扫描上传失败队列并重试（Quartz Job 调用）
     */
    public void scanAndRetryFailedUploads() {
        if (uploadMetaStore == null || uploadFailQueueDir == null) {
            logger.debug("⏭️ 上传失败队列未初始化，跳过扫描");
            return;
        }

        try {
            List<UploadTask> failedTasks = uploadMetaStore.recoverFailedTasks(uploadFailQueueDir);

            if (failedTasks.isEmpty()) {
                logger.debug("📭 上传失败队列为空");
                return;
            }

            logger.info("🔍 发现 {} 个待重试的上传失败任务", failedTasks.size());

            int retriedCount = 0;
            int finalFailureCount = 0;

            for (UploadTask task : failedTasks) {
                try {
                    if (shouldRetryFailedFile(task)) {
                        retrySingleUploadFile(task);
                        retriedCount++;
                    } else {
                        markAsFinalFailure(task);
                        finalFailureCount++;
                    }
                } catch (Exception e) {
                    logger.error("❌ 处理失败任务异常: transferId={}, error={}",
                        task.getTransferId(), e.getMessage());
                }
            }

            logger.info("📊 上传失败队列扫描完成: 总数={}, 重试={}, 最终失败={}",
                failedTasks.size(), retriedCount, finalFailureCount);

        } catch (Exception e) {
            logger.error("❌ 扫描上传失败队列异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断失败文件是否应该重试
     */
    public boolean shouldRetryFailedFile(UploadTask task) {
        int currentRetries = task.getRetryCount();

        AgentTaskConfig config = findTaskConfigForUpload(task);

        if (config == null) {
            logger.warn("⚠️ 未找到任务配置，使用默认值: transferId={}", task.getTransferId());
            return currentRetries < maxRetries;
        }

        if (config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
            logger.warn("⚠️ 任务未启用重试: transferId={}", task.getTransferId());
            return false;
        }

        int maxAllowed = config.getRetryConfig().getMaxRetryCount();
        boolean shouldRetry = currentRetries < maxAllowed;

        logger.debug("🔍 重试检查: transferId={}, current={}, max={}, shouldRetry={}",
            task.getTransferId(), currentRetries, maxAllowed, shouldRetry);

        return shouldRetry;
    }

    /**
     * 重试单个失败的上传文件
     */
    private void retrySingleUploadFile(UploadTask task) {
        try {
            String transferId = task.getTransferId();

            task.incrementRetryCount();
            int newRetryCount = task.getRetryCount();

            logger.info("🔄 准备重试: transferId={}, file={}, retryCount={}/{}, status={}",
                transferId, task.getLocalFilePath(),
                newRetryCount, getMaxRetriesForTask(task),
                task.getStatus());

            task.setStatus(UploadTaskStatus.PREPARED);
            task.setExceptionDesc(null);
            
            // 更新时间戳
            task.updateTimestamp();

            if (uploadMetaStore != null) {
                uploadMetaStore.saveTask(task);
                
                logger.info("💾 已更新任务状态: transferId={}, retryCount={}, 状态已重置为PREPARED",
                    transferId, newRetryCount);
            }

            if (delegate instanceof AgentUploader uploader) {
                uploader.resubmitTask(task);
                logger.info("✅ 失败文件已重新提交到工作队列: transferId={}", transferId);
            } else {
                logger.error("❌ 委托对象不是AgentUploader类型，无法重新提交: transferId={}", transferId);
            }

        } catch (Exception e) {
            logger.error("❌ 重试失败文件异常: transferId={}, error={}",
                task.getTransferId(), e.getMessage());
        }
    }

    /**
     * 标记上传任务为最终失败（达到最大重试次数）
     */
    public void markAsFinalFailure(UploadTask task) {
        try {
            String transferId = task.getTransferId();

            logger.error("❌ 达到最大重试次数，标记为最终失败: transferId={}, file={}, retryCount={}/{}",
                transferId, task.getLocalFilePath(),
                task.getRetryCount(), getMaxRetriesForTask(task));

            task.setStatus(UploadTaskStatus.FAILED);
            
            // 更新时间戳
            task.updateTimestamp();

            String originalDesc = task.getExceptionDesc() != null ? task.getExceptionDesc() : "";
            task.setExceptionDesc(originalDesc + " | FINAL_FAILURE: 达到最大重试次数");

            if (uploadMetaStore != null) {
                uploadMetaStore.saveTask(task);
            }

            logger.warn("⚠️ 任务已标记为最终失败，保留在失败队列: transferId={}", transferId);

        } catch (Exception e) {
            logger.error("❌ 标记最终失败异常: transferId={}, error={}",
                task.getTransferId(), e.getMessage());
        }
    }

    // ==================== 重试策略方法 ====================

    /**
     * 判断是否应重试（实时调用）
     */
    public boolean shouldRetry(Long subtaskId, String error) {
        AtomicInteger count = retryCountMap.computeIfAbsent(subtaskId, k -> new AtomicInteger(0));

        int current = count.incrementAndGet();

        if (current > maxRetries) {
            logger.warn("❌ 达到最大重试次数: subtaskId={}, max={}, error={}",
                subtaskId, maxRetries, error);
            return false;
        }

        if (retryExecutor != null) {
            long delay = calculateNextRetryDelay(subtaskId, current);
            Long nextRetryAt = retryExecutor.apply(subtaskId);

            logger.info("🔄 调度重试: subtaskId={}, attempt={}/{}, delay={}ms, nextAt={}",
                subtaskId, current, maxRetries, delay, nextRetryAt);
        } else {
            logger.info("🔄 应重试: subtaskId={}, attempt={}/{}, error={}",
                subtaskId, current, maxRetries, error);
        }

        return true;
    }

    /**
     * 计算下次重试延迟（指数退避）
     */
    public long calculateNextRetryDelay(Long subtaskId, int attempt) {
        long delay = (long) (initialDelayMs * Math.pow(2, attempt - 1));

        if (delay > maxDelayMs) {
            delay = maxDelayMs;
        }

        return delay;
    }

    /**
     * 记录成功（清除重试计数）
     */
    public void recordSuccess(Long subtaskId) {
        retryCountMap.remove(subtaskId);
        logger.info("✅ 清除重试计数记录: subtaskId={}", subtaskId);
    }

    /**
     * 获取当前重试次数
     */
    public int getRetryCount(Long subtaskId) {
        AtomicInteger count = retryCountMap.get(subtaskId);
        return count != null ? count.get() : 0;
    }

    /**
     * 清除所有重试记录
     */
    public void clearAll() {
        retryCountMap.clear();
        logger.info("🗑️ 所有重试记录已清除");
    }

    // ==================== 任务配置管理 ====================

    /**
     * 注册任务配置（从 Proxy 接收配置后调用）
     */
    public void registerTaskConfig(Long taskId, AgentTaskConfig config) {
        taskConfigMap.put(taskId, config);

        if (config.getRetryConfig() != null) {
            logger.info("✅ 注册任务重试配置: taskId={}, maxRetries={}次, intervalMin={}min, backoff={}, enabled={}",
                taskId,
                config.getRetryConfig().getMaxRetryCount(),
                config.getRetryConfig().getIntervalMin(),
                config.getRetryConfig().getBackoffType(),
                config.getRetryConfig().isEnabled());
        }
    }

    /**
     * 根据 taskId 查找任务配置
     */
    public AgentTaskConfig findTaskConfigByTaskId(Long taskId) {
        return taskConfigMap.get(taskId);
    }

    // ==================== ProgressReporter 管理 ====================

    public void setGlobalProgressReporter(ProgressReporter progressReporter) {
        this.globalProgressReporter = progressReporter;
        logger.info("✅ 已设置全局ProgressReporter: {}", 
            progressReporter != null ? "已配置" : "null");
    }

    private AgentTaskConfig findTaskConfigForUpload(UploadTask task) {
        if (!taskConfigMap.isEmpty()) {
            return taskConfigMap.values().iterator().next();
        }
        return null;
    }

    private int getMaxRetriesForTask(UploadTask task) {
        AgentTaskConfig config = findTaskConfigForUpload(task);
        if (config != null && config.getRetryConfig() != null) {
            return config.getRetryConfig().getMaxRetryCount();
        }
        return maxRetries;  // 默认值
    }
}
