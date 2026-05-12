package com.cq.proxy.controller;

import com.cq.proxy.repository.entity.BatchTransferSubtask;
import com.cq.proxy.service.batch.ProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 进度接收控制器
 * 接收Agent上报的子任务进度、完成、失败、重试状态
 * 支持完整的子任务生命周期管理
 */
@RestController
@RequestMapping("/api/batch/subtask")
public class ProgressReceiverController {

    private static final Logger log = LoggerFactory.getLogger(ProgressReceiverController.class);

    private final ProgressService progressService;

    public ProgressReceiverController(ProgressService progressService) {
        this.progressService = progressService;
    }

    /**
     * 创建子任务（Agent开始传输前调用）
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createSubTask(@RequestBody Map<String, Object> subtaskData) {
        Map<String, Object> result = new HashMap<>();

        try {
            BatchTransferSubtask subtask = convertToSubtask(subtaskData);
            Long subtaskId = progressService.createSubTask(subtask);

            result.put("code", 200);
            result.put("msg", "success");
            result.put("data", Map.of("subtaskId", subtaskId));
            log.info("✅ 子任务创建: taskId={}, file={}", subtask.getTaskId(), subtask.getFileName());

        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 子任务创建失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 接收单个子任务进度更新
     */
    @PostMapping("/progress")
    public ResponseEntity<Map<String, Object>> receiveProgress(@RequestBody Map<String, Object> progressData) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long subtaskId = ((Number) progressData.get("subtaskId")).longValue();
            int transferredBytes = ((Number) progressData.get("transferredBytes")).intValue();
            int totalBytes = ((Number) progressData.get("totalBytes")).intValue();

            Long timestamp = progressData.containsKey("timestamp") ?
                ((Number) progressData.get("timestamp")).longValue() : System.currentTimeMillis();

            if (progressService.isStaleData(subtaskId, timestamp)) {
                result.put("code", 200);
                result.put("msg", "stale data ignored");
                return ResponseEntity.ok(result);
            }

            Integer transferredChunks = progressData.containsKey("transferredChunks") ?
                ((Number) progressData.get("transferredChunks")).intValue() : null;
            Integer totalChunks = progressData.containsKey("totalChunks") ?
                ((Number) progressData.get("totalChunks")).intValue() : null;
            Long speedBytesPerSec = progressData.containsKey("speedBytesPerSec") ?
                ((Number) progressData.get("speedBytesPerSec")).longValue() : null;

            progressService.updateProgressExt(subtaskId, transferredChunks, totalChunks,
                (long) transferredBytes, speedBytesPerSec);

