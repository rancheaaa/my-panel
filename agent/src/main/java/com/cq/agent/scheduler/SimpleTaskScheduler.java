package com.cq.agent.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 轻量级任务调度器，替代Quartz Scheduler。
 * 基于ScheduledExecutorService实现，支持：
 * - 固定间隔调度（替代SimpleScheduleBuilder）
 * - Cron表达式调度（替代CronScheduleBuilder）
 * - 暂停/恢复/删除任务
 * - 动态更新Cron表达式
 */
public class SimpleTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimpleTaskScheduler.class);

    private final ScheduledExecutorService executor;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();

    public SimpleTaskScheduler(int poolSize) {
        this.executor = Executors.newScheduledThreadPool(poolSize,
                r -> {
                    Thread t = new Thread(r, "scheduler-thread");
                    t.setDaemon(true);
                    return t;
                });
    }

    public SimpleTaskScheduler() {
        this(4);
    }

    /**
     * 调度固定间隔任务
     */
    public void scheduleFixedRate(String taskName, Runnable task, long initialDelayMs, long intervalMs) {
        remove(taskName);
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                wrapTask(taskName, task), initialDelayMs, intervalMs, TimeUnit.MILLISECONDS);
        tasks.put(taskName, new ScheduledTask(taskName, task, TaskType.FIXED_RATE, future, null, intervalMs));
        log.info("Scheduled fixed-rate task: name={}, intervalMs={}", taskName, intervalMs);
    }

    /**
     * 调度Cron表达式任务
     */
    public void scheduleCron(String taskName, Runnable task, String cronExpression) {
        remove(taskName);
        CronExpression cron = new CronExpression(cronExpression);
        ScheduledTask scheduledTask = new ScheduledTask(taskName, task, TaskType.CRON, null, cron, 0);
        tasks.put(taskName, scheduledTask);
        scheduleNextCronExecution(scheduledTask);
        log.info("Scheduled cron task: name={}, cron={}", taskName, cronExpression);
    }

    /**
     * 立即触发一次任务执行
     */
    public void triggerNow(String taskName) {
        ScheduledTask scheduledTask = tasks.get(taskName);
        if (scheduledTask != null) {
            executor.submit(wrapTask(taskName, scheduledTask.runnable));
            log.debug("Triggered task immediately: name={}", taskName);
        }
    }

    /**
     * 暂停任务
     */
    public void pause(String taskName) {
        ScheduledTask scheduledTask = tasks.get(taskName);
        if (scheduledTask != null && !scheduledTask.paused) {
            scheduledTask.paused = true;
            if (scheduledTask.future != null) {
                scheduledTask.future.cancel(false);
                scheduledTask.future = null;
            }
            log.info("Paused task: name={}", taskName);
        }
    }

    /**
     * 恢复任务
     */
    public void resume(String taskName) {
        ScheduledTask scheduledTask = tasks.get(taskName);
        if (scheduledTask != null && scheduledTask.paused) {
            scheduledTask.paused = false;
            if (scheduledTask.type == TaskType.FIXED_RATE) {
                scheduledTask.future = executor.scheduleAtFixedRate(
                        wrapTask(taskName, scheduledTask.runnable), 0,
                        scheduledTask.intervalMs, TimeUnit.MILLISECONDS);
            } else if (scheduledTask.type == TaskType.CRON) {
                scheduleNextCronExecution(scheduledTask);
            }
            log.info("Resumed task: name={}", taskName);
        }
    }

    /**
     * 移除任务
     */
    public void remove(String taskName) {
        ScheduledTask scheduledTask = tasks.remove(taskName);
        if (scheduledTask != null) {
            if (scheduledTask.future != null) {
                scheduledTask.future.cancel(false);
            }
            log.debug("Removed task: name={}", taskName);
        }
    }

    /**
     * 更新Cron表达式（重新调度）
     */
    public void rescheduleCron(String taskName, String newCronExpression) {
        ScheduledTask scheduledTask = tasks.get(taskName);
        if (scheduledTask != null && scheduledTask.type == TaskType.CRON) {
            if (scheduledTask.future != null) {
                scheduledTask.future.cancel(false);
            }
            scheduledTask.cronExpression = new CronExpression(newCronExpression);
            scheduledTask.future = null;
            if (!scheduledTask.paused) {
                scheduleNextCronExecution(scheduledTask);
            }
            log.info("Rescheduled cron task: name={}, newCron={}", taskName, newCronExpression);
        }
    }

    /**
     * 检查任务是否存在
     */
    public boolean exists(String taskName) {
        return tasks.containsKey(taskName);
    }

    /**
     * 关闭调度器
     */
    public void shutdown() {
        try {
            // 取消所有任务
            for (ScheduledTask task : tasks.values()) {
                if (task.future != null) {
                    task.future.cancel(false);
                }
            }
            tasks.clear();

            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            log.info("SimpleTaskScheduler shutdown complete");
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void scheduleNextCronExecution(ScheduledTask scheduledTask) {
        if (scheduledTask.paused) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextTime = scheduledTask.cronExpression.getNextExecutionTime(now);
            long delayMs = Duration.between(now, nextTime).toMillis();

            if (delayMs < 0) {
                delayMs = 0;
            }

            scheduledTask.future = executor.schedule(
                    () -> {
                        try {
                            scheduledTask.runnable.run();
                        } catch (Exception e) {
                            log.error("Cron task execution error: name={}, error={}",
                                    scheduledTask.name, e.getMessage());
                        }
                        // 执行完后调度下一次
                        scheduleNextCronExecution(scheduledTask);
                    },
                    delayMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to schedule next cron execution: name={}, error={}",
                    scheduledTask.name, e.getMessage());
        }
    }

    private Runnable wrapTask(String taskName, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Task execution error: name={}, error={}", taskName, e.getMessage());
            }
        };
    }

    // ==================== 内部类 ====================

    private enum TaskType {
        FIXED_RATE, CRON
    }

    private static class ScheduledTask {
        final String name;
        final Runnable runnable;
        final TaskType type;
        volatile ScheduledFuture<?> future;
        volatile CronExpression cronExpression;
        final long intervalMs;
        volatile boolean paused = false;

        ScheduledTask(String name, Runnable runnable, TaskType type,
                      ScheduledFuture<?> future, CronExpression cronExpression, long intervalMs) {
            this.name = name;
            this.runnable = runnable;
            this.type = type;
            this.future = future;
            this.cronExpression = cronExpression;
            this.intervalMs = intervalMs;
        }
    }
}
