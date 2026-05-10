package com.cq.agent.batch.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 任务级并发控制
 * 支持全局最大并发限制和单个任务特定覆盖
 */
public class TaskConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(TaskConcurrencyLimiter.class);

    private final int globalMax;
    private final Semaphore globalSemaphore;
    
    private final Map<String, Semaphore> taskSemaphores = new ConcurrentHashMap<>();
    private final Map<String, Integer> taskMaxLimits = new ConcurrentHashMap<>();

    public TaskConcurrencyLimiter(int globalMax, int defaultTaskMax) {
        this.globalMax = globalMax;
        this.globalSemaphore = new Semaphore(globalMax, true);
        
        log.info("✅ 任务并发控制初始化: global={}, taskDefault={}", 
            globalMax, defaultTaskMax);
    }

    /**
     * 尝试获取许可
     * @param taskId 任务ID
     * @return true表示获取成功
     */
    public boolean tryAcquire(String taskId) {
        if (!globalSemaphore.tryAcquire()) {
            log.debug("⏳ 全局并发已满: taskId={}", taskId);
            return false;
        }
        
        int taskMax = taskMaxLimits.getOrDefault(taskId, Integer.MAX_VALUE);
        
        if (taskMax < Integer.MAX_VALUE) {
            Semaphore taskSema = taskSemaphores.computeIfAbsent(taskId, 
                k -> new Semaphore(taskMax, true));
                
            if (!taskSema.tryAcquire()) {
                globalSemaphore.release();
                log.debug("⏳ 任务并发已满: taskId={}, max={}", taskId, taskMax);
                return false;
            }
        }
        
        log.debug("🔐 获取任务许可: taskId={}", taskId);
        return true;
    }

    /**
     * 释放许可
     */
    public void release(String taskId) {
        globalSemaphore.release();
        
        Semaphore taskSema = taskSemaphores.get(taskId);
        if (taskSema != null) {
            taskSema.release();
        }
        
        log.debug("🔓 释放任务许可: taskId={}", taskId);
    }

    /**
     * 设置任务特定最大值
     */
    public void setTaskMax(String taskId, int max) {
        taskMaxLimits.put(taskId, max);
        taskSemaphores.remove(taskId); // 清除旧信号量，下次重新创建
        
        log.info("✅ 设置任务限制: taskId={}, max={}", taskId, max);
    }

    /**
     * 获取全局最大值
     */
    public int getGlobalMax() {
        return globalMax;
    }

    /**
     * 获取当前全局可用许可
     */
    public int getGlobalAvailablePermits() {
        return globalSemaphore.availablePermits();
    }
}
