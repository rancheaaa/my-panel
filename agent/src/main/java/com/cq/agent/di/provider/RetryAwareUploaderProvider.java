package com.cq.agent.di.provider;

import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryAwareUploaderProvider implements Provider<RetryAwareUploaderDecorator> {

    private static final Logger logger = LoggerFactory.getLogger(RetryAwareUploaderProvider.class);

    private final AgentUploader coreUploader;
    private final AgentConfig config;
    private final FileBatchCompletionTracker fileBatchTracker;
    private final ProgressReporter progressReporter;

    @Inject
    public RetryAwareUploaderProvider(AgentUploader coreUploader,
                                      AgentConfig config,
                                      FileBatchCompletionTracker fileBatchTracker,
                                      ProgressReporter progressReporter) {
        this.coreUploader = coreUploader;
        this.config = config;
        this.fileBatchTracker = fileBatchTracker;
        this.progressReporter = progressReporter;
    }

    @Override
    public RetryAwareUploaderDecorator get() {
        RetryAwareUploaderDecorator retryAwareUploader = new RetryAwareUploaderDecorator(
                coreUploader, config, fileBatchTracker, progressReporter);
        logger.info("Upload/Download services initialized with decorator chain: Core -> RetryAware");
        return retryAwareUploader;
    }
}
