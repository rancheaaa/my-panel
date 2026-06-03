package com.cq.agent.client.upload;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.agent.batch.scheduler.BatchUploadListener;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.report.SubTaskEvent;
import com.cq.agent.config.AgentConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.agent.scheduler.SimpleTaskScheduler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.Setter;
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
 * 重试感知的上传装饰者（继承AgentUploader）
 * 将 RetryManager 的功能集成到装饰者中，提供：
 * - 失败队列扫描与自动重试
 * - 指数退避策略
 * - 最大重试次数控制
 * - 任务配置管理
 * 设计原则：
 * - 继承AgentUploader，直接调用父类方法，无需委托转换
 * - 统一AgentTaskConfig获取的方法，全部从taskConfigMap中获取
 * - listener统一由父类AgentUploader的listenerCache管理，不再内部维护retryListenerCache
 * - 单一职责：只关注重试逻辑
 */
public class RetryAwareUploader extends AgentUploader {

    private static final Logger logger = LoggerFactory.getLogger(RetryAwareUploader.class);

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final int DEFAULT_MAX_SCAN_COUNT = 100;

    @Getter
    private AgentConfig agentConfig;

    /** 全局ProgressReporter实例 */
    @Getter
    private ProgressReporter globalProgressReporter;

    // ===== 配置文件管理器 =====
    @Getter
    private ConfigFileManager configFileManager;

    // ===== 最终失败队列目录 =====
    private final Path uploadFinalFailureQueueDir;

    // ===== 状态跟踪 =====
    private final Map<Long, AgentTaskConfig> taskConfigMap = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private volatile FileBatchCompletionTracker fileBatchTracker;

    // ===== 重试执行器（可选）=====
    @Getter
    @Setter
    private Function<Long, Long> retryExecutor;

    // ===== 轻量调度器（替代Quartz） =====
    private SimpleTaskScheduler retryScheduler;

    /**
     * 构造函数（完整依赖注入模式）
     *
     * @param agentConfig                Agent配置
     * @param fileBatchTracker           文件批次完成追踪器
     * @param progressReporter           进度上报器
     * @param configFileManager          配置文件管理器
     * @param uploadFinalFailureQueueDir 最终失败队列目录
     */
    public RetryAwareUploader(AgentConfig agentConfig,
                              FileBatchCompletionTracker fileBatchTracker, ProgressReporter progressReporter,
                              ConfigFileManager configFileManager, Path uploadFinalFailureQueueDir) {
        super(agentConfig);
        this.agentConfig = agentConfig;
        this.fileBatchTracker = fileBatchTracker;
        this.globalProgressReporter = progressReporter;
        this.configFileManager = configFileManager;
        this.uploadFinalFailureQueueDir = uploadFinalFailureQueueDir;

    }

    public void initRetry() {
        initRetryScheduler(agentConfig.getFailedQueueScanIntervalMs());
        loadRetryConfigFromPersistence();
        super.init();
        logger.info("✅ RetryAwareUploader初始化完成（继承模式）");
    }

    @Override
    public void shutdown() {
        shutdownRetryScheduler();
        super.shutdown();
    }

    private void initRetryScheduler(long scanIntervalMs) {
        try {
            this.retryScheduler = new SimpleTaskScheduler(1);
            this.retryScheduler.scheduleFixedRate("failedQueueScanner",
                    this::scanAndRetryFailedUploads, scanIntervalMs, scanIntervalMs);
            logger.info("✅ 失败队列扫描调度器已启动, 扫描间隔: {}ms", scanIntervalMs);
        } catch (Exception e) {
            logger.warn("⚠️ 初始化失败队列扫描调度器失败: {}", e.getMessage());
        }
    }

    public void shutdownRetryScheduler() {
        try {
            if (retryScheduler != null) {
                retryScheduler.shutdown();
                logger.info("⏹️ 失败队列扫描调度器已关闭");
            }
        } catch (Exception e) {
            logger.error("❌ 关闭失败队列扫描调度器异常: {}", e.getMessage(), e);
        }
    }

