package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.transfer.BatchTransferManager;
import com.cq.agent.batch.transfer.RetryManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 批量任务调度管理器
 * 负责管理所有批量传输任务的Quartz调度
 * 集成RetryManager处理重试逻辑
 * 集成BatchTransferManager控制并发传输
 * 集成FileScanner进行文件扫描（spec.md 4.5）
 */
public class BatchTaskSchedulerManager {

    private static final Logger log = LoggerFactory.getLogger(BatchTaskSchedulerManager.class);

    private final ConfigFileManager configFileManager;
    private final QuartzTaskScheduler quartzTaskScheduler;

    private RetryManager retryManager;
    private BatchTransferManager transferManager;
    private FileScanner fileScanner;

    public BatchTaskSchedulerManager(ConfigFileManager configFileManager) throws SchedulerException {
        this.configFileManager = configFileManager;
        Scheduler scheduler = new StdSchedulerFactory().getScheduler();
        scheduler.start();
        this.quartzTaskScheduler = new QuartzTaskScheduler(scheduler);
        log.info("✅ BatchTaskSchedulerManager初始化完成");
    }

    public void setRetryManager(RetryManager retryManager) {
        this.retryManager = retryManager;
        log.info("🔄 已设置RetryManager");
    }

    public RetryManager getRetryManager() {
        return retryManager;
    }

    public void setTransferManager(BatchTransferManager transferManager) {
        this.transferManager = transferManager;
        log.info("🔐 已设置BatchTransferManager");
    }

    public BatchTransferManager getTransferManager() {
        return transferManager;
    }

    /**
     * 设置文件扫描器（spec.md 4.5）
     */
    public void setFileScanner(FileScanner fileScanner) {
        this.fileScanner = fileScanner;
        log.info("📁 已设置FileScanner");
    }

    public FileScanner getFileScanner() {
        return fileScanner;
    }

    /**
     * 获取指定任务的重试次数（委托给RetryManager）
     * @param taskId 任务ID
     * @return 当前重试次数
     */
    public int getRetryCount(Long taskId) {
        if (retryManager == null) {
            return 0;
        }
        return retryManager.getRetryCount(taskId);
    }

    /**
     * 获取最大重试次数（委托给RetryManager）
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        if (retryManager == null) {
            return 0;
        }
        return retryManager.getMaxRetries();
    }

    /**
     * 启动所有本地RUNNING状态的任务
     */
    public void startAllRunningTasks() {
        log.info("🚀 启动所有RUNNING状态的任务...");
        List<BatchTransferTaskConfig> allConfigs = configFileManager.loadAllTaskConfigs();
        int startedCount = 0;

        for (BatchTransferTaskConfig config : allConfigs) {
            Long taskId = config.getTaskId();

            if ("RUNNING".equals(config.getStatus())) {
                startTask(config);
                startedCount++;
            } else {
                log.info("⏸️  任务状态为{}，跳过启动: taskId={}", config.getStatus(), taskId);
            }
        }

        log.info("✅ 已启动{}个RUNNING任务", startedCount);
    }

    /**
     * 启动单个任务
     */
    public void startTask(BatchTransferTaskConfig config) {
        Long taskId = config.getTaskId();
        String cronExpression = config.getCronExpression();

        if (cronExpression == null || cronExpression.isBlank()) {
            log.warn("⚠️  任务缺少Cron表达式，无法调度: taskId={}", taskId);
            return;
        }

        Runnable task = createTaskRunnable(config);

        quartzTaskScheduler.startTask(config, task);
        log.info("✅ 任务已启动: taskId={}, cron={}", taskId, cronExpression);
    }

    /**
     * 暂停任务
     */
    public void pauseTask(Long taskId) {
        quartzTaskScheduler.pauseTask(taskId);
        log.info("⏸️  任务已暂停: taskId={}", taskId);
    }

    /**
     * 恢复任务
     */
    public void resumeTask(Long taskId) {
        quartzTaskScheduler.resumeTask(taskId);
        log.info("▶️  任务已恢复: taskId={}", taskId);
    }

    /**
     * 更新任务（热更新）
     */
    public void updateTask(BatchTransferTaskConfig config) {
        Long taskId = config.getTaskId();

        if (quartzTaskScheduler.isTaskRunning(taskId)) {
            quartzTaskScheduler.updateTask(config);
            log.info("🔄 任务已更新: taskId={}", taskId);
        } else {
            startTask(config);
            log.info("🚀 任务未运行，已启动: taskId={}", taskId);
        }
    }

