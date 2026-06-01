package com.cq.proxy.service.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流保护
 * 使用滑动窗口算法限制Agent上报频率，防止数据库被压垮
 */
public class ProgressRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ProgressRateLimiter.class);

    public static final int MAX_QPS = 1000;
    private static final long WINDOW_MS = 1000;

    private final AtomicInteger currentCount = new AtomicInteger(0);
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    
    private volatile boolean shutdown = false;

    /**
     * 尝试获取许可
     * @return true表示允许通过，false表示被限流
     */
    public boolean tryAcquire() {
        if (shutdown) {
            throw new IllegalStateException("Rate limiter已关闭");
        }

        long now = System.currentTimeMillis();
        long windowStartTime = windowStart.get();
        
        if (now - windowStartTime >= WINDOW_MS) {
            synchronized (this) {
                if (windowStart.get() == windowStartTime) {
                    currentCount.set(0);
                    windowStart.set(now);
                }
            }
        }
        
        int count = currentCount.incrementAndGet();
        
        if (count > MAX_QPS) {
            currentCount.decrementAndGet();
            return false;
        }
        
        return true;
    }

    /**
     * 获取当前窗口已使用配额
     */
    public int getCurrentUsage() {
        return currentCount.get();
    }

    /**
     * 获取剩余可用配额
     */
    public int getRemainingQuota() {
        return Math.max(0, MAX_QPS - currentCount.get());
    }

    /**
     * 关闭限流器
     */
    public void shutdown() {
        this.shutdown = true;
        log.info("ProgressRateLimiter已关闭");
    }
}
