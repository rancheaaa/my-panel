package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressEvent;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.transfer.RetryManager;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.UploadListener;
import com.cq.agent.client.upload.UploadTask;
import lombok.Getter;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * 批量任务调度管理器
 * 负责管理所有批量传输任务的Quartz调度
 * 集成RetryManager处理重试逻辑
 * 集成FileScanner进行文件扫描（spec.md 4.5）
 * 集成AgentUploader进行P2P文件传输（spec.md 4.6）
 */
public class BatchTaskSchedulerManager {

    private static final Logger log = LoggerFactory.getLogger(BatchTaskSchedulerManager.class);

    private final ConfigFileManager configFileManager;
    private final QuartzTaskScheduler quartzTaskScheduler;

    @Getter
    private RetryManager retryManager;
    private FileScanner fileScanner;
    @Getter
    private AgentUploader agentUploader;
    @Getter
    private ProgressReporter progressReporter;

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

    /**
     * 设置文件扫描器（spec.md 4.5）
     */
    public void setFileScanner(FileScanner fileScanner) {
        this.fileScanner = fileScanner;
        log.info("📁 已设置FileScanner");
    }

    /**
     * 设置P2P上传器（spec.md 4.6）
     */
    public void setAgentUploader(AgentUploader agentUploader) {
        this.agentUploader = agentUploader;
        log.info("📤 已设置AgentUploader");
    }

    /**
     * 设置进度上报器（spec.md 4.8）
     */
    public void setProgressReporter(ProgressReporter progressReporter) {
        this.progressReporter = progressReporter;
        log.info("📊 已设置ProgressReporter");
    }

    /**
     * 获取指定任务的重试次数（委托给RetryManager）
     */
    public int getRetryCount(Long taskId) {
        if (retryManager == null) {
            return 0;
        }
        return retryManager.getRetryCount(taskId);
    }

    /**
     * 获取最大重试次数（委托给RetryManager）
     */
    public int getMaxRetries() {
        if (retryManager == null) {
            return 0;
        }
        return retryManager.getMaxRetries();
    }

    // ==================== 任务生命周期管理 ====================

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

    // ==================== 任务结果管理 ====================

    /**
     * 标记任务完成并释放资源
     */
    public void completeTask(Long taskId) {
        if (retryManager != null) {
            retryManager.recordSuccess(taskId);
            log.info("✅ 任务已完成: taskId={}", taskId);
        }
    }

