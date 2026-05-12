package com.cq.proxy.service.batch;

import com.cq.proxy.controller.dto.SubTaskDTO;
import com.cq.proxy.repository.entity.BatchTransferSubtask;
import com.cq.proxy.repository.mapper.BatchTransferSubtaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度更新服务实现
 * 处理Agent上报的子任务进度信息，真正写入数据库
 */
@Service
public class ProgressServiceImpl implements ProgressService {

    private static final Logger log = LoggerFactory.getLogger(ProgressServiceImpl.class);
    private static final long STALE_THRESHOLD_MS = 30000L;

    private final BatchTransferSubtaskMapper subtaskMapper;
    private final Map<Long, Long> lastUpdateTimeMap = new ConcurrentHashMap<>();

    public ProgressServiceImpl(BatchTransferSubtaskMapper subtaskMapper) {
        this.subtaskMapper = subtaskMapper;
    }

    @Override
    public Long createSubTask(BatchTransferSubtask subtask) {
        log.info("📝 创建子任务: taskId={}, file={}", subtask.getTaskId(), subtask.getFileName());

        try {
            if (subtask.getStatus() == null) {
                subtask.setStatus("QUEUED");
            }
            if (subtask.getCreateTime() == null) {
                subtask.setCreateTime(new Date());
            }
            if (subtask.getUpdateTime() == null) {
                subtask.setUpdateTime(new Date());
            }

            subtaskMapper.insert(subtask);
            lastUpdateTimeMap.put(subtask.getId(), System.currentTimeMillis());

            log.info("✅ 子任务已创建到DB: id={}, file={}", subtask.getId(), subtask.getFileName());
            return subtask.getId();
        } catch (Exception e) {
            log.error("❌ 创建子任务DB失败: error={}", e.getMessage());
            throw new RuntimeException("创建子任务失败", e);
        }
    }

    @Override
    public void updateProgress(Long subtaskId, int transferredBytes, int totalBytes) {
        updateProgressExt(subtaskId, null, null, (long) transferredBytes, null);
    }

    @Override
    public void updateProgressExt(Long subtaskId, Integer transferredChunks, Integer totalChunks,
            Long transferredBytes, Long speedBytesPerSec) {
        log.debug("📊 进度更新: subtask={}, bytes={}, chunks={}/{}",
                subtaskId, transferredBytes, transferredChunks, totalChunks);

        try {
            if (transferredChunks == null && transferredBytes != null) {
                long totalBytesEstimate = transferredBytes;
                totalChunks = (int) Math.ceil(totalBytesEstimate / 1048576.0);
                transferredChunks = transferredBytes > 0 ? (int) Math.ceil(transferredBytes / 1048576.0) : 0;
            }

            subtaskMapper.updateProgress(subtaskId, transferredChunks,
                    transferredBytes, speedBytesPerSec);
            lastUpdateTimeMap.put(subtaskId, System.currentTimeMillis());

            log.debug("✅ 进度已更新到DB: subtask={}", subtaskId);
        } catch (Exception e) {
            log.error("❌ 进度更新DB失败: subtask={}, error={}", subtaskId, e.getMessage());
            throw new RuntimeException("进度更新失败", e);
        }
    }

    @Override
    public boolean isStaleData(Long subtaskId, Long timestamp) {
        if (timestamp == null || subtaskId == null)
            return true;

        Long lastUpdate = lastUpdateTimeMap.get(subtaskId);
        if (lastUpdate == null) {
            return false;
        }

        return timestamp < lastUpdate;
    }

