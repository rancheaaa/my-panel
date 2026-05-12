package com.cq.proxy.controller;

import com.cq.proxy.controller.dto.*;
import com.cq.proxy.repository.entity.BatchTransferSubtask;
import com.cq.proxy.service.batch.ProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

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
    public ResponseEntity<ApiResponse<SubTaskCreateResponse>> createSubTask(@RequestBody SubTaskDTO dto) {
        try {
            BatchTransferSubtask subtask = convertToSubtask(dto);
            Long subtaskId = progressService.createSubTask(subtask);

            log.info("✅ 子任务创建: taskId={}, file={}", dto.getTaskId(), dto.getFileName());
            return ResponseEntity.ok(ApiResponse.success(new SubTaskCreateResponse(subtaskId)));

        } catch (Exception e) {
            log.error("❌ 子任务创建失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 接收单个子任务进度更新
     */
    @PostMapping("/progress")
    public ResponseEntity<ApiResponse<Void>> receiveProgress(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.badRequest("缺少必填字段: subtaskId"));
            }

            if (dto.getTransferredBytes() == null || dto.getTotalBytes() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.badRequest("缺少必填字段: transferredBytes, totalBytes"));
            }

            Long timestamp = dto.getTimestamp() != null ? dto.getTimestamp() : System.currentTimeMillis();

            if (progressService.isStaleData(dto.getSubtaskId(), timestamp)) {
                return ResponseEntity.ok(ApiResponse.success("stale data ignored", null));
            }

            progressService.updateProgressExt(
                    dto.getSubtaskId(),
                    dto.getTransferredChunks(),
                    dto.getTotalChunks(),
                    dto.getTransferredBytes(),
                    dto.getSpeedBytesPerSec());

            log.debug("✅ 子任务进度更新: subtaskId={}, {}/{} bytes",
                    dto.getSubtaskId(), dto.getTransferredBytes(), dto.getTotalBytes());
            return ResponseEntity.ok(ApiResponse.success());

        } catch (Exception e) {
            log.error("❌ 进度更新失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 接收完整子任务状态更新（包含所有字段）
     */
    @PostMapping("/status")
    public ResponseEntity<ApiResponse<Void>> receiveStatus(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.badRequest("缺少必填字段: subtaskId"));
            }

            if (dto.getStatus() == null || dto.getStatus().isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.badRequest("缺少必填字段: status"));
            }

            BatchTransferSubtask subtask = convertToSubtask(dto);
            progressService.updateSubTaskStatus(subtask);

            log.info("✅ 子任务状态更新: subtaskId={}, status={}", dto.getSubtaskId(), dto.getStatus());
            return ResponseEntity.ok(ApiResponse.success());

        } catch (Exception e) {
            log.error("❌ 状态更新失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 批量接收多个子任务进度
     */
    @PostMapping("/progress/batch")
    public ResponseEntity<ApiResponse<BatchUpdateResponse>> receiveBatchProgress(@RequestBody SubTaskDTO[] batchData) {
        try {
            if (batchData == null) {
                return ResponseEntity.badRequest().body(ApiResponse.badRequest("请求体不能为null"));
            }

            progressService.batchUpdateProgress(batchData);

            log.info("✅ 批量进度更新: count={}", batchData.length);
            return ResponseEntity.ok(ApiResponse.success(new BatchUpdateResponse(batchData.length)));

        } catch (Exception e) {
            log.error("❌ 批量进度更新失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 接收子任务完成通知
     */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<SubTaskStatusResponse>> receiveComplete(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.badRequest("缺少必填字段: subtaskId"));
            }

            progressService.markCompleted(dto.getSubtaskId(), dto.getTargetPath());

            log.info("✅ 子任务完成: subtaskId={}, path={}", dto.getSubtaskId(), dto.getTargetPath());
            return ResponseEntity.ok(ApiResponse.success(new SubTaskStatusResponse("COMPLETED")));

        } catch (Exception e) {
            log.error("❌ 完成通知处理失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 接收子任务失败通知
     */
    @PostMapping("/failed")
    public ResponseEntity<ApiResponse<SubTaskStatusResponse>> receiveFailed(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.badRequest("缺少必填字段: subtaskId"));
            }

            progressService.markFailed(
                    dto.getSubtaskId(),
                    dto.getErrorCode(),
                    dto.getErrorMessage(),
                    dto.getErrorStackTrace());

            log.warn("⚠️  子任务失败: subtaskId={}, error=[{}]: {}",
                    dto.getSubtaskId(), dto.getErrorCode(), dto.getErrorMessage());
            return ResponseEntity.ok(ApiResponse.success(new SubTaskStatusResponse("FAILED")));

        } catch (Exception e) {
            log.error("❌ 失败通知处理失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 接收重试中状态通知
     */
    @PostMapping("/retrying")
    public ResponseEntity<ApiResponse<RetryResponse>> receiveRetrying(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.badRequest("缺少必填字段: subtaskId"));
            }

            long nextRetryAt = progressService.scheduleNextRetry(dto.getSubtaskId());

            log.info("🔄 子任务重试中: subtaskId={}, nextRetryAt={}", dto.getSubtaskId(), nextRetryAt);
            return ResponseEntity.ok(ApiResponse.success(new RetryResponse("RETRYING", nextRetryAt)));

        } catch (Exception e) {
            log.error("❌ 重试通知处理失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 将DTO转换为实体类
     */
    private BatchTransferSubtask convertToSubtask(SubTaskDTO dto) {
        BatchTransferSubtask subtask = new BatchTransferSubtask();

        // 基础ID字段
        if (dto.getId() != null)
            subtask.setId(dto.getId());
        if (dto.getTaskId() != null)
            subtask.setTaskId(dto.getTaskId());

        // Agent信息
        if (dto.getSourceAgentId() != null)
            subtask.setSourceAgentId(dto.getSourceAgentId());
        if (dto.getSourceAgentName() != null)
            subtask.setSourceAgentName(dto.getSourceAgentName());
        if (dto.getTargetAgentId() != null)
            subtask.setTargetAgentId(dto.getTargetAgentId());
        if (dto.getTargetAgentName() != null)
            subtask.setTargetAgentName(dto.getTargetAgentName());

        // 文件信息
        if (dto.getSourcePath() != null)
            subtask.setSourcePath(dto.getSourcePath());
        if (dto.getTargetPath() != null)
            subtask.setTargetPath(dto.getTargetPath());
        if (dto.getFileName() != null)
            subtask.setFileName(dto.getFileName());
        if (dto.getFileSizeBytes() != null)
            subtask.setFileSizeBytes(dto.getFileSizeBytes());
        if (dto.getFileLastModified() != null)
            subtask.setFileLastModified(new Date(dto.getFileLastModified()));

        // 状态信息
        if (dto.getStatus() != null && !dto.getStatus().isBlank())
            subtask.setStatus(dto.getStatus());
        if (dto.getTransferId() != null)
            subtask.setTransferId(dto.getTransferId());

        // 进度信息
        if (dto.getTransferredChunks() != null)
            subtask.setTransferredChunks(dto.getTransferredChunks());
        if (dto.getTotalChunks() != null)
            subtask.setTotalChunks(dto.getTotalChunks());
        if (dto.getTransferredBytes() != null)
            subtask.setTransferredBytes(dto.getTransferredBytes());
        if (dto.getSpeedBytesPerSec() != null)
            subtask.setSpeedBytesPerSec(dto.getSpeedBytesPerSec());

        // 时间信息（时间戳转Date）
        if (dto.getStartedAt() != null)
            subtask.setStartedAt(new Date(dto.getStartedAt()));
        if (dto.getCompletedAt() != null)
            subtask.setCompletedAt(new Date(dto.getCompletedAt()));
        if (dto.getDurationMs() != null)
            subtask.setDurationMs(dto.getDurationMs());

        // 错误信息
        if (dto.getErrorCode() != null)
            subtask.setErrorCode(dto.getErrorCode());
        if (dto.getErrorMessage() != null)
            subtask.setErrorMessage(dto.getErrorMessage());
        if (dto.getErrorStackTrace() != null)
            subtask.setErrorStackTrace(dto.getErrorStackTrace());

        // 重试信息
        if (dto.getRetryCount() != null)
            subtask.setRetryCount(dto.getRetryCount());
        if (dto.getLastRetryAt() != null)
            subtask.setLastRetryAt(new Date(dto.getLastRetryAt()));
        if (dto.getNextRetryAfter() != null)
            subtask.setNextRetryAfter(new Date(dto.getNextRetryAfter()));

        return subtask;
    }
}