    /**
     * 删除任务
     */
    public void deleteTask(Long taskId) {
        quartzTaskScheduler.deleteTask(taskId);
        log.info("🗑️  任务已删除: taskId={}", taskId);
    }

    /**
     * 停止所有任务
     */
    public void shutdown() {
        quartzTaskScheduler.shutdown();
        if (retryManager != null) {
            retryManager.clearAll();
        }
        log.info("⏹️  所有任务已停止");
    }

    /**
     * 检查任务是否运行中
     */
    public boolean isTaskRunning(Long taskId) {
        return quartzTaskScheduler.isTaskRunning(taskId);
    }

    /**
     * 标记任务完成并释放资源
     * @param taskId 任务ID
     */
    public void completeTask(Long taskId) {
        String taskIdStr = String.valueOf(taskId);
        
        if (transferManager != null) {
            transferManager.release(taskIdStr);
            log.info("✅ 任务已完成，释放许可: taskId={}", taskId);
        }
        
        if (retryManager != null) {
            retryManager.recordSuccess(taskId);
        }
    }

    /**
     * 处理任务失败
     * @param taskId 任务ID
     * @param error 错误信息
     * @return true表示应继续重试，false表示最终失败
     */
    public boolean failTask(Long taskId, String error) {
        log.warn("⚠️  任务执行失败: taskId={}, error={}", taskId, error);

        // 释放传输许可
        if (transferManager != null) {
            transferManager.release(String.valueOf(taskId));
        }

        // 检查是否应重试
        if (retryManager == null) {
            log.warn("⚠️  RetryManager未设置，不进行重试");
            return false;
        }

        boolean shouldRetry = retryManager.shouldRetry(taskId, error);

        if (shouldRetry) {
            long delayMs = retryManager.calculateNextRetryDelay(taskId, retryManager.getRetryCount(taskId));
            log.info("🔄 将在{}ms后重试: taskId={}, attempt={}/{}", delayMs, taskId, 
                retryManager.getRetryCount(taskId), retryManager.getMaxRetries());
            
            // TODO: 使用Quartz调度延迟重试
            scheduleDelayedRetry(taskId, delayMs);
            
            return true;
        } else {
            log.error("❌ 任务最终失败（超过最大重试次数）: taskId={}, maxRetries={}", 
                taskId, retryManager.getMaxRetries());
            return false;
        }
    }

    /**
     * 创建任务执行Runnable（核心逻辑 - spec.md 4.5-4.7）
     * 
     * 执行流程：
     * 1. 获取传输许可（并发控制）
     * 2. 扫描源目录（FileScanner）
     * 3. 应用include/exclude模式过滤
     * 4. 对每个文件执行传输逻辑
     * 5. 记录传输结果
     * 6. 失败时触发重试
     */
    private Runnable createTaskRunnable(BatchTransferTaskConfig config) {
        return () -> {
            Long taskId = config.getTaskId();
            String taskIdStr = String.valueOf(taskId);
            
            log.info("🚀 开始执行任务: taskId={}, sourceDir={}", taskId, config.getSourceDir());

            try {
                // Step 1: 获取传输许可（并发控制）
                if (transferManager != null && !transferManager.tryAcquire(taskIdStr)) {
                    log.warn("⚠️  无法获取传输许可，跳过本次执行: taskId={}", taskId);
                    failTask(taskId, "获取传输许可超时");
                    return;
                }

                try {
                    // Step 2: 扫描源目录（spec.md 4.5）
                    List<FileScanner.ScannedFile> scannedFiles = scanSourceDirectory(config);
                    
                    // Step 3-4: 处理扫描到的文件（spec.md 4.6）
                    processScannedFiles(taskId, config, scannedFiles);
                    
                    // Step 5: 标记任务完成
                    completeTask(taskId);
                    log.info("✅ 任务执行成功: taskId={}, processedFiles={}", taskId, scannedFiles.size());
                    
                } catch (Exception e) {
                    log.error("❌ 任务执行异常: taskId={}, error={}", taskId, e.getMessage(), e);
                    failTask(taskId, e.getMessage());
                }
                
            } catch (Exception e) {
                log.error("❌ 任务执行严重异常: taskId={}, error={}", taskId, e.getMessage(), e);
            }
        };
    }

