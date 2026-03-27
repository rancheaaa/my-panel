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
    private String agentApiUrl;

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
    private int maxDownloadRateKBPerSecond;

    // Upload queue configuration
    private String uploadQueueDbPath;
    private String uploadMapDbPath;
    private String uploadSessionsDbPath;

    // Download queue configuration
    private String downloadQueueDbPath;
    private String downloadMapDbPath;

    // Upload client configuration
    private int uploadConcurrentUploads;
    private int uploadMaxQueueDepth;
    private int uploadWorkerCount;
    private int uploadMaxRetries;
    private long uploadRetryDelayMs;
    private int uploadConnectTimeoutSeconds;
    private int uploadRequestTimeoutSeconds;

    // Download client configuration
    private int downloadConcurrentDownloads;
    private int downloadMaxQueueDepth;
    private int downloadWorkerCount;
    private int downloadMaxRetries;
    private long downloadRetryDelayMs;
    private int downloadConnectTimeoutSeconds;
    private int downloadRequestTimeoutSeconds;

    // Registry configuration
    private String registryServerUrl;
    private String nodeName;
    private String osType;
    private String appId;
    private String remark;
    private int heartbeatIntervalSeconds;
    private boolean autoRegister;
    private boolean autoHeartbeat;

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

        this.agentApiUrl = getStringProperty("agent.api.url", null);
        if (this.agentApiUrl == null || this.agentApiUrl.isBlank()) {
            this.agentApiUrl = "http://" + this.agentIp + ":" + this.serverPort + "/";
            logger.info("agent.api.url is not configured, using default: {}", this.agentApiUrl);
        } else {
            if (!this.agentApiUrl.endsWith("/")) {
                this.agentApiUrl = this.agentApiUrl + "/";
            }
            logger.info("agent.api.url is {}", this.agentApiUrl);
        }

        // Server configuration
        this.serverPort = getIntProperty("server.port", 7777);
        this.bossThreads = getIntProperty("server.boss.threads", 1);
        this.workerThreads = getIntProperty("server.worker.threads", Runtime.getRuntime().availableProcessors() * 2);

        // Registry server configuration
        this.registryServerUrl = getStringProperty("registry.server.url", "http://localhost:8888");
        this.nodeName = getStringProperty("registry.node.name", null);
        this.osType = getStringProperty("registry.os.type", null);
        this.appId = getStringProperty("registry.app.id", null);
        this.remark = getStringProperty("registry.remark", null);
        this.heartbeatIntervalSeconds = getIntProperty("registry.heartbeat.interval.seconds", 300);
        this.autoRegister = getBooleanProperty("registry.auto.register", true);
        this.autoHeartbeat = getBooleanProperty("registry.auto.heartbeat", true);

        // Executor configuration
        this.executorThreadPoolSize = getIntProperty("executor.thread.pool.size", 10);
        this.defaultTimeoutSeconds = getLongProperty("executor.default.timeout.seconds", 30);
        this.maxTimeoutSeconds = getLongProperty("executor.max.timeout.seconds", 3600);

        // Connection configuration
        this.connectionIdleTimeoutSeconds = getIntProperty("connection.idle.timeout.seconds", 60);
        this.maxContentLength = getIntProperty("connection.max.content.length", 8 * 1024 * 1024);

        // File operation configuration
        this.fileBaseDirectory = getStringProperty("file.base.directory", "/tmp/my-panel/agent/agent_data");
        this.allowOutsideBaseDirectory = getBooleanProperty("file.allow.outside.base", true);
        this.maxFileSize = getLongProperty("file.max.size", 107374182400L);
        this.chunkSize = getIntProperty("file.chunk.size.bytes", 5 * 1024 * 1024);
        this.uploadSessionTimeoutMinutes = getLongProperty("upload.session.timeout.minutes", 60);
        this.maxUploadRateKBPerSecond = getIntProperty("upload.max.rate.kb.per.second", 0);

        // Upload queue configuration
        this.uploadQueueDbPath = getStringProperty("upload.queue.db.path", "upload_queue_db");
        this.uploadMapDbPath = getStringProperty("upload.map.db.path", "upload_map_db");
        this.uploadSessionsDbPath = getStringProperty("upload.sessions.db.path", "upload_sessions_db");

        // Download queue configuration
        this.downloadQueueDbPath = getStringProperty("download.queue.db.path", "download_queue_db");
        this.downloadMapDbPath = getStringProperty("download.map.db.path", "download_map_db");

        this.maxDownloadRateKBPerSecond = getIntProperty("download.max.rate.kb.per.second", 0);

        // Upload client configuration
        this.uploadConcurrentUploads = getIntProperty("upload.concurrent.uploads", 4);
        this.uploadMaxQueueDepth = getIntProperty("upload.max.queue.depth", 500);
        this.uploadWorkerCount = getIntProperty("upload.worker.count", 4);
        this.uploadMaxRetries = getIntProperty("upload.max.retries", 3);
        this.uploadRetryDelayMs = getLongProperty("upload.retry.delay.ms", 2000);
        this.uploadConnectTimeoutSeconds = getIntProperty("upload.connect.timeout.seconds", 10);
        this.uploadRequestTimeoutSeconds = getIntProperty("upload.request.timeout.seconds", 60);

        // Download client configuration
        this.downloadConcurrentDownloads = getIntProperty("download.concurrent.downloads", 4);
        this.downloadMaxQueueDepth = getIntProperty("download.max.queue.depth", 500);
        this.downloadWorkerCount = getIntProperty("download.worker.count", 4);
        this.downloadMaxRetries = getIntProperty("download.max.retries", 3);
        this.downloadRetryDelayMs = getLongProperty("download.retry.delay.ms", 2000);
        this.downloadConnectTimeoutSeconds = getIntProperty("download.connect.timeout.seconds", 10);
        this.downloadRequestTimeoutSeconds = getIntProperty("download.request.timeout.seconds", 60);

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

    public String getAgentApiUrl() {
        return agentApiUrl;
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

    public String getUploadSessionsDbPath() {
        return uploadSessionsDbPath;
    }

    public String getDownloadQueueDbPath() {
        return downloadQueueDbPath;
    }

    public String getDownloadMapDbPath() {
        return downloadMapDbPath;
    }

    public int getMaxDownloadRateKBPerSecond() {
        return maxDownloadRateKBPerSecond;
    }

    public int getUploadConcurrentUploads() {
        return uploadConcurrentUploads;
    }

    public int getUploadMaxQueueDepth() {
        return uploadMaxQueueDepth;
    }

    public int getUploadWorkerCount() {
        return uploadWorkerCount;
    }

    public int getUploadMaxRetries() {
        return uploadMaxRetries;
    }

    public long getUploadRetryDelayMs() {
        return uploadRetryDelayMs;
    }

    public int getUploadConnectTimeoutSeconds() {
        return uploadConnectTimeoutSeconds;
    }

    public int getUploadRequestTimeoutSeconds() {
        return uploadRequestTimeoutSeconds;
    }

    public int getDownloadConcurrentDownloads() {
        return downloadConcurrentDownloads;
    }

    public int getDownloadMaxQueueDepth() {
        return downloadMaxQueueDepth;
    }

    public int getDownloadWorkerCount() {
        return downloadWorkerCount;
    }

    public int getDownloadMaxRetries() {
        return downloadMaxRetries;
    }

    public long getDownloadRetryDelayMs() {
        return downloadRetryDelayMs;
    }

    public int getDownloadConnectTimeoutSeconds() {
        return downloadConnectTimeoutSeconds;
    }

    public int getDownloadRequestTimeoutSeconds() {
        return downloadRequestTimeoutSeconds;
    }

    public String getRegistryServerUrl() {
        return registryServerUrl;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getOsType() {
        return osType;
    }

    public String getAppId() {
        return appId;
    }

    public String getRemark() {
        return remark;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public boolean isAutoHeartbeat() {
        return autoHeartbeat;
    }

    public void setAgentApiUrl(String agentApiUrl) {
        this.agentApiUrl = agentApiUrl;
    }

    public void setUploadMapDbPath(String uploadMapDbPath) {
        this.uploadMapDbPath = uploadMapDbPath;
    }

    public void setUploadQueueDbPath(String uploadQueueDbPath) {
        this.uploadQueueDbPath = uploadQueueDbPath;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Override
    public String toString() {
        return "AgentConfig{" +
                "properties=" + properties +
                ", agentId='" + agentId + '\'' +
                ", agentIp='" + agentIp + '\'' +
                ", agentApiUrl='" + agentApiUrl + '\'' +
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
                ", maxDownloadRateKBPerSecond=" + maxDownloadRateKBPerSecond +
                ", uploadQueueDbPath='" + uploadQueueDbPath + '\'' +
                ", uploadMapDbPath='" + uploadMapDbPath + '\'' +
                ", uploadSessionsDbPath='" + uploadSessionsDbPath + '\'' +
                ", downloadQueueDbPath='" + downloadQueueDbPath + '\'' +
                ", downloadMapDbPath='" + downloadMapDbPath + '\'' +
                ", uploadConcurrentUploads=" + uploadConcurrentUploads +
                ", uploadMaxQueueDepth=" + uploadMaxQueueDepth +
                ", uploadWorkerCount=" + uploadWorkerCount +
                ", uploadMaxRetries=" + uploadMaxRetries +
                ", uploadRetryDelayMs=" + uploadRetryDelayMs +
                ", uploadConnectTimeoutSeconds=" + uploadConnectTimeoutSeconds +
                ", uploadRequestTimeoutSeconds=" + uploadRequestTimeoutSeconds +
                ", downloadConcurrentDownloads=" + downloadConcurrentDownloads +
                ", downloadMaxQueueDepth=" + downloadMaxQueueDepth +
                ", downloadWorkerCount=" + downloadWorkerCount +
                ", downloadMaxRetries=" + downloadMaxRetries +
                ", downloadRetryDelayMs=" + downloadRetryDelayMs +
                ", downloadConnectTimeoutSeconds=" + downloadConnectTimeoutSeconds +
                ", downloadRequestTimeoutSeconds=" + downloadRequestTimeoutSeconds +
                '}';
    }
}