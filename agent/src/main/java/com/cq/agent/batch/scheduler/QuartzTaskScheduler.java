package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quartz任务调度器
 * 基于Quartz实现定时任务调度，支持：
 * 1. 按任务ID隔离（每个任务独立的Job和Trigger）
 * 2. 标准Cron表达式解析
 * 3. 热启动、暂停、恢复、更新、删除
 * 4. 启动时立即执行一次
 *
 * 符合spec.md设计要求
 */
public class QuartzTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuartzTaskScheduler.class);
    private static final String JOB_GROUP = "batch-transfer";
    private static final String TRIGGER_GROUP = "batch-transfer-triggers";
    private static final String TASK_DATA_KEY = "taskConfig";
    private static final String TASK_RUNNABLE_KEY = "taskRunnable";

    private final Scheduler quartzScheduler;
    private final Map<Long, Runnable> taskRunnables = new ConcurrentHashMap<>();

    public QuartzTaskScheduler(Scheduler quartzScheduler) {
        this.quartzScheduler = quartzScheduler;
    }

    /**
     * 启动任务调度
     * @param config 任务配置
     * @param task 要执行的任务
     */
    public void startTask(BatchTransferTaskConfig config, Runnable task) {
        Long taskId = config.getTaskId();
        String jobName = getJobName(taskId);
        String triggerName = getTriggerName(taskId);

        try {
            // 如果任务已存在，先删除
            deleteTask(taskId);

            // 保存任务Runnable
            taskRunnables.put(taskId, task);

            // 创建JobDetail
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put(TASK_DATA_KEY, config);
            jobDataMap.put(TASK_RUNNABLE_KEY, task);

            JobDetail jobDetail = JobBuilder.newJob(BatchTransferJob.class)
                .withIdentity(jobName, JOB_GROUP)
                .usingJobData(jobDataMap)
                .build();

            // 创建CronTrigger
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder
                .cronSchedule(config.getCronExpression())
                .withMisfireHandlingInstructionDoNothing();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerName, TRIGGER_GROUP)
                .withSchedule(scheduleBuilder)
                .build();

            // 调度任务
            quartzScheduler.scheduleJob(jobDetail, trigger);

            // 立即执行一次（首次扫描）
            quartzScheduler.triggerJob(new JobKey(jobName, JOB_GROUP));

            log.info("✅ Quartz任务启动: taskId={}, cron={}", taskId, config.getCronExpression());

        } catch (SchedulerException e) {
            log.error("❌ 启动Quartz任务失败: taskId={}, error={}", taskId, e.getMessage());
            throw new RuntimeException("启动任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 暂停任务
     * @param taskId 任务ID
     */
    public void pauseTask(Long taskId) {
        try {
            JobKey jobKey = new JobKey(getJobName(taskId), JOB_GROUP);
            if (quartzScheduler.checkExists(jobKey)) {
                quartzScheduler.pauseJob(jobKey);
                log.info("⏸️  Quartz任务已暂停: taskId={}", taskId);
            } else {
                log.warn("⚠️  任务不存在，无法暂停: taskId={}", taskId);
            }
        } catch (SchedulerException e) {
            log.error("❌ 暂停任务失败: taskId={}, error={}", taskId, e.getMessage());
            throw new RuntimeException("暂停任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 恢复任务
     * @param taskId 任务ID
     */
    public void resumeTask(Long taskId) {
        try {
            JobKey jobKey = new JobKey(getJobName(taskId), JOB_GROUP);
            if (quartzScheduler.checkExists(jobKey)) {
                quartzScheduler.resumeJob(jobKey);
                log.info("▶️  Quartz任务已恢复: taskId={}", taskId);
            } else {
                log.warn("⚠️  任务不存在，无法恢复: taskId={}", taskId);
            }
        } catch (SchedulerException e) {
            log.error("❌ 恢复任务失败: taskId={}, error={}", taskId, e.getMessage());
            throw new RuntimeException("恢复任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新任务（热更新Cron表达式）
     * @param config 新的任务配置
     */
    public void updateTask(BatchTransferTaskConfig config) {
        Long taskId = config.getTaskId();
        String triggerName = getTriggerName(taskId);

        try {
            TriggerKey triggerKey = new TriggerKey(triggerName, TRIGGER_GROUP);

            if (quartzScheduler.checkExists(triggerKey)) {
                // 更新Trigger的Cron表达式
                CronScheduleBuilder scheduleBuilder = CronScheduleBuilder
                    .cronSchedule(config.getCronExpression())
                    .withMisfireHandlingInstructionDoNothing();

                CronTrigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(scheduleBuilder)
                    .build();

                quartzScheduler.rescheduleJob(triggerKey, newTrigger);
                log.info("🔄 Quartz任务已更新: taskId={}, newCron={}", taskId, config.getCronExpression());
            } else {
                // Trigger不存在，重新启动任务
                Runnable task = taskRunnables.get(taskId);
                if (task != null) {
                    startTask(config, task);
                } else {
                    log.warn("⚠️  无法更新任务，找不到原任务Runnable: taskId={}", taskId);
                }
            }
        } catch (SchedulerException e) {
            log.error("❌ 更新任务失败: taskId={}, error={}", taskId, e.getMessage());
            throw new RuntimeException("更新任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除任务
     * @param taskId 任务ID
     */
    public void deleteTask(Long taskId) {
        try {
            JobKey jobKey = new JobKey(getJobName(taskId), JOB_GROUP);
            if (quartzScheduler.checkExists(jobKey)) {
                quartzScheduler.deleteJob(jobKey);
                taskRunnables.remove(taskId);
                log.info("🗑️  Quartz任务已删除: taskId={}", taskId);
            }
        } catch (SchedulerException e) {
            log.error("❌ 删除任务失败: taskId={}, error={}", taskId, e.getMessage());
            throw new RuntimeException("删除任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查任务是否正在运行
     * @param taskId 任务ID
     * @return 是否运行中
     */
    public boolean isTaskRunning(Long taskId) {
        try {
            JobKey jobKey = new JobKey(getJobName(taskId), JOB_GROUP);
            return quartzScheduler.checkExists(jobKey);
        } catch (SchedulerException e) {
            log.error("❌ 检查任务状态失败: taskId={}, error={}", taskId, e.getMessage());
            return false;
        }
    }

    /**
     * 停止所有任务
     */
    public void shutdown() {
        try {
            for (JobKey jobKey : quartzScheduler.getJobKeys(GroupMatcher.jobGroupEquals(JOB_GROUP))) {
                quartzScheduler.deleteJob(jobKey);
            }
            taskRunnables.clear();
            log.info("⏹️  所有Quartz任务已停止");
        } catch (SchedulerException e) {
            log.error("❌ 停止所有任务失败: {}", e.getMessage());
        }
    }

    /**
     * 获取底层Quartz Scheduler实例
     * 用于高级调度操作（如延迟重试）
     */
    public Scheduler getScheduler() {
        return quartzScheduler;
    }

    // ==================== 内部方法 ====================

    private String getJobName(Long taskId) {
        return "batch-task-" + taskId;
    }

    private String getTriggerName(Long taskId) {
        return "batch-trigger-" + taskId;
    }

    // ==================== Quartz Job 实现 ====================

    /**
     * 批量传输任务Job
     */
    public static class BatchTransferJob implements Job {

        private static final Logger jobLog = LoggerFactory.getLogger(BatchTransferJob.class);

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            JobDataMap dataMap = context.getMergedJobDataMap();
            BatchTransferTaskConfig config = (BatchTransferTaskConfig) dataMap.get(TASK_DATA_KEY);
            Runnable task = (Runnable) dataMap.get(TASK_RUNNABLE_KEY);

            if (task != null) {
                try {
                    jobLog.debug("🚀 执行批量传输任务: taskId={}", config.getTaskId());
                    task.run();
                } catch (Exception e) {
                    jobLog.error("❌ 任务执行异常: taskId={}, error={}", config.getTaskId(), e.getMessage());
                    throw new JobExecutionException(e);
                }
            } else {
                jobLog.warn("⚠️  任务Runnable为空: taskId={}", config.getTaskId());
            }
        }
    }
}
