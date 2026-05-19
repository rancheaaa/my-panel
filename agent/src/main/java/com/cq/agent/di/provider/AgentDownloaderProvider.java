package com.cq.agent.di.provider;

import com.cq.agent.client.download.AgentDownloader;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class AgentDownloaderProvider implements Provider<AgentDownloader> {

    private final AgentConfig config;

    @Inject
    public AgentDownloaderProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public AgentDownloader get() {
        AgentDownloader downloader = new AgentDownloader(config);
        downloader.init();
        return downloader;
    }
}
