package com.cq.agent.batch.scheduler;

import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobDataMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 失败队列扫描 Job
 * 由 Quartz 定时触发，扫描失败队列并重试失败的文件
 * 已从使用 RetryManager 改为使用 RetryAwareUploaderDecorator（装饰者模式）
 * 添加 @DisallowConcurrentExecution 防止扫描时间超过间隔时并发执行导致重复重试
 */
@DisallowConcurrentExecution
public class FailedQueueScannerJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(FailedQueueScannerJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("⏰ 开始扫描失败队列...");

        try {
            RetryAwareUploaderDecorator retryDecorator = getRetryDecorator(context);

            if (retryDecorator != null) {
                // 扫描上传失败队列（装饰者内部实现）
                retryDecorator.scanAndRetryFailedUploads();

                log.info("✅ 失败队列扫描完成");
            } else {
                log.warn("⚠️ RetryAwareUploaderDecorator 未初始化，跳过扫描");
            }

        } catch (Exception e) {
            log.error("❌ 执行失败队列扫描Job异常: {}", e.getMessage(), e);
            throw new JobExecutionException("失败队列扫描失败", e, false);
        }
    }

    /**
     * 从执行上下文获取 RetryAwareUploaderDecorator 实例
     */
    private RetryAwareUploaderDecorator getRetryDecorator(JobExecutionContext context) {
        // 方式1: 从 JobDataMap 获取
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        if (jobDataMap != null) {
            Object retryDecorator = jobDataMap.get("retryAwareUploader");
            if (retryDecorator instanceof RetryAwareUploaderDecorator) {
                return (RetryAwareUploaderDecorator) retryDecorator;
            }
        }

        log.warn("⚠️ 无法从 JobDataMap 获取 RetryAwareUploaderDecorator，请确保在调度时注入");
        return null;
    }
}
