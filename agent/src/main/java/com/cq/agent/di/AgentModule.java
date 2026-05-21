package com.cq.agent.di;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.VersionManager;
import com.cq.agent.batch.report.FallbackPersistenceService;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.download.AgentDownloader;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.client.upload.UploadService;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.di.provider.*;
import com.cq.agent.executor.CommandExecutor;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * Agent Guice模块
 * 继承链: BatchTaskSchedulerUploaderDecorator extends RetryAwareUploaderDecorator extends AgentUploader
 * 单实例绑定: AgentUploader/RetryAwareUploaderDecorator/UploadService 均指向 BatchTaskSchedulerUploaderDecorator 实例
 */
public class AgentModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AgentConfig.class).toInstance(new AgentConfig());
        bind(CommandExecutor.class).in(Singleton.class);
        bind(FileService.class).in(Singleton.class);
        bind(ChunkedTransferService.class).in(Singleton.class);
        bind(VersionManager.class).in(Singleton.class);
        bind(FileScanner.class).in(Singleton.class);
        bind(ConfigFileManager.class).toProvider(ConfigFileManagerProvider.class).in(Singleton.class);
        bind(FileBatchCompletionTracker.class).toProvider(FileBatchCompletionTrackerProvider.class).in(Singleton.class);
        bind(ProgressReporter.class).toProvider(ProgressReporterProvider.class).in(Singleton.class);
        bind(AgentDownloader.class).toProvider(AgentDownloaderProvider.class).in(Singleton.class);

        // 核心上传组件：单实例绑定
        // BatchTaskSchedulerUploaderDecorator IS-A RetryAwareUploaderDecorator IS-A AgentUploader
        bind(BatchTaskSchedulerUploaderDecorator.class).toProvider(BatchTaskSchedulerProvider.class).in(Singleton.class);
        bind(RetryAwareUploaderDecorator.class).to(BatchTaskSchedulerUploaderDecorator.class);
        bind(AgentUploader.class).to(BatchTaskSchedulerUploaderDecorator.class);
        bind(UploadService.class).to(BatchTaskSchedulerUploaderDecorator.class);

        bind(ConfigChangeListener.class).toProvider(ConfigChangeListenerProvider.class).in(Singleton.class);
        bind(FallbackPersistenceService.class).toProvider(FallbackPersistenceServiceProvider.class).in(Singleton.class);
        bind(HttpServer.class).toProvider(HttpServerProvider.class).in(Singleton.class);
        bind(AgentRegistryService.class).toProvider(AgentRegistryServiceProvider.class).in(Singleton.class);
        bind(AgentBootstrap.class).in(Singleton.class);
    }
}