    public AgentTaskConfig loadRetryConfigFromPersistenceByTaskId(long id) {
        if (configFileManager == null) {
            logger.debug("ConfigFileManager未初始化，跳过加载持久化配置");
            return null;
        }

        try {
            // 直接按taskId加载单个配置，避免加载全部配置文件
            AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(id);
            if (taskConfig == null) {
                logger.debug("本地无taskId={}的持久化配置", id);
            }
            return taskConfig;
        } catch (Exception e) {
            logger.error("❌ 加载持久化重试配置失败: taskId={}, error={}", id, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从本地持久化的JSON配置中加载重试参数
     * 注册所有启用重试的任务配置
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

            int registeredCount = 0;
            for (AgentTaskConfig taskConfig : allConfigs) {
                registerTaskConfig(taskConfig.getTaskId(), taskConfig);
                RetryConfig retryConfig = taskConfig.getRetryConfig();
                if (retryConfig != null && retryConfig.isEnabled()) {
                    registeredCount++;
                }
            }

            if (registeredCount > 0) {
                logger.info("✅ 从持久化配置加载完成: 总配置={}, 启用重试={}", allConfigs.size(), registeredCount);
            } else {
                logger.info("⚠️ 所有持久化配置均未启用重试，使用默认值");
            }

        } catch (Exception e) {
            logger.error("❌ 加载持久化重试配置失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 核心重试方法 ====================
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
                        // IO异常的文件排到最后（使用当前时间+1小时，避免Long.MAX_VALUE溢出风险）
                        return System.currentTimeMillis() + 3_600_000L;
                    }
                }));

        try (Stream<Path> paths = Files.list(failedQueueDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
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
                return System.currentTimeMillis() + 3_600_000L;
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
                // 不在这里刷新文件时间戳：刷新会导致所有文件时间戳变为当前时间，
                // 破坏按修改时间排序的语义，下次扫描无法正确按"最老优先"排序
                failedTasks.add(task);

            } catch (IOException | JsonSyntaxException e) {
                logger.warn("加载失败任务失败，跳过损坏的文件: path={}", path, e);
            }
        }

