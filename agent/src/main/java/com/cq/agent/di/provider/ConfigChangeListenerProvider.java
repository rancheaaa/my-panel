package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.VersionManager;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigChangeListenerProvider implements Provider<ConfigChangeListener> {

    private static final Logger logger = LoggerFactory.getLogger(ConfigChangeListenerProvider.class);

    private final ConfigFileManager configFileManager;
    private final VersionManager versionManager;
    private final BatchTaskSchedulerUploaderDecorator batchTaskUploader;

    @Inject
    public ConfigChangeListenerProvider(ConfigFileManager configFileManager,
                                        VersionManager versionManager,
                                        BatchTaskSchedulerUploaderDecorator batchTaskUploader) {
        this.configFileManager = configFileManager;
        this.versionManager = versionManager;
        this.batchTaskUploader = batchTaskUploader;
    }

    @Override
    public ConfigChangeListener get() {
        ConfigChangeListener configChangeListener = new ConfigChangeListener(
                configFileManager, versionManager,
                taskId -> {
                    AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(taskId);
                    if (taskConfig != null) {
                        batchTaskUploader.updateTask(taskConfig);
                        batchTaskUploader.registerTaskConfig(taskId, taskConfig);
                    }
                },
                ctx -> {
                    if ("NEW_TASK".equals(ctx.field)) {
                        AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                        if (taskConfig != null && "RUNNING".equals(taskConfig.getStatus())) {
                            batchTaskUploader.startTask(taskConfig);
                            batchTaskUploader.registerTaskConfig(ctx.taskId, taskConfig);
                        }
                    } else if ("CONFIG_UPDATED".equals(ctx.field)) {
                        AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                        if (taskConfig != null) {
                            batchTaskUploader.updateTask(taskConfig);
                            batchTaskUploader.registerTaskConfig(ctx.taskId, taskConfig);
                        }
                    }
                });

        logger.info("ConfigChangeListener initialized with callbacks registered");
        return configChangeListener;
    }
}
