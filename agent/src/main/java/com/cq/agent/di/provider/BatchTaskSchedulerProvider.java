package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchTaskSchedulerProvider implements Provider<BatchTaskSchedulerUploaderDecorator> {

    private static final Logger logger = LoggerFactory.getLogger(BatchTaskSchedulerProvider.class);

    private final RetryAwareUploaderDecorator retryAwareUploader;
    private final ConfigFileManager configFileManager;
    private final FileScanner fileScanner;
    private final ProgressReporter progressReporter;
    private final FileBatchCompletionTracker fileBatchTracker;

    @Inject
    public BatchTaskSchedulerProvider(RetryAwareUploaderDecorator retryAwareUploader,
                                      ConfigFileManager configFileManager,
                                      FileScanner fileScanner,
                                      ProgressReporter progressReporter,
                                      FileBatchCompletionTracker fileBatchTracker) {
        this.retryAwareUploader = retryAwareUploader;
        this.configFileManager = configFileManager;
        this.fileScanner = fileScanner;
        this.progressReporter = progressReporter;
        this.fileBatchTracker = fileBatchTracker;
    }

    @Override
    public BatchTaskSchedulerUploaderDecorator get() {
        try {
            BatchTaskSchedulerUploaderDecorator batchTaskUploader =
                    new BatchTaskSchedulerUploaderDecorator(retryAwareUploader, configFileManager,
                            retryAwareUploader, fileScanner, progressReporter, fileBatchTracker);
            logger.info("BatchTaskSchedulerUploaderDecorator initialized successfully");
            return batchTaskUploader;
        } catch (Exception e) {
            logger.error("Failed to initialize BatchTaskSchedulerUploaderDecorator: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to initialize BatchTaskSchedulerUploaderDecorator", e);
        }
    }
}
