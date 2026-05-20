package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.client.upload.UploadTask;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class RetryAwareUploaderProvider implements Provider<RetryAwareUploaderDecorator> {

    private static final Logger logger = LoggerFactory.getLogger(RetryAwareUploaderProvider.class);

    private final AgentUploader coreUploader;
    private final AgentConfig config;
    private final FileBatchCompletionTracker fileBatchTracker;
    private final ProgressReporter progressReporter;
    private final ConfigFileManager configFileManager;

    @Inject
    public RetryAwareUploaderProvider(AgentUploader coreUploader,
                                      AgentConfig config,
                                      FileBatchCompletionTracker fileBatchTracker,
                                      ProgressReporter progressReporter,
                                      ConfigFileManager configFileManager) {
        this.coreUploader = coreUploader;
        this.config = config;
        this.fileBatchTracker = fileBatchTracker;
        this.progressReporter = progressReporter;
        this.configFileManager = configFileManager;
    }

    @Override
    public RetryAwareUploaderDecorator get() {
        try {
            TransferMetaStore<UploadTask> uploadMetaStore = new TransferMetaStore<>(
                    Path.of(config.getUploadSendingQueueDir()), UploadTask.class);
            Path uploadFailQueueDir = Path.of(config.getUploadFailRetryQueueDir());
            Path uploadFinalFailureQueueDir = Path.of(config.getUploadFinalFailureQueueDir());

            RetryAwareUploaderDecorator retryAwareUploader = new RetryAwareUploaderDecorator(
                    coreUploader, fileBatchTracker, progressReporter,
                    uploadMetaStore, uploadFailQueueDir, configFileManager,
                    uploadFinalFailureQueueDir);
            logger.info("Upload/Download services initialized with decorator chain: Core -> RetryAware");
            return retryAwareUploader;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create RetryAwareUploaderDecorator", e);
        }
    }
}
