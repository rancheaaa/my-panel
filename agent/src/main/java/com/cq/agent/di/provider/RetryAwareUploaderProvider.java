package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * RetryAwareUploaderDecorator的Guice Provider
 * 创建继承AgentUploader的重试装饰者实例
 */
public class RetryAwareUploaderProvider implements Provider<RetryAwareUploaderDecorator> {

    private static final Logger logger = LoggerFactory.getLogger(RetryAwareUploaderProvider.class);

    private final AgentConfig config;
    private final FileBatchCompletionTracker fileBatchTracker;
    private final ProgressReporter progressReporter;
    private final ConfigFileManager configFileManager;

    @Inject
    public RetryAwareUploaderProvider(AgentConfig config,
                                      FileBatchCompletionTracker fileBatchTracker,
                                      ProgressReporter progressReporter,
                                      ConfigFileManager configFileManager) {
        this.config = config;
        this.fileBatchTracker = fileBatchTracker;
        this.progressReporter = progressReporter;
        this.configFileManager = configFileManager;
    }

    @Override
    public RetryAwareUploaderDecorator get() {
        try {
            Path uploadFinalFailureQueueDir = Path.of(config.getUploadFinalFailureQueueDir());

            RetryAwareUploaderDecorator retryAwareUploader = new RetryAwareUploaderDecorator(
                    config, fileBatchTracker, progressReporter,
                    configFileManager, uploadFinalFailureQueueDir);
            logger.info("RetryAwareUploaderDecorator initialized (inheritance mode)");
            return retryAwareUploader;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create RetryAwareUploaderDecorator", e);
        }
    }
}
