package com.cq.proxy.service.batch;

import com.cq.proxy.repository.mapper.BatchTransferSubtaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度更新服务实现
 * 处理Agent上报的子任务进度信息，真正写入数据库
 * 符合spec.md设计要求
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
    public void updateProgress(Long subtaskId, int transferredBytes, int totalBytes) {
        log.debug("📊 进度更新: subtask={}, {}/{} bytes ({}%)",
            subtaskId, transferredBytes, totalBytes,
            totalBytes > 0 ? (transferredBytes * 100 / totalBytes) : 0);

        try {
            // 从totalBytes估算chunks (假设每块1MB)
            int totalChunks = totalBytes > 0 ? (int) Math.ceil(totalBytes / 1048576.0) : 0;
            int transferredChunks = transferredBytes > 0 ? (int) Math.ceil(transferredBytes / 1048576.0) : 0;

            subtaskMapper.updateProgress(subtaskId, transferredChunks,
                (long) transferredBytes, null);
            lastUpdateTimeMap.put(subtaskId, System.currentTimeMillis());

            log.debug("✅ 进度已更新到DB: subtask={}", subtaskId);
        } catch (Exception e) {
            log.error("❌ 进度更新DB失败: subtask={}, error={}", subtaskId, e.getMessage());
            throw new RuntimeException("进度更新失败", e);
        }
    }

    @Override
    public boolean isStaleData(Long subtaskId, Long timestamp) {
        if (timestamp == null || subtaskId == null) return true;

        Long lastUpdate = lastUpdateTimeMap.get(subtaskId);
        if (lastUpdate == null) {
            return false; // 没有记录，接受数据
        }

        // 如果上报的时间戳比上次更新还旧，则认为是过期数据
        return timestamp < lastUpdate;
    }

    @Override
    public void batchUpdateProgress(List<?> progressList) {
        if (progressList == null || progressList.isEmpty()) return;

        log.info("📦 批量进度更新: {} 条记录", progressList.size());

        try {
            for (Object item : progressList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> progress = (Map<String, Object>) item;

                    Long subtaskId = ((Number) progress.get("subtaskId")).longValue();
                    Integer transferredChunks = progress.containsKey("transferredChunks")
                        ? ((Number) progress.get("transferredChunks")).intValue() : null;
                    Long transferredBytes = progress.containsKey("transferredBytes")
                        ? ((Number) progress.get("transferredBytes")).longValue() : null;
                    Long speedBytesPerSec = progress.containsKey("speedBytesPerSec")
                        ? ((Number) progress.get("speedBytesPerSec")).longValue() : null;

                    if (transferredChunks != null && transferredBytes != null) {
                        subtaskMapper.updateProgress(subtaskId, transferredChunks,
                            transferredBytes, speedBytesPerSec);
                        lastUpdateTimeMap.put(subtaskId, System.currentTimeMillis());
                    }
                }
            }
            log.info("✅ 批量进度已更新到DB: count={}", progressList.size());
        } catch (Exception e) {
            log.error("❌ 批量进度更新DB失败: error={}", e.getMessage());
            throw new RuntimeException("批量进度更新失败", e);
        }
    }

    @Override
    public void markCompleted(Long subtaskId, String targetPath) {
        log.info("✅ 子任务完成: subtask={}, target={}", subtaskId, targetPath);

        try {
            // 计算传输耗时（如果有开始时间）
            Long startedAt = lastUpdateTimeMap.get(subtaskId);
            Long durationMs = startedAt != null
                ? System.currentTimeMillis() - startedAt : null;

            subtaskMapper.updateStatusCompleted(subtaskId, durationMs);
            lastUpdateTimeMap.remove(subtaskId);

            log.info("✅ 完成状态已更新到DB: subtask={}", subtaskId);
        } catch (Exception e) {
            log.error("❌ 完成状态更新DB失败: subtask={}, error={}", subtaskId, e.getMessage());
            throw new RuntimeException("完成状态更新失败", e);
        }
    }

    @Override
    public void markFailed(Long subtaskId, String errorCode, String errorMessage) {
        log.warn("❌ 子任务失败: subtask={}, code={}, msg={}", subtaskId, errorCode, errorMessage);

        try {
            subtaskMapper.updateStatusFailed(subtaskId, errorCode, errorMessage);
            lastUpdateTimeMap.remove(subtaskId);

            log.info("✅ 失败状态已更新到DB: subtask={}", subtaskId);
        } catch (Exception e) {
            log.error("❌ 失败状态更新DB失败: subtask={}, error={}", subtaskId, e.getMessage());
            throw new RuntimeException("失败状态更新失败", e);
        }
    }

    @Override
    public long scheduleNextRetry(Long subtaskId) {
        // 默认5秒后重试
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
