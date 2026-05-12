package com.cq.agent.batch.transfer;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.client.download.AgentDownloader;
import com.cq.agent.client.download.DownloadTask;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.UploadTask;
import com.cq.agent.client.upload.UploadTaskStatus;
import com.cq.agent.client.Util;
import lombok.Getter;
import lombok.Setter;
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
 * 重试管理器
 * 管理文件传输失败后的重试逻辑
 * 符合spec.md设计要求：
 * - 指数退避：首次等待intervalMin分钟，后续每次翻倍（上限2小时）
 * - 最大重试次数控制
 * - 支持重试执行器调度
 */
public class RetryManager {

    private static final Logger log = LoggerFactory.getLogger(RetryManager.class);

    /**
     * -- GETTER --
     *  获取最大重试次数
     */
    @Getter
    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;

    private final Map<Long, AtomicInteger> retryCountMap = new ConcurrentHashMap<>();

    // ===== 新增：依赖注入字段 =====
    private TransferMetaStore<UploadTask> uploadMetaStore;
    private TransferMetaStore<DownloadTask> downloadMetaStore;
    private Path uploadFailQueueDir;
    private Path downloadFailQueueDir;
    private AgentUploader agentUploader;
    private AgentDownloader agentDownloader;

    // ===== 新增：任务配置缓存（从Proxy推送）=====
    private final Map<Long, AgentTaskConfig> taskConfigMap = new ConcurrentHashMap<>();

    /**
     * -- SETTER --
     *  设置重试执行器
     */
    @Setter
    private Function<Long, Long> retryExecutor;

    /**
     * 构造函数
     * @param maxRetries 最大重试次数
     * @param intervalMin 首次重试等待时间（分钟）
     * @param maxDelayHours 最大退避时间（小时）
     */
    public RetryManager(int maxRetries, long intervalMin, long maxDelayHours) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = TimeUnit.MINUTES.toMillis(intervalMin);
        this.maxDelayMs = TimeUnit.HOURS.toMillis(maxDelayHours);

