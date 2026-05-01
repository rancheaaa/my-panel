package com.cq.proxy.service.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProgressAggregator
{
    private static final Logger logger = LoggerFactory.getLogger(ProgressAggregator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<String, Map<String, Object>> latestReports = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ProgressAggregator(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void receiveSubtaskProgress(Object report)
    {
        if (!(report instanceof Map<?, ?> rawReport))
        {
            logger.warn("Ignored invalid progress payload: {}", report);
            return;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawReport.entrySet())
        {
            if (entry.getKey() != null)
            {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        String reportKey = buildReportKey(normalized);
        latestReports.put(reportKey, normalized);
    }

    @Scheduled(fixedDelay = 500)
    public void flushAggregatedUpdates()
    {
        if (latestReports.isEmpty())
        {
            return;
        }
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>(latestReports);
        latestReports.clear();

        for (Map<String, Object> report : batch.values())
        {
            try
            {
                updateSubtaskProgress(report);
            }
            catch (Exception e)
            {
                logger.error("Failed to flush progress for report: {}", e.getMessage());
            }
        }

        Map<Long, Map<String, Object>> taskSummaries = calculateTaskSummaries(batch);
        for (Map.Entry<Long, Map<String, Object>> entry : taskSummaries.entrySet())
        {
            try
            {
                updateTaskProgress(entry.getKey(), entry.getValue());
            }
            catch (Exception e)
            {
                logger.error("Failed to update task {} progress: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void updateSubtaskProgress(Map<String, Object> report)
    {
        Object subtaskIdObj = report.get("subtaskId");
        if (subtaskIdObj == null) return;
        Long subtaskId = ((Number) subtaskIdObj).longValue();

        String status = (String) report.get("status");
        if (status == null) return;

        StringBuilder sql = new StringBuilder("UPDATE batch_transfer_subtask SET status = ?");
        Object[] values;
        if ("COMPLETED".equals(status))
        {
            sql.append(", completed_at = NOW(), transferred_chunks = total_chunks, transferred_bytes = file_size_bytes");
            sql.append(" WHERE id = ? AND status IN ('QUEUED','SENDING','RETRYING')");
            values = new Object[]{status, subtaskId};
        }
        else if ("FAILED".equals(status))
        {
            String errorCode = (String) report.get("errorCode");
            String errorMessage = (String) report.get("errorMessage");
            sql.append(", error_code = ?, error_message = ? WHERE id = ? AND status IN ('QUEUED','SENDING','RETRYING')");
            values = new Object[]{status, errorCode, errorMessage, subtaskId};
        }
        else if ("SENDING".equals(status))
        {
            Object transferredChunks = report.get("transferredChunks");
            Object transferredBytes = report.get("transferredBytes");
            Object speedBytesPerSec = report.get("speedBytesPerSec");
            sql.append(", transferred_chunks = ?, transferred_bytes = ?, speed_bytes_per_sec = ? WHERE id = ? AND status IN ('QUEUED','SENDING')");
            values = new Object[]{status, transferredChunks, transferredBytes, speedBytesPerSec, subtaskId};
        }
        else
        {
            sql.append(" WHERE id = ?");
            values = new Object[]{status, subtaskId};
        }
        jdbcTemplate.update(sql.toString(), values);
    }

    private Map<Long, Map<String, Object>> calculateTaskSummaries(Map<String, Map<String, Object>> batch)
    {
        Map<Long, Map<String, Object>> summaries = new LinkedHashMap<>();
        for (Map<String, Object> report : batch.values())
        {
            Object taskIdObj = report.get("taskId");
            if (taskIdObj == null) continue;
            Long taskId = ((Number) taskIdObj).longValue();
            summaries.computeIfAbsent(taskId, k -> new LinkedHashMap<>());
        }
        return summaries;
    }

    private void updateTaskProgress(Long taskId, Map<String, Object> summary)
    {
        try
        {
            Map<String, Object> stats = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) as total, " +
                            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
                            "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
                            "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN file_size_bytes ELSE 0 END), 0) as transferred_bytes " +
                            "FROM batch_transfer_subtask WHERE task_id = ?", taskId);
            int total = ((Number) stats.get("total")).intValue();
            int completed = ((Number) stats.get("completed")).intValue();
            int failed = ((Number) stats.get("failed")).intValue();
            long transferredBytes = ((Number) stats.get("transferred_bytes")).longValue();

            String newStatus = null;
            if (total > 0 && completed == total)
            {
                newStatus = "COMPLETED";
            }
            else if (total > 0 && (completed + failed) == total && failed > 0)
            {
                newStatus = "PARTIAL_FAILED";
            }

            if (newStatus != null)
            {
                jdbcTemplate.update(
                        "UPDATE batch_transfer_task SET transferred_files = ?, transferred_size_bytes = ?, " +
                                "failed_files = ?, status = ?, completed_at = NOW() WHERE id = ?",
                        completed, transferredBytes, failed, newStatus, taskId);
            }
            else
            {
                jdbcTemplate.update(
                        "UPDATE batch_transfer_task SET transferred_files = ?, transferred_size_bytes = ?, " +
                                "failed_files = ? WHERE id = ?",
                        completed, transferredBytes, failed, taskId);
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to update task {} aggregated progress: {}", taskId, e.getMessage());
        }
    }

    private String buildReportKey(Map<String, Object> report)
    {
        Object subtaskId = report.get("subtaskId");
        if (subtaskId != null)
        {
            return "subtask:" + subtaskId;
        }
        Object taskId = report.get("taskId");
        Object filePath = report.get("filePath");
        Object targetAgentId = report.get("targetAgentId");
        return String.format("task:%s|target:%s|file:%s", taskId, targetAgentId, filePath);
    }
}
