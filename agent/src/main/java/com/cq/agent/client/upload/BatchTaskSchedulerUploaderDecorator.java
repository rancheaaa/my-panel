package com.cq.agent.client.upload;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.agent.batch.scheduler.BatchUploadListener;
import com.cq.agent.batch.scheduler.QuartzTaskScheduler;
import com.cq.agent.batch.scheduler.TargetRouter;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.TransferFileStateManager;
import com.cq.agent.config.AgentConfig;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class BatchTaskSchedulerUploaderDecorator implements UploadService {

    private static final Logger log = LoggerFactory.getLogger(BatchTaskSchedulerUploaderDecorator.class);

    @Getter
    private final UploadService delegate;

    private final AgentConfig agentConfig;

    @Setter
    @Getter
    private ProgressReporter progressReporter;

    @Setter
    @Getter
    private FileBatchCompletionTracker fileBatchTracker;

    @Getter
    private ConfigFileManager configFileManager;

    @Setter
    @Getter
    private FileScanner fileScanner;

    @Setter
    @Getter
    private RetryAwareUploaderDecorator retryAwareUploader;

    private final QuartzTaskScheduler quartzTaskScheduler;

    public BatchTaskSchedulerUploaderDecorator(UploadService delegate, ConfigFileManager configFileManager)
            throws SchedulerException {
        this.delegate = delegate;
        this.agentConfig = delegate != null ? delegate.getAgentConfig() : null;
        this.configFileManager = configFileManager;
        Scheduler quartzScheduler = new StdSchedulerFactory().getScheduler();
        quartzScheduler.start();
        this.quartzTaskScheduler = new QuartzTaskScheduler(quartzScheduler);
    }

    public BatchTaskSchedulerUploaderDecorator(UploadService delegate, ConfigFileManager configFileManager,
            RetryAwareUploaderDecorator retryAwareUploader, FileScanner fileScanner,
            ProgressReporter progressReporter, FileBatchCompletionTracker fileBatchTracker) throws SchedulerException {
        this(delegate, configFileManager);
        this.retryAwareUploader = retryAwareUploader;
        this.fileScanner = fileScanner;
        this.progressReporter = progressReporter;
        this.fileBatchTracker = fileBatchTracker;
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

    // ====================
    // 任务生命周期管理（原BatchTaskSchedulerManager职能）====================

    public void startAllRunningTasks() {
        log.info("启动所有RUNNING状态的任务...");
        if (configFileManager == null || quartzTaskScheduler == null) {
            log.warn("configFileManager或quartzTaskScheduler未初始化，跳过启动");
            return;
        }
        List<AgentTaskConfig> allConfigs = configFileManager.loadAllTaskConfigs();
        int startedCount = 0;

        for (AgentTaskConfig config : allConfigs) {
            Long taskId = config.getTaskId();
            if ("RUNNING".equals(config.getStatus())) {
                startTask(config);
                startedCount++;
            } else {
                log.info("任务状态为{}，跳过启动: taskId={}", config.getStatus(), taskId);
            }
        }
        log.info("已启动{}个RUNNING任务", startedCount);
    }

    public void startTask(AgentTaskConfig config) {
        Long taskId = config.getTaskId();
        String cronExpression = config.getScanConfig() != null ? config.getScanConfig().getCronExpression() : null;

        if (cronExpression == null || cronExpression.isBlank()) {
            log.warn("任务缺少Cron表达式，无法调度: taskId={}", taskId);
            return;
        }

        Runnable task = createTaskRunnable(config);

        quartzTaskScheduler.startTask(config, task);
        log.info("任务已启动: taskId={}, cron={}", taskId, cronExpression);
    }

    public void pauseTask(Long taskId) {
        quartzTaskScheduler.pauseTask(taskId);
        log.info("任务已暂停: taskId={}", taskId);
    }

    public void resumeTask(Long taskId) {
        quartzTaskScheduler.resumeTask(taskId);
        log.info("任务已恢复: taskId={}", taskId);
    }

    public void updateTask(AgentTaskConfig config) {
        Long taskId = config.getTaskId();

        if (quartzTaskScheduler.isTaskRunning(taskId)) {
            quartzTaskScheduler.updateTask(config);
            log.info("任务已更新: taskId={}", taskId);
        } else {
            startTask(config);
            log.info("任务未运行，已启动: taskId={}", taskId);
        }
    }

    public void deleteTask(Long taskId) {
        quartzTaskScheduler.deleteTask(taskId);
        if (configFileManager != null) {
            configFileManager.deleteTaskConfig(taskId);
        }
        log.info("任务已删除: taskId={}", taskId);
    }

    public void shutdown() {
        if (quartzTaskScheduler != null) {
            quartzTaskScheduler.shutdown();
        }
        log.info("BatchTaskSchedulerUploaderDecorator已关闭");
    }

    public boolean isTaskRunning(Long taskId) {
        if (quartzTaskScheduler == null) {
            return false;
        }
        return quartzTaskScheduler.isTaskRunning(taskId);
    }

    public void completeTask(Long taskId) {
        if (retryAwareUploader != null) {
            retryAwareUploader.recordSuccess(taskId);
            log.info("任务已完成: taskId={}", taskId);
        }
    }

    // ==================== 核心执行流程 ====================

    private Runnable createTaskRunnable(AgentTaskConfig initialConfig) {
        Long initialTaskId = initialConfig.getTaskId();

        return () -> {
            AgentTaskConfig config = loadLatestConfig(initialTaskId, initialConfig);

            log.info("开始执行任务: taskId={}, sourceDir={}, version={}",
                    config.getTaskId(), config.getSourceDir(), config.getVersion());

            try {
                List<ScannedFile> scannedFiles = scanSourceDirectory(config);
                Long scanBatchId = generateScanBatchId(config.getTaskId());
                processScannedFiles(config.getTaskId(), config, scannedFiles, scanBatchId);
                completeTask(config.getTaskId());
                log.info("任务执行成功: taskId={}, processedFiles={}, scanBatchId={}",
                        config.getTaskId(), scannedFiles.size(), scanBatchId);
            } catch (Exception e) {
                log.error("任务执行异常: taskId={}, error={}", config.getTaskId(), e.getMessage(), e);
            }
        };
    }

    private AgentTaskConfig loadLatestConfig(Long initialTaskId, AgentTaskConfig fallback) {
        if (configFileManager == null) {
            return fallback;
        }
        AgentTaskConfig loaded = configFileManager.loadTaskConfig(initialTaskId);
        return loaded != null ? loaded : fallback;
    }

    private List<ScannedFile> scanSourceDirectory(AgentTaskConfig config) {
        String sourceDir = config.getSourceDir();
        if (fileScanner == null) {
            log.warn("FileScanner未配置，跳过文件扫描: taskId={}", config.getTaskId());
            return List.of();
        }
        Integer maxScanFiles = null;
        if (config.getScanConfig() != null) {
            maxScanFiles = config.getScanConfig().getMaxScanFiles();
        }
        List<ScannedFile> scannedFiles = fileScanner.scan(
                sourceDir,
                config.getIncludePatterns(),
                config.getExcludePatterns(),
                maxScanFiles);
        log.info("文件扫描完成: dir={}, found={} files", sourceDir, scannedFiles.size());
        return scannedFiles;
    }

    private void processScannedFiles(Long taskId, AgentTaskConfig config,
            List<ScannedFile> scannedFiles, Long scanBatchId) {
        if (scannedFiles == null || scannedFiles.isEmpty()) {
            log.info("未扫描到文件，跳过传输: taskId={}", taskId);
            return;
        }
        if (!hasTargetAgents(config)) {
            log.info("无目标Agent配置，跳过传输: taskId={}", taskId);
            return;
        }

        BatchUploadResult result = uploadScannedFiles(taskId, config, scannedFiles, scanBatchId);

        if (result.hasFailures()) {
            log.warn("部分子任务提交发送队列失败 ({}/{}): {}",
                    result.getFailedCount(), result.getTotalSubtasks(),
                    String.join(", ", result.getFailedFiles()));
        }
    }

    // ==================== 批量上传调度核心方法 ====================

    public BatchUploadResult uploadScannedFiles(Long taskId, AgentTaskConfig config,
            List<ScannedFile> scannedFiles, Long scanBatchId) {
        BatchUploadResult result = new BatchUploadResult();

        if (scannedFiles == null || scannedFiles.isEmpty()) {
            return result;
        }

        if (!hasTargetAgents(config)) {
            return result;
        }

        List<TargetAgentInfo> allTargets = config.getTargetAgents();
        TransferConfig transferConfig = config.getTransferConfig();
        String routingStrategy = transferConfig != null && transferConfig.getRoutingStrategy() != null
                ? transferConfig.getRoutingStrategy()
                : "BROADCAST";

        TargetRouter router = new TargetRouter();

        int totalSubtasks = 0;
        List<String> failedFiles = new ArrayList<>();

        for (ScannedFile scannedFile : scannedFiles) {
            Long fileBatchId = generateFileBatchId(taskId, scannedFile.getFileName(), scanBatchId);

            List<TargetAgentInfo> routedTargets = router.route(allTargets, transferConfig);
            totalSubtasks += routedTargets.size();

            initFileBatchIfNeeded(fileBatchId, scanBatchId, scannedFile, config, routedTargets);

            for (TargetAgentInfo targetAgent : routedTargets) {
                try {
                    String localFilePath = scannedFile.getAbsolutePath();
                    String remoteTargetInfo = buildRemoteTargetInfo(config, targetAgent, scannedFile);

                    // 通过检查隐藏文件判断是否已在传输中（替代原有的队列检查逻辑）
                    if (TransferFileStateManager.isTransferring(Paths.get(localFilePath))) {
                        log.info("文件已在传输中（隐藏文件存在），跳过: fileName={}, target={}",
                                scannedFile.getFileName(), targetAgent.getAgentId());
                        continue;
                    }

                    if (shouldSkipTargetCompleted(localFilePath, taskId, targetAgent, scannedFile)) {
                        continue;
                    }

                    // 提交前将原始文件标记为传输中（重命名为隐藏文件）
                    Path hiddenPath = hideFileBeforeUpload(scannedFile);

                    UploadListener listener = createBatchUploadListener(
                            taskId, scannedFile, config, targetAgent,
                            scanBatchId, fileBatchId);

                    boolean submitted = delegate.uploadFile(hiddenPath.toString(), remoteTargetInfo, listener);
                    result.incrementSubmitted();

                    if (!submitted) {
                        handleUploadFailure(fileBatchId, targetAgent, scannedFile, failedFiles);
                    }
                } catch (Exception e) {
                    log.error("文件传输异常: fileName={}, target={}, error={}",
                            scannedFile.getFileName(), targetAgent.getAgentId(), e.getMessage());
                    failedFiles.add(scannedFile.getFileName() + "->" + targetAgent.getAgentId());
                }
            }
        }

        result.setTotalSubtasks(totalSubtasks);
        result.setFailedFiles(failedFiles);
        result.setScannedCount(scannedFiles.size());

        log.info("P2P传输调度完成: {}个文件, strategy={}, 总子任务={}, taskId={}",
                scannedFiles.size(), routingStrategy, totalSubtasks, taskId);

        return result;
    }

    private void initFileBatchIfNeeded(Long fileBatchId, Long scanBatchId, ScannedFile scannedFile,
            AgentTaskConfig config, List<TargetAgentInfo> routedTargets) {
        if (routedTargets.size() <= 1 || fileBatchTracker == null) {
            return;
        }
        try {
            fileBatchTracker.initFileBatchIfAbsent(fileBatchId, scanBatchId, scannedFile, config, routedTargets);
        } catch (Exception e) {
            log.warn("初始化文件批次追踪失败: fileBatchId={}, error={}", fileBatchId, e.getMessage());
        }
    }

    /**
     * 将文件标记为传输中（重命名为隐藏文件）。
     * 如果文件已经被隐藏（一对多场景中第一个目标已隐藏），直接返回已有的隐藏路径。
     *
     * @param scannedFile 扫描到的文件
     * @return 隐藏后的文件路径
     * @throws IOException 如果文件不存在或隐藏失败
     */
    private Path hideFileBeforeUpload(ScannedFile scannedFile) throws IOException {
        Path originalPath = Paths.get(scannedFile.getAbsolutePath());

        // 如果已经是隐藏文件路径（一对多场景中第一个目标已隐藏），直接返回
        if (TransferFileStateManager.isTransferringFile(originalPath)) {
            log.debug("文件已是隐藏状态，直接使用: {}", originalPath);
            return originalPath;
        }

        // 如果隐藏文件已存在（文件已被隐藏），直接返回隐藏路径
        if (TransferFileStateManager.isTransferring(originalPath)) {
            Path hiddenPath = TransferFileStateManager.getTransferringPath(originalPath);
            log.debug("文件已在传输中，使用已有隐藏路径: {}", hiddenPath);
            // 更新scannedFile的路径为隐藏路径
            scannedFile.setOriginalAbsolutePath(scannedFile.getAbsolutePath());
            scannedFile.setAbsolutePath(hiddenPath.toString());
            return hiddenPath;
        }

        // 首次隐藏：将原始文件重命名为隐藏文件
        Path hiddenPath = TransferFileStateManager.hideFile(originalPath);
        // 保存原始路径，更新为隐藏路径
        scannedFile.setOriginalAbsolutePath(originalPath.toString());
        scannedFile.setAbsolutePath(hiddenPath.toString());
        return hiddenPath;
    }

    private boolean shouldSkipTargetCompleted(String localFilePath, Long taskId,
            TargetAgentInfo targetAgent, ScannedFile scannedFile) {
        if (fileBatchTracker == null) {
            return false;
        }
        String targetAgentKey = extractTargetAgentKeyFromInfo(targetAgent);
        if (targetAgentKey == null) {
            return false;
        }
        if (fileBatchTracker.isTargetCompletedInBatch(localFilePath, taskId, targetAgentKey)) {
            log.info("文件目标已完成，跳过: fileName={}, target={}",
                    scannedFile.getFileName(), targetAgent.getAgentId());
            return true;
        }
        return false;
    }

    private void handleUploadFailure(Long fileBatchId, TargetAgentInfo targetAgent,
            ScannedFile scannedFile, List<String> failedFiles) {
        log.warn("文件上传提交失败: fileName={}, target={}",
                scannedFile.getFileName(), targetAgent.getAgentId());
        failedFiles.add(scannedFile.getFileName() + "->" + targetAgent.getAgentId());
        if (fileBatchTracker != null && fileBatchId != null) {
            try {
                fileBatchTracker.markFailed(fileBatchId, targetAgent.getAgentId(), true);
            } catch (Exception e) {
                log.warn("标记批次失败状态异常: fileBatchId={}, error={}", fileBatchId, e.getMessage());
            }
        }
    }

    private UploadListener createBatchUploadListener(Long taskId, ScannedFile scannedFile,
            AgentTaskConfig config, TargetAgentInfo targetAgent,
            Long scanBatchId, Long fileBatchId) {
        return new BatchUploadListener(
                taskId, scannedFile, config, targetAgent, progressReporter,
                agentConfig != null ? agentConfig.getUploadSuccessQueueDir() : null,
                agentConfig != null ? agentConfig.getUploadSendingQueueDir() : null,
                scanBatchId, fileBatchId,
                fileBatchTracker);
    }

    @SuppressWarnings("all")
    private boolean hasTargetAgents(AgentTaskConfig config) {
        return config.getTargetAgents() != null && !config.getTargetAgents().isEmpty();
    }

    private String extractTargetAgentKeyFromInfo(TargetAgentInfo targetAgent) {
        if (targetAgent == null) {
            return null;
        }
        String agentName = targetAgent.getAgentName();
        if (agentName != null && agentName.contains("@")) {
            return agentName.substring(agentName.lastIndexOf("@") + 1);
        }
        return agentName;
    }

    private String buildRemoteTargetInfo(AgentTaskConfig config, TargetAgentInfo targetAgent, ScannedFile scannedFile) {
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
            if (targetDir == null) {
                targetDir = "/";
            }

            TransferConfig transferConfig = config.getTransferConfig();
            boolean preserveDir = transferConfig != null && transferConfig.isPreserveDirStructure();

            // 使用原始路径计算远程目标路径（不使用隐藏文件路径）
            String originalPath = scannedFile.getOriginalAbsolutePath();

            String relativePath;
            if (preserveDir) {
                String sourceDir = config.getSourceDir();
                if (sourceDir != null && !sourceDir.trim().isEmpty()) {
                    String absPath = normalizePath(originalPath);
                    String normSourceDir = normalizePath(sourceDir);

                    if (absPath.startsWith(normSourceDir)) {
                        relativePath = absPath.substring(normSourceDir.length());
                        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                            relativePath = relativePath.substring(1);
                        }
                        relativePath = relativePath.replace("\\", "/");
                    } else {
                        log.warn("preserveDirStructure=true但sourceDir不匹配: sourceDir={}, filePath={}",
                                sourceDir, originalPath);
                        relativePath = scannedFile.getFileName();
                    }
                } else {
                    relativePath = scannedFile.getFileName();
                }
            } else {
                relativePath = scannedFile.getFileName();
            }

            String separator = targetDir.endsWith("/") || targetDir.endsWith("\\") ? "" : "/";
            String destPath = targetDir + separator + relativePath;

            return ipPort + "@" + username + ":" + destPath;

        } catch (Exception e) {
            log.error("构建remoteTargetInfo失败: error={}", e.getMessage());
            throw new RuntimeException("构建目标路径失败: " + e.getMessage(), e);
        }
    }

    private String normalizePath(String path) {
        if (path == null)
            return null;
        return path.replace("/", "\\").trim();
    }

    private Long generateScanBatchId(Long taskId) {
        long timePart = System.currentTimeMillis() % 100000000000L;
        long taskPart = (taskId != null ? taskId : 0L) % 10000;
        long randomPart = java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 10000);
        return timePart * 1000000 + taskPart * 100 + randomPart;
    }

    private Long generateFileBatchId(Long taskId, String fileName, Long scanBatchId) {
        long scanBatchPrefix = scanBatchId != null ? scanBatchId / 1000000 : System.currentTimeMillis() % 100000000000L;
        int nameHash = Math.abs(fileName.hashCode());
        long taskPart = (taskId != null ? taskId : 0L) % 10000;
        long randomPart = java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1000);
        long fileHashPart = (nameHash % 10000L) * 100000L + taskPart * 1000L + randomPart;
        return scanBatchPrefix * 100000000L + fileHashPart;
    }

    // ==================== 结果对象 ====================

    @Data
    public static class BatchUploadResult {
        private int submittedCount = 0;
        private int totalSubtasks = 0;
        private int scannedCount = 0;
        private List<String> failedFiles = List.of();

        public void incrementSubmitted() {
            submittedCount++;
        }

        public boolean hasFailures() {
            return !failedFiles.isEmpty();
        }

        public int getFailedCount() {
            return failedFiles.size();
        }
    }
}
