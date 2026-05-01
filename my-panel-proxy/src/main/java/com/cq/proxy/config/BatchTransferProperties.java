package com.cq.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "batch")
public class BatchTransferProperties
{
    private Monitor monitor = new Monitor();
    private Queue queue = new Queue();
    private Retry retry = new Retry();

    @Data
    public static class Monitor
    {
        private long queueAnalysisIntervalMs = 30000;
        private int snapshotRetentionDays = 7;
    }

    @Data
    public static class Queue
    {
        private int warningDepth = 1000;
        private int criticalDepth = 5000;
        private long maxWaitTimeMs = 300000;
    }

    @Data
    public static class Retry
    {
        private String schedulerCron = "0 */30 * * * ?";
        private int defaultMaxDays = 7;
        private int defaultIntervalMin = 30;
    }
}