    /**
     * 扫描源目录（spec.md 4.5）
     * 
     * @param config 任务配置
     * @return 扫描到的文件列表
     */
    private List<FileScanner.ScannedFile> scanSourceDirectory(BatchTransferTaskConfig config) {
        String sourceDir = config.getSourceDir();
        
        // 如果没有配置FileScanner，返回空列表（向后兼容）
        if (fileScanner == null) {
            log.warn("⚠️  FileScanner未配置，跳过文件扫描: taskId={}", config.getTaskId());
            return List.of();
        }

        log.debug("📁 开始扫描目录: {}", sourceDir);
        log.debug("📋 包含模式: {}", config.getIncludePatterns());
        log.debug("🚫 排除模式: {}", config.getExcludePatterns());

        // 获取最大扫描文件数限制
        Integer maxScanFiles = null;
        if (config.getScanConfig() != null) {
            maxScanFiles = config.getScanConfig().getMaxScanFiles();
        }

        // 执行扫描
        List<FileScanner.ScannedFile> scannedFiles = fileScanner.scan(
            sourceDir,
            config.getIncludePatterns(),
            config.getExcludePatterns(),
            maxScanFiles
        );

        log.info("📁 文件扫描完成: dir={}, found={} files", sourceDir, scannedFiles.size());
        return scannedFiles;
    }

    /**
     * 处理扫描到的文件（spec.md 4.6）
     * 
     * @param taskId 任务ID
     * @param config 任务配置
     * @param scannedFiles 扫描到的文件列表
     */
    private void processScannedFiles(Long taskId, BatchTransferTaskConfig config, 
                                     List<FileScanner.ScannedFile> scannedFiles) {
        if (scannedFiles == null || scannedFiles.isEmpty()) {
            log.info("ℹ️  未扫描到文件，跳过传输: taskId={}", taskId);
            return;
        }

        log.info("📦 开始处理{}个文件: taskId={}", scannedFiles.size(), taskId);

        for (FileScanner.ScannedFile scannedFile : scannedFiles) {
            try {
                // TODO: 实际的P2P传输逻辑
                // 这里应该调用AgentUploader或其他传输组件
                // 格式：remoteTargetInfo = "ip:port@username:destFilePath"
                
                log.debug("📤 处理文件: fileName={}, size={}bytes", 
                    scannedFile.getFileName(), scannedFile.getFileSize());
                
                // 模拟传输成功（实际实现时应调用真实的上传逻辑）
                simulateFileTransfer(taskId, config, scannedFile);
                
            } catch (Exception e) {
                log.error("❌ 文件处理失败: fileName={}, error={}", 
                    scannedFile.getFileName(), e.getMessage());
                
                // 单个文件失败不影响整个任务，继续处理其他文件
                // 但可以记录失败信息用于后续重试
            }
        }
    }

    /**
     * 模拟文件传输（占位符实现）
     * 实际实现时应调用AgentUploader进行P2P传输
     */
    private void simulateFileTransfer(Long taskId, BatchTransferTaskConfig config, 
                                      FileScanner.ScannedFile scannedFile) {
        // TODO: 实际实现
        // 1. 构建目标路径：targetDir + relativePath
        // 2. 获取目标Agent信息（IP、端口等）
        // 3. 调用AgentUploader.uploadFile(localPath, remoteTargetInfo, listener)
        // 4. 通过UploadListener回调记录进度
        
        log.debug("✅ 文件传输模拟完成: fileName={}", scannedFile.getFileName());
    }

    /**
     * 调度延迟重试（使用Quartz）
     * 
     * @param taskId 任务ID
     * @param delayMs 延迟时间（毫秒）
     */
    private void scheduleDelayedRetry(Long taskId, long delayMs) {
        try {
            log.info("⏰ 调度延迟重试: taskId={}, delayMs={}", taskId, delayMs);
            
            // TODO: 实现基于Quartz的延迟重试调度
            // 可以创建一个一次性触发器，在delayMs后执行重试
            
        } catch (Exception e) {
            log.error("❌ 调度延迟重试失败: taskId={}, error={}", taskId, e.getMessage());
        }
    }
}
