package com.cq.agent.batch.queue;

import lombok.Data;

@Data
public class BatchUploadTask
{
    private Long subtaskId;
    private Long taskId;
    private String filePath;
    private long fileSizeBytes;
    private String targetAgentId;
    private String targetAgentApiUrl;
    private int priority;
    private int retryCount;
    private long enqueueTime;
    private String absolutePath;
    private String targetDir;
    private String postAction;
    private String sourceBaseDir;
    private String backupDir;
    private String backupMode;
    private boolean preserveDirStructure;
}
