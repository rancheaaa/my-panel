package com.cq.agent.di;

import com.cq.agent.client.upload.BatchTaskSchedulerUploader;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public class AgentBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(AgentBootstrap.class);

    private final HttpServer server;
    private final AgentConfig config;
    private final BatchTaskSchedulerUploader batchTaskUploader;
    private final AgentRegistryService registryService;

    public AgentBootstrap(HttpServer server,
                          AgentConfig config,
                          BatchTaskSchedulerUploader batchTaskUploader,
                          AgentRegistryService registryService) {
        this.server = server;
        this.config = config;
        this.batchTaskUploader = batchTaskUploader;
        this.registryService = registryService;
    }

    public void start() throws InterruptedException {
        batchTaskUploader.startAllRunningTasks();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            registryService.stop();
            // BatchTaskSchedulerUploaderDecorator.shutdown() 会依次关闭:
            // Quartz调度器 -> RetryScheduler -> AgentUploader工作线程
            batchTaskUploader.shutdown();
            server.stop();
        }));

        server.start();
        logger.info("Agent started successfully on port {}", server.getActualPort());

        registryService.setActualPort(server.getActualPort());
        registryService.start();

        printApiEndpoints(config);
        server.awaitTermination();
    }

    private void printApiEndpoints(AgentConfig config) {
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
