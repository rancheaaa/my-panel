package com.cq.agent.batch.config;

import java.util.List;

/**
 * 批量传输任务配置（Agent本地持久化格式）
 * 符合spec.md设计要求
 */
public class BatchTransferTaskConfig {
    private Long taskId;
    private String taskName;
    private String sourceAgentId;
    private String sourceAgentName;
    private String sourceDir;
    private List<String> targetDirs;
    private List<String> includePatterns;
    private List<String> excludePatterns;
    private List<String> targetAgentIds;
    private List<String> targetAgentNames;
    private String cronExpression;
    private int maxRetries;
    private String status;
    private Long version;
    private String receivedAt;
    private String persistedAt;
    private String startedAt;

    // 嵌套配置对象
    private ScanConfig scanConfig;
    private TransferConfig transferConfig;
    private RetryConfig retryConfig;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getSourceAgentId() { return sourceAgentId; }
    public void setSourceAgentId(String sourceAgentId) { this.sourceAgentId = sourceAgentId; }

    public String getSourceAgentName() { return sourceAgentName; }
    public void setSourceAgentName(String sourceAgentName) { this.sourceAgentName = sourceAgentName; }

    public String getSourceDir() { return sourceDir; }
    public void setSourceDir(String sourceDir) { this.sourceDir = sourceDir; }

    public List<String> getTargetDirs() { return targetDirs; }
    public void setTargetDirs(List<String> targetDirs) { this.targetDirs = targetDirs; }

    public List<String> getIncludePatterns() { return includePatterns; }
    public void setIncludePatterns(List<String> includePatterns) { this.includePatterns = includePatterns; }

    public List<String> getExcludePatterns() { return excludePatterns; }
    public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }

    public List<String> getTargetAgentIds() { return targetAgentIds; }
    public void setTargetAgentIds(List<String> targetAgentIds) { this.targetAgentIds = targetAgentIds; }

    public List<String> getTargetAgentNames() { return targetAgentNames; }
    public void setTargetAgentNames(List<String> targetAgentNames) { this.targetAgentNames = targetAgentNames; }

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

    /**
     * 向后兼容：返回maxRetries
     * 如果retryConfig存在，返回retryConfig.maxRetryCount
     */
    public int getMaxRetries() {
        if (retryConfig != null) {
            return retryConfig.getMaxRetryCount();
        }
        return maxRetries;
    }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getReceivedAt() { return receivedAt; }
    public void setReceivedAt(String receivedAt) { this.receivedAt = receivedAt; }

    public String getPersistedAt() { return persistedAt; }
    public void setPersistedAt(String persistedAt) { this.persistedAt = persistedAt; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public ScanConfig getScanConfig() { return scanConfig; }
    public void setScanConfig(ScanConfig scanConfig) { this.scanConfig = scanConfig; }

    public TransferConfig getTransferConfig() { return transferConfig; }
    public void setTransferConfig(TransferConfig transferConfig) { this.transferConfig = transferConfig; }

    public RetryConfig getRetryConfig() { return retryConfig; }
    public void setRetryConfig(RetryConfig retryConfig) { this.retryConfig = retryConfig; }

    // ==================== 嵌套配置类 ====================

    /**
     * 扫描配置
     */
    public static class ScanConfig {
        private String cronExpression;
        private int maxScanFiles;

        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

        public int getMaxScanFiles() { return maxScanFiles; }
        public void setMaxScanFiles(int maxScanFiles) { this.maxScanFiles = maxScanFiles; }
    }

    /**
     * 传输配置
     */
    public static class TransferConfig {
        private String routingStrategy;
        private int maxBandwidthKbS;
        private boolean preserveDirStructure;
        private String postTransferAction;

        public String getRoutingStrategy() { return routingStrategy; }
        public void setRoutingStrategy(String routingStrategy) { this.routingStrategy = routingStrategy; }

        public int getMaxBandwidthKbS() { return maxBandwidthKbS; }
        public void setMaxBandwidthKbS(int maxBandwidthKbS) { this.maxBandwidthKbS = maxBandwidthKbS; }

        public boolean isPreserveDirStructure() { return preserveDirStructure; }
        public void setPreserveDirStructure(boolean preserveDirStructure) { this.preserveDirStructure = preserveDirStructure; }

        public String getPostTransferAction() { return postTransferAction; }
        public void setPostTransferAction(String postTransferAction) { this.postTransferAction = postTransferAction; }
    }

    /**
     * 重试配置
     */
    public static class RetryConfig {
        private boolean enabled;
        private int maxDays;
        private int intervalMin;
        private int maxRetryCount;
        private String backoffType;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxDays() { return maxDays; }
        public void setMaxDays(int maxDays) { this.maxDays = maxDays; }

        public int getIntervalMin() { return intervalMin; }
        public void setIntervalMin(int intervalMin) { this.intervalMin = intervalMin; }

        public int getMaxRetryCount() { return maxRetryCount; }
        public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }

        public String getBackoffType() { return backoffType; }
        public void setBackoffType(String backoffType) { this.backoffType = backoffType; }
    }
}
