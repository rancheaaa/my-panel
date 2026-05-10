package com.cq.agent.batch.config;

/**
 * 任务元数据信息
 */
public class TaskMetaInfo {
    private Long taskId;
    private String taskName;
    private long lastModified;
    
    public TaskMetaInfo() {}
    
    public TaskMetaInfo(Long taskId, String taskName, long lastModified) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.lastModified = lastModified;
    }
    
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    
    @Override
    public String toString() {
        return "TaskMetaInfo{taskId=" + taskId + ", taskName='" + taskName + 
               "', lastModified=" + lastModified + "}";
    }
}
