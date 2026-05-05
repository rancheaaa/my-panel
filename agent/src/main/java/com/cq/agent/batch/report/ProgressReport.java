package com.cq.agent.batch.report;

import lombok.Data;
import java.util.Date;

@Data
public class ProgressReport
{
    private String reportId;
    private Long taskId;
    private Long subtaskId;
    private String targetAgentId;
    private String filePath;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String transferId;
    private ProgressDetail progress;
    private PerformanceInfo performance;
    private Date timestamp;

    @Data
    public static class ProgressDetail
    {
        private int transferredChunks;
        private int totalChunks;
        private long transferredBytes;
        private long totalBytes;
    }

    @Data
    public static class PerformanceInfo
    {
        private long currentSpeedBytesPerSec;
        private long avgSpeedBytesPerSec;
    }
}
