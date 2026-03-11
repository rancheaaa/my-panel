 package com.cq.agent;

 import com.cq.agent.config.AgentConfig;
 import com.cq.agent.executor.CommandExecutor;
 import com.cq.agent.server.HttpServer;
 import com.cq.agent.service.ChunkedTransferService;
 import com.cq.agent.service.FileService;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

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

        // Load configuration
        AgentConfig config = new AgentConfig();
        logger.info("Configuration loaded: {}", config);

        // Log OS type
        String osName = System.getProperty("os.name");
        logger.info("Running on {} system", osName);

        CommandExecutor commandExecutor = new CommandExecutor(config);
        FileService fileService = new FileService(config);
        ChunkedTransferService chunkedTransferService = new ChunkedTransferService(config);

        HttpServer server = new HttpServer(config, commandExecutor, fileService, chunkedTransferService);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            server.stop();
        }));

        try {
            server.start();
            logger.info("Agent started successfully");
            logger.info("Server port: {}", config.getServerPort());
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
            logger.info("File base directory: {}", config.getFileBaseDirectory());
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
