package com.cq.agent.batch.scheduler;

import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级重试调度器
 * 使用独立的 Quartz 实例管理文件失败队列扫描，与 BatchTaskSchedulerManager 完全隔离。
 */
public class FileRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileRetryScheduler.class);

    private final Scheduler retryScheduler;
    private final RetryAwareUploaderDecorator retryAwareUploader;

    /**
     * 构造函数
     * @param retryAwareUploader 重试感知的上传装饰者
     * @param scanIntervalMs 失败队列扫描间隔（毫秒）
     * @throws SchedulerException 如果 Quartz 调度器初始化失败
     */
    public FileRetryScheduler(RetryAwareUploaderDecorator retryAwareUploader,
                             long scanIntervalMs) throws SchedulerException {
        this.retryAwareUploader = retryAwareUploader;

        this.retryScheduler = new StdSchedulerFactory().getScheduler();
        this.retryScheduler.start();

        scheduleFailedQueueScannerJob(scanIntervalMs);

        log.info("✅ FileRetryScheduler初始化完成, 扫描间隔: {}ms", scanIntervalMs);
    }

    /**
     * 关闭调度器
     */
    public void shutdown() {
        try {
            if (retryScheduler != null && !retryScheduler.isShutdown()) {
                retryScheduler.shutdown(true);
                log.info("⏹️ FileRetryScheduler已关闭");
            }
        } catch (SchedulerException e) {
            log.error("❌ 关闭FileRetryScheduler异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 注册并调度 FailedQueueScannerJob
     */
    private void scheduleFailedQueueScannerJob(long scanIntervalMs) {
        try {
            JobDetail failedQueueScannerJob = JobBuilder.newJob(FailedQueueScannerJob.class)
                    .withIdentity("failedQueueScanner", "retry-group")
                    .build();

            failedQueueScannerJob.getJobDataMap().put("retryAwareUploader", retryAwareUploader);

            Trigger failedQueueScannerTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("failedQueueScannerTrigger", "retry-group")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(scanIntervalMs)
                            .repeatForever())
                    .build();

            retryScheduler.scheduleJob(failedQueueScannerJob, failedQueueScannerTrigger);

            log.info("✅ FailedQueueScannerJob已注册, 扫描间隔: {}ms ({}分钟)",
                    scanIntervalMs, scanIntervalMs / 60000);
        } catch (Exception e) {
            log.warn("⚠️ 注册FailedQueueScannerJob失败: {}", e.getMessage());
        }
    }
}
