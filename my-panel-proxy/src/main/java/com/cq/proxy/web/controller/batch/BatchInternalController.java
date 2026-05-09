package com.cq.proxy.web.controller.batch;

import com.cq.proxy.service.batch.ProgressAggregator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "批量传输内部接口", description = "Agent上报接口(内部调用)")
@RestController
@RequestMapping("/api/internal/batch")
public class BatchInternalController
{
    private static final Logger logger = LoggerFactory.getLogger(BatchInternalController.class);
    private final ProgressAggregator progressAggregator;
    private final JdbcTemplate jdbcTemplate;

    public BatchInternalController(ProgressAggregator progressAggregator,
                                   JdbcTemplate jdbcTemplate)
    {
        this.progressAggregator = progressAggregator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "接收进度上报")
    @PostMapping("/progress")
    public Map<String, Object> receiveProgress(@RequestBody Map<String, Object> report)
    {
        progressAggregator.receiveSubtaskProgress(report);
        return Map.of("success", true);
    }

    @Operation(summary = "持久化子任务到数据库(定时扫描用)")
    @PostMapping("/subtasks/persist")
    public Map<String, Object> persistSubtasks(@RequestBody Map<String, Object> request)
    {
        try
        {
            Number taskIdNum = request.get("taskId") != null ? ((Number) request.get("taskId")) : null;
            if (taskIdNum == null)
            {
                return Map.of("success", false, "message", "taskId is required");
            }
            Long taskId = taskIdNum.longValue();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subtasks = (List<Map<String, Object>>) request.get("subtasks");
            if (subtasks == null || subtasks.isEmpty())
            {
                return Map.of("success", true, "message", "no subtasks to persist", "persistedSubtasks", List.of());
            }

            List<Map<String, Object>> persisted = new java.util.ArrayList<>();
            for (Map<String, Object> st : subtasks)
            {
                String sourceAgentId = st.get("sourceAgentId") != null ? String.valueOf(st.get("sourceAgentId")) : null;
                String sourceAgentName = st.get("sourceAgentName") != null ? String.valueOf(st.get("sourceAgentName")) : null;
                String targetAgentId = st.get("targetAgentId") != null ? String.valueOf(st.get("targetAgentId")) : null;
                String targetAgentName = st.get("targetAgentName") != null ? String.valueOf(st.get("targetAgentName")) : null;
                String sourcePath = st.get("sourcePath") != null ? String.valueOf(st.get("sourcePath")) : null;
                String targetPath = st.get("targetPath") != null ? String.valueOf(st.get("targetPath")) : null;
                String filePath = st.get("filePath") != null ? String.valueOf(st.get("filePath")) : null;
                String fileName = st.get("fileName") != null ? String.valueOf(st.get("fileName")) :
                        (filePath != null && filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath);
                long fileSizeBytes = st.get("fileSizeBytes") instanceof Number ? ((Number) st.get("fileSizeBytes")).longValue() : 0L;
                Object fileMd5Obj = st.get("fileMd5");
                String fileMd5 = fileMd5Obj != null ? String.valueOf(fileMd5Obj) : null;
                Object fileLastModifiedObj = st.get("fileLastModified");
                java.sql.Timestamp fileLastModified = fileLastModifiedObj instanceof java.util.Date ?
                        new java.sql.Timestamp(((java.util.Date) fileLastModifiedObj).getTime()) :
                        (fileLastModifiedObj instanceof Number ? new java.sql.Timestamp(((Number) fileLastModifiedObj).longValue()) : null);

                int rows = jdbcTemplate.update(
                        "INSERT INTO batch_transfer_subtask (task_id, source_agent_id, source_agent_name, " +
                                "target_agent_id, target_agent_name, source_path, target_path, file_name, file_size_bytes, " +
                                "status, transferred_chunks, total_chunks, transferred_bytes, " +
                                "retry_count, proxy_retry_count, create_time, update_time, file_md5, file_last_modified) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 0, 0, 0, 0, NOW(), NOW(), ?, ?) " +
                                "ON DUPLICATE KEY UPDATE status='QUEUED', transferred_chunks=0, total_chunks=0, " +
                                "transferred_bytes=0, retry_count=0, proxy_retry_count=0, update_time=NOW(), " +
                                "file_md5 = COALESCE(?, file_md5), file_last_modified = COALESCE(?, file_last_modified)",
                        taskId, sourceAgentId, sourceAgentName, targetAgentId, targetAgentName,
                        sourcePath, targetPath, fileName, fileSizeBytes, fileMd5, fileLastModified,
                        fileMd5, fileLastModified);

                Long id = rows > 0 ? jdbcTemplate.queryForObject(
                        "SELECT id FROM batch_transfer_subtask WHERE task_id=? AND source_path=? AND target_agent_id=? ORDER BY id DESC LIMIT 1",
                        Long.class, taskId, sourcePath, targetAgentId) : null;

                Map<String, Object> persistedSt = new java.util.LinkedHashMap<>(st);
                persistedSt.put("id", id);
                persistedSt.put("subtaskId", id);
                persisted.add(persistedSt);
            }

            logger.info("Persisted {} subtasks for task {}", persisted.size(), taskId);
            return Map.of("success", true, "taskId", taskId, "persistedSubtasks", persisted);
        }
        catch (Exception e)
        {
            logger.error("Failed to persist subtasks: {}", e.getMessage(), e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
