package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.client.upload.BatchTaskSchedulerUploader;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.executor.CommandExecutor;
import com.cq.agent.server.HttpServer;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class HttpServerProvider implements Provider<HttpServer> {

    private final AgentConfig config;
    private final CommandExecutor commandExecutor;
    private final FileService fileService;
    private final ChunkedTransferService chunkedTransferService;
    private final ConfigFileManager configFileManager;
    private final ConfigChangeListener configChangeListener;
    private final BatchTaskSchedulerUploader batchTaskUploader;

    @Inject
    public HttpServerProvider(AgentConfig config,
                              CommandExecutor commandExecutor,
                              FileService fileService,
                              ChunkedTransferService chunkedTransferService,
                              ConfigFileManager configFileManager,
                              ConfigChangeListener configChangeListener,
                              BatchTaskSchedulerUploader batchTaskUploader) {
        this.config = config;
        this.commandExecutor = commandExecutor;
        this.fileService = fileService;
        this.chunkedTransferService = chunkedTransferService;
        this.configFileManager = configFileManager;
        this.configChangeListener = configChangeListener;
        this.batchTaskUploader = batchTaskUploader;
    }

    @Override
    public HttpServer get() {
        return new HttpServer(config, commandExecutor, fileService, chunkedTransferService,
                configFileManager, configChangeListener, batchTaskUploader);
    }
}
