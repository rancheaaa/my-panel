package com.cq.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Properties;
import java.util.UUID;

/**
 * Configuration loader for the agent.
 * Loads configuration from agent.properties file.
 * Supports external config file override.
 */
public class AgentConfig {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfig.class);
    private static final String CONFIG_FILE_NAME = "agent.properties";

    private final Properties properties;

    // Agent identifier
    private String agentId;
    private String agentIp;

    // Server configuration
    private int serverPort;
    private int bossThreads;
    private int workerThreads;

    // Executor configuration
    private int executorThreadPoolSize;
    private long defaultTimeoutSeconds;
    private long maxTimeoutSeconds;

    // Connection configuration
    private int connectionIdleTimeoutSeconds;
    private int maxContentLength;

    // File operation configuration
    private String fileBaseDirectory;
    private boolean allowOutsideBaseDirectory;
    private long maxFileSize;
    private int chunkSize;
    private long uploadSessionTimeoutMinutes;
    private int maxUploadRateKBPerSecond;

    // Upload queue configuration
    private String uploadQueueDbPath;
    private String uploadMapDbPath;

    public AgentConfig() {
        this.properties = new Properties();
        loadConfiguration();
        parseConfiguration();
    }

    private void loadConfiguration() {
        // First, load default configuration from classpath
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (is != null) {
                properties.load(is);
                logger.info("Loaded default configuration from classpath");
            }
        } catch (IOException e) {
            logger.warn("Failed to load default configuration from classpath", e);
        }

        // Then, try to load external configuration to override defaults
        Path externalConfig = Path.of(CONFIG_FILE_NAME);
        if (Files.exists(externalConfig)) {
            try (InputStream is = Files.newInputStream(externalConfig)) {
                properties.load(is);
                logger.info("Loaded external configuration from: {}", externalConfig.toAbsolutePath());
            } catch (IOException e) {
                logger.warn("Failed to load external configuration", e);
            }
        }

        // Also check for config in ./config directory
        Path configDirConfig = Path.of("config", CONFIG_FILE_NAME);
        if (Files.exists(configDirConfig)) {
            try (InputStream is = Files.newInputStream(configDirConfig)) {
                properties.load(is);
                logger.info("Loaded configuration from config directory: {}", configDirConfig.toAbsolutePath());
            } catch (IOException e) {
                logger.warn("Failed to load configuration from config directory", e);
            }
        }
    }

    private void parseConfiguration() {
        // Agent ID
        this.agentId = getStringProperty("agent.id", null);
        if (this.agentId == null || this.agentId.isBlank()) {
            this.agentId = UUID.randomUUID().toString();
            logger.info("agent.id is not configured, generated a random UUID: {}", this.agentId);
        }

        this.agentIp = getStringProperty("agent.ip", null);
        if (this.agentIp == null || this.agentIp.isBlank()) {
            this.agentIp = findFirstNonLoopbackAddress();
            logger.info("agent.ip is {}", this.agentIp);

        }

        // Server configuration
        this.serverPort = getIntProperty("server.port", 8090);
        this.bossThreads = getIntProperty("server.boss.threads", 1);
        this.workerThreads = getIntProperty("server.worker.threads", 0);

        // Executor configuration
        this.executorThreadPoolSize = getIntProperty("executor.thread.pool.size", 10);
        this.defaultTimeoutSeconds = getLongProperty("executor.default.timeout.seconds", 30);
        this.maxTimeoutSeconds = getLongProperty("executor.max.timeout.seconds", 3600);

        // Connection configuration
        this.connectionIdleTimeoutSeconds = getIntProperty("connection.idle.timeout.seconds", 60);
        this.maxContentLength = getIntProperty("connection.max.content.length", 1048576);

        // File operation configuration
        this.fileBaseDirectory = getStringProperty("file.base.directory", System.getProperty("user.home"));
        this.allowOutsideBaseDirectory = getBooleanProperty("file.allow.outside.base", true);
        this.maxFileSize = getLongProperty("file.max.size", 104857600);
        this.chunkSize = getIntProperty("file.chunk.size.bytes", 4 * 1024 * 1024);
        this.uploadSessionTimeoutMinutes = getLongProperty("upload.session.timeout.minutes", 60);
        this.maxUploadRateKBPerSecond = getIntProperty("upload.max.rate.kb.per.second", 0);

        // Upload queue configuration
        this.uploadQueueDbPath = getStringProperty("upload.queue.db.path", "upload_queue_db");
        this.uploadMapDbPath = getStringProperty("upload.map.db.path", "upload_map_db");
        // Validate configuration
        validateConfiguration();
    }

    private void validateConfiguration() {
        if (serverPort < 1 || serverPort > 65535) {
            logger.warn("Invalid server.port: {}, using default 8090", serverPort);
            serverPort = 8090;
        }

        if (executorThreadPoolSize < 1) {
            logger.warn("Invalid executor.thread.pool.size: {}, using default 10", executorThreadPoolSize);
            executorThreadPoolSize = 10;
        }

        if (defaultTimeoutSeconds < 1) {
            logger.warn("Invalid executor.default.timeout.seconds: {}, using default 30", defaultTimeoutSeconds);
            defaultTimeoutSeconds = 30;
        }

        if (maxTimeoutSeconds < defaultTimeoutSeconds) {
            logger.warn("executor.max.timeout.seconds ({}) is less than default ({}), adjusting",
                    maxTimeoutSeconds, defaultTimeoutSeconds);
            maxTimeoutSeconds = defaultTimeoutSeconds;
        }

        if (chunkSize < 1024) {
            logger.warn("Invalid file.chunk.size.bytes: {}, using minimum 1024", chunkSize);
            chunkSize = 1024;
        }

        if (maxFileSize > 0 && chunkSize > maxFileSize) {
            logger.warn("file.chunk.size.bytes ({}) is greater than file.max.size ({}), adjusting", chunkSize, maxFileSize);
            chunkSize = (int) Math.min(maxFileSize, Integer.MAX_VALUE);
        }

        if (uploadSessionTimeoutMinutes < 1) {
            logger.warn("Invalid upload.session.timeout.minutes: {}, using default 60", uploadSessionTimeoutMinutes);
            uploadSessionTimeoutMinutes = 60;
        }

        if (maxContentLength < chunkSize) {
            logger.warn("connection.max.content.length ({}) is less than file.chunk.size.bytes ({}), adjusting", maxContentLength, chunkSize);
            maxContentLength = chunkSize;
        }
    }

    private int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private long getLongProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private String getStringProperty(String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private String findFirstNonLoopbackAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        logger.info("No agent.ip configured, automatically detected IP: {}", inetAddress.getHostAddress());
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            logger.warn("Failed to get network interfaces, defaulting to 0.0.0.0", e);
        }
        logger.info("Could not find a suitable non-loopback IP, defaulting to 0.0.0.0");
        return "0.0.0.0";
    }

    // Getters
    public String getAgentId() {
        return agentId;
    }

    public String getAgentIp() {
        return agentIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public int getExecutorThreadPoolSize() {
        return executorThreadPoolSize;
    }

    public long getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public long getMaxTimeoutSeconds() {
        return maxTimeoutSeconds;
    }

    public int getConnectionIdleTimeoutSeconds() {
        return connectionIdleTimeoutSeconds;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public String getFileBaseDirectory() {
        return fileBaseDirectory;
    }

    public boolean isAllowOutsideBaseDirectory() {
        return allowOutsideBaseDirectory;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public long getUploadSessionTimeoutMinutes() {
        return uploadSessionTimeoutMinutes;
    }

    public int getMaxUploadRateKBPerSecond() {
        return maxUploadRateKBPerSecond;
    }

    public String getUploadQueueDbPath() {
        return uploadQueueDbPath;
    }

    public String getUploadMapDbPath() {
        return uploadMapDbPath;
    }

    public void setUploadMapDbPath(String uploadMapDbPath) {
        this.uploadMapDbPath = uploadMapDbPath;
    }

    @Override
    public String toString() {
        return "AgentConfig{" +
                "properties=" + properties +
                ", agentId='" + agentId + '\'' +
                ", agentIp='" + agentIp + '\'' +
                ", serverPort=" + serverPort +
                ", bossThreads=" + bossThreads +
                ", workerThreads=" + workerThreads +
                ", executorThreadPoolSize=" + executorThreadPoolSize +
                ", defaultTimeoutSeconds=" + defaultTimeoutSeconds +
                ", maxTimeoutSeconds=" + maxTimeoutSeconds +
                ", connectionIdleTimeoutSeconds=" + connectionIdleTimeoutSeconds +
                ", maxContentLength=" + maxContentLength +
                ", fileBaseDirectory='" + fileBaseDirectory + '\'' +
                ", allowOutsideBaseDirectory=" + allowOutsideBaseDirectory +
                ", maxFileSize=" + maxFileSize +
                ", chunkSize=" + chunkSize +
                ", uploadSessionTimeoutMinutes=" + uploadSessionTimeoutMinutes +
                ", maxUploadRateKBPerSecond=" + maxUploadRateKBPerSecond +
                ", uploadQueueDbPath='" + uploadQueueDbPath + '\'' +
                ", uploadMapDbPath='" + uploadMapDbPath + '\'' +
                '}';
    }
}