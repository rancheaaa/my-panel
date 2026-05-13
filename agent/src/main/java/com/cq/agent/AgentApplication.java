package com.cq.agent;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.VersionManager;
import com.cq.agent.batch.scheduler.BatchTaskSchedulerManager;
import com.cq.agent.batch.scheduler.FileRetryScheduler;
import com.cq.agent.batch.scheduler.FailedQueueScannerJob;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.BatchListenerAwareAgentUploaderDecorator;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.client.download.AgentDownloader;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.executor.CommandExecutor;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.List;

public class AgentApplication {

    private static final Logger logger = LoggerFactory.getLogger(AgentApplication.class);

    public static void main(String[] args) {
        try {
            AgentConfig config = initializeConfig();

            CommandExecutor commandExecutor = new CommandExecutor(config);
            FileService fileService = new FileService(config);
            ChunkedTransferService chunkedTransferService = new ChunkedTransferService(config);

            ConfigFileManager configFileManager = initializeBatchConfigManagement(config);
            BatchTaskSchedulerManager taskSchedulerManager = initializeTaskScheduler(configFileManager);

            RetryAwareUploaderDecorator retryAwareUploader = initializeUploadDownloadServices(config);
            ConfigChangeListener configChangeListener = setupConfigChangeListener(configFileManager, taskSchedulerManager, retryAwareUploader);
            
            connectComponentsToScheduler(config, taskSchedulerManager, retryAwareUploader, configFileManager, configChangeListener);
            taskSchedulerManager.startAllRunningTasks();
            
            // Initialize file-level retry scheduler with independent Quartz instance
            FileRetryScheduler fileRetryScheduler;
            try {
                fileRetryScheduler = new FileRetryScheduler(retryAwareUploader, config.getFailedQueueScanIntervalMs());
                logger.info("FileRetryScheduler initialized (independent Quartz instance)");
            } catch (Exception e) {
                logger.error("Failed to initialize FileRetryScheduler: {}", e.getMessage());
                throw new RuntimeException("Unable to initialize file retry scheduler", e);
            }

            HttpServer server = new HttpServer(config, commandExecutor, fileService, chunkedTransferService,
                    configFileManager, configChangeListener, taskSchedulerManager);
            startServerAndRegister(server, config, taskSchedulerManager, fileRetryScheduler);
        } catch (Exception e) {
            logger.error("Failed to start agent", e);
            System.exit(1);
        }
    }

    private static AgentConfig initializeConfig() {
        logger.info("Starting Agent application...");
        AgentConfig config = new AgentConfig();
        logger.info("Configuration loaded: {}", config);
        logger.info("Agent ID: {}", config.getAgentId());
        
        String osName = System.getProperty("os.name");
        logger.info("Running on {} system", osName);
        
        return config;
    }

    private static ConfigFileManager initializeBatchConfigManagement(AgentConfig config) {
        String batchConfigDir = Path.of(config.getFileBaseDirectory(), "batch-config").toString();
        ConfigFileManager configFileManager = new ConfigFileManager(batchConfigDir);
        VersionManager versionManager = new VersionManager();
        new ConfigChangeListener(configFileManager, versionManager);
        logger.info("Batch configuration management initialized");
        return configFileManager;
    }

    private static BatchTaskSchedulerManager initializeTaskScheduler(ConfigFileManager configFileManager) {
        try {
            BatchTaskSchedulerManager taskSchedulerManager = new BatchTaskSchedulerManager(configFileManager);
            logger.info("BatchTaskSchedulerManager initialized successfully");
            return taskSchedulerManager;
        } catch (Exception e) {
            logger.error("Failed to initialize BatchTaskSchedulerManager: {}", e.getMessage(), e);
            System.exit(1);
            throw new RuntimeException(e);
        }
    }

    private static RetryAwareUploaderDecorator initializeUploadDownloadServices(AgentConfig config) {
        AgentUploader coreUploader = new AgentUploader(config);
        coreUploader.init();

        AgentDownloader coreDownloader = new AgentDownloader(config);
        coreDownloader.init();

        BatchListenerAwareAgentUploaderDecorator listenerAwareUploader =
                new BatchListenerAwareAgentUploaderDecorator(coreUploader);
        RetryAwareUploaderDecorator retryAwareUploader =
                new RetryAwareUploaderDecorator(listenerAwareUploader, 10, 30, 2);

        retryAwareUploader.initWithConfig(config);

        logger.info("Upload/Download services initialized with decorator chain: Core → ListenerAware → RetryAware");

        return retryAwareUploader;
    }

