package com.cq.proxy.service.batch;

import com.cq.proxy.config.BatchTransferProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueueMonitor
{
    private static final Logger logger = LoggerFactory.getLogger(QueueMonitor.class);
    private final BatchTransferProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, QueueSnapshotState> latestSnapshots = new ConcurrentHashMap<>();
    private final Map<String, CongestionAssessment> latestAssessments = new ConcurrentHashMap<>();

    @Autowired
    public QueueMonitor(BatchTransferProperties properties, JdbcTemplate jdbcTemplate)
    {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void receiveSnapshot(Object report)
    {
        if (!(report instanceof Map<?, ?> rawReport))
        {
            logger.warn("Ignored invalid queue snapshot payload: {}", report);
            return;
        }
        QueueSnapshotState snapshot = QueueSnapshotState.from(rawReport);
        if (snapshot.agentId() == null || snapshot.agentId().isBlank())
        {
            logger.warn("Ignored queue snapshot without agentId");
            return;
        }
        latestSnapshots.put(snapshot.agentId(), snapshot);
        logger.debug("Received queue snapshot, agentId={}, sendDepth={}, retryDepth={}",
                snapshot.agentId(), snapshot.sendQueueDepth(), snapshot.retryQueueDepth());
    }

    @Scheduled(fixedDelayString = "${batch.monitor.queue-analysis-interval-ms:30000}")
    public void analyzeQueues()
    {
        for (QueueSnapshotState snapshot : latestSnapshots.values())
        {
            CongestionAssessment assessment = calculateCongestion(snapshot);
            CongestionAssessment previous = latestAssessments.put(snapshot.agentId(), assessment);
            if (previous == null || !previous.level().equals(assessment.level()) || !previous.reason().equals(assessment.reason()))
            {
                handleCongestionChange(snapshot, previous, assessment);
            }
        }
    }

    private CongestionAssessment calculateCongestion(QueueSnapshotState snapshot)
    {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (snapshot.sendQueueDepth() >= properties.getQueue().getCriticalDepth())
        {
            score += 3;
            reasons.add("sendQueueDepth达到CRITICAL阈值");
        }
        else if (snapshot.sendQueueDepth() >= properties.getQueue().getWarningDepth())
        {
            score += 1;
            reasons.add("sendQueueDepth达到WARNING阈值");
        }

        if (snapshot.sendQueueAvgWaitMs() >= properties.getQueue().getMaxWaitTimeMs())
        {
            score += 3;
            reasons.add("平均等待时间超过最大阈值");
        }
        else if (snapshot.sendQueueAvgWaitMs() >= properties.getQueue().getMaxWaitTimeMs() / 2)
        {
            score += 1;
            reasons.add("平均等待时间偏高");
        }

        if (snapshot.sendQueueUtilizationPct().compareTo(BigDecimal.valueOf(80)) >= 0)
        {
            score += 2;
            reasons.add("发送队列利用率超过80%");
        }
        else if (snapshot.sendQueueUtilizationPct().compareTo(BigDecimal.valueOf(50)) >= 0)
        {
            score += 1;
            reasons.add("发送队列利用率超过50%");
        }

        String level = score >= 5 ? "CRITICAL" : score >= 2 ? "WARNING" : "NORMAL";
        return new CongestionAssessment(level, String.join("; ", reasons), generateMitigationSuggestions(snapshot, level));
    }

    private List<String> generateMitigationSuggestions(QueueSnapshotState snapshot, String level)
    {
        List<String> suggestions = new ArrayList<>();
        if (snapshot.sendQueueDepth() > properties.getQueue().getWarningDepth())
        {
            suggestions.add("建议降低扫描频率，减少新的任务入队");
        }
        if (snapshot.processingRatePerSec().compareTo(BigDecimal.TEN) < 0)
        {
            suggestions.add("建议降低maxScanFiles，缩短单批分发规模");
        }
        if (snapshot.sendQueueAvgWaitMs() > properties.getQueue().getMaxWaitTimeMs() / 2)
        {
            suggestions.add("建议临时降低带宽限制，减轻目标端压力");
        }
        if ("CRITICAL".equals(level))
        {
            suggestions.add("建议暂停非紧急任务，优先释放传输资源");
        }
        return suggestions;
    }

    private void handleCongestionChange(QueueSnapshotState snapshot,
                                        CongestionAssessment previous,
                                        CongestionAssessment current)
    {
        if ("NORMAL".equals(current.level()))
        {
            logger.info("Queue congestion resolved, agentId={}, previousLevel={}", snapshot.agentId(),
                    previous == null ? "UNKNOWN" : previous.level());
            resolvePreviousAlerts(snapshot.agentId());
            return;
        }
        logger.warn("Queue congestion changed, agentId={}, level={}, reason={}, suggestions={}",
                snapshot.agentId(), current.level(), current.reason(), current.suggestions());
        persistAlertEvent(snapshot, current);
    }

    private void persistAlertEvent(QueueSnapshotState snapshot, CongestionAssessment assessment)
    {
        try
        {
            String alertCategory = "QUEUE_CONGESTION";
            String alertTitle = String.format("Agent %s 队列%s", snapshot.agentId(), assessment.level());
            String alertMessage = String.format("%s; 缓解建议: %s", assessment.reason(), String.join("; ", assessment.suggestions()));
            String metricsSnapshot = String.format("{\"sendQueueDepth\":%d,\"retryQueueDepth\":%d,\"avgWaitMs\":%d,\"utilizationPct\":%s}",
                    snapshot.sendQueueDepth(), snapshot.retryQueueDepth(), snapshot.sendQueueAvgWaitMs(), snapshot.sendQueueUtilizationPct());
            jdbcTemplate.update(
                    "INSERT INTO batch_alert_event (agent_id, alert_level, alert_category, alert_title, alert_message, " +
                            "metrics_snapshot, is_resolved, notification_sent, create_time, update_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 0, 0, NOW(), NOW())",
                    snapshot.agentId(), assessment.level(), alertCategory, alertTitle, alertMessage, metricsSnapshot);
        }
        catch (Exception e)
        {
            logger.error("Failed to persist alert event for agent {}: {}", snapshot.agentId(), e.getMessage());
        }
    }

    private void resolvePreviousAlerts(String agentId)
    {
        try
        {
            jdbcTemplate.update(
                    "UPDATE batch_alert_event SET is_resolved = 1, resolved_at = NOW(), update_time = NOW() " +
                            "WHERE agent_id = ? AND is_resolved = 0 AND alert_category = 'QUEUE_CONGESTION'",
                    agentId);
        }
        catch (Exception e)
        {
            logger.error("Failed to resolve alerts for agent {}: {}", agentId, e.getMessage());
        }
    }

    private record CongestionAssessment(String level, String reason, List<String> suggestions)
    {
    }

    private record QueueSnapshotState(String agentId,
                                      int sendQueueDepth,
                                      int retryQueueDepth,
                                      long sendQueueAvgWaitMs,
                                      BigDecimal sendQueueUtilizationPct,
                                      BigDecimal processingRatePerSec,
                                      Instant snapshotTime)
    {
        static QueueSnapshotState from(Map<?, ?> report)
        {
            Map<String, Object> root = toStringMap(report);
            Map<String, Object> sendQueue = toStringMap(root.get("sendQueue"));
            Map<String, Object> retryQueue = toStringMap(root.get("retryQueue"));
            Map<String, Object> processingStats = toStringMap(root.get("processingStats"));
            int sendDepth = toInt(sendQueue.get("depth"));
            int capacity = Math.max(1, toInt(sendQueue.get("capacity")));
            BigDecimal utilization = BigDecimal.valueOf(sendDepth)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
            BigDecimal processingRate = BigDecimal.valueOf(toInt(processingStats.get("completedLast1Min"))
                    + toInt(processingStats.get("failedLast1Min")))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            return new QueueSnapshotState(
                    stringValue(root.get("agentId")),
                    sendDepth,
                    toInt(retryQueue.get("depth")),
                    toLong(sendQueue.get("avgWaitTimeMs")),
                    utilization,
                    processingRate,
                    parseInstant(root.get("snapshotTime"))
            );
        }

        private static Map<String, Object> toStringMap(Object value)
        {
            if (!(value instanceof Map<?, ?> rawMap))
            {
                return Map.of();
            }
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet())
            {
                if (entry.getKey() != null)
                {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return converted;
        }

        private static int toInt(Object value)
        {
            return value instanceof Number number ? number.intValue() : 0;
        }

        private static long toLong(Object value)
        {
            return value instanceof Number number ? number.longValue() : 0L;
        }

        private static String stringValue(Object value)
        {
            return value == null ? null : String.valueOf(value);
        }

        private static Instant parseInstant(Object value)
        {
            try
            {
                return value == null ? Instant.now() : Instant.parse(String.valueOf(value));
            }
            catch (Exception ignore)
            {
                return Instant.now();
            }
        }
    }
}
