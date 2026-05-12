package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.transfer.RetryManager;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobDataMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 失败队列扫描 Job
 * 由 Quartz 定时触发，扫描失败队列并重试失败的文件
 */
public class FailedQueueScannerJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(FailedQueueScannerJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("⏰ 开始扫描失败队列...");

        try {
            RetryManager retryManager = getRetryManager(context);

            if (retryManager != null) {
                // 扫描上传失败队列
                retryManager.scanAndRetryFailedUploads();

                // 扫描下载失败队列
//                retryManager.scanAndRetryFailedDownloads();

                log.info("✅ 失败队列扫描完成");
            } else {
                log.warn("⚠️ RetryManager 未初始化，跳过扫描");
            }

        } catch (Exception e) {
            log.error("❌ 执行失败队列扫描Job异常: {}", e.getMessage(), e);
            throw new JobExecutionException("失败队列扫描失败", e, false);
        }
    }

    /**
     * 从执行上下文获取 RetryManager 实例
     * 支持从 JobDataMap 或 Spring 容器获取
     */
    private RetryManager getRetryManager(JobExecutionContext context) {
        // 方式1: 从 JobDataMap 获取
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        if (jobDataMap != null) {
            Object retryManager = jobDataMap.get("retryManager");
            if (retryManager instanceof RetryManager) {
                return (RetryManager) retryManager;
            }
        }

        // 方式2: TODO: 从 Spring 容器获取（如果使用Spring集成）
        // return ApplicationContext.getBean(RetryManager.class);

        log.warn("⚠️ 无法从 JobDataMap 获取 RetryManager，请确保在调度时注入");
        return null;
    }
}
