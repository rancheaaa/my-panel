package com.cq.agent.di.provider;

import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class FileBatchCompletionTrackerProvider implements Provider<FileBatchCompletionTracker> {

    private final AgentConfig config;

    @Inject
    public FileBatchCompletionTrackerProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public FileBatchCompletionTracker get() {
        return new FileBatchCompletionTracker(config.getFilebatchPendingDir());
    }
}
