package com.cq.agent.batch.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 进度上报器
 * 负责向Proxy上报进度信息，支持失败重试和本地回退
 */
public class ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(ProgressReporter.class);

    private final String proxyBaseUrl;
    private final ProgressBuffer buffer = new ProgressBuffer();
    
    private BiFunction<String, String, Boolean> httpClient;
    private Function<List<ProgressEvent>, Boolean> batchHttpClient;
    private Consumer<ProgressEvent> fallbackHandler;

    public ProgressReporter(String proxyBaseUrl) {
        this.proxyBaseUrl = proxyBaseUrl;
        log.info("✅ 进度上报器初始化: proxy={}", proxyBaseUrl);
    }

    /**
     * 设置HTTP客户端
     */
    public void setHttpClient(BiFunction<String, String, Boolean> client) {
        this.httpClient = client;
    }

    /**
     * 设置批量HTTP客户端
     */
    public void setBatchHttpClient(Function<List<ProgressEvent>, Boolean> client) {
        this.batchHttpClient = client;
    }

    /**
     * 设置本地回退处理器
     */
    public void setFallbackHandler(Consumer<ProgressEvent> handler) {
        this.fallbackHandler = handler;
    }

    /**
     * 上报单个进度（无重试）
     */
    public boolean reportProgress(Long subtaskId, int transferredBytes, int totalBytes) {
        ProgressEvent event = new ProgressEvent(subtaskId, transferredBytes, totalBytes, 
            System.currentTimeMillis());
        
        return reportToProxy(event);
    }

    /**
     * 上报单个进度（带重试）
     */
    public boolean reportProgressWithRetry(Long subtaskId, int transferredBytes, 
                                           int totalBytes, int maxRetries) {
        ProgressEvent event = new ProgressEvent(subtaskId, transferredBytes, totalBytes, 
            System.currentTimeMillis());
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (reportToProxy(event)) {
                    log.info("✅ 进度上报成功(重试): subtaskId={}, attempt={}", subtaskId, attempt);
                    return true;
                }
                
                if (attempt < maxRetries) {
                    long delay = calculateRetryDelay(attempt);
                    Thread.sleep(delay);
                }
            } catch (Exception e) {
                log.warn("⚠️  上报异常: subtaskId={}, attempt={}/{}, error={}", 
                    subtaskId, attempt, maxRetries, e.getMessage());
            }
        }
        
        fallbackToLocal(event);
        return false;
    }

    /**
     * 添加到缓冲区
     */
    public void addToBuffer(ProgressEvent event) {
        buffer.addEvent(event);
    }

    /**
     * 刷新缓冲区（批量上报）
     */
    public boolean flushBuffer() {
        if (batchHttpClient != null && buffer.getBufferedCount() > 0) {
            // 手动触发flush并获取事件
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

    /**
     * 关闭上报器
     */
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
                log.warn("❌ 上报失败: {}", event);
                fallbackToLocal(event);
            }
            
            return success;
        } catch (Exception e) {
            log.error("❌ 上报异常: {}, error={}", event, e.getMessage());
            fallbackToLocal(event);
            return false;
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
            "{\"subtaskId\":%d,\"transferredBytes\":%d,\"totalBytes\":%d,\"timestamp\":%d}",
            event.getSubtaskId(), event.getTransferredBytes(), 
            event.getTotalBytes(), event.getTimestamp()
        );
    }
}
