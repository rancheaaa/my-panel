package com.cq.agent.batch.transfer;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 批量传输并发控制
 * 使用Semaphore限制同时进行的文件传输数量
 * 支持全局并发限制和任务级并发限制
 * 防止系统过载和资源耗尽
 */
public class BatchTransferManager {

    private static final Logger log = LoggerFactory.getLogger(BatchTransferManager.class);

    private final Semaphore semaphore;
    /**
     * -- GETTER --
     *  获取最大许可数
     */
    @Getter
    private final int maxPermits;
    private final TaskConcurrencyLimiter taskLimiter;

    public BatchTransferManager(int maxConcurrentTransfers) {
        this.maxPermits = maxConcurrentTransfers;
        this.semaphore = new Semaphore(maxConcurrentTransfers, true);
        this.taskLimiter = new TaskConcurrencyLimiter(maxConcurrentTransfers, 10);

        log.info("✅ 并发控制初始化: 全局max={}, 支持任务级限制", maxConcurrentTransfers);
    }

    /**
     * 获取许可（阻塞直到可用）
     * @return true表示获取成功
     */
    public boolean acquire() {
        try {
            semaphore.acquire();
            log.debug("🔐 获取传输许可: available={}", getAvailablePermits());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("⚠️  获取许可被中断");
            return false;
        }
    }

    /**
     * 尝试获取许可（带超时）
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true表示获取成功
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        try {
            boolean acquired = semaphore.tryAcquire(timeout, unit);

            if (acquired) {
                log.debug("🔐 获取传输许可(超时): available={}", getAvailablePermits());
            } else {
                log.debug("⏳ 获取许可超时: timeout={} {}", timeout, unit);
            }

            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("⚠️  获取许可被中断");
            return false;
        }
    }

    /**
     * 获取任务级许可（先全局后任务级）
     * @param taskId 任务ID
     * @return true表示获取成功
     */
    public boolean tryAcquire(String taskId) {
        return taskLimiter.tryAcquire(taskId);
    }

    /**
     * 释放任务级许可
     * @param taskId 任务ID
     */
    public void release(String taskId) {
        taskLimiter.release(taskId);
    }

    /**
     * 设置任务级最大并发数
     * @param taskId 任务ID
     * @param maxConcurrent 最大并发数
     */
    public void setTaskMax(String taskId, int maxConcurrent) {
        taskLimiter.setTaskMax(taskId, maxConcurrent);
        log.info("📊 设置任务并发限制: taskId={}, max={}", taskId, maxConcurrent);
    }

    /**
     * 释放许可
     */
    public void release() {
        semaphore.release();
        log.debug("🔓 释放传输许可: available={}", getAvailablePermits());
    }

    /**
     * 获取当前可用许可数
     */
    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * 获取当前已使用许可数
     */
    public int getUsedPermits() {
        return maxPermits - semaphore.availablePermits();
    }
}
