package com.cq.proxy.service.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度更新服务实现
 * 处理Agent上报的子任务进度信息
 */
@Service
public class ProgressServiceImpl implements ProgressService {

    private static final Logger log = LoggerFactory.getLogger(ProgressServiceImpl.class);

    private static final long STALE_THRESHOLD_MS = 30000L;

    private final Map<Long, Long> lastUpdateTimeMap = new ConcurrentHashMap<>();

    @Override
    public void updateProgress(Long subtaskId, int transferredBytes, int totalBytes) {
        log.debug("📊 进度更新: subtask={}, {}/{} bytes ({}%)", 
            subtaskId, transferredBytes, totalBytes, totalBytes > 0 ? (transferredBytes * 100 / totalBytes) : 0);
        lastUpdateTimeMap.put(subtaskId, System.currentTimeMillis());
    }

    @Override
    public boolean isStaleData(Long subtaskId, Long timestamp) {
        if (timestamp == null || subtaskId == null) return true;
        long age = System.currentTimeMillis() - timestamp;
        return age > STALE_THRESHOLD_MS;
    }

    @Override
    public void batchUpdateProgress(List<?> progressList) {
        if (progressList == null || progressList.isEmpty()) return;
        log.info("📦 批量进度更新: {} 条记录", progressList.size());
        for (Object item : progressList) {
            log.trace("  - {}", item);
        }
    }

    @Override
    public void markCompleted(Long subtaskId, String targetPath) {
        log.info("✅ 子任务完成: subtask={}, target={}", subtaskId, targetPath);
        lastUpdateTimeMap.remove(subtaskId);
    }

    @Override
    public void markFailed(Long subtaskId, String errorCode, String errorMessage) {
        log.warn("❌ 子任务失败: subtask={}, code={}, msg={}", subtaskId, errorCode, errorMessage);
    }

    @Override
    public long scheduleNextRetry(Long subtaskId) {
        long nextRetry = System.currentTimeMillis() + 5000L;
        log.info("🔄 调度重试: subtask={}, nextRetryTime={}", subtaskId, nextRetry);
        return nextRetry;
    }
}
