package com.cq.agent.batch.queue;

import com.cq.agent.batch.report.ProxyReportClient;
import com.cq.agent.batch.report.QueueSnapshotReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QueueMetricsCollector
{
    private static final Logger logger = LoggerFactory.getLogger(QueueMetricsCollector.class);
    private static final long DEFAULT_COLLECT_INTERVAL_SEC = 30;
    private static final int WARNING_DEPTH_THRESHOLD = 1000;
    private static final int CRITICAL_DEPTH_THRESHOLD = 5000;
    private static final long MAX_WAIT_MS = 300_000L;

    private final String agentId;
    private final BatchTransferQueueManager queueManager;
    private final ProxyReportClient proxyReportClient;
    private final ScheduledExecutorService scheduler;
    private final long collectIntervalSec;

    private volatile String congestionLevel = "NORMAL";
    private volatile boolean congested = false;

    public QueueMetricsCollector(String agentId,
                                  BatchTransferQueueManager queueManager,
                                  ProxyReportClient proxyReportClient)
    {
        this(agentId, queueManager, proxyReportClient, DEFAULT_COLLECT_INTERVAL_SEC);
    }

    public QueueMetricsCollector(String agentId,
                                  BatchTransferQueueManager queueManager,
                                  ProxyReportClient proxyReportClient,
                                  long collectIntervalSec)
    {
        this.agentId = agentId;
        this.queueManager = queueManager;
        this.proxyReportClient = proxyReportClient;
        this.collectIntervalSec = collectIntervalSec;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "queue-metrics-collector");
            t.setDaemon(true);
            return t;
        });
    }

    public void start()
    {
        scheduler.scheduleAtFixedRate(this::collectAndReport, collectIntervalSec, collectIntervalSec, TimeUnit.SECONDS);
        logger.info("QueueMetricsCollector started, agentId={}, interval={}s", agentId, collectIntervalSec);
    }

    public void stop()
    {
        scheduler.shutdownNow();
        logger.info("QueueMetricsCollector stopped, agentId={}", agentId);
    }

    public QueueSnapshotReport collectSnapshot()
    {
        QueueMetrics metrics = queueManager.getMetrics();
        calculateCongestion(metrics);

        QueueSnapshotReport snapshot = new QueueSnapshotReport();
        snapshot.setAgentId(agentId);
        snapshot.setSnapshotTime(new Date());

        QueueSnapshotReport.QueueInfo sendInfo = new QueueSnapshotReport.QueueInfo();
        sendInfo.setDepth(metrics.getSendQueueDepth());
        sendInfo.setPeakDepth(metrics.getSendQueuePeakDepth());
        sendInfo.setCapacity(metrics.getSendQueueCapacity());
        sendInfo.setAvgWaitTimeMs(metrics.getSendQueueAvgWaitMs());
        snapshot.setSendQueue(sendInfo);

        QueueSnapshotReport.QueueInfo retryInfo = new QueueSnapshotReport.QueueInfo();
        retryInfo.setDepth(metrics.getRetryQueueDepth());
        retryInfo.setPeakDepth(metrics.getRetryQueuePeakDepth());
        retryInfo.setCapacity(metrics.getRetryQueueCapacity());
        retryInfo.setNextScheduleTime(metrics.getRetryNextScheduleTime());
        snapshot.setRetryQueue(retryInfo);

        QueueSnapshotReport.ProcessingStats stats = new QueueSnapshotReport.ProcessingStats();
        stats.setAvgProcessTimeMs(0L);
        snapshot.setProcessingStats(stats);

        return snapshot;
    }

    public String getCongestionLevel()
    {
        return congestionLevel;
    }

    public boolean isCongested()
    {
        return congested;
    }

    private void collectAndReport()
    {
        try
        {
            QueueSnapshotReport snapshot = collectSnapshot();
            proxyReportClient.reportQueueSnapshot(snapshot);
            if (congested)
            {
                logger.warn("Agent {} is CONGESTED (level={}): sendQueue={}, retryQueue={}",
                        agentId, congestionLevel,
                        snapshot.getSendQueue().getDepth(),
                        snapshot.getRetryQueue().getDepth());
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to collect and report queue metrics: {}", e.getMessage());
        }
    }

    private void calculateCongestion(QueueMetrics metrics)
    {
        int score = 0;
        if (metrics.getSendQueueDepth() > CRITICAL_DEPTH_THRESHOLD) score += 3;
        else if (metrics.getSendQueueDepth() > WARNING_DEPTH_THRESHOLD) score += 1;

        if (metrics.getSendQueueAvgWaitMs() > MAX_WAIT_MS) score += 3;
        else if (metrics.getSendQueueAvgWaitMs() > MAX_WAIT_MS / 2) score += 1;

        BigDecimal utilizationPct = BigDecimal.ZERO;
        if (metrics.getSendQueueCapacity() > 0)
        {
            utilizationPct = BigDecimal.valueOf(metrics.getSendQueueDepth())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(metrics.getSendQueueCapacity()), 2, RoundingMode.HALF_UP);
        }
        if (utilizationPct.intValue() > 80) score += 2;
        else if (utilizationPct.intValue() > 50) score += 1;

        String oldLevel = congestionLevel;
        if (score >= 5)
        {
            congestionLevel = "CRITICAL";
            congested = true;
        }
        else if (score >= 2)
        {
            congestionLevel = "WARNING";
            congested = true;
        }
        else
        {
            congestionLevel = "NORMAL";
            congested = false;
        }

        if (!oldLevel.equals(congestionLevel))
        {
            logger.info("Agent {} congestion level changed: {} -> {}", agentId, oldLevel, congestionLevel);
        }
    }
}
