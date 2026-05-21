package com.cq.agent.client.upload;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.agent.batch.scheduler.BatchUploadListener;
import com.cq.agent.batch.scheduler.FailedQueueScannerJob;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.report.SubTaskEvent;
import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.config.AgentConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.Setter;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 重试感知的上传装饰者（基于接口）
 * 将 RetryManager 的功能集成到装饰者中，提供：
 * - 失败队列扫描与自动重试
 * - 指数退避策略
 * - 最大重试次数控制
 * - 任务配置管理
 * 设计原则：
 * - 统一AgentTaskConfig获取的方法，全部从taskConfigMap中获取
 * - 实现 UploadService 接口（与核心类相同的契约）
 * - 通过组合包装原始 UploadService
 * - 单一职责：只关注重试逻辑
 */
public class RetryAwareUploaderDecorator implements UploadService {

    private static final Logger logger = LoggerFactory.getLogger(RetryAwareUploaderDecorator.class);

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final int DEFAULT_MAX_SCAN_COUNT = 100;

    /** 委托对象（必须实现 UploadService 接口） */
    @Getter
    private final UploadService delegate;

    /** 全局ProgressReporter实例 */
    @Getter
    private ProgressReporter globalProgressReporter;

    // ===== 配置文件管理器 =====
    private ConfigFileManager configFileManager;

    // ===== 依赖项 =====
    private TransferMetaStore<UploadTask> uploadMetaStore;
    private Path uploadFailQueueDir;
    private Path uploadFinalFailureQueueDir;

    // ===== 状态跟踪 =====
    private final Map<Long, AgentTaskConfig> taskConfigMap = new ConcurrentHashMap<>();

    /** 重试监听器缓存（按transferId复用，避免每次重试都new） */
    private final ConcurrentHashMap<String, BatchUploadListener> retryListenerCache = new ConcurrentHashMap<>();

    @Setter
    private volatile FileBatchCompletionTracker fileBatchTracker;

    // ===== 重试执行器（可选）=====
    @Getter
    @Setter
    private Function<Long, Long> retryExecutor;

    // ===== Quartz 失败队列扫描调度器（原 FileRetryScheduler 职能）=====
    private Scheduler retryScheduler;

    /**
     * 构造函数（使用默认重试配置）
     * 
     * @param delegate 被装饰的上传服务实例
     */
    public RetryAwareUploaderDecorator(UploadService delegate) {
        this.delegate = delegate;
    }

    public RetryAwareUploaderDecorator(UploadService delegate,
            FileBatchCompletionTracker fileBatchTracker, ProgressReporter progressReporter,
            TransferMetaStore<UploadTask> uploadMetaStore, Path uploadFailQueueDir,
            ConfigFileManager configFileManager, Path uploadFinalFailureQueueDir) {
        this.delegate = delegate;
        this.fileBatchTracker = fileBatchTracker;
        this.globalProgressReporter = progressReporter;
        this.uploadMetaStore = uploadMetaStore;
        this.uploadFailQueueDir = uploadFailQueueDir;
        this.configFileManager = configFileManager;
        this.uploadFinalFailureQueueDir = uploadFinalFailureQueueDir;
        initRetryScheduler(5 * 60 * 1000L);
        loadRetryConfigFromPersistence();
        logger.info("✅ 重试装饰者初始化完成（依赖注入模式）");
    }

