package com.cq.agent.di;

import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.VersionManager;
import com.cq.agent.batch.report.FallbackPersistenceService;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.BatchTaskSchedulerUploader;
import com.cq.agent.client.upload.RetryAwareUploader;
import com.cq.agent.client.upload.UploadService;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.executor.CommandExecutor;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class AppContext {

    private static final Logger logger = LoggerFactory.getLogger(AppContext.class);

    private static volatile AppContext instance;

    private final AgentConfig agentConfig;
    private final CommandExecutor commandExecutor;
    private final FileService fileService;
    private final ChunkedTransferService chunkedTransferService;
    private final VersionManager versionManager;
    private final FileScanner fileScanner;
    private final ConfigFileManager configFileManager;
    private final FileBatchCompletionTracker fileBatchCompletionTracker;
    private final ProgressReporter progressReporter;
    private final FallbackPersistenceService fallbackPersistenceService;
    private final BatchTaskSchedulerUploader batchTaskSchedulerUploader;
    private final RetryAwareUploader retryAwareUploader;
    private final ConfigChangeListener configChangeListener;
    private final HttpServer httpServer;
    private final AgentRegistryService agentRegistryService;
    private final AgentBootstrap agentBootstrap;

    private volatile boolean initialized;

    private AppContext() {
        this.agentConfig = new AgentConfig();
        this.commandExecutor = new CommandExecutor(agentConfig);
        this.fileService = new FileService(agentConfig);
        this.chunkedTransferService = new ChunkedTransferService(agentConfig);
        this.versionManager = new VersionManager();
        this.fileScanner = new FileScanner();

        String batchConfigDir = Path.of(agentConfig.getFileBaseDirectory(), "batch-config").toString();
        this.configFileManager = new ConfigFileManager(batchConfigDir);

        this.fileBatchCompletionTracker = new FileBatchCompletionTracker(agentConfig.getFilebatchPendingDir());
        this.progressReporter = new ProgressReporter(agentConfig);

        this.fallbackPersistenceService = new FallbackPersistenceService(
                agentConfig.getProgressFallbackDir(), progressReporter, 30, 10);
        progressReporter.setFallbackPersistenceService(fallbackPersistenceService);

        this.batchTaskSchedulerUploader = createBatchTaskSchedulerUploader();

        Path uploadFinalFailureQueueDir = Path.of(agentConfig.getUploadFinalFailureQueueDir());
        this.retryAwareUploader = new RetryAwareUploader(
                agentConfig, fileBatchCompletionTracker, progressReporter,
                configFileManager, uploadFinalFailureQueueDir);

        this.configChangeListener = createConfigChangeListener();
        this.httpServer = new HttpServer(agentConfig, commandExecutor, fileService,
                chunkedTransferService, configFileManager, configChangeListener, batchTaskSchedulerUploader);
        this.agentRegistryService = new AgentRegistryService(agentConfig);
        this.agentBootstrap = new AgentBootstrap(httpServer, agentConfig,
                batchTaskSchedulerUploader, agentRegistryService);
    }

    public static AppContext getInstance() {
        if (instance == null) {
            synchronized (AppContext.class) {
                if (instance == null) {
                    instance = new AppContext();
                }
            }
        }
        return instance;
    }

    static void resetForTest() {
        synchronized (AppContext.class) {
            instance = null;
        }
    }

    public void init() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            doInit();
            initialized = true;
        }
    }

    private void doInit() {
        fallbackPersistenceService.startAutoRetry();
        logger.info("FallbackPersistenceService integrated and auto-retry started: storageDir={}",
                agentConfig.getProgressFallbackDir());

        batchTaskSchedulerUploader.init();
        logger.info("BatchTaskSchedulerUploaderDecorator initialized (inheritance mode)");

        retryAwareUploader.initRetry();
        logger.info("RetryAwareUploaderDecorator initialized (inheritance mode)");

        logger.info("AppContext initialization completed");
    }

    private BatchTaskSchedulerUploader createBatchTaskSchedulerUploader() {
        try {
            return new BatchTaskSchedulerUploader(
                    agentConfig, configFileManager, fileBatchCompletionTracker,
                    progressReporter, fileScanner);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create BatchTaskSchedulerUploader", e);
        }
    }

    private ConfigChangeListener createConfigChangeListener() {
        ConfigChangeListener listener = new ConfigChangeListener(
                configFileManager, versionManager,
                taskId -> {
                    AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(taskId);
                    if (taskConfig != null) {
                        batchTaskSchedulerUploader.updateTask(taskConfig);
                        retryAwareUploader.registerTaskConfig(taskId, taskConfig);
                    }
                },
                ctx -> {
                    if ("NEW_TASK".equals(ctx.field)) {
                        AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                        if (taskConfig != null && "RUNNING".equals(taskConfig.getStatus())) {
                            batchTaskSchedulerUploader.startTask(taskConfig);
                            retryAwareUploader.registerTaskConfig(ctx.taskId, taskConfig);
                        }
                    } else if ("CONFIG_UPDATED".equals(ctx.field)) {
                        AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                        if (taskConfig != null) {
                            batchTaskSchedulerUploader.updateTask(taskConfig);
                            retryAwareUploader.registerTaskConfig(ctx.taskId, taskConfig);
                        }
                    }
                });
        logger.info("ConfigChangeListener initialized with callbacks registered");
        return listener;
    }

    public AgentConfig getAgentConfig() {
        return agentConfig;
    }

    public CommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    public FileService getFileService() {
        return fileService;
    }

    public ChunkedTransferService getChunkedTransferService() {
        return chunkedTransferService;
    }

    public VersionManager getVersionManager() {
        return versionManager;
    }

    public FileScanner getFileScanner() {
        return fileScanner;
    }

    public ConfigFileManager getConfigFileManager() {
        return configFileManager;
    }

    public FileBatchCompletionTracker getFileBatchCompletionTracker() {
        return fileBatchCompletionTracker;
    }

    public ProgressReporter getProgressReporter() {
        return progressReporter;
    }

    public FallbackPersistenceService getFallbackPersistenceService() {
        return fallbackPersistenceService;
    }

    public BatchTaskSchedulerUploader getBatchTaskSchedulerUploader() {
        return batchTaskSchedulerUploader;
    }

    public RetryAwareUploader getRetryAwareUploader() {
        return retryAwareUploader;
    }

    public UploadService getUploadService() {
        return retryAwareUploader;
    }

    public ConfigChangeListener getConfigChangeListener() {
        return configChangeListener;
    }

    public HttpServer getHttpServer() {
        return httpServer;
    }

    public AgentRegistryService getAgentRegistryService() {
        return agentRegistryService;
    }

    public AgentBootstrap getAgentBootstrap() {
        return agentBootstrap;
    }
}
