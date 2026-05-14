package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.UploadService;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.client.upload.UploadListener;
import com.cq.agent.config.AgentConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import lombok.Getter;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private RetryAwareUploaderDecorator retryAwareUploader; // 使用装饰者替代RetryManager
    private FileScanner fileScanner;
    @Getter
    private UploadService agentUploader; // 使用接口类型（支持装饰者）
    @Getter
    private ProgressReporter progressReporter;
    private AgentConfig agentConfig;

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
     * 
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

    public void setAgentConfig(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
        log.info("⚙️ 已设置AgentConfig");
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
        log.info("⏹️ BatchTaskSchedulerManager已关闭（FileRetryScheduler需单独关闭）");
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
        if (retryAwareUploader != null) {
            retryAwareUploader.recordSuccess(taskId);
            log.info("✅ 任务已完成: taskId={}", taskId);
        }
    }

    // ==================== 核心执行流程（spec.md 4.5-4.7）====================

    /**
     * 处理任务执行失败（仅记录日志，不触发重试）
     * 设计原则：
     * - 文件传输失败由 FileRetryScheduler + FailedQueueScannerJob 处理
     * - 任务级别失败（配置错误、目录不存在等）直接标记为最终失败
     * - 不再进行任务级别的延迟重试
     *
     * @param taskId 任务ID
     * @param error  错误信息
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
                List<ScannedFile> scannedFiles = scanSourceDirectory(config);
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
     * 扫描源目录（spec.md 4.5）
     */
    private List<ScannedFile> scanSourceDirectory(AgentTaskConfig config) {
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

        List<ScannedFile> scannedFiles = fileScanner.scan(
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
            List<ScannedFile> scannedFiles) {
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

        List<TargetAgentInfo> allTargets = config.getTargetAgents();
        TransferConfig transferConfig = config.getTransferConfig();
        String routingStrategy = transferConfig != null && transferConfig.getRoutingStrategy() != null
                ? transferConfig.getRoutingStrategy()
                : "BROADCAST";

        TargetRouter router = new TargetRouter();

        int totalSubtasks = 0;
        List<String> failedFiles = new java.util.ArrayList<>();

        for (ScannedFile scannedFile : scannedFiles) {
            List<TargetAgentInfo> routedTargets = router.route(allTargets, transferConfig);
            totalSubtasks += routedTargets.size();

            for (TargetAgentInfo targetAgent : routedTargets) {
                try {
                    log.debug("📤 准备传输文件: fileName={}, size={}bytes, target={}, strategy={}",
                            scannedFile.getFileName(), scannedFile.getFileSize(),
                            targetAgent.getAgentId(), routingStrategy);

                    String localFilePath = scannedFile.getAbsolutePath();

                    String remoteTargetInfo = buildRemoteTargetInfo(targetAgent, scannedFile);

                    if (agentUploader instanceof RetryAwareUploaderDecorator uploader) {
                        if (uploader.isFileAlreadyQueued(localFilePath, remoteTargetInfo)) {
                            log.info("⏭️ 文件已在传输队列中，跳过: fileName={}, target={}",
                                    scannedFile.getFileName(), targetAgent.getAgentId());
                            continue;
                        }
                    }

                    UploadListener listener = new BatchUploadListener(
                            taskId, scannedFile, config, targetAgent, progressReporter,
                            agentConfig != null ? agentConfig.getUploadSuccessQueueDir() : null,
                            agentConfig != null ? agentConfig.getUploadSendingQueueDir() : null);

                    boolean uploadSubmitted = agentUploader.uploadFile(localFilePath, remoteTargetInfo, listener);

                    if (!uploadSubmitted) {
                        log.warn("⚠️  文件上传提交失败: fileName={}, target={}",
                                scannedFile.getFileName(), targetAgent.getAgentId());
                        failedFiles.add(scannedFile.getFileName() + "->" + targetAgent.getAgentId());
                    } else {
                        log.debug("✅ 文件上传已提交到队列: fileName={}, target={}",
                                scannedFile.getFileName(), targetAgent.getAgentId());
                    }

                } catch (Exception e) {
                    log.error("❌ 文件传输异常: fileName={}, target={}, error={}",
                            scannedFile.getFileName(), targetAgent.getAgentId(), e.getMessage());
                    failedFiles.add(scannedFile.getFileName() + "->" + targetAgent.getAgentId());
                }
            }
        }

        log.info("📦 P2P传输调度完成: {}个文件, strategy={}, 总子任务={}, taskId={}",
                scannedFiles.size(), routingStrategy, totalSubtasks, taskId);

        if (!failedFiles.isEmpty()) {
            log.warn("⚠️ 部分子任务提交发送队列失败 ({}/{}): {}",
                    failedFiles.size(), totalSubtasks,
                    String.join(", ", failedFiles));
            log.info("ℹ️ 失败的文件将进入失败队列，等待 RetryManager 定时扫描和重试");
            if (agentUploader != null) {
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

    private String buildRemoteTargetInfo(TargetAgentInfo targetAgent, ScannedFile scannedFile) {
        try {
            String targetAgentName = targetAgent.getAgentName();
            if (targetAgentName == null || targetAgentName.isEmpty()) {
                targetAgentName = targetAgent.getAgentId();
            }

            String[] parts = targetAgentName.split("@");
            if (parts.length != 2) {
                throw new IllegalArgumentException("目标Agent名称格式错误: " + targetAgentName);
            }

            String username = parts[0];
            String ipPort = parts[1];

            String targetDir = targetAgent.getTargetDir();
            String destPath = targetDir + "/" + scannedFile.getFileName();

            String remoteTargetInfo = ipPort + "@" + username + ":" + destPath;

            log.debug("🎯 构建目标路径: {}", remoteTargetInfo);
            return remoteTargetInfo;

        } catch (Exception e) {
            log.error("❌ 构建remoteTargetInfo失败: error={}", e.getMessage());
            throw new RuntimeException("构建目标路径失败: " + e.getMessage(), e);
        }
    }

}
