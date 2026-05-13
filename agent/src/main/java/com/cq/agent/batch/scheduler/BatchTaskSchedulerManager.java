package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.UploadService;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.client.upload.UploadListener;
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
    private RetryAwareUploaderDecorator retryAwareUploader;  // 使用装饰者替代RetryManager
    private FileScanner fileScanner;
    @Getter
    private UploadService agentUploader;  // 使用接口类型（支持装饰者）
    @Getter
    private ProgressReporter progressReporter;

    public BatchTaskSchedulerManager(ConfigFileManager configFileManager) throws SchedulerException {
        this.configFileManager = configFileManager;
        Scheduler scheduler = new StdSchedulerFactory().getScheduler();
        scheduler.start();
        this.quartzTaskScheduler = new QuartzTaskScheduler(scheduler);
        log.info("✅ BatchTaskSchedulerManager初始化完成");
    }

    /**
     * 设置重试感知的上传装饰者（替代原来的RetryManager）
     */
    public void setRetryAwareUploader(RetryAwareUploaderDecorator retryAwareUploader) {
        this.retryAwareUploader = retryAwareUploader;
        log.info("🔄 已设置RetryAwareUploaderDecorator (替代RetryManager)");
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
     * @param agentUploader 上传服务实例（支持装饰者包装）
     */
    public void setAgentUploader(UploadService agentUploader) {
        this.agentUploader = agentUploader;
        log.info("📤 已设置UploadService (支持装饰者模式)");
    }

    /**
     * 设置进度上报器（spec.md 4.8）
     */
    public void setProgressReporter(ProgressReporter progressReporter) {
        this.progressReporter = progressReporter;
        log.info("📊 已设置ProgressReporter");
    }

    /**
     * 获取指定任务的重试次数（委托给RetryAwareUploaderDecorator）
     */
    public int getRetryCount(Long taskId) {
        if (retryAwareUploader == null) {
            return 0;
        }
        return retryAwareUploader.getRetryCount(taskId);
    }

    /**
     * 获取最大重试次数（委托给RetryAwareUploaderDecorator）
     */
    public int getMaxRetries() {
        if (retryAwareUploader == null) {
            return 10; // 默认值
        }
        return retryAwareUploader.getMaxRetries();
    }

    // ==================== 任务生命周期管理 ====================

    /**
     * 启动所有本地RUNNING状态的任务
     */
    public void startAllRunningTasks() {
        log.info("🚀 启动所有RUNNING状态的任务...");
        List<AgentTaskConfig> allConfigs = configFileManager.loadAllTaskConfigs();
        int startedCount = 0;

        for (AgentTaskConfig config : allConfigs) {
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
    public void startTask(AgentTaskConfig config) {
        Long taskId = config.getTaskId();
        String cronExpression = config.getScanConfig() != null ? config.getScanConfig().getCronExpression() : null;

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
    public void updateTask(AgentTaskConfig config) {
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
        if (retryAwareUploader != null) {
            retryAwareUploader.clearAll();
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
     * 调度失败队列扫描 Job（供 AgentApplication 启动时调用）
     */
    public void scheduleFailedQueueScannerJob(JobDetail jobDetail, Trigger trigger) throws Exception {
        Scheduler scheduler = quartzTaskScheduler.getScheduler();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    // ==================== 任务结果管理 ====================

    /**
     * 标记任务完成并释放资源
     */
    public void completeTask(Long taskId) {
        if (retryAwareUploader != null) {
            retryAwareUploader.recordSuccess(taskId);
            log.info("✅ 任务已完成: taskId={}", taskId);
        }
    }

    /**
     * 处理任务失败
     * 
     * @return true表示应继续重试，false表示最终失败
     */
    public boolean failTask(Long taskId, String error) {
        log.warn("⚠️  任务执行失败: taskId={}, error={}", taskId, error);

        // 检查是否应重试
        if (retryAwareUploader == null) {
            log.warn("⚠️  RetryAwareUploader未设置，不进行重试");
            return false;
        }

        boolean shouldRetry = retryAwareUploader.shouldRetry(taskId, error);

        if (shouldRetry) {
            long delayMs = retryAwareUploader.calculateNextRetryDelay(taskId, retryAwareUploader.getRetryCount(taskId));
            log.info("🔄 将在{}ms后重试: taskId={}, attempt={}/{}", delayMs, taskId,
                    retryAwareUploader.getRetryCount(taskId), retryAwareUploader.getMaxRetries());

            // 使用Quartz调度延迟重试
            scheduleDelayedRetry(taskId, delayMs);

            return true;
        } else {
            log.error("❌ 任务最终失败（超过最大重试次数）: taskId={}, maxRetries={}",
                    taskId, retryAwareUploader.getMaxRetries());
            return false;
        }
    }

    // ==================== 核心执行流程（spec.md 4.5-4.7）====================

    /**
     * 处理任务执行失败（仅记录日志，不触发重试）
     * 
     * 设计原则：
     * - 文件传输失败由 FileRetryScheduler + FailedQueueScannerJob 处理
     * - 任务级别失败（配置错误、目录不存在等）直接标记为最终失败
     * - 不再进行任务级别的延迟重试
     *
     * @param taskId 任务ID
     * @param error 错误信息
     */
    private void handleTaskFailure(Long taskId, String error) {
        log.error("❌ 任务执行失败（最终失败）: taskId={}, error={}", taskId, error);
    }

    /**
     * 创建任务执行Runnable
     * 执行流程：
     * 1. 从ConfigFileManager加载最新配置（支持热更新）
     * 2. 扫描源目录（FileScanner - spec.md 4.5）
     * 3. P2P文件传输（AgentUploader - spec.md 4.6）
     * 4. 记录结果并处理重试（spec.md 4.7）
     */
    private Runnable createTaskRunnable(AgentTaskConfig initialConfig) {
        Long initialTaskId = initialConfig.getTaskId();

        return () -> {
            // 🔑 关键：每次执行时从磁盘加载最新配置（热更新核心！）
            AgentTaskConfig config = configFileManager.loadTaskConfig(initialTaskId);
            if (config == null) {
                log.warn("⚠️ 无法加载任务配置，使用初始配置: taskId={}", initialTaskId);
                config = initialConfig;
            }

            log.info("🚀 开始执行任务: taskId={}, sourceDir={}, version={}",
                    config.getTaskId(), config.getSourceDir(), config.getVersion());

            try {
                List<FileScanner.ScannedFile> scannedFiles = scanSourceDirectory(config);
                processScannedFiles(config.getTaskId(), config, scannedFiles);
                completeTask(config.getTaskId());
                log.info("✅ 任务执行成功: taskId={}, processedFiles={}", config.getTaskId(), scannedFiles.size());
            } catch (Exception e) {
                log.error("❌ 任务执行异常: taskId={}, error={}", config.getTaskId(), e.getMessage(), e);
                handleTaskFailure(config.getTaskId(), e.getMessage());
            }
        };
    }

    /**
     * 创建任务执行Runnable（供DelayedRetryJob等外部类调用）
     */
    public Runnable createTaskRunnableForRetry(AgentTaskConfig config) {
        return createTaskRunnable(config);
    }

    /**
     * 扫描源目录（spec.md 4.5）
     */
    private List<FileScanner.ScannedFile> scanSourceDirectory(AgentTaskConfig config) {
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
                maxScanFiles);

        log.info("📁 文件扫描完成: dir={}, found={} files", sourceDir, scannedFiles.size());
        return scannedFiles;
    }

    /**
     * 处理扫描到的文件 - P2P传输（spec.md 4.6）
     * 对每个文件调用AgentUploader进行P2P传输
     * 通过BatchUploadListener捕获进度事件
     * 如果任何文件传输失败，抛出异常以触发重试机制
     */
    private void processScannedFiles(Long taskId, AgentTaskConfig config,
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

                // 创建上传监听器（包含postTransferAction逻辑）
                UploadListener listener = new BatchUploadListener(taskId, scannedFile, config, progressReporter);

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

        // 如果有任何文件失败，记录日志但不抛出异常
        // 失败的文件已经通过 moveToFailedQueue() 自动移入失败队列
        // RetryManager 会定期扫描并重试这些文件
        if (!failedFiles.isEmpty()) {
            log.warn("⚠️ 部分文件提交发送队列失败 ({}/{}): {}",
                    failedFiles.size(), scannedFiles.size(),
                    String.join(", ", failedFiles));
            log.info("ℹ️ 失败的文件将进入失败队列，等待 RetryManager 定时扫描和重试");
            if (agentUploader != null) {
                // 使用具体类的方法（不在接口中的实现细节）
                if (agentUploader instanceof AgentUploader uploader) {
                    log.info("ℹ️ 上传失败队列路径: {}", uploader.getFailedQueueDir());
                }
            }
        }
    }

    /**
     * 检查是否有目标Agent配置
     */
    private boolean hasTargetAgents(AgentTaskConfig config) {
        return config.getTargetAgents() != null && !config.getTargetAgents().isEmpty();
    }

    /**
     * 构建remoteTargetInfo字符串（spec.md 4.6）
     * 格式：ip:port@username:destFilePath
     * 示例：192.168.1.100:7777@root:/remote/target/app.log
     */
    private String buildRemoteTargetInfo(AgentTaskConfig config, FileScanner.ScannedFile scannedFile) {
        try {
            if (config.getTargetAgents() == null || config.getTargetAgents().isEmpty()) {
                throw new IllegalArgumentException("目标Agent列表为空");
            }
            com.cq.panel.common.dto.batch.TargetAgentInfo firstAgent = config.getTargetAgents().getFirst();
            String targetAgentName = firstAgent.getAgentName();
            if (targetAgentName == null || targetAgentName.isEmpty()) {
                targetAgentName = firstAgent.getAgentId();
            }

            String[] parts = targetAgentName.split("@");
            if (parts.length != 2) {
                throw new IllegalArgumentException("目标Agent名称格式错误: " + targetAgentName);
            }

            String username = parts[0];
            String ipPort = parts[1];

            String targetDir = firstAgent.getTargetDir();
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
     * @param taskId  任务ID
     * @param delayMs 延迟时间（毫秒）
     */
    private void scheduleDelayedRetry(Long taskId, long delayMs) {
        try {
            log.info("⏰ 调度延迟重试: taskId={}, delayMs={}", taskId, delayMs);

            // 创建JobDataMap并放入依赖对象
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("taskId", taskId);
            jobDataMap.put(DelayedRetryJob.CONFIG_FILE_MANAGER_KEY, configFileManager);
            jobDataMap.put(DelayedRetryJob.TASK_SCHEDULER_MANAGER_KEY, this);

            // 创建一次性触发器
            JobDetail jobDetail = JobBuilder.newJob(DelayedRetryJob.class)
                    .withIdentity("retry-job-" + taskId + "-" + System.currentTimeMillis())
                    .usingJobData(jobDataMap)
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
}
