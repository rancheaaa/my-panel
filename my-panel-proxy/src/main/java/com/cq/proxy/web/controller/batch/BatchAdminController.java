package com.cq.proxy.web.controller.batch;

import com.cq.proxy.service.batch.BatchTaskScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "批量传输管理接口", description = "Admin调用Proxy的管理接口")
@RestController
@RequestMapping("/api/v1/batch")
public class BatchAdminController
{
    private static final Logger logger = LoggerFactory.getLogger(BatchAdminController.class);
    private final BatchTaskScheduler batchTaskScheduler;
    private final JdbcTemplate jdbcTemplate;

    public BatchAdminController(BatchTaskScheduler batchTaskScheduler, JdbcTemplate jdbcTemplate)
    {
        this.batchTaskScheduler = batchTaskScheduler;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "启动批量传输任务")
    @PostMapping("/tasks/{taskId}/start")
    public Map<String, Object> startTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: start task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        @SuppressWarnings("unchecked")
        java.util.List<String> targetAgents = (java.util.List<String>) request.get("targetAgents");
        String transferMode = (String) request.getOrDefault("transferMode", "ONE_TO_MANY");
        String routingStrategy = (String) request.getOrDefault("routingStrategy", "BROADCAST");
        String routingConfig = (String) request.get("routingConfig");
        Object scanRequest = request.get("scanRequest");
        Number maxBandwidth = (Number) request.get("maxBandwidthBytesPerSec");
        Long maxBandwidthBytesPerSec = maxBandwidth != null ? maxBandwidth.longValue() : null;
        String targetDirs = (String) request.get("targetDirs");
        Number preserveDirStructureNum = (Number) request.get("preserveDirStructure");
        Integer preserveDirStructure = preserveDirStructureNum != null ? preserveDirStructureNum.intValue() : 1;
        String scanCronExpression = (String) request.get("scanCronExpression");

        BatchTaskScheduler.StartResult result = batchTaskScheduler.startTask(
                taskId, sourceAgentId, sourceAgentApiUrl, scanRequest, targetAgents,
                transferMode, routingStrategy, routingConfig, maxBandwidthBytesPerSec,
                targetDirs, preserveDirStructure, scanCronExpression);

