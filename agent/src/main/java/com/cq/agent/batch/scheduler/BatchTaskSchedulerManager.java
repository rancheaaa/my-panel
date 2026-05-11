package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 批量任务调度管理器
 * 负责管理所有批量传输任务的Quartz调度
 * 连接ConfigFileManager和QuartzTaskScheduler
 */
public class BatchTaskSchedulerManager {

    private static final Logger log = LoggerFactory.getLogger(BatchTaskSchedulerManager.class);

    private final ConfigFileManager configFileManager;
    private final QuartzTaskScheduler quartzTaskScheduler;

    public BatchTaskSchedulerManager(ConfigFileManager configFileManager) throws SchedulerException {
        this.configFileManager = configFileManager;
        Scheduler scheduler = new StdSchedulerFactory().getScheduler();
        scheduler.start();
        this.quartzTaskScheduler = new QuartzTaskScheduler(scheduler);
        log.info("✅ BatchTaskSchedulerManager初始化完成");
    }

    /**
     * 启动所有本地RUNNING状态的任务
     * 在Agent启动时调用
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
     * @param config 任务配置
     */
    public void startTask(BatchTransferTaskConfig config) {
        Long taskId = config.getTaskId();
        String cronExpression = config.getCronExpression();

        if (cronExpression == null || cronExpression.isBlank()) {
            log.warn("⚠️  任务缺少Cron表达式，无法调度: taskId={}", taskId);
            return;
        }

        // 创建任务执行逻辑（扫描并传输文件）
        Runnable task = createTaskRunnable(config);

        quartzTaskScheduler.startTask(config, task);
        log.info("✅ 任务已启动: taskId={}, cron={}", taskId, cronExpression);
    }

    /**
     * 暂停任务
     * @param taskId 任务ID
     */
    public void pauseTask(Long taskId) {
        quartzTaskScheduler.pauseTask(taskId);
        log.info("⏸️  任务已暂停: taskId={}", taskId);
    }

    /**
     * 恢复任务
     * @param taskId 任务ID
     */
    public void resumeTask(Long taskId) {
        quartzTaskScheduler.resumeTask(taskId);
        log.info("▶️  任务已恢复: taskId={}", taskId);
    }

    /**
     * 更新任务（热更新）
     * @param config 新的任务配置
     */
    public void updateTask(BatchTransferTaskConfig config) {
        Long taskId = config.getTaskId();

        if (quartzTaskScheduler.isTaskRunning(taskId)) {
            // 任务正在运行，更新Cron表达式
            quartzTaskScheduler.updateTask(config);
            log.info("🔄 任务已更新: taskId={}", taskId);
        } else {
            // 任务未运行，启动它
            startTask(config);
            log.info("🚀 任务未运行，已启动: taskId={}", taskId);
        }
    }

    /**
     * 删除任务
     * @param taskId 任务ID
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
        log.info("⏹️  所有任务已停止");
    }

    /**
     * 检查任务是否运行中
     * @param taskId 任务ID
     * @return 是否运行中
     */
    public boolean isTaskRunning(Long taskId) {
        return quartzTaskScheduler.isTaskRunning(taskId);
    }

    // ==================== 内部方法 ====================

    /**
     * 创建任务执行逻辑
     * @param config 任务配置
     * @return 任务Runnable
     */
    private Runnable createTaskRunnable(BatchTransferTaskConfig config) {
        return () -> {
            Long taskId = config.getTaskId();
            log.info("🚀 执行任务扫描: taskId={}, sourceDir={}", taskId, config.getSourceDir());

            try {
                // TODO: 实际文件扫描和传输逻辑
                // 1. 扫描源目录
                // 2. 匹配文件模式
                // 3. 传输到目标Agent
                // 4. 记录传输结果
                log.debug("📁 扫描目录: {}", config.getSourceDir());
                log.debug("📋 包含模式: {}", config.getIncludePatterns());
                log.debug("🚫 排除模式: {}", config.getExcludePatterns());
            } catch (Exception e) {
                log.error("❌ 任务执行异常: taskId={}, error={}", taskId, e.getMessage());
            }
        };
    }
}
