package com.cq.agent.batch.config;

import java.util.List;

/**
 * 批量传输任务配置（Agent本地持久化格式）
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
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
