package com.cq.proxy.service.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 批量写入优化
 * 缓存Agent上报的进度数据，达到阈值或定时器触发时批量写入数据库
 */
public class ProgressBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(ProgressBatchWriter.class);

    public static final int BATCH_SIZE = 50;
    public static final long FLUSH_INTERVAL_MS = 1000;

    private final ProgressService progressService;
    private final List<Map<String, Object>> buffer = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger pendingCount = new AtomicInteger(0);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ProgressBatchWriter(ProgressService progressService) {
        this.progressService = progressService;
        startFlushScheduler();
    }

    /**
     * 添加进度数据到缓冲区
     * 达到阈值自动触发flush
     */
    public void add(Map<String, Object> progressData) {
        lock.lock();
        try {
            buffer.add(progressData);
            int count = pendingCount.incrementAndGet();
            
            if (count >= BATCH_SIZE) {
                flushInternal();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 手动触发批量写入
     * @return 写入的数据条数
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
     * 获取待写入数据数量
     */
    public int getPendingCount() {
        return pendingCount.get();
    }

    /**
     * 关闭写入器，flush剩余数据
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        flush();
        log.info("ProgressBatchWriter已关闭");
    }

    // ==================== 内部方法 ====================

    private int flushInternal() {
        if (buffer.isEmpty()) {
            return 0;
        }

        List<Map<String, Object>> toFlush = new ArrayList<>(buffer);
        buffer.clear();
        pendingCount.set(0);

        try {
            progressService.batchUpdateProgress(toFlush);
            log.debug("✅ 批量flush: {} 条", toFlush.size());
            return toFlush.size();
        } catch (Exception e) {
            log.error("❌ 批量flush失败: {}", e.getMessage());
            throw new RuntimeException("Batch flush failed", e);
        }
    }

    private void startFlushScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            if (pendingCount.get() > 0) {
                flush();
            }
        }, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        log.info("✅ 定时flush启动: interval={}ms", FLUSH_INTERVAL_MS);
    }
}
