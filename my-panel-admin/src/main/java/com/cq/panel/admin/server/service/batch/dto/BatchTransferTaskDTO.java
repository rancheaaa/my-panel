package com.cq.panel.admin.server.service.batch.dto;

import java.util.List;

/**
 * 批量传输任务数据传输对象
 */
public class BatchTransferTaskDTO {

    private String taskName;
    private String taskDescription;
    private String sourceAgentId;
    private String sourceAgentName;
    private String sourceDir;
    private String targetDirs;
    private List<String> includePatterns;
    private List<String> excludePatterns;
    private String scanCronExpression;
    private Integer maxScanFiles;
    private List<String> targetAgentIds;
    private List<String> targetAgentNames;
    private Integer retryEnabled;
    private Integer retryMaxDays;
    private Integer retryIntervalMin;
    private Integer maxRetryCount;
    private String retryBackoffType;
    private String postTransferAction;
    private String backupDir;
    private String backupMode;
    private Integer preserveDirStructure;
    private String transferMode;
    private String routingStrategy;
    private String routingConfig;
    private String remark;

    public BatchTransferTaskDTO() {
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public String getSourceAgentId() {
        return sourceAgentId;
    }

    public void setSourceAgentId(String sourceAgentId) {
        this.sourceAgentId = sourceAgentId;
    }

    public String getSourceAgentName() {
        return sourceAgentName;
    }

    public void setSourceAgentName(String sourceAgentName) {
        this.sourceAgentName = sourceAgentName;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    public String getTargetDirs() {
        return targetDirs;
    }

    public void setTargetDirs(String targetDirs) {
        this.targetDirs = targetDirs;
    }

    public List<String> getIncludePatterns() {
        return includePatterns;
    }

    public void setIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public String getScanCronExpression() {
        return scanCronExpression;
    }

    public void setScanCronExpression(String scanCronExpression) {
        this.scanCronExpression = scanCronExpression;
    }

    public Integer getMaxScanFiles() {
        return maxScanFiles;
    }

    public void setMaxScanFiles(Integer maxScanFiles) {
        this.maxScanFiles = maxScanFiles;
    }

    public List<String> getTargetAgentIds() {
        return targetAgentIds;
    }

    public void setTargetAgentIds(List<String> targetAgentIds) {
        this.targetAgentIds = targetAgentIds;
    }

    public List<String> getTargetAgentNames() {
        return targetAgentNames;
    }

    public void setTargetAgentNames(List<String> targetAgentNames) {
        this.targetAgentNames = targetAgentNames;
    }

    public Integer getRetryEnabled() {
        return retryEnabled;
    }

    public void setRetryEnabled(Integer retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    public Integer getRetryMaxDays() {
        return retryMaxDays;
    }

    public void setRetryMaxDays(Integer retryMaxDays) {
        this.retryMaxDays = retryMaxDays;
    }

    public Integer getRetryIntervalMin() {
        return retryIntervalMin;
    }

    public void setRetryIntervalMin(Integer retryIntervalMin) {
        this.retryIntervalMin = retryIntervalMin;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public String getRetryBackoffType() {
        return retryBackoffType;
    }

    public void setRetryBackoffType(String retryBackoffType) {
        this.retryBackoffType = retryBackoffType;
    }

    public String getPostTransferAction() {
        return postTransferAction;
    }

    public void setPostTransferAction(String postTransferAction) {
        this.postTransferAction = postTransferAction;
    }

    public String getBackupDir() {
        return backupDir;
    }

    public void setBackupDir(String backupDir) {
        this.backupDir = backupDir;
    }

    public String getBackupMode() {
        return backupMode;
    }

    public void setBackupMode(String backupMode) {
        this.backupMode = backupMode;
    }

    public Integer getPreserveDirStructure() {
        return preserveDirStructure;
    }

    public void setPreserveDirStructure(Integer preserveDirStructure) {
        this.preserveDirStructure = preserveDirStructure;
    }

    public String getTransferMode() {
        return transferMode;
    }

    public void setTransferMode(String transferMode) {
        this.transferMode = transferMode;
    }

    public String getRoutingStrategy() {
        return routingStrategy;
    }

    public void setRoutingStrategy(String routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    public String getRoutingConfig() {
        return routingConfig;
    }

    public void setRoutingConfig(String routingConfig) {
        this.routingConfig = routingConfig;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
