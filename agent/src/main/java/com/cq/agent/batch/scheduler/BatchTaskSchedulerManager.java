package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.transfer.BatchTransferManager;
import com.cq.agent.batch.transfer.RetryManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 批量任务调度管理器
 * 负责管理所有批量传输任务的Quartz调度
 * 集成RetryManager处理重试逻辑
 * 集成BatchTransferManager控制并发传输
 */
public class BatchTaskSchedulerManager {

    private static final Logger log = LoggerFactory.getLogger(BatchTaskSchedulerManager.class);

    private final ConfigFileManager configFileManager;
    private final QuartzTaskScheduler quartzTaskScheduler;

    private RetryManager retryManager;
    private BatchTransferManager transferManager;

    public BatchTaskSchedulerManager(ConfigFileManager configFileManager) throws SchedulerException {
        this.configFileManager = configFileManager;
        Scheduler scheduler = new StdSchedulerFactory().getScheduler();
        scheduler.start();
        this.quartzTaskScheduler = new QuartzTaskScheduler(scheduler);
        log.info("✅ BatchTaskSchedulerManager初始化完成");
    }

    public void setRetryManager(RetryManager retryManager) {
        this.retryManager = retryManager;
        log.info("🔄 已设置RetryManager");
    }

    public RetryManager getRetryManager() {
        return retryManager;
    }

    public void setTransferManager(BatchTransferManager transferManager) {
        this.transferManager = transferManager;
        log.info("🔐 已设置BatchTransferManager");
    }

    public BatchTransferManager getTransferManager() {
        return transferManager;
    }

    /**
     * 启动所有本地RUNNING状态的任务
     */
    public void startAllRunningTasks() {
        log.info("🚀 启动所有RUNNING状态的任务...");
        List<BatchTransferTaskConfig> allConfigs = configFileManager.loadAllTaskConfigs();
        int startedCount = 0;

        for (BatchTransferTaskConfig config : allConfigs) {
            Long taskId = config.getTaskId();

            if ("RUNNING".equals(config.getStatus())) {
                startTask(config);
                startedCount++;
            } else {
                log.info("⏸️  任务状态为{}，跳过启动: taskId={}", config.getStatus(), taskId);
            }
        }

        log.info("✅ 已启动{}个RUNNING任务", startedCount);
    }

    /**
     * 启动单个任务
     */
    public void startTask(BatchTransferTaskConfig config) {
        Long taskId = config.getTaskId();
        String cronExpression = config.getCronExpression();

        if (cronExpression == null || cronExpression.isBlank()) {
            log.warn("⚠️  任务缺少Cron表达式，无法调度: taskId={}", taskId);
            return;
        }

        Runnable task = createTaskRunnable(config);

        quartzTaskScheduler.startTask(config, task);
        log.info("✅ 任务已启动: taskId={}, cron={}", taskId, cronExpression);
    }

    /**
     * 暂停任务
     */
    public void pauseTask(Long taskId) {
        quartzTaskScheduler.pauseTask(taskId);
        log.info("⏸️  任务已暂停: taskId={}", taskId);
    }

    /**
     * 恢复任务
     */
    public void resumeTask(Long taskId) {
        quartzTaskScheduler.resumeTask(taskId);
        log.info("▶️  任务已恢复: taskId={}", taskId);
    }

    /**
     * 更新任务（热更新）
     */
    public void updateTask(BatchTransferTaskConfig config) {
        Long taskId = config.getTaskId();

        if (quartzTaskScheduler.isTaskRunning(taskId)) {
            quartzTaskScheduler.updateTask(config);
            log.info("🔄 任务已更新: taskId={}", taskId);
        } else {
            startTask(config);
            log.info("🚀 任务未运行，已启动: taskId={}", taskId);
        }
    }

    /**
     * 删除任务
     */
    public void deleteTask(Long taskId) {
        quartzTaskScheduler.deleteTask(taskId);
        log.info("🗑️  任务已删除: taskId={}", taskId);
    }

    /**
     * 停止所有任务
     */
    public void shutdown() {
        quartzTaskScheduler.shutdown();
        if (retryManager != null) {
            retryManager.clearAll();
        }
        log.info("⏹️  所有任务已停止");
    }

    /**
     * 检查任务是否运行中
     */
    public boolean isTaskRunning(Long taskId) {
        return quartzTaskScheduler.isTaskRunning(taskId);
    }

    /**
     * 标记任务完成并释放资源
     * @param taskId 任务ID
     */
    public void completeTask(Long taskId) {
        String taskIdStr = String.valueOf(taskId);
        
        if (transferManager != null) {
            transferManager.release(taskIdStr);
            log.info("✅ 任务已完成，释放许可: taskId={}", taskId);
        }
        
        if (retryManager != null) {
            retryManager.recordSuccess(taskId);
        }
    }

    /**
     * 处理任务失败
     * @param taskId 任务ID
     * @param error 错误信息
     * @return true表示应继续重试，false表示最终失败
     */
    public boolean failTask(Long taskId, String error) {
        log.warn("⚠️  任务执行失败: taskId={}, error={}", taskId, error);

        // 释放传输许可
        if (transferManager != null) {
            transferManager.release(String.valueOf(taskId));
        }

        // 检查是否应重试
        if (retryManager == null) {
            log.warn("⚠️  RetryManager未设置，不进行重试");
            return false;
        }

        boolean shouldRetry = retryManager.shouldRetry(taskId, error);

        if (shouldRetry) {
            long delayMs = retryManager.calculateNextRetryDelay(taskId, retryManager.getRetryCount(taskId));
            log.info("🔄 将在{}ms后重试: taskId={}, attempt={}/{}", delayMs, taskId, 
                retryManager.getRetryCount(taskId), retryManager.getMaxRetries());
            
            // TODO: 调度延迟重试
            return true;
        } else {
            log.error("❌ 任务最终失败（超过最大重试次数）: taskId={}, maxRetries={}", 
                taskId, retryManager.getMaxRetries());
            return false;
        }
    }

    private Runnable createTaskRunnable(BatchTransferTaskConfig config) {
        return () -> {
            Long taskId = config.getTaskId();
            String taskIdStr = String.valueOf(taskId);
            
            log.info("🚀 执行任务扫描: taskId={}, sourceDir={}", taskId, config.getSourceDir());

            try {
                // 获取传输许可
                if (transferManager != null && !transferManager.tryAcquire(taskIdStr)) {
                    log.warn("⚠️  无法获取传输许可，跳过本次执行: taskId={}", taskId);
                    failTask(taskId, "获取传输许可超时");
                    return;
                }

                try {
                    // TODO: 实际文件扫描和传输逻辑
                    // 1. 扫描源目录
                    // 2. 匹配文件模式
                    // 3. 传输到目标Agent
                    // 4. 记录传输结果
                    log.debug("📁 扫描目录: {}", config.getSourceDir());
                    log.debug("📋 包含模式: {}", config.getIncludePatterns());
                    log.debug("🚫 排除模式: {}", config.getExcludePatterns());
                    
                    // 模拟成功
                    completeTask(taskId);
                    
                } catch (Exception e) {
                    log.error("❌ 任务执行异常: taskId={}, error={}", taskId, e.getMessage());
                    failTask(taskId, e.getMessage());
                }
                
            } catch (Exception e) {
                log.error("❌ 任务执行异常: taskId={}, error={}", taskId, e.getMessage());
            }
        };
    }
}
