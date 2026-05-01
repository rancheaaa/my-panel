package com.cq.agent.batch.report;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class QueueSnapshotReport
{
    private String agentId;
    private Date snapshotTime;
    private QueueInfo sendQueue;
    private QueueInfo retryQueue;
    private ProcessingStats processingStats;
    private List<Long> activeTasks;

    @Data
    public static class QueueInfo
    {
        private int depth;
        private int peakDepth;
        private int capacity;
        private long avgWaitTimeMs;
        private Date nextScheduleTime;
    }

    @Data
    public static class ProcessingStats
    {
        private int completedLast1Min;
        private int failedLast1Min;
        private long avgProcessTimeMs;
    }
}
