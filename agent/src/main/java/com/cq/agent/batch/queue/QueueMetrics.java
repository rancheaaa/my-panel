package com.cq.agent.batch.queue;

import lombok.Data;
import java.util.Date;

@Data
public class QueueMetrics
{
    private int sendQueueDepth;
    private int sendQueuePeakDepth;
    private int sendQueueCapacity;
    private double sendQueueUtilizationPct;
    private long sendQueueAvgWaitMs;
    private int retryQueueDepth;
    private int retryQueuePeakDepth;
    private int retryQueueCapacity;
    private double processingRatePerSec;
    private double successRatePct;
    private Date retryNextScheduleTime;
}
