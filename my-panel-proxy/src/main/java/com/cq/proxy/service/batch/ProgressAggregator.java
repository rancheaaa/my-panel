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
                updateTaskStatistics(entry.getKey(), entry.getValue());
            }
            catch (Exception e)
            {
                logger.error("Failed to update task {} statistics: {}", entry.getKey(), e.getMessage());
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

    private void updateTaskStatistics(Long taskId, Map<String, Object> summary)
    {
        try
        {
            Map<String, Object> stats = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) as total, " +
                            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
                            "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
                            "SUM(CASE WHEN status = 'SENDING' THEN 1 ELSE 0 END) as running, " +
                            "SUM(CASE WHEN status = 'QUEUED' THEN 1 ELSE 0 END) as queued, " +
                            "SUM(CASE WHEN status = 'RETRYING' THEN 1 ELSE 0 END) as retrying, " +
                            "COALESCE(SUM(file_size_bytes), 0) as total_size_bytes, " +
                            "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN file_size_bytes ELSE 0 END), 0) as transferred_bytes " +
                            "FROM batch_transfer_subtask WHERE task_id = ?", taskId);

            int total = ((Number) stats.get("total")).intValue();
            int completed = ((Number) stats.get("completed")).intValue();
            int failed = ((Number) stats.get("failed")).intValue();
            int running = ((Number) stats.get("running")).intValue();
            int queued = ((Number) stats.get("queued")).intValue();
            int retrying = ((Number) stats.get("retrying")).intValue();
            long totalSizeBytes = ((Number) stats.get("total_size_bytes")).longValue();
            long transferredBytes = ((Number) stats.get("transferred_bytes")).longValue();

            BigDecimal progressPercent = total > 0
                    ? BigDecimal.valueOf(completed * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM batch_transfer_statistics WHERE task_id = ?",
                    Integer.class, taskId);

            if (exists != null && exists > 0)
            {
                jdbcTemplate.update(
                        "UPDATE batch_transfer_statistics SET " +
                                "snapshot_time = NOW(), " +
                                "total_subtasks = ?, completed_count = ?, failed_count = ?, running_count = ?, queued_count = ?, retrying_count = ?, " +
                                "total_size_bytes = ?, transferred_bytes = ?, transferred_files = ?, failed_files = ?, " +
                                "remaining_bytes = ?, progress_percent = ?, last_activity_at = NOW(), data_version = data_version + 1 " +
                                "WHERE task_id = ?",
                        total, completed, failed, running, queued, retrying,
                        totalSizeBytes, transferredBytes, completed, failed,
                        Math.max(0L, totalSizeBytes - transferredBytes),
                        progressPercent, taskId);
            }
            else
            {
                jdbcTemplate.update(
                        "INSERT INTO batch_transfer_statistics " +
                                "(task_id, snapshot_time, total_subtasks, completed_count, failed_count, running_count, queued_count, retrying_count, " +
                                "total_size_bytes, transferred_bytes, transferred_files, failed_files, remaining_bytes, progress_percent, " +
                                "started_at, last_activity_at, data_version, create_time, update_time) " +
                                "VALUES (?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 1, NOW(), NOW())",
                        taskId,
                        total, completed, failed, running, queued, retrying,
                        totalSizeBytes, transferredBytes, completed, failed,
                        Math.max(0L, totalSizeBytes - transferredBytes),
                        progressPercent);
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to update task {} aggregated statistics: {}", taskId, e.getMessage());
        }
    }

    public void receivePostProcessResult(Object result)
    {
        if (!(result instanceof Map<?, ?> raw))
        {
            logger.warn("Ignored invalid post-process payload: {}", result);
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet())
        {
            if (entry.getKey() != null)
            {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        Object taskIdObj = data.get("taskId");
        if (taskIdObj == null) return;
        Long taskId = ((Number) taskIdObj).longValue();
        try
        {
            Object successCount = data.get("successCount");
            Object failedCount = data.get("failedCount");
            int success = successCount instanceof Number ? ((Number) successCount).intValue() : 0;
            int fail = failedCount instanceof Number ? ((Number) failedCount).intValue() : 0;

            jdbcTemplate.update(
                    "UPDATE batch_transfer_statistics SET " +
                            "post_process_completed = COALESCE(post_process_completed, 0) + ?, " +
                            "post_process_failed = COALESCE(post_process_failed, 0) + ?, " +
                            "post_processed_at = NOW(), " +
                            "last_activity_at = NOW(), update_time = NOW() " +
                            "WHERE task_id = ?",
                    success, fail, taskId);

            if (success > 0 || fail > 0)
            {
                logger.info("Updated post-process stats for task {}: success={}, failed={}", taskId, success, fail);
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to save post-process result for task {}: {}", taskId, e.getMessage());
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
