package com.cq.agent.di.provider;

import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class ProgressReporterProvider implements Provider<ProgressReporter> {

    private final AgentConfig config;

    @Inject
    public ProgressReporterProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public ProgressReporter get() {
        return new ProgressReporter(config);
    }
}
