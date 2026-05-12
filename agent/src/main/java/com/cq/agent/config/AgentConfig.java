package com.cq.agent.config;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Configuration loader for the agent.
 * Loads configuration from agent.properties file.
 * Supports external config file override.
 */
@Data
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
    private int portProbeMaxSteps;

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

    // Transfer metadata directory (for resumable uploads/downloads)
    private String transfersMetaDir;

    // Registry configuration
    private List<String> registryServerUrls;
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

    @SuppressWarnings("all")
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
        this.serverPort = getIntProperty("server.port", 7777);
        this.bossThreads = getIntProperty("server.boss.threads", 1);
        this.workerThreads = getIntProperty("server.worker.threads", Runtime.getRuntime().availableProcessors() * 2);
        this.portProbeMaxSteps = getIntProperty("server.port.probe.max.steps", 10);

        // Registry server configuration
        String registryServerUrlStr = getStringProperty("registry.server.url", "http://localhost:9876,http://localhost:9876");
        if (registryServerUrlStr != null && !registryServerUrlStr.isEmpty()) {
            String[] urls = registryServerUrlStr.split(",");
            List<String> urlList = new ArrayList<>();
            for (String url : urls) {
                String trimmedUrl = url.trim();
                if (!trimmedUrl.isEmpty()) {
                    urlList.add(trimmedUrl);
                }
            }
            this.registryServerUrls = urlList;
        } else {
            this.registryServerUrls = Collections.emptyList();
        }
        this.nodeName = getStringProperty("registry.node.name", null);
        this.osType = getStringProperty("registry.os.type", null);
        this.appId = getStringProperty("registry.app.id", null);
        this.remark = getStringProperty("registry.remark", null);
        this.heartbeatIntervalSeconds = getIntProperty("registry.heartbeat.interval.seconds", 30);
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
        this.fileBaseDirectory = getStringProperty("file.base.directory", "/tmp/my-panel/admin/data/");
        this.allowOutsideBaseDirectory = getBooleanProperty("file.allow.outside.base", true);
        this.maxFileSize = getLongProperty("file.max.size", 107374182400L);
        this.chunkSize = getIntProperty("file.chunk.size.bytes", 5 * 1024 * 1024);
        this.uploadSessionTimeoutMinutes = getLongProperty("upload.session.timeout.minutes", 60);
        this.maxUploadRateKBPerSecond = getIntProperty("upload.max.rate.kb.per.second", 0);

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

        // Transfer metadata directory
        this.transfersMetaDir = getStringProperty("transfer.meta.dir", "./data/transfers");

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
        String value = getConfigValue(key);
        if (value != null) {
            return parseIntegerValue(key, value, defaultValue);
        }
        
        logger.debug("Using default value for {}: {}", key, defaultValue);
        return defaultValue;
    }
    
    private int parseIntegerValue(String key, String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private long getLongProperty(String key, long defaultValue) {
        String value = getConfigValue(key);
        if (value != null) {
            return parseLongValue(key, value, defaultValue);
        }
        
        logger.debug("Using default Long value for {}: {}", key, defaultValue);
        return defaultValue;
    }
    
    private long parseLongValue(String key, String value, long defaultValue) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private String getStringProperty(String key, String defaultValue) {
        String value = getConfigValue(key);
        if (value != null) {
            return value.trim();
        }
        
        logger.debug("Using default String value for {}: {}", key, defaultValue);
        return defaultValue;
    }

    @SuppressWarnings("all")
    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getConfigValue(key);
        if (value != null) {
            return parseBooleanValue(key, value, defaultValue);
        }
        
        logger.debug("Using default Boolean value for {}: {}", key, defaultValue);
        return defaultValue;
    }
    
    private boolean parseBooleanValue(String key, String value, boolean defaultValue) {
        String trimmedValue = value.trim().toLowerCase();
        if ("true".equals(trimmedValue) || "1".equals(trimmedValue) || "yes".equals(trimmedValue) || "on".equals(trimmedValue)) {
            return true;
        } else if ("false".equals(trimmedValue) || "0".equals(trimmedValue) || "no".equals(trimmedValue) || "off".equals(trimmedValue)) {
            return false;
        } else {
            logger.warn("Invalid boolean value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 公共配置获取方法 - 按照优先级顺序获取配置值
     * 优先级: 系统属性 -> 环境变量 -> 配置文件
     * 
     * @param key 配置键
     * @return 配置值，如果所有来源都没有找到则返回null
     */
    private String getConfigValue(String key) {
        String value;
        
        // 1. 首先检查系统属性
        value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            logger.debug("Using system property for {}: {}", key, value);
            return value;
        }
        
        // 2. 检查环境变量（将点转换为下划线，并转为大写）
        String envKey = key.replace('.', '_').toUpperCase();
        value = System.getenv(envKey);
        if (value != null && !value.trim().isEmpty()) {
            logger.debug("Using environment variable for {} ({}): {}", key, envKey, value);
            return value;
        }
        
        // 3. 检查配置文件
        value = properties.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            logger.debug("Using config file for {}: {}", key, value);
            return value;
        }
        
        // 所有来源都没有找到配置值
        return null;
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
}