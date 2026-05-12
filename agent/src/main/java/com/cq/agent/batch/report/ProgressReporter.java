package com.cq.agent.batch.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 进度上报器
 * 负责向Proxy上报进度信息和子任务明细，支持失败重试和本地回退
 * 符合spec.md设计要求
 */
public class ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(ProgressReporter.class);
    private static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();

    private final String proxyBaseUrl;
    private final ProgressBuffer buffer = new ProgressBuffer();

    @Setter
    private BiFunction<String, String, Boolean> httpClient;
    @Setter
    private Function<List<ProgressEvent>, Boolean> batchHttpClient;
    @Setter
    private Consumer<ProgressEvent> fallbackHandler;

    public ProgressReporter(String proxyBaseUrl) {
        this.proxyBaseUrl = proxyBaseUrl;
        log.info("✅ 进度上报器初始化: proxy={}", proxyBaseUrl);
    }

    /**
     * 创建子任务（上报到Proxy并保存到数据库）
     */
    public boolean createSubTask(SubTaskEvent event) {
        if (httpClient == null) {
            log.debug("HTTP客户端未设置，模拟创建成功");
            return true;
        }

        String url = proxyBaseUrl + "/api/batch/subtask/create";
        String data = GSON.toJson(event);

        try {
            boolean success = httpClient.apply(url, data);
            if (success) {
                log.info("✅ 子任务创建成功: subtaskId={}, file={}", event.getSubtaskId(), event.getFileName());
            } else {
                log.warn("❌ 子任务创建失败(4xx): {}", event.getSubtaskId());
            }
            return success;
        } catch (Exception e) {
            log.error("❌ 子任务创建异常: {}, error={}", event.getSubtaskId(), e.getMessage());
            throw e;
        }
    }

    /**
     * 上报单个进度（无重试）- 符合spec.md的完整格式
     */
    public boolean reportProgress(ProgressEvent event) {
        return reportToProxy(event);
    }

    /**
     * 上报单个进度（无重试）- 旧版本兼容
     */
    public boolean reportProgress(Long subtaskId, int transferredBytes, int totalBytes) {
        ProgressEvent event = new ProgressEvent(subtaskId, transferredBytes, totalBytes,
            System.currentTimeMillis());
        return reportToProxy(event);
    }

    /**
     * 上报完整子任务状态更新（包含所有字段）
     */
    public boolean reportSubTaskStatus(SubTaskEvent event) {
        if (httpClient == null) {
            log.debug("HTTP客户端未设置，模拟成功");
            return true;
        }

        String url = proxyBaseUrl + "/api/batch/subtask/status";
        String data = GSON.toJson(event);

        try {
            boolean success = httpClient.apply(url, data);
            if (success) {
                log.debug("✅ 子任务状态上报: {}", event);
            } else {
                log.warn("❌ 子任务状态上报失败(4xx): {}", event.getSubtaskId());
            }
            return success;
        } catch (Exception e) {
            log.error("❌ 子任务状态上报异常: {}, error={}", event.getSubtaskId(), e.getMessage());
            throw e;
        }
    }

    /**
     * 上报单个进度（带重试）- 符合spec.md的完整格式
     */
    public boolean reportProgressWithRetry(ProgressEvent event, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (reportToProxy(event)) {
                    log.info("✅ 进度上报成功(重试): subtaskId={}, attempt={}",
                        event.getSubtaskId(), attempt);
                    return true;
                }

                log.warn("⚠️ 上报返回失败(4xx错误，不重试): subtaskId={}, attempt={}",
                    event.getSubtaskId(), attempt);
                fallbackToLocal(event);
                return false;

            } catch (Exception e) {
                log.warn("⚠️ 上报异常(网络/5xx): subtaskId={}, attempt={}/{}, error={}",
                    event.getSubtaskId(), attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    long delay = calculateRetryDelay(attempt);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        fallbackToLocal(event);
                        return false;
                    }
                }
            }
        }

        log.error("❌ 超过最大重试次数: subtaskId={}, maxRetries={}",
            event.getSubtaskId(), maxRetries);
        fallbackToLocal(event);
        return false;
    }

    /**
     * 上报单个进度（带重试）- 旧版本兼容
     */
    public boolean reportProgressWithRetry(Long subtaskId, int transferredBytes,
                                           int totalBytes, int maxRetries) {
        ProgressEvent event = new ProgressEvent(subtaskId, transferredBytes, totalBytes,
            System.currentTimeMillis());
        return reportProgressWithRetry(event, maxRetries);
    }

    public void addToBuffer(ProgressEvent event) {
        buffer.addEvent(event);
    }

    public boolean flushBuffer() {
        if (batchHttpClient != null && buffer.getBufferedCount() > 0) {
            List<ProgressEvent> events = new CopyOnWriteArrayList<>();
            buffer.setFlushConsumer(events::addAll);

            int flushed = buffer.flush();

            if (flushed > 0) {
                boolean result = batchHttpClient.apply(events);
                if (!result) {
                    log.warn("❌ 批量上报失败: count={}", flushed);
                    for (ProgressEvent event : events) {
                        fallbackToLocal(event);
                    }
                }
                return result;
            }
        }

        return true;
    }

    public void shutdown() {
        buffer.shutdown();
        log.info("ProgressReporter已关闭");
    }

    // ==================== 内部方法 ====================

    private boolean reportToProxy(ProgressEvent event) {
        if (httpClient == null) {
            log.debug("HTTP客户端未设置，使用模拟成功");
            return true;
        }

        String url = proxyBaseUrl + "/api/batch/subtask/progress";
        String data = buildJsonPayload(event);

        try {
            boolean success = httpClient.apply(url, data);

            if (success) {
                log.debug("✅ 进度上报: {}", event);
            } else {
                log.warn("❌ 上报失败(4xx): {}", event);
            }

            return success;
        } catch (Exception e) {
            log.error("❌ 上报异常(网络/5xx): {}, error={}", event, e.getMessage());
            throw e;
        }
    }

    private void fallbackToLocal(ProgressEvent event) {
        if (fallbackHandler != null) {
            try {
                fallbackHandler.accept(event);
                log.info("🔄 回退到本地: {}", event.getSubtaskId());
            } catch (Exception e) {
                log.error("❌ 本地回退失败: {}", e.getMessage());
            }
        }
    }

    private long calculateRetryDelay(int attempt) {
        return Math.min(1000L * (long) Math.pow(2, attempt - 1), 5000L);
    }

    private String buildJsonPayload(ProgressEvent event) {
        return String.format(
            "{\"subtaskId\":%d,\"taskId\":%d,\"transferId\":\"%s\",\"status\":\"%s\"," +
            "\"transferredChunks\":%d,\"totalChunks\":%d,\"transferredBytes\":%d," +
            "\"speedBytesPerSec\":%d,\"timestamp\":%d,\"sequenceNumber\":%d}",
            event.getSubtaskId(),
            event.getTaskId() != null ? event.getTaskId() : 0,
            event.getTransferId() != null ? event.getTransferId() : "",
            event.getStatus() != null ? event.getStatus() : "SENDING",
            event.getTransferredChunks(),
            event.getTotalChunks(),
            event.getTransferredBytes(),
            event.getSpeedBytesPerSec(),
            event.getTimestamp(),
            event.getSequenceNumber()
        );
    }
}