    /**
     * 处理任务失败
     * @return true表示应继续重试，false表示最终失败
     */
    public boolean failTask(Long taskId, String error) {
        log.warn("⚠️  任务执行失败: taskId={}, error={}", taskId, error);

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
            
            // 使用Quartz调度延迟重试
            scheduleDelayedRetry(taskId, delayMs);
            
            return true;
        } else {
            log.error("❌ 任务最终失败（超过最大重试次数）: taskId={}, maxRetries={}", 
                taskId, retryManager.getMaxRetries());
            return false;
        }
    }

    // ==================== 核心执行流程（spec.md 4.5-4.7）====================

    /**
     * 创建任务执行Runnable
     * 执行流程：
     * 1. 扫描源目录（FileScanner - spec.md 4.5）
     * 2. P2P文件传输（AgentUploader - spec.md 4.6）
     * 3. 记录结果并处理重试（spec.md 4.7）
     */
    private Runnable createTaskRunnable(BatchTransferTaskConfig config) {
        return () -> {
            Long taskId = config.getTaskId();
            
            log.info("🚀 开始执行任务: taskId={}, sourceDir={}", taskId, config.getSourceDir());

            try {
                // Step 1: 扫描源目录（spec.md 4.5）
                List<FileScanner.ScannedFile> scannedFiles = scanSourceDirectory(config);
                
                // Step 2: P2P文件传输（spec.md 4.6）
                processScannedFiles(taskId, config, scannedFiles);
                
                // Step 3: 标记任务完成
                completeTask(taskId);
                log.info("✅ 任务执行成功: taskId={}, processedFiles={}", taskId, scannedFiles.size());
                
            } catch (Exception e) {
                log.error("❌ 任务执行异常: taskId={}, error={}", taskId, e.getMessage(), e);
                failTask(taskId, e.getMessage());
            }
        };
    }

    /**
     * 扫描源目录（spec.md 4.5）
     */
    private List<FileScanner.ScannedFile> scanSourceDirectory(BatchTransferTaskConfig config) {
        String sourceDir = config.getSourceDir();
        
        if (fileScanner == null) {
            log.warn("⚠️  FileScanner未配置，跳过文件扫描: taskId={}", config.getTaskId());
            return List.of();
        }

        log.debug("📁 开始扫描目录: {}", sourceDir);
        log.debug("📋 包含模式: {}", config.getIncludePatterns());
        log.debug("🚫 排除模式: {}", config.getExcludePatterns());

        Integer maxScanFiles = null;
        if (config.getScanConfig() != null) {
            maxScanFiles = config.getScanConfig().getMaxScanFiles();
        }

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
     * 处理扫描到的文件 - P2P传输（spec.md 4.6）
     * 
     * 对每个文件调用AgentUploader进行P2P传输
     * 通过BatchUploadListener捕获进度事件
     * 如果任何文件传输失败，抛出异常以触发重试机制
     */
    private void processScannedFiles(Long taskId, BatchTransferTaskConfig config, 
                                     List<FileScanner.ScannedFile> scannedFiles) {
        if (scannedFiles == null || scannedFiles.isEmpty()) {
            log.info("ℹ️  未扫描到文件，跳过传输: taskId={}", taskId);
            return;
        }

        // 检查是否有目标Agent
        if (!hasTargetAgents(config)) {
            log.info("ℹ️  无目标Agent配置，跳过传输: taskId={}", taskId);
            return;
        }

        // 检查是否有AgentUploader
        if (agentUploader == null) {
            log.warn("⚠️  AgentUploader未配置，跳过P2P传输: taskId={}", taskId);
            return;
        }

        log.info("📦 开始P2P传输{}个文件: taskId={}", scannedFiles.size(), taskId);

        List<String> failedFiles = new java.util.ArrayList<>();

        for (FileScanner.ScannedFile scannedFile : scannedFiles) {
            try {
                log.debug("📤 准备传输文件: fileName={}, size={}bytes", 
                    scannedFile.getFileName(), scannedFile.getFileSize());

                // 构建本地路径
                String localFilePath = scannedFile.getAbsolutePath();

                // 构建目标路径信息（格式：ip:port@username:destPath）
                String remoteTargetInfo = buildRemoteTargetInfo(config, scannedFile);

                // 创建上传监听器
                UploadListener listener = new BatchUploadListener(taskId, scannedFile);

                // 调用AgentUploader进行P2P传输
                boolean uploadSubmitted = agentUploader.uploadFile(localFilePath, remoteTargetInfo, listener);

                if (!uploadSubmitted) {
                    log.warn("⚠️  文件上传提交失败: fileName={}", scannedFile.getFileName());
                    failedFiles.add(scannedFile.getFileName());
                } else {
                    log.debug("✅ 文件上传已提交到队列: fileName={}", scannedFile.getFileName());
                }
                
            } catch (Exception e) {
                log.error("❌ 文件传输异常: fileName={}, error={}", 
                    scannedFile.getFileName(), e.getMessage());
                failedFiles.add(scannedFile.getFileName());
                
                // 单个文件失败继续处理其他文件，最后统一报告
            }
        }

        // 如果有任何文件失败，抛出异常以触发重试机制
        if (!failedFiles.isEmpty()) {
            throw new RuntimeException(String.format(
                "部分文件传输失败 (%d/%d): %s", 
                failedFiles.size(), 
                scannedFiles.size(),
                String.join(", ", failedFiles)
            ));
        }
    }

    /**
     * 检查是否有目标Agent配置
     */
    private boolean hasTargetAgents(BatchTransferTaskConfig config) {
        return config.getTargetAgentIds() != null && !config.getTargetAgentIds().isEmpty();
    }

    /**
     * 构建remoteTargetInfo字符串（spec.md 4.6）
     * 
     * 格式：ip:port@username:destFilePath
     * 示例：192.168.1.100:7777@root:/remote/target/app.log
     */
    private String buildRemoteTargetInfo(BatchTransferTaskConfig config, FileScanner.ScannedFile scannedFile) {
        try {
            // 从targetAgentNames解析目标信息
            // 格式：username@ip:port
            String targetAgentName = config.getTargetAgentNames().getFirst();  // 取第一个目标
            
            String[] parts = targetAgentName.split("@");
            if (parts.length != 2) {
                throw new IllegalArgumentException("目标Agent名称格式错误: " + targetAgentName);
            }

            String username = parts[0];
            String ipPort = parts[1];  // ip:port

            // 构建目标路径
            String targetDir = config.getTargetDirs().getFirst();  // 取第一个目标目录
            String destPath = targetDir + "/" + scannedFile.getFileName();

            // 组装remoteTargetInfo
            String remoteTargetInfo = ipPort + "@" + username + ":" + destPath;

            log.debug("🎯 构建目标路径: {}", remoteTargetInfo);
            return remoteTargetInfo;

        } catch (Exception e) {
            log.error("❌ 构建remoteTargetInfo失败: error={}", e.getMessage());
            throw new RuntimeException("构建目标路径失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用Quartz调度延迟重试（spec.md 4.7）
     * 
     * @param taskId 任务ID
     * @param delayMs 延迟时间（毫秒）
     */
    private void scheduleDelayedRetry(Long taskId, long delayMs) {
        try {
            log.info("⏰ 调度延迟重试: taskId={}, delayMs={}", taskId, delayMs);

            // 创建一次性触发器
            JobDetail jobDetail = JobBuilder.newJob(DelayedRetryJob.class)
                .withIdentity("retry-job-" + taskId + "-" + System.currentTimeMillis())
                .usingJobData("taskId", taskId)
                .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("retry-trigger-" + taskId + "-" + System.currentTimeMillis())
                .startAt(new Date(System.currentTimeMillis() + delayMs))
                .build();

            // 调度任务
            Scheduler scheduler = quartzTaskScheduler.getScheduler();
            scheduler.scheduleJob(jobDetail, trigger);

            log.info("✅ 延迟重试已调度: taskId={}, executeAt={}ms later", taskId, delayMs);
            
        } catch (Exception e) {
            log.error("❌ 调度延迟重试失败: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    // ==================== 内部类 ====================

    /**
     * 批量上传监听器（spec.md 4.6）
     * 桥接AgentUploader事件到任务管理器和进度上报
     */
    private class BatchUploadListener implements UploadListener {

        private final Long taskId;
        private final FileScanner.ScannedFile scannedFile;

        public BatchUploadListener(Long taskId, FileScanner.ScannedFile scannedFile) {
            this.taskId = taskId;
            this.scannedFile = scannedFile;
        }

        @Override
        public void onProgress(int totalChunks, int uploadedChunks, double progress) {
            log.debug("📊 上传进度: taskId={}, file={}, {}/{} ({}%)",
                taskId, scannedFile.getFileName(), uploadedChunks, totalChunks, progress);

            if (progressReporter != null) {
                ProgressEvent event = new ProgressEvent();
                event.setSubtaskId(taskId);
                event.setTaskId(taskId);
                event.setStatus("SENDING");
                event.setTransferredChunks(uploadedChunks);
                event.setTotalChunks(totalChunks);
                event.setTransferredBytes((long) (scannedFile.getFileSize() * progress));
                event.setTimestamp(System.currentTimeMillis());
                event.setSequenceNumber(uploadedChunks);

                progressReporter.reportProgress(event);
            }
        }

        @Override
        public void onComplete(UploadTask task) {
            log.info("✅ 文件上传完成: taskId={}, file={}, transferId={}",
                taskId, scannedFile.getFileName(), task.getTransferId());

            if (progressReporter != null) {
                ProgressEvent event = new ProgressEvent();
                event.setSubtaskId(taskId);
                event.setTaskId(taskId);
                event.setTransferId(task.getTransferId());
                event.setStatus("COMPLETED");
                event.setTransferredChunks(1);
                event.setTotalChunks(1);
                event.setTransferredBytes(scannedFile.getFileSize());
                event.setTimestamp(System.currentTimeMillis());

                progressReporter.reportProgress(event);
            }
        }

        @Override
        public void onError(String errorMessage) {
            log.error("❌ 文件上传失败: taskId={}, file={}, error={}",
                taskId, scannedFile.getFileName(), errorMessage);

            if (progressReporter != null) {
                ProgressEvent event = new ProgressEvent();
                event.setSubtaskId(taskId);
                event.setTaskId(taskId);
                event.setStatus("FAILED");
                event.setTimestamp(System.currentTimeMillis());

                progressReporter.reportProgress(event);
            }

            // 注意：这里不直接调用failTask，因为单个文件失败不应导致整个任务失败
            // 错误会在processScannedFiles中通过hasFailure标志处理
        }
    }

    /**
     * 延迟重试作业（Quartz Job）
     */
    public class DelayedRetryJob implements Job {

        private static final Logger jobLog = LoggerFactory.getLogger(DelayedRetryJob.class);

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            JobDataMap dataMap = context.getMergedJobDataMap();
            long taskId = dataMap.getLong("taskId");

            jobLog.info("⏰ 执行延迟重试任务: taskId={}", taskId);

            try {
                BatchTransferTaskConfig config = configFileManager.loadTaskConfig(taskId);
                
                if (config == null) {
                    jobLog.warn("⚠️  未找到任务配置: taskId={}, 跳过重试", taskId);
                    return;
                }
                
                if (!"RUNNING".equals(config.getStatus())) {
                    jobLog.info("ℹ️  任务状态为{}，跳过重试: taskId={}", config.getStatus(), taskId);
                    return;
                }

                jobLog.info("🔄 重新执行任务: taskId={}, sourceDir={}", taskId, config.getSourceDir());

                Runnable task = createTaskRunnable(config);
                task.run();

                jobLog.info("✅ 延迟重试执行完成: taskId={}", taskId);
                
            } catch (Exception e) {
                jobLog.error("❌ 延迟重试执行失败: taskId={}, error={}", taskId, e.getMessage(), e);
                
                failTask(taskId, "延迟重试失败: " + e.getMessage());
            }
        }
    }
}
