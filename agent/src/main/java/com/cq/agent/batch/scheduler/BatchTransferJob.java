package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 批量传输任务Job（Quartz Job实现）
 * 负责执行实际的批量文件传输任务
 */
public class BatchTransferJob implements Job {

    private static final Logger jobLog = LoggerFactory.getLogger(BatchTransferJob.class);

    public static final String TASK_DATA_KEY = "taskConfig";
    public static final String TASK_RUNNABLE_KEY = "taskRunnable";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        AgentTaskConfig config = (AgentTaskConfig) dataMap.get(TASK_DATA_KEY);
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
