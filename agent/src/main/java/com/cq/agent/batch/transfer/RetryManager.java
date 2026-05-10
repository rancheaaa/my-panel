package com.cq.agent.batch.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 重试管理器
 * 管理文件传输失败后的重试逻辑
 * 支持指数退避、最大重试次数、成功清除
 */
public class RetryManager {

    private static final Logger log = LoggerFactory.getLogger(RetryManager.class);

    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;
    
    private final Map<Long, AtomicInteger> retryCountMap = new ConcurrentHashMap<>();
    
    private Function<Long, Long> retryExecutor;

    public RetryManager(int maxRetries, long initialDelaySeconds, long maxDelaySeconds) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelaySeconds * 1000;
        this.maxDelayMs = maxDelaySeconds * 1000;
        
        log.info("✅ 重试管理器初始化: max={}, initial={}s, max={}s", 
            maxRetries, initialDelaySeconds, maxDelaySeconds);
    }

    /**
     * 设置重试执行器
     */
    public void setRetryExecutor(Function<Long, Long> executor) {
        this.retryExecutor = executor;
    }

    /**
     * 判断是否应重试
     * @param subtaskId 子任务ID
     * @param error 错误信息
     * @return true表示应重试
     */
    public boolean shouldRetry(Long subtaskId, String error) {
        AtomicInteger count = retryCountMap.computeIfAbsent(subtaskId, k -> new AtomicInteger(0));
        
        int current = count.incrementAndGet();
        
        if (current > maxRetries) {
            log.warn("❌ 达到最大重试次数: subtaskId={}, max={}, error={}", 
                subtaskId, maxRetries, error);
            return false;
        }
        
        if (retryExecutor != null) {
            long delay = calculateNextRetryDelay(subtaskId, current);
            Long nextRetryAt = retryExecutor.apply(subtaskId);
            
            log.info("🔄 调度重试: subtaskId={}, attempt={}/{}, delay={}ms, nextAt={}", 
                subtaskId, current, maxRetries, delay, nextRetryAt);
        } else {
            log.info("🔄 应重试: subtaskId={}, attempt={}/{}, error={}", 
                subtaskId, current, maxRetries, error);
        }
        
        return true;
    }

    /**
     * 计算下次重试延迟（指数退避）
     */
    public long calculateNextRetryDelay(Long subtaskId, int attempt) {
        long delay = (long) (initialDelayMs * Math.pow(2, attempt - 1));
        
        if (delay > maxDelayMs) {
            delay = maxDelayMs;
        }
        
        return delay;
    }

    /**
     * 记录成功（清除重试计数）
     */
    public void recordSuccess(Long subtaskId) {
        retryCountMap.remove(subtaskId);
        log.info("✅ 重试成功，清除记录: subtaskId={}", subtaskId);
    }

    /**
     * 获取当前重试次数
     */
    public int getRetryCount(Long subtaskId) {
        AtomicInteger count = retryCountMap.get(subtaskId);
        return count != null ? count.get() : 0;
    }

    /**
     * 获取最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 清除所有重试记录
     */
    public void clearAll() {
        retryCountMap.clear();
        log.info("🗑️  所有重试记录已清除");
    }
}
