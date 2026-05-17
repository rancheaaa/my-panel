package com.cq.proxy.controller;

import com.cq.proxy.dto.*;
import com.cq.proxy.repository.entity.BatchTransferSubtask;
import com.cq.proxy.service.batch.ProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@RequestMapping("/api/batch/subtask")
public class ProgressReceiverController {

    private static final Logger log = LoggerFactory.getLogger(ProgressReceiverController.class);

    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final ProgressService progressService;

    public ProgressReceiverController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @PostMapping("/create")
    public ApiResponse<SubTaskCreateResponse> createSubTask(@RequestBody SubTaskDTO dto) {
        try {
            BatchTransferSubtask subtask = convertToSubtask(dto);
            Long subtaskId = progressService.createSubTask(subtask);

            log.info("✅ 子任务创建: taskId={}, file={}", dto.getTaskId(), dto.getFileName());
            return ApiResponse.success(new SubTaskCreateResponse(subtaskId));

        } catch (Exception e) {
            log.error("❌ 子任务创建失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/progress")
    public ApiResponse<Void> receiveProgress(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ApiResponse.badRequest("缺少必填字段: subtaskId");
            }

            if (dto.getTransferredBytes() == null) {
                return ApiResponse.badRequest("缺少必填字段: transferredBytes");
            }

            Long timestamp = dto.getTimestamp() != null ? dto.getTimestamp() : System.currentTimeMillis();

            if (progressService.isStaleData(dto.getSubtaskId(), timestamp)) {
                return ApiResponse.success("stale data ignored", null);
            }

            progressService.updateProgressExt(
                    dto.getSubtaskId(),
                    dto.getTransferredChunks(),
                    dto.getTotalChunks(),
                    dto.getTransferredBytes(),
                    dto.getSpeedBytesPerSec());

            if (dto.getTransferId() != null) {
                BatchTransferSubtask update = new BatchTransferSubtask();
                update.setId(dto.getSubtaskId());
                update.setTransferId(dto.getTransferId());
                progressService.updateSubTaskStatus(update);
            }

            log.debug("✅ 子任务进度更新: subtaskId={}, {}/{} bytes",
                    dto.getSubtaskId(), dto.getTransferredBytes(), dto.getTotalBytes());
            return ApiResponse.success();

        } catch (Exception e) {
            log.error("❌ 进度更新失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/status")
    public ApiResponse<Void> receiveStatus(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ApiResponse.badRequest("缺少必填字段: subtaskId");
            }

            if (dto.getStatus() == null || dto.getStatus().isBlank()) {
                return ApiResponse.badRequest("缺少必填字段: status");
            }

            BatchTransferSubtask subtask = convertToSubtask(dto);
            progressService.updateSubTaskStatus(subtask);

            log.info("✅ 子任务状态更新: subtaskId={}, status={}", dto.getSubtaskId(), dto.getStatus());
            return ApiResponse.success();

        } catch (Exception e) {
            log.error("❌ 状态更新失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/progress/batch")
    public ApiResponse<BatchUpdateResponse> receiveBatchProgress(@RequestBody SubTaskDTO[] batchData) {
        try {
            if (batchData == null) {
                return ApiResponse.badRequest("请求体不能为null");
            }

            progressService.batchUpdateProgress(batchData);

            log.info("✅ 批量进度更新: count={}", batchData.length);
            return ApiResponse.success(new BatchUpdateResponse(batchData.length));

        } catch (Exception e) {
            log.error("❌ 批量进度更新失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/complete")
    public ApiResponse<SubTaskStatusResponse> receiveComplete(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ApiResponse.badRequest("缺少必填字段: subtaskId");
            }

            BatchTransferSubtask subtask = new BatchTransferSubtask();
            subtask.setId(dto.getSubtaskId());
            subtask.setStatus("COMPLETED");
            subtask.setTransferId(dto.getTransferId());
            subtask.setTransferredChunks(dto.getTransferredChunks());
            subtask.setTotalChunks(dto.getTotalChunks());
            subtask.setTransferredBytes(dto.getTransferredBytes());
            Long speed = dto.getSpeedBytesPerSec();
            // 如果Agent未发送速度，则根据transferredBytes和durationMs计算
            if (speed == null && dto.getTransferredBytes() != null && dto.getDurationMs() != null && dto.getDurationMs() > 0) {
                speed = dto.getTransferredBytes() * 1000L / dto.getDurationMs();
            }
            subtask.setSpeedBytesPerSec(speed);
            subtask.setStartedAt(dto.getStartedAt() != null ? parseDateTime(dto.getStartedAt()) : null);
            subtask.setCompletedAt(dto.getCompletedAt() != null ? parseDateTime(dto.getCompletedAt()) : new Date());
            subtask.setDurationMs(dto.getDurationMs());
            if (dto.getTargetPath() != null) {
                subtask.setTargetPath(dto.getTargetPath());
            }

            progressService.updateSubTaskStatus(subtask);

            log.info("✅ 子任务完成: subtaskId={}, path={}", dto.getSubtaskId(), dto.getTargetPath());
            return ApiResponse.success(new SubTaskStatusResponse("COMPLETED"));

        } catch (Exception e) {
            log.error("❌ 完成通知处理失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/failed")
    public ApiResponse<SubTaskStatusResponse> receiveFailed(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ApiResponse.badRequest("缺少必填字段: subtaskId");
            }

            BatchTransferSubtask subtask = new BatchTransferSubtask();
            subtask.setId(dto.getSubtaskId());
            subtask.setStatus("FAILED");
            subtask.setErrorCode(dto.getErrorCode());
            subtask.setErrorMessage(dto.getErrorMessage());
            subtask.setErrorStackTrace(dto.getErrorStackTrace());
            subtask.setTransferredChunks(dto.getTransferredChunks());
            subtask.setTotalChunks(dto.getTotalChunks());
            subtask.setTransferredBytes(dto.getTransferredBytes());
            subtask.setStartedAt(dto.getStartedAt() != null ? parseDateTime(dto.getStartedAt()) : null);
            subtask.setCompletedAt(new Date());
            subtask.setDurationMs(dto.getDurationMs());

            progressService.updateSubTaskStatus(subtask);

            log.warn("⚠️  子任务失败: subtaskId={}, error=[{}]: {}",
                    dto.getSubtaskId(), dto.getErrorCode(), dto.getErrorMessage());
            return ApiResponse.success(new SubTaskStatusResponse("FAILED"));

        } catch (Exception e) {
            log.error("❌ 失败通知处理失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/retrying")
    public ApiResponse<RetryResponse> receiveRetrying(@RequestBody SubTaskDTO dto) {
        try {
            if (dto.getSubtaskId() == null) {
                return ApiResponse.badRequest("缺少必填字段: subtaskId");
            }

            long nextRetryAt = progressService.scheduleNextRetry(dto.getSubtaskId());

            log.info("🔄 子任务重试中: subtaskId={}, nextRetryAt={}", dto.getSubtaskId(), nextRetryAt);
            return ApiResponse.success(new RetryResponse("RETRYING", nextRetryAt));

        } catch (Exception e) {
            log.error("❌ 重试通知处理失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    private BatchTransferSubtask convertToSubtask(SubTaskDTO dto) {
        BatchTransferSubtask subtask = new BatchTransferSubtask();

        if (dto.getId() != null) {
            subtask.setId(dto.getId());
        } else if (dto.getSubtaskId() != null) {
            subtask.setId(dto.getSubtaskId());
        }

        if (dto.getTaskId() != null)
            subtask.setTaskId(dto.getTaskId());

        if (dto.getScanBatchId() != null)
            subtask.setScanBatchId(dto.getScanBatchId());
        if (dto.getFileBatchId() != null)
            subtask.setFileBatchId(dto.getFileBatchId());

        if (dto.getSourceAgentId() != null)
            subtask.setSourceAgentId(dto.getSourceAgentId());
        if (dto.getSourceAgentName() != null)
            subtask.setSourceAgentName(dto.getSourceAgentName());
        if (dto.getTargetAgentId() != null)
            subtask.setTargetAgentId(dto.getTargetAgentId());
        if (dto.getTargetAgentName() != null)
            subtask.setTargetAgentName(dto.getTargetAgentName());

        if (dto.getSourcePath() != null)
            subtask.setSourcePath(dto.getSourcePath());
        if (dto.getTargetPath() != null)
            subtask.setTargetPath(dto.getTargetPath());
        if (dto.getFileName() != null)
            subtask.setFileName(dto.getFileName());
        if (dto.getFileSizeBytes() != null)
            subtask.setFileSizeBytes(dto.getFileSizeBytes());
        if (dto.getFileLastModified() != null)
            subtask.setFileLastModified(parseDateTime(dto.getFileLastModified()));

        if (dto.getStatus() != null && !dto.getStatus().isBlank())
            subtask.setStatus(dto.getStatus());
        if (dto.getTransferId() != null)
            subtask.setTransferId(dto.getTransferId());

        if (dto.getTransferredChunks() != null)
            subtask.setTransferredChunks(dto.getTransferredChunks());
        if (dto.getTotalChunks() != null)
            subtask.setTotalChunks(dto.getTotalChunks());
        if (dto.getTransferredBytes() != null)
            subtask.setTransferredBytes(dto.getTransferredBytes());
        if (dto.getSpeedBytesPerSec() != null)
            subtask.setSpeedBytesPerSec(dto.getSpeedBytesPerSec());

        if (dto.getStartedAt() != null)
            subtask.setStartedAt(parseDateTime(dto.getStartedAt()));
        if (dto.getCompletedAt() != null)
            subtask.setCompletedAt(parseDateTime(dto.getCompletedAt()));
        if (dto.getDurationMs() != null)
            subtask.setDurationMs(dto.getDurationMs());

        if (dto.getErrorCode() != null)
            subtask.setErrorCode(dto.getErrorCode());
        if (dto.getErrorMessage() != null)
            subtask.setErrorMessage(dto.getErrorMessage());
        if (dto.getErrorStackTrace() != null)
            subtask.setErrorStackTrace(dto.getErrorStackTrace());

        if (dto.getRetryCount() != null)
            subtask.setRetryCount(dto.getRetryCount());
        if (dto.getLastRetryAt() != null)
            subtask.setLastRetryAt(parseDateTime(dto.getLastRetryAt()));
        if (dto.getNextRetryAfter() != null)
            subtask.setNextRetryAfter(parseDateTime(dto.getNextRetryAfter()));

        return subtask;
    }

    /**
     * 解析时间字符串为Date对象
     * 支持两种格式：
     * 1. Long类型的时间戳毫秒（如 "1778746500000"）
     * 2. 日期字符串（如 "2026-05-14 15:55:00"）
     */
    private Date parseDateTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }

        try {
            long timestamp = Long.parseLong(timeStr);
            return new Date(timestamp);
        } catch (NumberFormatException e) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_PATTERN);
                return sdf.parse(timeStr);
            } catch (ParseException ex) {
                log.warn("⚠️ 无法解析时间字符串: {}", timeStr);
                return null;
            }
        }
    }
}
