package com.cq.agent.batch.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务元数据信息（tasks.meta.json结构）
 * 符合spec.md设计要求
 */
public class TaskMetaInfo {

    private List<TaskMeta> tasks = new ArrayList<>();

    public List<TaskMeta> getTasks() { return tasks; }
    public void setTasks(List<TaskMeta> tasks) { this.tasks = tasks; }

    /**
     * 单个任务的元数据
     */
    public static class TaskMeta {
        private Long taskId;
        private String taskName;
        private String status;
        private Long version;
        private String receivedAt;
        private String persistedAt;
        private long lastModified;

        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }

        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Long getVersion() { return version; }
        public void setVersion(Long version) { this.version = version; }

        public String getReceivedAt() { return receivedAt; }
        public void setReceivedAt(String receivedAt) { this.receivedAt = receivedAt; }

        public String getPersistedAt() { return persistedAt; }
        public void setPersistedAt(String persistedAt) { this.persistedAt = persistedAt; }

        public long getLastModified() { return lastModified; }
        public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    }
}
