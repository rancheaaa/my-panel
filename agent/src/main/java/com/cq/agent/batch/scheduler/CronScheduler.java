package com.cq.agent.batch.scheduler;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cron调度器
 * 基于Cron表达式调度任务执行，支持动态更新、暂停/恢复
 */
public class CronScheduler {

    private static final Logger log = LoggerFactory.getLogger(CronScheduler.class);

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;

    /**
     * -- SETTER --
     *  设置要执行的任务
     */
    @Setter
    private Runnable task;
    private String cronExpression;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 启动调度器
     * @param cronExpr Cron表达式（简化版：支持 "0/N * * * * ?" 格式）
     */
    public void start(String cronExpr) {
        if (running.get()) {
            stop();
        }
        
        this.cronExpression = cronExpr;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CronScheduler-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        
        this.paused.set(false);
        this.running.set(true);
        
        long intervalMs = parseCronInterval(cronExpression);
        
        // 立即执行一次
        executeTask();
        
        // 调度后续执行
        scheduleNextExecution(intervalMs);
        
        log.info("✅ Cron调度器启动: cron={}, interval={}ms", cronExpression, intervalMs);
    }

    /**
     * 停止调度器
     */
    public void stop() {
        running.set(false);
        paused.set(false);
        
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("⏹️  Cron调度器已停止");
    }

    /**
     * 暂停调度器（当前任务会完成，但不再调度新的）
     */
    public void pause() {
        paused.set(true);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        log.info("⏸️  Cron调度器已暂停");
    }

    /**
     * 恢复调度器
     */
    public void resume() {
        if (!running.get()) {
            log.warn("⚠️  调度器未运行，无法恢复");
            return;
        }
        
        paused.set(false);
        long intervalMs = parseCronInterval(cronExpression);
        scheduleNextExecution(intervalMs);
        
        log.info("▶️  Cron调度器已恢复: interval={}ms", intervalMs);
    }

    /**
     * 动态更新Cron表达式
     */
    public void updateCronExpression(String newCronExpr) {
        this.cronExpression = newCronExpr;
        
        if (running.get() && !paused.get()) {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
            
            long newIntervalMs = parseCronInterval(newCronExpr);
            scheduleNextExecution(newIntervalMs);
            
            log.info("🔄 Cron表达式更新: {} → {} (interval={}ms)", 
                cronExpression, newCronExpr, newIntervalMs);
        } else {
            log.debug("Cron表达式已保存: {} (调度器未运行或已暂停)", newCronExpr);
        }
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 是否已暂停
     */
    public boolean isPaused() {
        return paused.get();
    }

    // ==================== 内部方法 ====================

    private void scheduleNextExecution(long intervalMs) {
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }
        
        scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
            if (!paused.get()) {
                executeTask();
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void executeTask() {
        try {
            if (task != null) {
                task.run();
            }
        } catch (Exception e) {
            log.error("❌ 任务执行异常", e);
        }
    }

    /**
     * 解析简化的Cron表达式为毫秒间隔
     * 支持: "0/N * * * * ?" 格式（N秒间隔）
     */
    private long parseCronInterval(String cronExpr) {
        try {
            if (cronExpr == null || cronExpr.isEmpty()) {
                return 60000; // 默认1分钟
            }
            
            String[] parts = cronExpr.split("\\s+");
            if (parts.length >= 1 && parts[0].contains("/")) {
                String intervalStr = parts[0].split("/")[1];
                return Long.parseLong(intervalStr) * 1000;
            }
            
            return 60000; // 默认1分钟
            
        } catch (Exception e) {
            log.warn("⚠️  无法解析Cron表达式: {}, 使用默认60秒", cronExpr);
            return 60000;
        }
    }
}
