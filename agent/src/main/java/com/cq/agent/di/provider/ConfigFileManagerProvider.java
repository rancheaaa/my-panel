package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import java.nio.file.Path;

public class ConfigFileManagerProvider implements Provider<ConfigFileManager> {

    private final AgentConfig config;

    @Inject
    public ConfigFileManagerProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public ConfigFileManager get() {
        String batchConfigDir = Path.of(config.getFileBaseDirectory(), "batch-config").toString();
        return new ConfigFileManager(batchConfigDir);
    }
}
