package com.cq.agent.di.provider;

import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class AgentUploaderProvider implements Provider<AgentUploader> {

    private final AgentConfig config;

    @Inject
    public AgentUploaderProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public AgentUploader get() {
        AgentUploader uploader = new AgentUploader(config);
        uploader.init();
        return uploader;
    }
}