        log.info("✅ 重试管理器初始化: maxRetries={}, initialDelay={}min, maxDelay={}h",
            maxRetries, intervalMin, maxDelayHours);
    }

    /**
     * 初始化依赖项（在 Agent 启动时调用一次）
     * @param uploadMetaStore 上传任务元数据存储
     * @param downloadMetaStore 下载任务元数据存储
     * @param uploadFailQueueDir 上传失败队列目录
     * @param downloadFailQueueDir 下载失败队列目录
     * @param uploader 上传器实例
     * @param downloader 下载器实例
     */
    public void init(TransferMetaStore<UploadTask> uploadMetaStore,
                     TransferMetaStore<DownloadTask> downloadMetaStore,
                     Path uploadFailQueueDir,
                     Path downloadFailQueueDir,
                     AgentUploader uploader,
                     AgentDownloader downloader) {
        this.uploadMetaStore = uploadMetaStore;
        this.downloadMetaStore = downloadMetaStore;
        this.uploadFailQueueDir = uploadFailQueueDir;
        this.downloadFailQueueDir = downloadFailQueueDir;
        this.agentUploader = uploader;
        this.agentDownloader = downloader;

        log.info("✅ RetryManager 初始化完成: 上传失败队列={}, 下载失败队列={}",
            uploadFailQueueDir, downloadFailQueueDir);
    }

    /**
     * 注册任务配置（从 Proxy 接收配置后调用）
     * @param taskId 任务ID
     * @param config 批量任务配置（包含 RetryConfig）
     */
    public void registerTaskConfig(Long taskId, AgentTaskConfig config) {
        taskConfigMap.put(taskId, config);

        if (config.getRetryConfig() != null) {
            log.info("✅ 注册任务重试配置: taskId={}, maxRetries={}次, intervalMin={}min, backoff={}, enabled={}",
                taskId,
                config.getRetryConfig().getMaxRetryCount(),
                config.getRetryConfig().getIntervalMin(),
                config.getRetryConfig().getBackoffType(),
                config.getRetryConfig().isEnabled());
        }
    }

    /**
     * 根据 taskId 查找任务配置
     * @param taskId 任务ID
     * @return 任务配置，如果不存在返回 null
     */
    public AgentTaskConfig findTaskConfigByTaskId(Long taskId) {
        return taskConfigMap.get(taskId);
    }

    // ==================== 失败队列扫描与重试核心逻辑 ====================

    /**
     * 扫描上传失败队列并重试（Quartz Job 调用）
     */
    public void scanAndRetryFailedUploads() {
        if (uploadMetaStore == null || uploadFailQueueDir == null) {
            log.debug("⏭️ 上传失败队列未初始化，跳过扫描");
            return;
        }

        try {
            List<UploadTask> failedTasks = uploadMetaStore.recoverFailedTasks(uploadFailQueueDir);

            if (failedTasks.isEmpty()) {
                log.debug("📭 上传失败队列为空");
                return;
            }

            log.info("🔍 发现 {} 个待重试的上传失败任务", failedTasks.size());

            int retriedCount = 0;
            int finalFailureCount = 0;

            for (UploadTask task : failedTasks) {
                try {
                    if (shouldRetryFailedFile(task)) {
                        retrySingleUploadFile(task);
                        retriedCount++;
                    } else {
                        markUploadAsFinalFailure(task);
                        finalFailureCount++;
                    }
                } catch (Exception e) {
                    log.error("❌ 处理失败任务异常: transferId={}, error={}",
                        task.getTransferId(), e.getMessage());
                }
            }

            log.info("📊 上传失败队列扫描完成: 总数={}, 重试={}, 最终失败={}",
                failedTasks.size(), retriedCount, finalFailureCount);

        } catch (Exception e) {
            log.error("❌ 扫描上传失败队列异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断失败文件是否应该重试
     */
    public boolean shouldRetryFailedFile(UploadTask task) {
        int currentRetries = task.getRetryCount();

        AgentTaskConfig config = findTaskConfigForUpload(task);

        if (config == null) {
            log.warn("⚠️ 未找到任务配置，使用默认值: transferId={}", task.getTransferId());
            return currentRetries < maxRetries;
        }

        if (config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
            log.warn("⚠️ 任务未启用重试: transferId={}", task.getTransferId());
            return false;
        }

        int maxAllowed = config.getRetryConfig().getMaxRetryCount();
        boolean shouldRetry = currentRetries < maxAllowed;

        log.debug("🔍 重试检查: transferId={}, current={}, max={}, shouldRetry={}",
            task.getTransferId(), currentRetries, maxAllowed, shouldRetry);

        return shouldRetry;
    }

    /**
     * 重试单个失败的上传文件
     * 关键：每次重试后立即更新 JSON 中的 retryCount
     */
    private void retrySingleUploadFile(UploadTask task) {
        try {
            String transferId = task.getTransferId();

            // 1. 增加重试计数（原子操作）
            task.incrementRetryCount();
            int newRetryCount = task.getRetryCount();

            log.info("🔄 准备重试: transferId={}, file={}, retryCount={}/{}, status={}",
                transferId, task.getLocalFilePath(),
                newRetryCount, getMaxRetriesForTask(task),
                task.getStatus());

            // 2. 重置状态为 PREPARED（准备重新传输）
            task.setStatus(UploadTaskStatus.PREPARED);

            // 3. 清除异常描述
            task.setExceptionDesc(null);

            // 4. 更新时间戳
            task.setUpdateTime(Util.currentTime());

            // 5. ✅ 保存到正常目录（从失败队列移出，同时更新JSON中的retryCount）
            if (uploadMetaStore != null) {
                uploadMetaStore.saveTask(task);

                log.info("💾 已更新任务状态: transferId={}, retryCount={}, 状态已重置为PREPARED",
                    transferId, newRetryCount);
            }

            // 6. 重新提交传输（异步）
            if (agentUploader != null) {
                agentUploader.resubmitTask(task);
                log.info("✅ 失败文件已重新提交到工作队列: transferId={}", transferId);
            } else {
                log.error("❌ AgentUploader 未初始化，无法重新提交: transferId={}", transferId);
            }

        } catch (Exception e) {
            log.error("❌ 重试失败文件异常: transferId={}, error={}",
                task.getTransferId(), e.getMessage(), e);
        }
    }

    /**
     * 标记上传任务为最终失败（达到最大重试次数）
     */
    public void markUploadAsFinalFailure(UploadTask task) {
        try {
            String transferId = task.getTransferId();

            log.error("❌ 达到最大重试次数，标记为最终失败: transferId={}, file={}, retryCount={}/{}",
                transferId, task.getLocalFilePath(),
                task.getRetryCount(), getMaxRetriesForTask(task));

            // 1. 保持 FAILED 状态
            task.setStatus(UploadTaskStatus.FAILED);

            // 2. 更新时间戳
            task.setUpdateTime(Util.currentTime());

            // 3. 在异常描述中添加最终失败信息
            String originalDesc = task.getExceptionDesc() != null ? task.getExceptionDesc() : "";
            task.setExceptionDesc(originalDesc + " | FINAL_FAILURE: 达到最大重试次数");

            // 4. 保存（保留在失败队列中）
            if (uploadMetaStore != null) {
                uploadMetaStore.saveTask(task);
            }

            // 5. TODO: 通知 Proxy 任务最终失败
            // notifyProxyFinalFailure(task);

            log.warn("⚠️ 任务已标记为最终失败，保留在失败队列: transferId={}", transferId);

        } catch (Exception e) {
            log.error("❌ 标记最终失败异常: transferId={}, error={}",
                task.getTransferId(), e.getMessage());
        }
    }

    /**
     * 辅助方法：根据 UploadTask 查找对应的任务配置
     */
    private AgentTaskConfig findTaskConfigForUpload(UploadTask task) {
        if (!taskConfigMap.isEmpty()) {
            return taskConfigMap.values().iterator().next();
        }
        return null;
    }

    /**
     * 获取任务的最大重试次数
     */
    private int getMaxRetriesForTask(UploadTask task) {
        AgentTaskConfig config = findTaskConfigForUpload(task);
        if (config != null && config.getRetryConfig() != null) {
            return config.getRetryConfig().getMaxRetryCount();
        }
        return maxRetries;  // 默认值
    }

    /**
     * 判断是否应重试
     * @param subtaskId 子任务ID
     * @param error 错误信息
     * @return true表示应重试
     */
    public boolean shouldRetry(Long subtaskId, String error) {
        AtomicInteger count = retryCountMap.computeIfAbsent(subtaskId, k -> new AtomicInteger(0));

        int current = count.incrementAndGet();

        if (current > maxRetries) {
            log.warn("❌ 达到最大重试次数: subtaskId={}, max={}, error={}",
                subtaskId, maxRetries, error);
            return false;
        }

        if (retryExecutor != null) {
            long delay = calculateNextRetryDelay(subtaskId, current);
            Long nextRetryAt = retryExecutor.apply(subtaskId);

            log.info("🔄 调度重试: subtaskId={}, attempt={}/{}, delay={}ms, nextAt={}",
                subtaskId, current, maxRetries, delay, nextRetryAt);
        } else {
            log.info("🔄 应重试: subtaskId={}, attempt={}/{}, error={}",
                subtaskId, current, maxRetries, error);
        }

        return true;
    }

    /**
     * 计算下次重试延迟（指数退避）
     * 首次等待intervalMin分钟，后续每次翻倍，上限2小时
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
        log.info("✅ 清除重试计数记录: subtaskId={}", subtaskId);
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
        log.info("🗑️  所有重试记录已清除");
    }
}
