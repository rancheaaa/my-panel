package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 延迟重试作业（Quartz Job）
 * 用于在指定延迟后重新执行失败的任务
 */
public class DelayedRetryJob implements Job {

    private static final Logger jobLog = LoggerFactory.getLogger(DelayedRetryJob.class);

    public static final String CONFIG_FILE_MANAGER_KEY = "configFileManager";
    public static final String TASK_SCHEDULER_MANAGER_KEY = "taskSchedulerManager";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        long taskId = dataMap.getLong("taskId");

        jobLog.info("⏰ 执行延迟重试任务: taskId={}", taskId);

        try {
            ConfigFileManager configFileManager = (ConfigFileManager) dataMap.get(CONFIG_FILE_MANAGER_KEY);
            BatchTaskSchedulerManager taskSchedulerManager = (BatchTaskSchedulerManager) dataMap
                    .get(TASK_SCHEDULER_MANAGER_KEY);

            if (configFileManager == null || taskSchedulerManager == null) {
                jobLog.error("❌ 缺少必要依赖: configFileManager={}, taskSchedulerManager={}",
                        configFileManager, taskSchedulerManager);
                return;
            }

            AgentTaskConfig config = configFileManager.loadTaskConfig(taskId);

            if (config == null) {
                jobLog.warn("⚠️  未找到任务配置: taskId={}, 跳过重试", taskId);
                return;
            }

            if (!"RUNNING".equals(config.getStatus())) {
                jobLog.info("ℹ️  任务状态为{}，跳过重试: taskId={}", config.getStatus(), taskId);
                return;
            }

            jobLog.info("🔄 重新执行任务: taskId={}, sourceDir={}", taskId, config.getSourceDir());

            Runnable task = taskSchedulerManager.createTaskRunnableForRetry(config);
            task.run();

            jobLog.info("✅ 延迟重试执行完成: taskId={}", taskId);

        } catch (Exception e) {
            jobLog.error("❌ 延迟重试执行失败: taskId={}, error={}", taskId, e.getMessage(), e);

            BatchTaskSchedulerManager taskSchedulerManager = (BatchTaskSchedulerManager) dataMap
                    .get(TASK_SCHEDULER_MANAGER_KEY);
            if (taskSchedulerManager != null) {
                taskSchedulerManager.failTask(taskId, "延迟重试失败: " + e.getMessage());
            }
        }
    }
}