        logger.info("从失败队列恢复任务数: count={}, 总扫描上限={}, 堆大小={}",
                failedTasks.size(), maxScanCount, oldestFilesHeap.size());
        return failedTasks;
    }

    private void reportNextRetryTime(UploadTask task, AgentTaskConfig config, int currentRetries,
            long nextRetryTimeMs) {
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

    /**
     * 上报最新的retryCount到Proxy，确保Agent和Proxy两端重试计数一致
     * Agent是重试计数的唯一权威来源，Proxy只存储Agent上报的值
     */
    private void reportRetryCountToProxy(UploadTask task, AgentTaskConfig config, int retryCount) {
        if (globalProgressReporter == null || task.getSubtaskId() == null) {
            return;
        }
        try {
            SubTaskEvent event = new SubTaskEvent();
            event.setSubtaskId(task.getSubtaskId());
            event.setTaskId(task.getTaskId());
            event.setStatus("RETRYING");
            event.setRetryCount(retryCount);
            if (config != null) {
                event.setSourceAgentId(config.getSourceAgentId());
                event.setSourceAgentName(config.getSourceAgentName());
            }
            globalProgressReporter.reportRetrying(event);
        } catch (Exception e) {
            logger.warn("上报重试计数失败: transferId={}, error={}", task.getTransferId(), e.getMessage());
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

    private void deleteFailedTaskJsonFile(String transferId) {
        // 不移除listener：重试提交成功后listener需要保留给新的传输过程使用
        // listener只在真正传输成功(AgentUploader.processTask成功路径)或最终失败(markAsFinalFailure)时移除
        if (failedQueueDir == null || transferId == null || transferId.isEmpty()) {
            return;
        }

        // 文件名格式: {STATUS}-upload-{transferId}.json，使用正则精确匹配避免contains误删
        String regex = ".+-upload-" + java.util.regex.Pattern.quote(transferId) + "\\.json$";
        try (Stream<Path> paths = Files.list(failedQueueDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches(regex))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                            logger.info("🗑️ 已删除失败任务JSON文件: path={}", path);
                        } catch (IOException e) {
                            logger.error("❌ 删除失败任务JSON文件失败: path={}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("❌ 扫描失败队列目录删除文件失败: dir={}", failedQueueDir, e);
        }
    }

    /**
     * 扫描上传失败队列并重试（Quartz Job 调用）
     */
    public void scanAndRetryFailedUploads() {
        if (metaStore == null || failedQueueDir == null) {
            logger.debug("⏭️ 上传失败队列未初始化，跳过扫描");
            return;
        }

        try {
            List<UploadTask> failedTasks = recoverFailedTasks(failedQueueDir);

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
                        boolean retried = retrySingleUploadFile(task);
                        if (retried) {
                            retriedCount++;
                        }
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
            logger.info("任务未启用重试或配置不存在，标记为最终失败: taskId={}, transferId={}",
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
                // 解析创建时间失败不应阻止重试，跳过maxDays检查继续后续判断
                logger.warn("⚠️ 解析任务创建时间失败，跳过maxDays检查: transferId={}", task.getTransferId());
            }
        }
        // 检查是否超过最大重试次数（retryCount表示已重试次数，>=maxRetryCount时不再重试）
        final int retryCount = task.getRetryCount();
        if (retryCount >= config.getRetryConfig().getMaxRetryCount()) {
            logger.info("重试次数已达最大: transferId={}, retryCount={}, maxRetryCount={}",
                    task.getTransferId(), retryCount, config.getRetryConfig().getMaxRetryCount());
            return false;
        }
        return true;
    }

    /**
     * 重试单个失败的上传文件
     * 
     * @return true=已提交重试, false=未到重试时间或提交失败
     */
    private boolean retrySingleUploadFile(UploadTask task) {
        try {
            AgentTaskConfig config = findTaskConfigForUpload(task.getTaskId());
            if (config == null || config.getRetryConfig() == null) {
                logger.warn("任务配置或重试配置为空，跳过重试: transferId={}", task.getTransferId());
                return false;
            }
            final long currentTimeMs = System.currentTimeMillis();
            long nextRetryTimeMs = calculateNextRetryTimeMs(task);
            String backoffType = config.getRetryConfig().getBackoffType();
            boolean isReached = currentTimeMs >= nextRetryTimeMs;
            int currentRetries = task.getRetryCount();
            final Integer intervalMin = config.getRetryConfig().getIntervalMin();
            final Integer maxDays = config.getRetryConfig().getMaxDays();
            if (!isReached) {
                reportNextRetryTime(task, config, currentRetries, nextRetryTimeMs);
                logger.debug("该任务未到达重试时间: transferId={}, currentRetries={}, backoffType={}, " +
                        "intervalMin={}min, maxDays={}d, nextRetryTime={}, currentTime={}",
                        task.getTransferId(), currentRetries, backoffType,
                        intervalMin, maxDays, LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(nextRetryTimeMs),
                                java.time.ZoneId.systemDefault()).format(FORMATTER),
                        LocalDateTime.now().format(FORMATTER));
                return false;
            }
            String transferId = task.getTransferId();

            // 先创建listener和提交任务，成功后再增加retryCount
            // 避免提交失败时retryCount已被增加并持久化，导致下次扫描时提前达到最大重试次数
            task.setStatus(UploadTaskStatus.PREPARED);
            task.setExceptionDesc(null);
            task.updateTimestamp();

            UploadListener retryListener = getOrCreateRetryListener(task);

            logger.info("🔄 准备重试: transferId={}, file={}, retryCount={}/{}, status={}",
                    transferId, task.getLocalFilePath(),
                    currentRetries + 1, getMaxRetriesForTask(task),
                    task.getStatus());

            // 直接调用父类AgentUploader的resubmitTask方法
            boolean submitted = resubmitTask(task, retryListener);

            if (submitted) {
                // 提交成功后才增加retryCount并持久化
                task.incrementRetryCount();
                int newRetryCount = task.getRetryCount();
                if (metaStore != null) {
                    metaStore.saveTask(task);
                    logger.info("已更新任务状态: transferId={}, retryCount={}, 状态已重置为PREPARED",
                            transferId, newRetryCount);
                }
                deleteFailedTaskJsonFile(transferId);
                // 上报最新的retryCount到Proxy，确保两端一致
                reportRetryCountToProxy(task, config, newRetryCount);
                logger.info("✅ 重试任务提交成功: transferId={}, retryCount={}", transferId, newRetryCount);
                return true;
            } else {
                // 提交失败，不增加retryCount，任务保持原状态等待下次扫描
                logger.warn("⚠️ 重试任务提交失败: transferId={}, retryCount保持不变={}", transferId, currentRetries);
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ 重试失败文件异常: transferId={}, error={}",
                    task.getTransferId(), e.getMessage());
            return false;
        }
    }

    private UploadListener getOrCreateRetryListener(UploadTask task) {
        String transferId = task.getTransferId();
        // 从父类listenerCache中查找已缓存的listener
        UploadListener cached = getListener(transferId);
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
            } else {
                // 文件不是隐藏状态时，originalAbsolutePath使用absolutePath作为fallback
                // 避免computeTargetPath在preserveDirStructure=true时因originalAbsolutePath为null而NPE
                restoredFile.setOriginalAbsolutePath(filePath);
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

            // 使用父类的putListenerIfAbsent原子操作，避免并发重复创建
            UploadListener prev = putListenerIfAbsent(transferId, retryListener);
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
     * 标记上传任务为最终失败（不可再重试）
     * 原因可能是：达到最大重试次数、超过最大重试天数、重试未启用等
     */
    public void markAsFinalFailure(UploadTask task) {
        try {
            String transferId = task.getTransferId();
            // 从父类listenerCache中移除listener
            removeListener(transferId);

            logger.error("❌ 标记为最终失败(不可再重试): transferId={}, file={}, retryCount={}/{}",
                    transferId, task.getLocalFilePath(),
                    task.getRetryCount(), getMaxRetriesForTask(task));

            task.setStatus(UploadTaskStatus.FAILED);
            task.updateTimestamp();

            String originalDesc = task.getExceptionDesc() != null ? task.getExceptionDesc() : "";
            task.setExceptionDesc(originalDesc + " | FINAL_FAILURE: 不可重试");

            if (metaStore != null) {
                metaStore.saveTask(task);
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
            Path sourcePath = Path.of(localFilePath);

            // 如果文件处于隐藏状态(.transferring)，先恢复为原始文件名
            if (TransferFileStateManager.isTransferringFile(sourcePath)) {
                // 广播模式下，检查是否还有其他目标正在传输或重试该文件
                // 如果有，不能恢复隐藏文件，否则其他目标的重试会因为文件不存在而失败
                boolean hasOtherTransfers = hasInflightUploadForFile(localFilePath);
                if (hasOtherTransfers) {
                    logger.info("其他目标仍在传输该文件，跳过重命名: transferId={}, file={}",
                            task.getTransferId(), localFilePath);
                    return;
                }
                Path restoredPath = TransferFileStateManager.unhideFileSafely(sourcePath);
                if (restoredPath != null) {
                    logger.info("隐藏文件已恢复: {} -> {}", sourcePath.getFileName(), restoredPath.getFileName());
                    sourcePath = restoredPath;
                }
            }

            java.io.File sourceFile = sourcePath.toFile();
            if (!sourceFile.exists()) {
                logger.debug("源文件不存在，跳过重命名: path={}", sourcePath);
                return;
            }
            String failedFilePath = sourcePath + ".failed";
            java.io.File failedFile = new java.io.File(failedFilePath);
            boolean renamed = sourceFile.renameTo(failedFile);
            if (renamed) {
                logger.info("📝 源文件已重命名为.failed: {} -> {}", sourcePath, failedFilePath);
            } else {
                logger.warn("⚠️ 源文件重命名失败: {} -> {}", sourcePath, failedFilePath);
            }
        } catch (Exception e) {
            logger.error("❌ 源文件重命名为.failed异常: path={}, error={}", localFilePath, e.getMessage());
        }
    }

    private void moveToFinalFailureQueue(UploadTask task) {
        if (uploadFinalFailureQueueDir == null || metaStore == null) {
            logger.debug("最终失败队列目录或元数据存储未初始化，跳过移动: transferId={}", task.getTransferId());
            return;
        }
        try {
            if (!Files.exists(uploadFinalFailureQueueDir)) {
                Files.createDirectories(uploadFinalFailureQueueDir);
                logger.info("📁 创建最终失败队列目录: dir={}", uploadFinalFailureQueueDir);
            }
            metaStore.moveToFailedQueue(task.getTransferId(), uploadFinalFailureQueueDir);
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
            AgentTaskConfig config = taskConfigMap.get(task.getTaskId());
            if (config == null) {
                config = loadRetryConfigFromPersistenceByTaskId(task.getTaskId());
                if (config != null) {
                    taskConfigMap.put(task.getTaskId(), config);
                }
            }
            int retryCount = task.getRetryCount();
            String errMsg;
            if (config == null || config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
                errMsg = "已达最大重试次数或者超过最大重试天数: retryCount=" + retryCount;
            } else {
                final int maxRetryCount = config.getRetryConfig().getMaxRetryCount();
                final int maxDays = config.getRetryConfig().getMaxDays();
                errMsg = "已达最大重试次数或者超过最大重试天数: retryCount=" + retryCount + ",maxRetryCount=" + maxRetryCount
                        + ",maxRetryDay=" + maxDays;
            }
            SubTaskEvent event = new SubTaskEvent();
            event.setSubtaskId(task.getSubtaskId());
            event.setTaskId(task.getTaskId());
            event.setStatus("FAILED");
            event.setErrorCode("FINAL_FAILURE");
            event.setErrorMessage(errMsg);
            long completedTime = System.currentTimeMillis();
            event.setCompletedAt(new Date(completedTime));
            if (task.getCreateTime() != null) {
                try {
                    long startedTime = LocalDateTime.parse(task.getCreateTime(),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    event.setDurationMs(completedTime - startedTime);
                } catch (Exception ignored) {
                }
            }
            globalProgressReporter.reportFailed(event);
            logger.info("� 最终失败已上报: transferId={}, subtaskId={}, retryCount={}",
                    task.getTransferId(), task.getSubtaskId(), retryCount);
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
