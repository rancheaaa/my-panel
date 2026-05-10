package com.cq.agent.batch.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 进度缓冲区
 * 缓存Agent上报的进度事件，达到阈值或定时器触发时批量flush
 */
public class ProgressBuffer {

    private static final Logger log = LoggerFactory.getLogger(ProgressBuffer.class);

    public static final int MAX_SIZE = 10;
    public static final long FLUSH_INTERVAL_MS = 1000;

    private final List<ProgressEvent> buffer = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    private Consumer<List<ProgressEvent>> flushConsumer;

    public ProgressBuffer() {
        this(MAX_SIZE, FLUSH_INTERVAL_MS);
    }

    public ProgressBuffer(int maxSize, long flushIntervalMs) {
        startFlushScheduler(flushIntervalMs);
        
        log.info("✅ 进度缓冲区初始化: maxSize={}, flushInterval={}ms", 
            maxSize, flushIntervalMs);
    }

    /**
     * 添加进度事件到缓冲区
     */
    public void addEvent(ProgressEvent event) {
        lock.lock();
        try {
            buffer.add(event);
            
            if (buffer.size() >= MAX_SIZE) {
                flushInternal();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 手动触发flush
     * @return 刷新的事件数量
     */
    public int flush() {
        lock.lock();
        try {
            return flushInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前缓冲区事件数量
     */
    public int getBufferedCount() {
        return buffer.size();
    }

    /**
     * 设置flush消费者
     */
    public void setFlushConsumer(Consumer<List<ProgressEvent>> consumer) {
        this.flushConsumer = consumer;
    }

    /**
     * 关闭缓冲区
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // flush剩余数据
        if (!buffer.isEmpty()) {
            flushInternal();
        }
        
        log.info("ProgressBuffer已关闭");
    }

    // ==================== 内部方法 ====================

    private int flushInternal() {
        if (buffer.isEmpty()) {
            return 0;
        }

        List<ProgressEvent> toFlush = new ArrayList<>(buffer);
        buffer.clear();

        try {
            if (flushConsumer != null) {
                flushConsumer.accept(toFlush);
            }
            log.debug("✅ 批量flush: {} 条", toFlush.size());
            return toFlush.size();
        } catch (Exception e) {
            log.error("❌ flush失败: {}", e.getMessage());
            throw new RuntimeException("Flush failed", e);
        }
    }

    private void startFlushScheduler(long intervalMs) {
        scheduler.scheduleAtFixedRate(() -> {
            if (!buffer.isEmpty()) {
                flush();
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        
        log.info("✅ 定时flush启动: interval={}ms", intervalMs);
    }
}
