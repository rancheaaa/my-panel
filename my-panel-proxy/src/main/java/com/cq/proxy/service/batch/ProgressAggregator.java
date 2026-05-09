package com.cq.proxy.service.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProgressAggregator
{
    private static final Logger logger = LoggerFactory.getLogger(ProgressAggregator.class);
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
            String transferId = report.get("transferId") != null ? String.valueOf(report.get("transferId")) : null;
            Map<?, ?> compProgress = report.get("progress") instanceof Map ? (Map<?, ?>) report.get("progress") : null;
            Object compTotalChunks = compProgress != null ? compProgress.get("totalChunks") : report.get("totalChunks");
            Object compTransferredChunks = compProgress != null ? compProgress.get("transferredChunks") : report.get("transferredChunks");
            sql.append(", completed_at = COALESCE(completed_at, NOW()), started_at = COALESCE(started_at, create_time, NOW())");
            sql.append(", transferred_chunks = COALESCE(?, total_chunks, 0), total_chunks = COALESCE(?, total_chunks, 0), transferred_bytes = file_size_bytes");
            sql.append(", duration_ms = TIMESTAMPDIFF(SECOND, COALESCE(started_at, create_time, NOW()), NOW()) * 1000");
            if (transferId != null && !transferId.isBlank() && !"-".equals(transferId)) {
                sql.append(", transfer_id = ?");
            }
            sql.append(" WHERE id = ? AND status IN ('QUEUED','SENDING','RETRYING','COMPLETED')");
            if (transferId != null && !transferId.isBlank() && !"-".equals(transferId)) {
                values = new Object[]{status, compTransferredChunks, compTotalChunks, transferId, subtaskId};
            } else {
                values = new Object[]{status, compTransferredChunks, compTotalChunks, subtaskId};
            }
        }
        else if ("FAILED".equals(status))
        {
            String errorCode = report.get("errorCode") != null ? String.valueOf(report.get("errorCode")) : "UNKNOWN";
            String errorMessage = report.get("errorMessage") != null ? String.valueOf(report.get("errorMessage")) : "";
            Map<?, ?> progress = report.get("progress") instanceof Map ? (Map<?, ?>) report.get("progress") : null;
            Object transferredChunks = progress != null ? progress.get("transferredChunks") : report.get("transferredChunks");
            Object transferredBytes = progress != null ? progress.get("transferredBytes") : report.get("transferredBytes");
            sql.append(", error_code = ?, error_message = ?");
            if (transferredChunks != null) sql.append(", transferred_chunks = ?");
            if (transferredBytes != null) sql.append(", transferred_bytes = ?");
            sql.append(", started_at = COALESCE(started_at, NOW()) WHERE id = ? AND status IN ('QUEUED','SENDING','RETRYING')");
            java.util.List<Object> valList = new java.util.ArrayList<>();
            valList.add(status);
            valList.add(errorCode);
            valList.add(errorMessage);
            if (transferredChunks != null) valList.add(transferredChunks);
            if (transferredBytes != null) valList.add(transferredBytes);
            valList.add(subtaskId);
            values = valList.toArray();
        }
        else if ("SENDING".equals(status))
        {
            Map<?, ?> progress = report.get("progress") instanceof Map ? (Map<?, ?>) report.get("progress") : null;
            Map<?, ?> performance = report.get("performance") instanceof Map ? (Map<?, ?>) report.get("performance") : null;

            Object transferredChunks = progress != null ? progress.get("transferredChunks") : report.get("transferredChunks");
            Object transferredBytes = progress != null ? progress.get("transferredBytes") : report.get("transferredBytes");
            Object totalChunks = progress != null ? progress.get("totalChunks") : report.get("totalChunks");
            Object speedBytesPerSec = performance != null ? performance.get("currentSpeedBytesPerSec") : report.get("speedBytesPerSec");

            sql.append(", transferred_chunks = COALESCE(?, transferred_chunks), total_chunks = COALESCE(?, total_chunks), transferred_bytes = COALESCE(?, transferred_bytes), speed_bytes_per_sec = COALESCE(?, speed_bytes_per_sec), started_at = COALESCE(started_at, NOW()) WHERE id = ? AND status IN ('QUEUED','SENDING')");
            values = new Object[]{status, transferredChunks, totalChunks, transferredBytes, speedBytesPerSec, subtaskId};
        }
        else
        {
            sql.append(" WHERE id = ?");
            values = new Object[]{status, subtaskId};
        }
        jdbcTemplate.update(sql.toString(), values);
        logger.debug("Updated subtask {} status={}, rows affected, sql={}", subtaskId, status, sql);
    }

    private String buildReportKey(Map<String, Object> report)
    {
        Object subtaskId = report.get("subtaskId");
        if (subtaskId != null)
        {
            return "subtask:" + subtaskId;
        }
        Object taskId = report.get("taskId");
        Object sourcePath = report.get("sourcePath");
        Object filePath = report.get("filePath");
        Object targetAgentId = report.get("targetAgentId");
        String path = sourcePath != null ? String.valueOf(sourcePath) : String.valueOf(filePath);
        return String.format("task:%s|target:%s|file:%s", taskId, targetAgentId, path);
    }
}