        return Map.of(
                "success", result.success,
                "message", result.message,
                "data", result.scanResult != null ? result.scanResult : Map.of()
        );
    }

    @Operation(summary = "暂停批量传输任务")
    @PutMapping("/tasks/{taskId}/pause")
    public Map<String, Object> pauseTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: pause task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        batchTaskScheduler.pauseTask(taskId, sourceAgentId, sourceAgentApiUrl);
        return Map.of("success", true, "message", "Task paused");
    }

    @Operation(summary = "恢复批量传输任务")
    @PutMapping("/tasks/{taskId}/resume")
    public Map<String, Object> resumeTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: resume task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        batchTaskScheduler.resumeTask(taskId, sourceAgentId, sourceAgentApiUrl);
        return Map.of("success", true, "message", "Task resumed");
    }

    @Operation(summary = "取消批量传输任务")
    @PutMapping("/tasks/{taskId}/cancel")
    public Map<String, Object> cancelTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: cancel task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        batchTaskScheduler.cancelTask(taskId, sourceAgentId, sourceAgentApiUrl);
        return Map.of("success", true, "message", "Task cancelled");
    }

    @Operation(summary = "更新批量传输任务配置")
    @PutMapping("/tasks/{taskId}/config")
    public Map<String, Object> updateTaskConfig(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: update config for task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        @SuppressWarnings("unchecked")
        java.util.List<String> targetAgents = (java.util.List<String>) request.get("targetAgents");
        Object scanRequest = request.get("scanRequest");
        Number maxBandwidth = (Number) request.get("maxBandwidthBytesPerSec");
        Long maxBandwidthBytesPerSec = maxBandwidth != null ? maxBandwidth.longValue() : null;
        String targetDirs = (String) request.get("targetDirs");
        Number preserveDirStructureNum = (Number) request.get("preserveDirStructure");
        Integer preserveDirStructure = preserveDirStructureNum != null ? preserveDirStructureNum.intValue() : 1;
        String scanCronExpression = (String) request.get("scanCronExpression");

        batchTaskScheduler.updateTaskConfig(taskId, sourceAgentId, sourceAgentApiUrl, scanRequest, targetAgents,
                targetDirs, preserveDirStructure, maxBandwidthBytesPerSec, scanCronExpression);

        return Map.of("success", true, "message", "Task config updated");
    }

    @Operation(summary = "上报Agent侧传输状态")
    @PostMapping("/agent-state")
    public Map<String, Object> reportAgentState(@RequestBody Map<String, Object> request)
    {
        String agentId = (String) request.get("agentId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> transfers = (List<Map<String, Object>>) request.get("transfers");
        if (agentId == null || transfers == null || transfers.isEmpty())
        {
            return Map.of("success", false, "message", "agentId and transfers are required");
        }

        int upserted = 0;
        for (Map<String, Object> t : transfers)
        {
            try
            {
                String transferId = String.valueOf(t.get("transferId"));
                if (transferId == null || transferId.isBlank()) continue;

                // 从transferId解析subtaskId (格式: batch-{subtaskId}-{uuid})
                Long subtaskId = t.get("subtaskId") != null ? ((Number) t.get("subtaskId")).longValue() : parseSubtaskIdFromTransferId(transferId);

                jdbcTemplate.update(
                        "INSERT INTO batch_transfer_agent_state " +
                                "(agent_id, transfer_id, subtask_id, task_id, file_path, file_name, remote_target_path, " +
                                "status, total_size, chunk_size, total_chunks, transferred_chunks, retry_count, exception_desc, " +
                                "create_time_str, update_time_str, enqueued_time, " +
                                "init_upload_start_time, init_upload_end_time, " +
                                "upload_chunks_start_time, upload_chunks_end_time, " +
                                "merge_chunks_start_time, merge_chunks_end_time, upload_success_time) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "status=VALUES(status), total_size=VALUES(total_size), chunk_size=VALUES(chunk_size), " +
                                "total_chunks=VALUES(total_chunks), transferred_chunks=VALUES(transferred_chunks), " +
                                "retry_count=VALUES(retry_count), exception_desc=VALUES(exception_desc), " +
                                "update_time_str=VALUES(update_time_str), " +
                                "upload_chunks_start_time=VALUES(upload_chunks_start_time), " +
                                "upload_chunks_end_time=VALUES(upload_chunks_end_time), " +
                                "merge_chunks_start_time=VALUES(merge_chunks_start_time), " +
                                "merge_chunks_end_time=VALUES(merge_chunks_end_time), " +
                                "upload_success_time=VALUES(upload_success_time)",
                        agentId, transferId, subtaskId,
                        t.get("taskId") != null ? ((Number) t.get("taskId")).longValue() : null,
                        (String) t.get("localFilePath"),
                        (String) t.get("fileName"),
                        (String) t.get("remoteTargetPath"),
                        t.get("status") != null ? String.valueOf(t.get("status")) : "UNKNOWN",
                        t.get("totalSize") != null ? ((Number) t.get("totalSize")).longValue() : 0L,
                        t.get("chunkSize") != null ? ((Number) t.get("chunkSize")).intValue() : 0,
                        t.get("totalChunks") != null ? ((Number) t.get("totalChunks")).intValue() : 0,
                        t.get("transferredChunks") != null ? ((Number) t.get("transferredChunks")).intValue() : 0,
                        t.get("retryCount") != null ? ((Number) t.get("retryCount")).intValue() : 0,
                        (String) t.get("exceptionDesc"),
                        (String) t.get("createTime"),
                        (String) t.get("updateTime"),
                        (String) t.get("enqueuedTime"),
                        (String) t.get("initUploadStartTime"),
                        (String) t.get("initUploadEndTime"),
                        (String) t.get("uploadChunksStartTime"),
                        (String) t.get("uploadChunksEndTime"),
                        (String) t.get("mergeChunksStartTime"),
                        (String) t.get("mergeChunksEndTime"),
                        (String) t.get("uploadSuccessTime")
                );
                upserted++;
            }
            catch (Exception e)
            {
                logger.warn("Failed to upsert agent state for transfer {}: {}", t.get("transferId"), e.getMessage());
            }
        }
        logger.debug("Agent[{}] reported {} transfer states, upserted={}", agentId, transfers.size(), upserted);
        return Map.of("success", true, "upserted", upserted);
    }

    private static Long parseSubtaskIdFromTransferId(String transferId)
    {
        if (transferId == null || !transferId.startsWith("batch-"))
        {
            return null;
        }
        try
        {
            String afterPrefix = transferId.substring(6); // skip "batch-"
            int dashIdx = afterPrefix.indexOf('-');
            if (dashIdx > 0)
            {
                return Long.parseLong(afterPrefix.substring(0, dashIdx));
            }
        }
        catch (NumberFormatException ignored)
        {
        }
        return null;
    }
}