    @Override
    public void batchUpdateProgress(SubTaskDTO[] dtoArray) {
        if (dtoArray == null || dtoArray.length == 0)
            return;

        log.info("📦 批量进度更新: {} 条记录", dtoArray.length);

        try {
            for (SubTaskDTO dto : dtoArray) {
                if (dto.getSubtaskId() != null && dto.getTransferredBytes() != null) {
                    updateProgressExt(
                            dto.getSubtaskId(),
                            dto.getTransferredChunks(),
                            dto.getTotalChunks(),
                            dto.getTransferredBytes(),
                            dto.getSpeedBytesPerSec());
                }
            }
            log.info("✅ 批量进度已更新到DB: count={}", dtoArray.length);
        } catch (Exception e) {
            log.error("❌ 批量进度更新DB失败: error={}", e.getMessage());
            throw new RuntimeException("批量进度更新失败", e);
        }
    }

    @Override
    public void updateSubTaskStatus(BatchTransferSubtask subtask) {
        log.info("📝 更新子任务状态: id={}, status={}", subtask.getId(), subtask.getStatus());

        try {
            subtask.setUpdateTime(new Date());
            subtaskMapper.updateByEntity(subtask);
            lastUpdateTimeMap.put(subtask.getId(), System.currentTimeMillis());

            log.debug("✅ 子任务状态已更新到DB: id={}", subtask.getId());
        } catch (Exception e) {
            log.error("❌ 更新子任务状态DB失败: id={}, error={}", subtask.getId(), e.getMessage());
            throw new RuntimeException("更新子任务状态失败", e);
        }
    }

    @Override
    public void markCompleted(Long subtaskId, String targetPath) {
        log.info("✅ 子任务完成: subtask={}, target={}", subtaskId, targetPath);

        try {
            Long startedAt = lastUpdateTimeMap.get(subtaskId);
            Long durationMs = startedAt != null
                    ? System.currentTimeMillis() - startedAt
                    : null;

            subtaskMapper.updateStatusCompleted(subtaskId, durationMs);
            if (targetPath != null) {
                BatchTransferSubtask update = new BatchTransferSubtask();
                update.setId(subtaskId);
                update.setTargetPath(targetPath);
                update.setCompletedAt(new Date());
                subtaskMapper.updateByEntity(update);
            }
            lastUpdateTimeMap.remove(subtaskId);

            log.info("✅ 完成状态已更新到DB: subtask={}", subtaskId);
        } catch (Exception e) {
            log.error("❌ 完成状态更新DB失败: subtask={}, error={}", subtaskId, e.getMessage());
            throw new RuntimeException("完成状态更新失败", e);
        }
    }

    @Override
    public void markFailed(Long subtaskId, String errorCode, String errorMessage, String errorStackTrace) {
        log.warn("❌ 子任务失败: subtask={}, code={}, msg={}", subtaskId, errorCode, errorMessage);

        try {
            subtaskMapper.updateStatusFailed(subtaskId, errorCode, errorMessage);
            if (errorStackTrace != null) {
                BatchTransferSubtask update = new BatchTransferSubtask();
                update.setId(subtaskId);
                update.setErrorStackTrace(errorStackTrace);
                subtaskMapper.updateByEntity(update);
            }
            lastUpdateTimeMap.remove(subtaskId);

            log.info("✅ 失败状态已更新到DB: subtask={}", subtaskId);
        } catch (Exception e) {
            log.error("❌ 失败状态更新DB失败: subtask={}, error={}", subtaskId, e.getMessage());
            throw new RuntimeException("失败状态更新失败", e);
        }
    }

    @Override
    public long scheduleNextRetry(Long subtaskId) {
        long nextRetry = System.currentTimeMillis() + 5000L;

        try {
            Date nextRetryAfter = new Date(nextRetry);
            subtaskMapper.updateStatusRetrying(subtaskId, nextRetryAfter);

            log.info("🔄 重试状态已更新到DB: subtask={}, nextRetryAt={}", subtaskId, nextRetryAfter);
        } catch (Exception e) {
            log.error("❌ 重试状态更新DB失败: subtask={}, error={}", subtaskId, e.getMessage());
            throw new RuntimeException("重试状态更新失败", e);
        }

        return nextRetry;
    }
}
