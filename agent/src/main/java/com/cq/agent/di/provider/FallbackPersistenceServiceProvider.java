package com.cq.agent.di.provider;

import com.cq.agent.batch.report.FallbackPersistenceService;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FallbackPersistenceServiceProvider implements Provider<FallbackPersistenceService> {

    private static final Logger logger = LoggerFactory.getLogger(FallbackPersistenceServiceProvider.class);

    private final AgentConfig config;
    private final ProgressReporter progressReporter;

    @Inject
    public FallbackPersistenceServiceProvider(AgentConfig config, ProgressReporter progressReporter) {
        this.config = config;
        this.progressReporter = progressReporter;
    }

    @Override
    public FallbackPersistenceService get() {
        FallbackPersistenceService fallbackPersistenceService =
                new FallbackPersistenceService(config.getProgressFallbackDir(), progressReporter, 30, 10);

        progressReporter.setFallbackPersistenceService(fallbackPersistenceService);
        fallbackPersistenceService.startAutoRetry();
        logger.info("FallbackPersistenceService integrated and auto-retry started: storageDir={}",
                config.getProgressFallbackDir());

        return fallbackPersistenceService;
    }
}