    private static ConfigChangeListener setupConfigChangeListener(ConfigFileManager configFileManager,
                                                                     BatchTaskSchedulerManager taskSchedulerManager,
                                                                     RetryAwareUploaderDecorator retryAwareUploader) {
        VersionManager versionManager = new VersionManager();
        ConfigChangeListener configChangeListener = new ConfigChangeListener(configFileManager, versionManager);

        configChangeListener.onCronChange(taskId -> {
            AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(taskId);
            if (taskConfig != null) {
                taskSchedulerManager.updateTask(taskConfig);
                retryAwareUploader.registerTaskConfig(taskId, taskConfig);
            }
        });

        configChangeListener.onAnyChange(ctx -> {
            if ("NEW_TASK".equals(ctx.field)) {
                AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                if (taskConfig != null && "RUNNING".equals(taskConfig.getStatus())) {
                    taskSchedulerManager.startTask(taskConfig);
                    retryAwareUploader.registerTaskConfig(ctx.taskId, taskConfig);
                }
            } else if ("CONFIG_UPDATED".equals(ctx.field)) {
                AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                if (taskConfig != null) {
                    taskSchedulerManager.updateTask(taskConfig);
                    retryAwareUploader.registerTaskConfig(ctx.taskId, taskConfig);
                }
            }
        });

        return configChangeListener;
    }

    private static void connectComponentsToScheduler(AgentConfig config,
                                                      BatchTaskSchedulerManager taskSchedulerManager,
                                                      RetryAwareUploaderDecorator retryAwareUploader,
                                                      ConfigFileManager configFileManager,
                                                      ConfigChangeListener configChangeListener) {
        taskSchedulerManager.setRetryAwareUploader(retryAwareUploader);
        taskSchedulerManager.setAgentUploader(retryAwareUploader);

        FileScanner fileScanner = new FileScanner();
        taskSchedulerManager.setFileScanner(fileScanner);

        List<String> registryUrls = config.getRegistryServerUrls();
        String registryUrl = (registryUrls != null && !registryUrls.isEmpty()) ? registryUrls.getFirst() : null;

        if (registryUrl != null && !registryUrl.isBlank()) {
            ProgressReporter progressReporter = new ProgressReporter(registryUrl);
            taskSchedulerManager.setProgressReporter(progressReporter);
            retryAwareUploader.setGlobalProgressReporter(progressReporter);
            logger.info("ProgressReporter initialized, registry URL: {}", registryUrl);
        } else {
            logger.warn("Registry URL not configured, progress reporting will be disabled");
        }

        configChangeListener.onCronChange(taskId -> {
            AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(taskId);
            if (taskConfig != null) {
                taskSchedulerManager.updateTask(taskConfig);
                retryAwareUploader.registerTaskConfig(taskId, taskConfig);
            }
        });
    }

    private static void startServerAndRegister(HttpServer server,
                                                AgentConfig config,
                                                BatchTaskSchedulerManager taskSchedulerManager,
                                                FileRetryScheduler fileRetryScheduler) {
        AgentRegistryService registryService = new AgentRegistryService(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            registryService.stop();
            taskSchedulerManager.shutdown();      // Close task scheduler
            fileRetryScheduler.shutdown();        // Close file retry scheduler
            server.stop();
        }));

        try {
            server.start();
            logger.info("Agent started successfully on port {}", server.getActualPort());

            registryService.setActualPort(server.getActualPort());
            registryService.start();

            printApiEndpoints(config);
            server.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Agent interrupted");
        } catch (Exception e) {
            logger.error("Failed to start agent", e);
            System.exit(1);
        }
    }

    private static void printApiEndpoints(AgentConfig config) {
        logger.info("");
        logger.info("Command API Endpoints:");
        logger.info("  GET  /api/health   - Health check");
        logger.info("  POST /api/execute  - Execute command (timeout: {}s)", config.getDefaultTimeoutSeconds());
        logger.info("");
        logger.info("File API Endpoints (FTP-like):");
        logger.info("  GET  /api/file/syst     - System info");
        logger.info("  GET  /api/file/feat     - Supported features");
        logger.info("  GET  /api/file/list     - List directory (LIST)");
        logger.info("  GET  /api/file/nlst     - Name list (NLST)");
        logger.info("  GET  /api/file/retr     - Retrieve file (RETR)");
        logger.info("  POST /api/file/stor     - Store file (STOR)");
        logger.info("  POST /api/file/stou     - Store unique (STOU)");
        logger.info("  POST /api/file/appe     - Append to file (APPE)");
        logger.info("  DELETE /api/file/dele   - Delete file (DELE)");
        logger.info("  POST /api/file/mkd      - Make directory (MKD)");
        logger.info("  DELETE /api/file/rmd    - Remove directory (RMD)");
        logger.info("  GET  /api/file/pwd      - Print working directory (PWD)");
        logger.info("  GET  /api/file/size     - Get file size (SIZE)");
        logger.info("  GET  /api/file/mdtm     - Get modification time (MDTM)");
        logger.info("  POST /api/file/mfmt     - Set modification time (MFMT)");
        logger.info("  POST /api/file/rename   - Rename file (RNFR/RNTO)");
        logger.info("  POST /api/file/copy     - Copy file");
        logger.info("  GET  /api/file/stat     - File status (STAT)");
        logger.info("  GET  /api/file/exists   - Check if exists");
        logger.info("  POST /api/file/chmod    - Change permissions (CHMOD)");
        logger.info("  GET  /api/file/checksum - Calculate checksum (MD5/SHA1/SHA256)");
        logger.info("  GET  /api/file/search   - Search files");
        logger.info("  GET  /api/file/disk     - Disk space info");
        logger.info("");
        logger.info("File base directory: {}", Path.of(config.getFileBaseDirectory()).toAbsolutePath().normalize());
    }
}
