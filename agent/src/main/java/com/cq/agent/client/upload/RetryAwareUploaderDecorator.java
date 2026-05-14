package com.cq.agent.client.upload;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.agent.batch.scheduler.BatchUploadListener;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.config.AgentConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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

    // ===== 状态跟踪 =====
    private final Map<Long, AgentTaskConfig> taskConfigMap = new ConcurrentHashMap<>();

    // ===== 重试执行器（可选）=====
    @Getter
    @Setter
    private Function<Long, Long> retryExecutor;

    private TransferMetaStore<UploadTask> metaStore;

    /**
     * 构造函数（使用默认重试配置）
     * 
     * @param delegate 被装饰的上传服务实例
     */
    public RetryAwareUploaderDecorator(UploadService delegate) {
        this.delegate = delegate;
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
     * 同时从本地持久化的JSON配置中加载重试参数
     */
    public void initWithConfig(AgentConfig config) {
        try {
            TransferMetaStore<UploadTask> uploadMetaStore = new TransferMetaStore<>(
                    Path.of(config.getUploadSendingQueueDir()), UploadTask.class);
            Path uploadFailQueueDir = Path.of(config.getUploadFailRetryQueueDir());

            initRetryDependencies(uploadMetaStore, uploadFailQueueDir);
            String configDir = Path.of(config.getFileBaseDirectory(), "batch-config").toString();
            if (!configDir.isEmpty()) {
                this.configFileManager = new ConfigFileManager(configDir);
                loadRetryConfigFromPersistence();
                logger.info("✅ 配置文件管理器已初始化: configDir={}", configDir);
            }
            this.metaStore = uploadMetaStore;
            logger.info("✅ 重试装饰者初始化完成（使用配置自动创建依赖）");
        } catch (Exception e) {
            logger.warn("⚠️ 重试装饰者初始化失败，将使用无持久化模式: {}", e.getMessage());
        }
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

    /**
     * 初始化重试相关的依赖项（手动传入依赖）
     */
    public void initRetryDependencies(TransferMetaStore<UploadTask> metaStore, Path failQueueDir) {
        this.uploadMetaStore = metaStore;
        this.uploadFailQueueDir = failQueueDir;

        logger.info("✅ 重试依赖初始化完成: failQueueDir={}", failQueueDir);
    }

    // ==================== 核心重试方法（从RetryManager移植）====================
    public List<UploadTask> recoverFailedTasks(Path failedQueueDir) {
        List<UploadTask> failedTasks = new ArrayList<>();

        if (failedQueueDir == null || !Files.exists(failedQueueDir)) {
            logger.debug("失败队列目录不存在: dir={}", failedQueueDir);
            return failedTasks;
        }

        int maxScanCount = DEFAULT_MAX_SCAN_COUNT;
        long currentTimeMs = System.currentTimeMillis();

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

                if (!isRetryTimeReached(task, currentTimeMs)) {
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

    private boolean isRetryTimeReached(UploadTask task, long currentTimeMs) {
        AgentTaskConfig config = taskConfigMap.get(task.getTaskId());
        if (config == null || config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
            logger.debug("任务未启用重试或配置不存在: taskId={}, transferId={}",
                    task.getTaskId(), task.getTransferId());
            return false;
        }

        RetryConfig retryConfig = config.getRetryConfig();
        int currentRetries = task.getRetryCount();
        int intervalMin = retryConfig.getIntervalMin() != null ? retryConfig.getIntervalMin() : 1;

        long nextRetryTimeMs;
        String backoffType = retryConfig.getBackoffType();

        if ("EXPONENTIAL".equalsIgnoreCase(backoffType)) {
            long baseIntervalMs = intervalMin * 60_000L;
            long exponentialDelay = baseIntervalMs * (long) Math.pow(2, currentRetries);
            nextRetryTimeMs = parseUpdateTimeToMs(task.getUpdateTime()) + exponentialDelay;
        } else if ("LINEAR".equalsIgnoreCase(backoffType)) {
            long linearDelay = intervalMin * 60_000L * (currentRetries + 1);
            nextRetryTimeMs = parseUpdateTimeToMs(task.getUpdateTime()) + linearDelay;
        } else {
            long fixedDelay = intervalMin * 60_000L;
            nextRetryTimeMs = parseUpdateTimeToMs(task.getUpdateTime()) + fixedDelay;
        }

        boolean isReached = currentTimeMs >= nextRetryTimeMs;

        logger.debug("重试时间检查: transferId={}, currentRetries={}, backoffType={}, " +
                "intervalMin={}min, nextRetryTime={}, currentTime={}, isReached={}",
                task.getTransferId(), currentRetries, backoffType,
                intervalMin, LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(nextRetryTimeMs),
                        java.time.ZoneId.systemDefault()).format(FORMATTER),
                LocalDateTime.now().format(FORMATTER), isReached);

        return isReached;
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
        int currentRetries = task.getRetryCount();

        AgentTaskConfig config = findTaskConfigForUpload(task.getTaskId());

        if (config == null) {
            logger.warn("⚠️ 未找到任务配置 transferId={}", task.getTransferId());
            return false;
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

            String localFilePath = task.getLocalFilePath();
            String remoteTargetInfo = buildRemoteTargetInfoFromTask(task);

            UploadListener retryListener = createRetryListener(task);

            // 上报重试状态到Proxy
            if (retryListener instanceof BatchUploadListener) {
                ((BatchUploadListener) retryListener).reportRetrying(newRetryCount);
            }

            logger.info("🚀 重新提交上传任务: transferId={}, localPath={}, remoteTarget={}",
                    transferId, localFilePath, remoteTargetInfo);

            boolean submitted = delegate.uploadFile(localFilePath, remoteTargetInfo, retryListener);

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

    private String buildRemoteTargetInfoFromTask(UploadTask task) {
        try {
            String remoteAgentApiUrl = task.getRemoteAgentApiUrl();
            String remoteAgentUsername = task.getRemoteAgentUsername();
            String remoteTargetPath = task.getRemoteTargetPath();

            if (remoteAgentApiUrl == null || remoteAgentApiUrl.isEmpty()) {
                throw new IllegalArgumentException("remoteAgentApiUrl为空");
            }

            java.net.URI uri = new java.net.URI(remoteAgentApiUrl);
            String host = uri.getHost();
            int port = uri.getPort();

            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("无法从remoteAgentApiUrl解析主机名: " + remoteAgentApiUrl);
            }

            String ipPort = port > 0 ? host + ":" + port : host;
            String username = remoteAgentUsername != null ? remoteAgentUsername : "root";
            String destPath = remoteTargetPath != null ? remoteTargetPath : "/tmp";

            String remoteTargetInfo = ipPort + "@" + username + ":" + destPath;

            logger.debug("🎯 构建重试目标路径: {}", remoteTargetInfo);
            return remoteTargetInfo;

        } catch (Exception e) {
            logger.error("⚠️ 构建remoteTargetInfo失败，使用默认值: error={}", e.getMessage());
            throw new IllegalStateException("构建remoteTargetInfo失败", e);
        }
    }

    private UploadListener createRetryListener(UploadTask task) {
        try {
            Long taskId = task.getTaskId();
            String filePath = task.getLocalFilePath();
            String fileName = task.getFileName();
            long fileSize = task.getFileSize() > 0 ? task.getFileSize() : task.getTotalSize();

            ScannedFile restoredFile = new ScannedFile();
            restoredFile.setFileName(fileName);
            restoredFile.setFileSize(fileSize);
            restoredFile.setLastModified(System.currentTimeMillis());
            restoredFile.setAbsolutePath(filePath);

            AgentTaskConfig agentTaskConfig = findTaskConfigForUpload(taskId);
            if (agentTaskConfig == null) {
                agentTaskConfig = loadRetryConfigFromPersistenceByTaskId(taskId);
                if (agentTaskConfig != null) {
                    registerTaskConfig(taskId, agentTaskConfig);
                }
            }

            BatchUploadListener retryListener = new BatchUploadListener(
                    taskId, restoredFile, agentTaskConfig,
                    findTargetAgentForTask(agentTaskConfig, task),
                    getGlobalProgressReporter(),
                    getAgentConfig() != null ? getAgentConfig().getUploadSuccessQueueDir() : null,
                    getAgentConfig() != null ? getAgentConfig().getUploadSendingQueueDir() : null);
            logger.info("✅ 创建重试监听器(恢复模式): transferId={}, file={}",
                    task.getTransferId(), fileName);
            return retryListener;

        } catch (Exception e) {
            logger.warn("⚠️ 创建BatchUploadListener失败: error={}", e.getMessage());
            throw new IllegalStateException("创建BatchUploadListener失败");
        }
    }

    private com.cq.panel.common.dto.batch.TargetAgentInfo findTargetAgentForTask(
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

    /**
     * 记录成功
     */
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

    private AgentTaskConfig findTaskConfigForUpload(long taskId) {
        return this.taskConfigMap.get(taskId);
    }

    private int getMaxRetriesForTask(UploadTask task) {
        AgentTaskConfig config = findTaskConfigForUpload(task.getTaskId());
        if (config != null && config.getRetryConfig() != null) {
            return config.getRetryConfig().getMaxRetryCount();
        }
        return 50; // 默认值
    }

    public boolean isFileAlreadyQueued(String localFilePath, String remoteTargetInfo) {
        if (localFilePath == null || remoteTargetInfo == null) {
            return false;
        }

        String targetAgentKey = extractTargetAgentKey(remoteTargetInfo);

        if (metaStore != null) {
            boolean inSendingQueue = metaStore.existsTaskWithLocalPath(localFilePath, targetAgentKey);
            if (inSendingQueue) {
                logger.debug("📋 文件已在发送队列中: file={}, target={}", localFilePath, targetAgentKey);
                return true;
            }
        }

        boolean inFailedQueue = existsInFailedQueue(localFilePath, targetAgentKey);
        if (inFailedQueue) {
            logger.debug("📋 文件已在失败重试队列中: file={}, target={}", localFilePath, targetAgentKey);
            return true;
        }

        return false;
    }

    private String extractTargetAgentKey(String remoteTargetInfo) {
        try {
            if (remoteTargetInfo.contains("@")) {
                String[] parts = remoteTargetInfo.split("@");
                if (parts.length >= 1) {
                    return parts[0].trim();
                }
            }
            return remoteTargetInfo;
        } catch (Exception e) {
            logger.warn("提取目标Agent标识失败: remoteTargetInfo={}", remoteTargetInfo, e);
            return remoteTargetInfo;
        }
    }

    private boolean existsInFailedQueue(String localFilePath, String targetAgentKey) {
        final String uploadFailRetryQueueDir = getAgentConfig().getUploadFailRetryQueueDir();
        final Path uploadFailRetryQueuePath = Paths.get(uploadFailRetryQueueDir);
        if (!Files.exists(uploadFailRetryQueuePath)) {
            return false;
        }

        try (Stream<Path> paths = Files.list(uploadFailRetryQueuePath)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .anyMatch(path -> {
                        try {
                            String json = Files.readString(path);
                            UploadTask task = gson.fromJson(json, UploadTask.class);

                            if (task == null) {
                                return false;
                            }

                            if (!localFilePath.equals(task.getLocalFilePath())) {
                                return false;
                            }

                            String taskTargetAgent = extractTargetAgentFromTask(task);
                            return targetAgentKey.equals(taskTargetAgent);

                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
    }

    private String extractTargetAgentFromTask(UploadTask task) {
        String remotePath = task.getRemoteAgentApiUrl();

        if (remotePath != null && remotePath.startsWith("http://")) {
            try {
                URI uri = new URI(remotePath);
                return uri.getHost() + ":" + uri.getPort();
            } catch (Exception e) {
                return remotePath;
            }
        }

        return remotePath;
    }
}