            result.put("code", 200);
            result.put("msg", "success");
            log.debug("✅ 子任务进度更新: subtaskId={}, {}/{} bytes",
                subtaskId, transferredBytes, totalBytes);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 进度更新失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 接收完整子任务状态更新（包含所有字段）
     */
    @PostMapping("/status")
    public ResponseEntity<Map<String, Object>> receiveStatus(@RequestBody Map<String, Object> statusData) {
        Map<String, Object> result = new HashMap<>();

        try {
            BatchTransferSubtask subtask = convertToSubtask(statusData);
            progressService.updateSubTaskStatus(subtask);

            result.put("code", 200);
            result.put("msg", "success");
            log.info("✅ 子任务状态更新: subtaskId={}, status={}", subtask.getId(), subtask.getStatus());

        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 状态更新失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 批量接收多个子任务进度
     */
    @PostMapping("/progress/batch")
    public ResponseEntity<Map<String, Object>> receiveBatchProgress(
            @RequestBody List<Map<String, Object>> batchData) {
        Map<String, Object> result = new HashMap<>();

        try {
            progressService.batchUpdateProgress(batchData);

            result.put("code", 200);
            result.put("data", Map.of("updatedCount", batchData.size()));
            log.info("✅ 批量进度更新: count={}", batchData.size());

        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 批量进度更新失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 接收子任务完成通知
     */
    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> receiveComplete(@RequestBody Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long subtaskId = ((Number) data.get("subtaskId")).longValue();
            String targetPath = (String) data.get("targetPath");

            progressService.markCompleted(subtaskId, targetPath);

            result.put("code", 200);
            result.put("data", Map.of("status", "COMPLETED"));
            log.info("✅ 子任务完成: subtaskId={}, path={}", subtaskId, targetPath);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 完成通知处理失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 接收子任务失败通知
     */
    @PostMapping("/failed")
    public ResponseEntity<Map<String, Object>> receiveFailed(@RequestBody Map<String, Object> errorData) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long subtaskId = ((Number) errorData.get("subtaskId")).longValue();
            String errorCode = (String) errorData.get("errorCode");
            String errorMessage = (String) errorData.get("errorMessage");
            String errorStackTrace = (String) errorData.get("errorStackTrace");

            progressService.markFailed(subtaskId, errorCode, errorMessage, errorStackTrace);

            result.put("code", 200);
            result.put("data", Map.of("status", "FAILED"));
            log.warn("⚠️  子任务失败: subtaskId={}, error=[{}]: {}",
                subtaskId, errorCode, errorMessage);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 失败通知处理失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 接收重试中状态通知
     */
    @PostMapping("/retrying")
    public ResponseEntity<Map<String, Object>> receiveRetrying(@RequestBody Map<String, Object> retryData) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long subtaskId = ((Number) retryData.get("subtaskId")).longValue();

            long nextRetryAt = progressService.scheduleNextRetry(subtaskId);

            result.put("code", 200);
            result.put("data", Map.of(
                "status", "RETRYING",
                "nextRetryAt", nextRetryAt
            ));
            log.info("🔄 子任务重试中: subtaskId={}, nextRetryAt={}", subtaskId, nextRetryAt);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            log.error("❌ 重试通知处理失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    private BatchTransferSubtask convertToSubtask(Map<String, Object> data) {
        BatchTransferSubtask subtask = new BatchTransferSubtask();

        if (data.containsKey("id")) subtask.setId(((Number) data.get("id")).longValue());
        if (data.containsKey("taskId")) subtask.setTaskId(((Number) data.get("taskId")).longValue());
        if (data.containsKey("sourceAgentId")) subtask.setSourceAgentId((String) data.get("sourceAgentId"));
        if (data.containsKey("sourceAgentName")) subtask.setSourceAgentName((String) data.get("sourceAgentName"));
        if (data.containsKey("targetAgentId")) subtask.setTargetAgentId((String) data.get("targetAgentId"));
        if (data.containsKey("targetAgentName")) subtask.setTargetAgentName((String) data.get("targetAgentName"));
        if (data.containsKey("sourcePath")) subtask.setSourcePath((String) data.get("sourcePath"));
        if (data.containsKey("targetPath")) subtask.setTargetPath((String) data.get("targetPath"));
        if (data.containsKey("fileName")) subtask.setFileName((String) data.get("fileName"));
        if (data.containsKey("fileSizeBytes")) subtask.setFileSizeBytes(((Number) data.get("fileSizeBytes")).longValue());
        if (data.containsKey("status")) subtask.setStatus((String) data.get("status"));
        if (data.containsKey("transferId")) subtask.setTransferId((String) data.get("transferId"));
        if (data.containsKey("transferredChunks")) subtask.setTransferredChunks(((Number) data.get("transferredChunks")).intValue());
        if (data.containsKey("totalChunks")) subtask.setTotalChunks(((Number) data.get("totalChunks")).intValue());
        if (data.containsKey("transferredBytes")) subtask.setTransferredBytes(((Number) data.get("transferredBytes")).longValue());
        if (data.containsKey("speedBytesPerSec")) subtask.setSpeedBytesPerSec(((Number) data.get("speedBytesPerSec")).longValue());
        if (data.containsKey("errorCode")) subtask.setErrorCode((String) data.get("errorCode"));
        if (data.containsKey("errorMessage")) subtask.setErrorMessage((String) data.get("errorMessage"));
        if (data.containsKey("errorStackTrace")) subtask.setErrorStackTrace((String) data.get("errorStackTrace"));

        return subtask;
    }
}