    private void initRetryScheduler(long scanIntervalMs) {
        try {
            this.retryScheduler = new StdSchedulerFactory().getScheduler();
            this.retryScheduler.start();

            JobDetail failedQueueScannerJob = JobBuilder.newJob(FailedQueueScannerJob.class)
                    .withIdentity("failedQueueScanner", "retry-group")
                    .build();
            failedQueueScannerJob.getJobDataMap().put("retryAwareUploader", this);

            Trigger failedQueueScannerTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("failedQueueScannerTrigger", "retry-group")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(scanIntervalMs)
                            .repeatForever())
                    .build();

            this.retryScheduler.scheduleJob(failedQueueScannerJob, failedQueueScannerTrigger);
            logger.info("✅ 失败队列扫描调度器已启动, 扫描间隔: {}ms", scanIntervalMs);
        } catch (Exception e) {
            logger.warn("⚠️ 初始化失败队列扫描调度器失败: {}", e.getMessage());
        }
    }

    public void shutdownRetryScheduler() {
        try {
            if (retryScheduler != null && !retryScheduler.isShutdown()) {
                retryScheduler.shutdown(true);
                logger.info("⏹️ 失败队列扫描调度器已关闭");
            }
        } catch (SchedulerException e) {
            logger.error("❌ 关闭失败队列扫描调度器异常: {}", e.getMessage(), e);
        }
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

    @Override
    public boolean resubmitTask(UploadTask task, UploadListener listener) {
        return this.delegate.resubmitTask(task, listener);
    }

    public AgentTaskConfig loadRetryConfigFromPersistenceByTaskId(long id) {
        if (configFileManager == null) {
            logger.error("⏭️ ConfigFileManager未初始化，跳过加载持久化配置");
            return null;
        }

        try {
            List<AgentTaskConfig> allConfigs = configFileManager.loadAllTaskConfigs();
            if (allConfigs.isEmpty()) {
                logger.info("📭 本地无持久化配置");
                return null;
            }

            for (AgentTaskConfig taskConfig : allConfigs) {
                if (taskConfig.getTaskId() == id) {
                    return taskConfig;
                }
            }
        } catch (Exception e) {
            logger.error("❌ 加载持久化重试配置失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 从本地持久化的JSON配置中加载重试参数
     * 优先级：任务配置 > 全局默认值
     */
    public void loadRetryConfigFromPersistence() {
        if (configFileManager == null) {
            logger.debug("⏭️ ConfigFileManager未初始化，跳过加载持久化配置");
            return;
        }

        try {
            List<AgentTaskConfig> allConfigs = configFileManager.loadAllTaskConfigs();
            if (allConfigs.isEmpty()) {
                logger.info("本地无持久化配置");
                return;
            }

            for (AgentTaskConfig taskConfig : allConfigs) {
                RetryConfig retryConfig = taskConfig.getRetryConfig();
                if (retryConfig != null && retryConfig.isEnabled()) {
                    registerTaskConfig(taskConfig.getTaskId(), taskConfig);
                    logger.info("✅ 从持久化配置加载重试参数: taskId={}, maxRetries={}, intervalMin={}min",
                            taskConfig.getTaskId(), retryConfig.getMaxRetryCount(), retryConfig.getIntervalMin());
                    return;
                }
            }

            logger.info("⚠️ 所有持久化配置均未启用重试，使用默认值");

        } catch (Exception e) {
            logger.error("❌ 加载持久化重试配置失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 核心重试方法（从RetryManager移植）====================
    public List<UploadTask> recoverFailedTasks(Path failedQueueDir) {
        List<UploadTask> failedTasks = new ArrayList<>();

        if (failedQueueDir == null || !Files.exists(failedQueueDir)) {
            logger.debug("失败队列目录不存在: dir={}", failedQueueDir);
            return failedTasks;
        }

        int maxScanCount = DEFAULT_MAX_SCAN_COUNT;
        PriorityQueue<Path> oldestFilesHeap = new PriorityQueue<>(maxScanCount + 1,
                Comparator.comparingLong(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis();
                    } catch (IOException e) {
                        return Long.MAX_VALUE;
                    }
                }));

        try (Stream<Path> paths = Files.list(failedQueueDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        oldestFilesHeap.offer(path);
                        if (oldestFilesHeap.size() > maxScanCount) {
                            oldestFilesHeap.poll();
                        }
                    });
        } catch (IOException e) {
            logger.error("扫描失败队列目录失败: dir={}", failedQueueDir, e);
            return failedTasks;
        }

        if (oldestFilesHeap.isEmpty()) {
            logger.debug("失败队列为空");
            return failedTasks;
        }

        List<Path> sortedOldestFiles = new ArrayList<>(oldestFilesHeap);
        sortedOldestFiles.sort(Comparator.comparingLong(path -> {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                return Long.MAX_VALUE;
            }
        }));

        for (Path path : sortedOldestFiles) {
            try {
                String json = Files.readString(path);
                UploadTask task = gson.fromJson(json, UploadTask.class);

                if (task == null || task.getTaskId() == null) {
                    logger.warn("跳过无效的失败任务文件: path={}", path);
                    continue;
                }
                refreshFileTimestamp(path);
                failedTasks.add(task);

            } catch (IOException | JsonSyntaxException e) {
                logger.warn("加载失败任务失败，跳过损坏的文件: path={}", path, e);
            }
        }

        logger.info("从失败队列恢复任务数: count={}, 总扫描上限={}, 堆大小={}",
                failedTasks.size(), maxScanCount, oldestFilesHeap.size());
        return failedTasks;
    }

    private void reportNextRetryTime(UploadTask task, AgentTaskConfig config, int currentRetries, long nextRetryTimeMs) {
        if (globalProgressReporter == null || task.getSubtaskId() == null) {
            return;
        }
        try {
            SubTaskEvent event = new SubTaskEvent();
            event.setSubtaskId(task.getSubtaskId());
            event.setTaskId(task.getTaskId());
            event.setStatus("RETRYING");
            event.setRetryCount(currentRetries);
            event.setNextRetryAfter(new java.util.Date(nextRetryTimeMs));
            if (config != null) {
                event.setSourceAgentId(config.getSourceAgentId());
                event.setSourceAgentName(config.getSourceAgentName());
            }
            globalProgressReporter.reportRetrying(event);
        } catch (Exception e) {
            logger.warn("上报下一次重试时间失败: transferId={}, error={}", task.getTransferId(), e.getMessage());
        }
    }

    public long calculateNextRetryTimeMs(UploadTask task) {
        AgentTaskConfig config = findTaskConfigForUpload(task.getTaskId());
        if (config == null || config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
            return System.currentTimeMillis() + 30 * 60 * 1000L;
        }

        RetryConfig retryConfig = config.getRetryConfig();
        int currentRetries = task.getRetryCount();
        int intervalMin = retryConfig.getIntervalMin() != null ? retryConfig.getIntervalMin() : 1;
        String backoffType = retryConfig.getBackoffType();

        long currentTimeMs = System.currentTimeMillis();
        long updateTimeMs = parseUpdateTimeToMs(task.getUpdateTime());
        long staleThresholdMs = Math.max(intervalMin * 2L * 60_000L, 60 * 60 * 1000L);
        long baseTimeMs = (currentTimeMs - updateTimeMs > staleThresholdMs) ? currentTimeMs : updateTimeMs;

        if ("EXPONENTIAL".equalsIgnoreCase(backoffType)) {
            long baseIntervalMs = intervalMin * 60_000L;
            int effectiveRetries = (baseTimeMs == currentTimeMs) ? 0 : currentRetries;
            return baseTimeMs + baseIntervalMs * (long) Math.pow(2, effectiveRetries);
        } else if ("LINEAR".equalsIgnoreCase(backoffType)) {
            return baseTimeMs + intervalMin * 60_000L * (currentRetries + 1);
        } else {
            return baseTimeMs + intervalMin * 60_000L;
        }
    }

    private long parseUpdateTimeToMs(String updateTimeStr) {
        if (updateTimeStr == null || updateTimeStr.isEmpty()) {
            return System.currentTimeMillis();
        }

        try {
            LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr, FORMATTER);
            return updateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            logger.warn("解析更新时间失败: {}, 使用当前时间", updateTimeStr);
            return System.currentTimeMillis();
        }
    }

    private void refreshFileTimestamp(Path filePath) {
        try {
            Files.setLastModifiedTime(filePath, java.nio.file.attribute.FileTime.from(java.time.Instant.now()));
        } catch (IOException e) {
            logger.warn("刷新文件时间戳失败: path={}", filePath, e);
        }
    }

    private void deleteFailedTaskJsonFile(String transferId) {
        retryListenerCache.remove(transferId);
        if (uploadFailQueueDir == null || transferId == null || transferId.isEmpty()) {
            return;
        }

        try (Stream<Path> paths = Files.list(uploadFailQueueDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> path.getFileName().toString().contains(transferId))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                            logger.info("🗑️ 已删除失败任务JSON文件: path={}", path);
                        } catch (IOException e) {
                            logger.error("❌ 删除失败任务JSON文件失败: path={}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("❌ 扫描失败队列目录删除文件失败: dir={}", uploadFailQueueDir, e);
        }
    }

    /**
     * 扫描上传失败队列并重试（Quartz Job 调用）
     */
    public void scanAndRetryFailedUploads() {
        if (uploadMetaStore == null || uploadFailQueueDir == null) {
            logger.debug("⏭️ 上传失败队列未初始化，跳过扫描");
            return;
        }

        try {
            List<UploadTask> failedTasks = recoverFailedTasks(uploadFailQueueDir);

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
        final long currentTimeMs = System.currentTimeMillis();
        AgentTaskConfig config = taskConfigMap.get(task.getTaskId());
        if (config == null) {
            config = loadRetryConfigFromPersistenceByTaskId(task.getTaskId());
            if (config != null) {
                taskConfigMap.put(task.getTaskId(), config);
            }
        }

        if (config == null || config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
            logger.debug("任务未启用重试或配置不存在: taskId={}, transferId={}",
                    task.getTaskId(), task.getTransferId());
            return false;
        }

        RetryConfig retryConfig = config.getRetryConfig();
        int maxDays = retryConfig.getMaxDays() != null ? retryConfig.getMaxDays() : 7;

        // 检查是否超过最大重试天数
        String createTimeStr = task.getCreateTime();
        if (createTimeStr != null) {
            try {
                long createTimeMs = parseUpdateTimeToMs(createTimeStr);
                long maxRetryDurationMs = maxDays * 24L * 60 * 60 * 1000L;
                if (currentTimeMs - createTimeMs > maxRetryDurationMs) {
                    logger.info("⏰ 重试已过期(超过{}天): transferId={}, createTime={}",
                            maxDays, task.getTransferId(), createTimeStr);
                    return false;
                }
            } catch (Exception e) {
                logger.warn("⚠️ 解析任务创建时间失败，跳过maxDays检查: transferId={}", task.getTransferId());
                return false;
            }
        }
        // 检查是否超过最大重试次数
        final int retryCount = task.getRetryCount();
        if (retryCount > config.getRetryConfig().getMaxRetryCount()) {
            logger.info("重试次数已达最大: transferId={}, retryCount={}", task.getTransferId(), retryCount);
            return false;
        }
        return true;
    }

    /**
     * 重试单个失败的上传文件
     */
    private void retrySingleUploadFile(UploadTask task) {
        try {
            AgentTaskConfig config = findTaskConfigForUpload(task.getTaskId());
            final long currentTimeMs = System.currentTimeMillis();
            long nextRetryTimeMs = calculateNextRetryTimeMs(task);
            String backoffType = config.getRetryConfig().getBackoffType();
            boolean isReached = currentTimeMs >= nextRetryTimeMs;
            int currentRetries = task.getRetryCount();
            final Integer intervalMin = config.getRetryConfig().getIntervalMin();
            final Integer maxDays = config.getRetryConfig().getMaxDays();
            if(!isReached) {
                reportNextRetryTime(task, config, currentRetries, nextRetryTimeMs);
                logger.debug("该任务未到达重试时间: transferId={}, currentRetries={}, backoffType={}, " +
                                "intervalMin={}min, maxDays={}d, nextRetryTime={}, currentTime={}",
                        task.getTransferId(), currentRetries, backoffType,
                        intervalMin, maxDays, LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(nextRetryTimeMs),
                                java.time.ZoneId.systemDefault()).format(FORMATTER),
                        LocalDateTime.now().format(FORMATTER));
                return;
            }
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

            UploadListener retryListener = createRetryListener(task);
            logger.info("🔄 下一次重试时间: transferId={}, nextRetryTime={}",
                    transferId, LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(nextRetryTimeMs),
                            java.time.ZoneId.systemDefault()).format(FORMATTER));
            logger.info("🚀 重新提交上传任务: transferId={}, file={}, retryCount={}",
                    transferId, task.getLocalFilePath(), newRetryCount);

            boolean submitted = resubmitTask(task, retryListener);

            if (submitted) {
                deleteFailedTaskJsonFile(transferId);
                logger.info("✅ 重试任务提交成功: transferId={}, retryCount={}", transferId, newRetryCount);
            } else {
                logger.warn("⚠️ 重试任务提交失败: transferId={}, retryCount={}", transferId, newRetryCount);
            }

        } catch (Exception e) {
            logger.error("❌ 重试失败文件异常: transferId={}, error={}",
                    task.getTransferId(), e.getMessage());
        }
    }

    private UploadListener createRetryListener(UploadTask task) {
        String transferId = task.getTransferId();
        BatchUploadListener cached = retryListenerCache.get(transferId);
        if (cached != null) {
            logger.debug("复用缓存的重试监听器: transferId={}", transferId);
            return cached;
        }

        try {
            Long taskId = task.getTaskId();
            Long scanBatchId = task.getScanBatchId();
            Long fileBatchId = task.getFileBatchId();
            String filePath = task.getLocalFilePath();
            String fileName = task.getFileName();
            long fileSize = task.getFileSize() > 0 ? task.getFileSize() : task.getTotalSize();

            ScannedFile restoredFile = new ScannedFile();
            restoredFile.setFileName(fileName);
            restoredFile.setFileSize(fileSize);
            restoredFile.setLastModified(System.currentTimeMillis());
            restoredFile.setAbsolutePath(filePath);

            Path filePathObj = Path.of(filePath);
            if (TransferFileStateManager.isTransferringFile(filePathObj)) {
                Path originalPath = TransferFileStateManager.getOriginalPath(filePathObj);
                restoredFile.setOriginalAbsolutePath(originalPath.toString());
            }

            AgentTaskConfig agentTaskConfig = findTaskConfigForUpload(taskId);
            if (agentTaskConfig == null) {
                agentTaskConfig = loadRetryConfigFromPersistenceByTaskId(taskId);
                if (agentTaskConfig != null) {
                    registerTaskConfig(taskId, agentTaskConfig);
                }
            }

            BatchUploadListener retryListener = BatchUploadListener.forRetry(
                    taskId, task.getSubtaskId(), restoredFile, agentTaskConfig,
                    findTargetAgentForTask(agentTaskConfig, task),
                    getGlobalProgressReporter(),
                    getAgentConfig() != null ? getAgentConfig().getUploadSuccessQueueDir() : null,
                    getAgentConfig() != null ? getAgentConfig().getUploadSendingQueueDir() : null,
                    scanBatchId, fileBatchId,
                    fileBatchTracker);

            BatchUploadListener prev = retryListenerCache.putIfAbsent(transferId, retryListener);
            if (prev != null) {
                return prev;
            }

            logger.info("✅ 创建并缓存重试监听器: transferId={}, subtaskId={}, file={}",
                    transferId, task.getSubtaskId(), fileName);
            return retryListener;

        } catch (Exception e) {
            logger.warn("⚠️ 创建BatchUploadListener失败: error={}", e.getMessage());
            throw new IllegalStateException("创建BatchUploadListener失败");
        }
    }

    private TargetAgentInfo findTargetAgentForTask(
            AgentTaskConfig config, UploadTask task) {
        if (config == null || config.getTargetAgents() == null || config.getTargetAgents().isEmpty()) {
            return null;
        }
        String remoteAgentApiUrl = task.getRemoteAgentApiUrl();
        if (remoteAgentApiUrl != null) {
            try {
                java.net.URI uri = new java.net.URI(remoteAgentApiUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                String ipPort = port > 0 ? host + ":" + port : host;
                String username = task.getRemoteAgentUsername() != null ? task.getRemoteAgentUsername() : "root";
                String reconstructedName = username + "@" + ipPort;
                for (com.cq.panel.common.dto.batch.TargetAgentInfo agent : config.getTargetAgents()) {
                    if (reconstructedName.equals(agent.getAgentName())) {
                        return agent;
                    }
                }
            } catch (Exception e) {
                logger.debug("无法从remoteAgentApiUrl匹配目标Agent，使用第一个: {}", e.getMessage());
            }
        }
        return config.getTargetAgents().getFirst();
    }

    /**
     * 标记上传任务为最终失败（达到最大重试次数）
     */
    public void markAsFinalFailure(UploadTask task) {
        try {
            String transferId = task.getTransferId();
            retryListenerCache.remove(transferId);

            logger.error("❌ 达到最大重试次数，标记为最终失败: transferId={}, file={}, retryCount={}/{}",
                    transferId, task.getLocalFilePath(),
                    task.getRetryCount(), getMaxRetriesForTask(task));

            task.setStatus(UploadTaskStatus.FAILED);
            task.updateTimestamp();

            String originalDesc = task.getExceptionDesc() != null ? task.getExceptionDesc() : "";
            task.setExceptionDesc(originalDesc + " | FINAL_FAILURE: 达到最大重试次数");

            if (uploadMetaStore != null) {
                uploadMetaStore.saveTask(task);
            }

            renameSourceFileToFailed(task);
            moveToFinalFailureQueue(task);
            deleteFailedTaskJsonFile(task.getTransferId());
            reportFinalFailure(task);
            updateFileBatchTrackerForFinalFailure(task);
            logger.warn("⚠️ 任务已标记为最终失败: transferId={}", transferId);

        } catch (Exception e) {
            logger.error("❌ 标记最终失败异常: transferId={}, error={}",
                    task.getTransferId(), e.getMessage());
        }
    }

    private void renameSourceFileToFailed(UploadTask task) {
        String localFilePath = task.getLocalFilePath();
        if (localFilePath == null || localFilePath.isBlank()) {
            logger.debug("源文件路径为空，跳过重命名: transferId={}", task.getTransferId());
            return;
        }
        try {
            java.io.File sourceFile = new java.io.File(localFilePath);
            if (!sourceFile.exists()) {
                logger.debug("源文件不存在，跳过重命名: path={}", localFilePath);
                return;
            }
            String failedFilePath = localFilePath + ".failed";
            java.io.File failedFile = new java.io.File(failedFilePath);
            boolean renamed = sourceFile.renameTo(failedFile);
            if (renamed) {
                logger.info("📝 源文件已重命名为.failed: {} -> {}", localFilePath, failedFilePath);
            } else {
                logger.warn("⚠️ 源文件重命名失败: {} -> {}", localFilePath, failedFilePath);
            }
        } catch (Exception e) {
            logger.error("❌ 源文件重命名为.failed异常: path={}, error={}", localFilePath, e.getMessage());
        }
    }

    private void moveToFinalFailureQueue(UploadTask task) {
        if (uploadFinalFailureQueueDir == null || uploadMetaStore == null) {
            logger.debug("最终失败队列目录或元数据存储未初始化，跳过移动: transferId={}", task.getTransferId());
            return;
        }
        try {
            if (!Files.exists(uploadFinalFailureQueueDir)) {
                Files.createDirectories(uploadFinalFailureQueueDir);
                logger.info("📁 创建最终失败队列目录: dir={}", uploadFinalFailureQueueDir);
            }
            uploadMetaStore.moveToFailedQueue(task.getTransferId(), uploadFinalFailureQueueDir);
            logger.info("📦 控制文件已移至最终失败队列: transferId={}, dir={}",
                    task.getTransferId(), uploadFinalFailureQueueDir);
        } catch (Exception e) {
            logger.error("❌ 移动控制文件到最终失败队列失败: transferId={}, error={}",
                    task.getTransferId(), e.getMessage());
        }
    }

    private void reportFinalFailure(UploadTask task) {
        if (globalProgressReporter == null || task.getSubtaskId() == null) {
            return;
        }
        try {
            SubTaskEvent event = new SubTaskEvent();
            event.setSubtaskId(task.getSubtaskId());
            event.setTaskId(task.getTaskId());
            event.setStatus("FAILED");
            event.setErrorCode("FINAL_FAILURE");
            event.setErrorMessage("达到最大重试次数: retryCount=" + task.getRetryCount());
            AgentTaskConfig config = findTaskConfigForUpload(task.getTaskId());
            if (config != null) {
                event.setSourceAgentId(config.getSourceAgentId());
                event.setSourceAgentName(config.getSourceAgentName());
            }
            globalProgressReporter.reportFailed(event);
            logger.info("📡 已上报最终失败状态: transferId={}, subtaskId={}",
                    task.getTransferId(), task.getSubtaskId());
        } catch (Exception e) {
            logger.warn("上报最终失败状态失败: transferId={}, error={}", task.getTransferId(), e.getMessage());
        }
    }

    private void updateFileBatchTrackerForFinalFailure(UploadTask task) {
        if (fileBatchTracker == null || task.getFileBatchId() == null) {
            return;
        }
        try {
            AgentTaskConfig config = findTaskConfigForUpload(task.getTaskId());
            if (config == null || config.getTargetAgents() == null || config.getTargetAgents().isEmpty()) {
                return;
            }
            TargetAgentInfo targetAgent = findTargetAgentForTask(config, task);
            if (targetAgent == null) {
                return;
            }
            fileBatchTracker.markFailed(task.getFileBatchId(), targetAgent.getAgentId(), true);
            logger.info("📋 FileBatchTracker已更新为最终失败: fileBatchId={}, targetAgentId={}, transferId={}",
                    task.getFileBatchId(), targetAgent.getAgentId(), task.getTransferId());
        } catch (Exception e) {
            logger.warn("更新FileBatchTracker最终失败状态异常: transferId={}, error={}", task.getTransferId(), e.getMessage());
        }
    }

    /**
     * 记录成功
     */
    @SuppressWarnings("all")
    public void recordSuccess(Long subtaskId) {
        // todo 预留钩子函数，暂时不要实现任何逻辑
    }

    // ==================== 任务配置管理 ====================

    /**
     * 注册任务配置（从 Proxy 接收配置后调用）
     * 同时动态更新重试参数（支持运行时配置热更新）
     */
    @SuppressWarnings("all")
    public void registerTaskConfig(Long taskId, AgentTaskConfig config) {
        taskConfigMap.put(taskId, config);
        if (config.getRetryConfig() != null && config.getRetryConfig().isEnabled()) {
            logger.info("✅ 注册任务重试配置(已应用): taskId={}, maxRetries={}次, intervalMin={}min, backoff={}, enabled={}",
                    taskId,
                    config.getRetryConfig().getMaxRetryCount(),
                    config.getRetryConfig().getIntervalMin(),
                    config.getRetryConfig().getBackoffType(),
                    config.getRetryConfig().isEnabled());
        } else {
            logger.info("✅ 注册任务配置(未启用重试): taskId={}, enabled={}",
                    taskId, config.getRetryConfig() != null ? config.getRetryConfig().isEnabled() : "null");
        }
    }

    // ==================== ProgressReporter 管理 ====================

    private AgentTaskConfig findTaskConfigForUpload(long taskId) {
        AgentTaskConfig config = this.taskConfigMap.get(taskId);
        if (config == null) {
            config = loadRetryConfigFromPersistenceByTaskId(taskId);
            if (config != null) {
                taskConfigMap.put(taskId, config);
            }
        }
        return config;
    }

    private int getMaxRetriesForTask(UploadTask task) {
        AgentTaskConfig config = findTaskConfigForUpload(task.getTaskId());
        if (config != null && config.getRetryConfig() != null) {
            return config.getRetryConfig().getMaxRetryCount();
        }
        return 50; // 默认值
    }
}
