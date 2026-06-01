package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.BatchTaskSchedulerUploader;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BatchTaskSchedulerUploaderDecorator的Guice Provider
 * 创建继承RetryAwareUploaderDecorator的批量调度装饰者实例
 * 该实例同时是AgentUploader、RetryAwareUploaderDecorator和BatchTaskSchedulerUploaderDecorator
 */
public class BatchTaskSchedulerProvider implements Provider<BatchTaskSchedulerUploader> {

    private static final Logger logger = LoggerFactory.getLogger(BatchTaskSchedulerProvider.class);

    private final AgentConfig config;
    private final ConfigFileManager configFileManager;
    private final FileBatchCompletionTracker fileBatchTracker;
    private final ProgressReporter progressReporter;
    private final FileScanner fileScanner;

    @Inject
    public BatchTaskSchedulerProvider(AgentConfig config,
                                      ConfigFileManager configFileManager,
                                      FileBatchCompletionTracker fileBatchTracker,
                                      ProgressReporter progressReporter,
                                      FileScanner fileScanner) {
        this.config = config;
        this.configFileManager = configFileManager;
        this.fileBatchTracker = fileBatchTracker;
        this.progressReporter = progressReporter;
        this.fileScanner = fileScanner;
    }

    @Override
    public BatchTaskSchedulerUploader get() {
        try {
            BatchTaskSchedulerUploader batchTaskUploader =
                    new BatchTaskSchedulerUploader(
                            config, configFileManager, fileBatchTracker,
                            progressReporter, fileScanner);
            // 初始化AgentUploader的工作线程
            batchTaskUploader.init();
            logger.info("BatchTaskSchedulerUploaderDecorator initialized (inheritance mode)");
            return batchTaskUploader;
        } catch (Exception e) {
            logger.error("Failed to initialize BatchTaskSchedulerUploaderDecorator: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to initialize BatchTaskSchedulerUploaderDecorator", e);
        }
    }
}
