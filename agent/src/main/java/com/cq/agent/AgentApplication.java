package com.cq.agent;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.VersionManager;
import com.cq.agent.batch.scheduler.BatchTaskSchedulerManager;
import com.cq.agent.batch.scheduler.FailedQueueScannerJob;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.transfer.RetryManager;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.download.AgentDownloader;
import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.client.upload.UploadTask;
import com.cq.agent.client.download.DownloadTask;
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

/**
 * Main entry point for the Agent application.
 * <p>
 * This agent provides HTTP interfaces for:
 * - Executing system commands (supports both Windows and Linux/Unix)
 * - File operations (FTP-like commands)
 * <p>
 * Configuration is loaded from agent.properties file.
 * The configuration file can be placed in:
 * - classpath (default)
 * - current working directory
 * - config/ subdirectory
 */
public class AgentApplication {

    private static final Logger logger = LoggerFactory.getLogger(AgentApplication.class);

    public static void main(String[] args) {
        logger.info("Starting Agent application...");
        AgentConfig config = new AgentConfig();
        logger.info("Configuration loaded: {}", config);
        logger.info("Agent ID: {}", config.getAgentId());

        // Log OS type
        String osName = System.getProperty("os.name");
        logger.info("Running on {} system", osName);

        CommandExecutor commandExecutor = new CommandExecutor(config);
        FileService fileService = new FileService(config);
        ChunkedTransferService chunkedTransferService = new ChunkedTransferService(config);
        logger.info("ChunkedTransferService initialized successfully");

        // Initialize batch config management
        String batchConfigDir = Path.of(config.getFileBaseDirectory(), "batch-config").toString();
        ConfigFileManager configFileManager = new ConfigFileManager(batchConfigDir);
        VersionManager versionManager = new VersionManager();
        ConfigChangeListener configChangeListener = new ConfigChangeListener(configFileManager, versionManager);

        // Initialize batch task scheduler manager (Quartz)
        BatchTaskSchedulerManager taskSchedulerManager;
        try {
            taskSchedulerManager = new BatchTaskSchedulerManager(configFileManager);
        } catch (Exception e) {
            logger.error("Failed to initialize BatchTaskSchedulerManager: {}", e.getMessage(), e);
            System.exit(1);
            return;
        }

        // Initialize retry manager for batch transfers (10 retries, 30min initial, 2h
        // max delay)
        RetryManager retryManager = new RetryManager(10, 30, 2);

        // Initialize upload/download agents for failed task retry mechanism
        AgentUploader agentUploader = new AgentUploader(config);
        agentUploader.init();

        AgentDownloader agentDownloader = new AgentDownloader(config);
        agentDownloader.init();

        // Initialize RetryManager with full dependencies (for failed queue scanning)
        try {
            TransferMetaStore<UploadTask> uploadMetaStore = new TransferMetaStore<>(
                    Path.of(config.getUploadSendingQueueDir()), UploadTask.class);
            TransferMetaStore<DownloadTask> downloadMetaStore = new TransferMetaStore<>(
                    Path.of(config.getDownloadSendingQueueDir()), DownloadTask.class);

            Path uploadFailQueueDir = Path.of(config.getUploadFailRetryQueueDir());
            Path downloadFailQueueDir = Path.of(config.getDownloadFailRetryQueueDir());

            retryManager.init(
                    uploadMetaStore,
                    downloadMetaStore,
                    uploadFailQueueDir,
                    downloadFailQueueDir,
                    agentUploader,
                    agentDownloader);

            logger.info("✅ RetryManager 初始化完成，失败队列扫描间隔: {}ms", config.getFailedQueueScanIntervalMs());
        } catch (Exception e) {
            logger.warn("⚠️ RetryManager 初始化失败，将使用无持久化模式: {}", e.getMessage());
        }

        // Connect components to task scheduler manager
        taskSchedulerManager.setRetryManager(retryManager);
        taskSchedulerManager.setAgentUploader(agentUploader);

        // Initialize and set FileScanner for batch file scanning (spec.md 4.5)
        FileScanner fileScanner = new FileScanner();
        taskSchedulerManager.setFileScanner(fileScanner);

        // Connect ConfigChangeListener to TaskSchedulerManager for hot updates
        configChangeListener.onCronChange(taskId -> {
            AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(taskId);
            if (taskConfig != null) {
                taskSchedulerManager.updateTask(taskConfig);
                retryManager.registerTaskConfig(taskId, taskConfig); // Register for retry mechanism
            }
        });
        configChangeListener.onAnyChange(ctx -> {
            if ("NEW_TASK".equals(ctx.field)) {
                AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                if (taskConfig != null && "RUNNING".equals(taskConfig.getStatus())) {
                    taskSchedulerManager.startTask(taskConfig);
                    retryManager.registerTaskConfig(ctx.taskId, taskConfig); // Register for retry mechanism
                }
            } else if ("CONFIG_UPDATED".equals(ctx.field)) {
                AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                if (taskConfig != null) {
                    taskSchedulerManager.updateTask(taskConfig);
                    retryManager.registerTaskConfig(ctx.taskId, taskConfig); // Update retry config
                }
            }
        });

        // Start all RUNNING tasks from local config
        taskSchedulerManager.startAllRunningTasks();

        // Register FailedQueueScannerJob for automatic retry of failed tasks
        try {
            JobDetail failedQueueScannerJob = JobBuilder.newJob(FailedQueueScannerJob.class)
                    .withIdentity("failedQueueScanner", "retry-group")
                    .build();

            // 将 RetryManager 放入 JobDataMap
            failedQueueScannerJob.getJobDataMap().put("retryManager", retryManager);

            Trigger failedQueueScannerTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("failedQueueScannerTrigger", "retry-group")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(config.getFailedQueueScanIntervalMs())
                            .repeatForever())
                    .build();

            taskSchedulerManager.scheduleFailedQueueScannerJob(failedQueueScannerJob, failedQueueScannerTrigger);

            logger.info("✅ FailedQueueScannerJob 已注册，扫描间隔: {}ms ({}分钟)",
                    config.getFailedQueueScanIntervalMs(),
                    config.getFailedQueueScanIntervalMs() / 60000);
        } catch (Exception e) {
            logger.warn("⚠️ 注册 FailedQueueScannerJob 失败: {}", e.getMessage());
        }

        HttpServer server = new HttpServer(config, commandExecutor, fileService, chunkedTransferService,
                configFileManager, configChangeListener, taskSchedulerManager);

        // Initialize registry service
        AgentRegistryService registryService = new AgentRegistryService(config);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            registryService.stop();
            taskSchedulerManager.shutdown();
            server.stop();
        }));

        try {
            server.start();
            logger.info("Agent started successfully on port {}", server.getActualPort());

            // Set actual port to registry service
            registryService.setActualPort(server.getActualPort());

            // Start registry service after server is ready
            registryService.start();

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
            server.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Agent interrupted");
        } catch (Exception e) {
            logger.error("Failed to start agent", e);
            System.exit(1);
        }
    }
}