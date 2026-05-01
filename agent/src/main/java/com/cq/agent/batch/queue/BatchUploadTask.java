package com.cq.agent.batch.queue;

import lombok.Data;

@Data
public class BatchUploadTask
{
    private Long subtaskId;
    private Long taskId;
    private String filePath;
    private String absolutePath;
    private long fileSizeBytes;
    private String targetAgentId;
    private String targetAgentApiUrl;
    private String targetDir;
    private boolean preserveDirStructure;
    private int priority;
    private int retryCount;
    private long enqueueTime;
}
