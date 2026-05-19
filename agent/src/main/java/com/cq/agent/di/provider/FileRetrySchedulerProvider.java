package com.cq.agent.di.provider;

import com.cq.agent.batch.scheduler.FileRetryScheduler;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileRetrySchedulerProvider implements Provider<FileRetryScheduler> {

    private static final Logger logger = LoggerFactory.getLogger(FileRetrySchedulerProvider.class);

    private final RetryAwareUploaderDecorator retryAwareUploader;
    private final AgentConfig config;

    @Inject
    public FileRetrySchedulerProvider(RetryAwareUploaderDecorator retryAwareUploader, AgentConfig config) {
        this.retryAwareUploader = retryAwareUploader;
        this.config = config;
    }

    @Override
    public FileRetryScheduler get() {
        try {
            FileRetryScheduler fileRetryScheduler =
                    new FileRetryScheduler(retryAwareUploader, config.getFailedQueueScanIntervalMs());
            logger.info("FileRetryScheduler initialized (independent Quartz instance)");
            return fileRetryScheduler;
        } catch (Exception e) {
            logger.error("Failed to initialize FileRetryScheduler: {}", e.getMessage());
            throw new RuntimeException("Unable to initialize file retry scheduler", e);
        }
    }
}
