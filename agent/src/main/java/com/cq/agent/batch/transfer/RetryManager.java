package com.cq.agent.batch.transfer;

import lombok.Getter;
import lombok.Setter;
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
 * 符合spec.md设计要求：
 * - 指数退避：首次等待intervalMin分钟，后续每次翻倍（上限2小时）
 * - 最大重试次数控制
 * - 支持重试执行器调度
 */
public class RetryManager {

    private static final Logger log = LoggerFactory.getLogger(RetryManager.class);

    /**
     * -- GETTER --
     *  获取最大重试次数
     */
    @Getter
    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;

    private final Map<Long, AtomicInteger> retryCountMap = new ConcurrentHashMap<>();

    /**
     * -- SETTER --
     *  设置重试执行器
     */
    @Setter
    private Function<Long, Long> retryExecutor;

    /**
     * 构造函数
     * @param maxRetries 最大重试次数
     * @param intervalMin 首次重试等待时间（分钟）
     * @param maxDelayHours 最大退避时间（小时）
     */
    public RetryManager(int maxRetries, long intervalMin, long maxDelayHours) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = TimeUnit.MINUTES.toMillis(intervalMin);
        this.maxDelayMs = TimeUnit.HOURS.toMillis(maxDelayHours);

        log.info("✅ 重试管理器初始化: maxRetries={}, initialDelay={}min, maxDelay={}h",
            maxRetries, intervalMin, maxDelayHours);
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
     * 首次等待intervalMin分钟，后续每次翻倍，上限2小时
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
     * 清除所有重试记录
     */
    public void clearAll() {
        retryCountMap.clear();
        log.info("🗑️  所有重试记录已清除");
    }
}
