package com.cq.agent.di.provider;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.registry.AgentRegistryService;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class AgentRegistryServiceProvider implements Provider<AgentRegistryService> {

    private final AgentConfig config;

    @Inject
    public AgentRegistryServiceProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public AgentRegistryService get() {
        return new AgentRegistryService(config);
    }
}
