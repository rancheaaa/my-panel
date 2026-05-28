package com.cq.agent.di;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.report.FallbackPersistenceService;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.BatchTaskSchedulerUploader;
import com.cq.agent.client.upload.RetryAwareUploader;
import com.cq.agent.client.upload.UploadService;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.executor.CommandExecutor;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.inject.Injector;
import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentModuleTest {

    private Injector injector;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(new AgentModule());
    }

    @Test
    void shouldCreateAgentConfig() {
        AgentConfig config = injector.getInstance(AgentConfig.class);
        assertNotNull(config);
        assertNotNull(config.getAgentId());
    }

    @Test
    void shouldCreateCommandExecutor() {
        CommandExecutor executor = injector.getInstance(CommandExecutor.class);
        assertNotNull(executor);
    }

    @Test
    void shouldCreateFileService() {
        FileService fileService = injector.getInstance(FileService.class);
        assertNotNull(fileService);
    }

    @Test
    void shouldCreateChunkedTransferService() {
        ChunkedTransferService service = injector.getInstance(ChunkedTransferService.class);
        assertNotNull(service);
    }

    @Test
    void shouldCreateConfigFileManager() {
        ConfigFileManager manager = injector.getInstance(ConfigFileManager.class);
        assertNotNull(manager);
    }

    @Test
    void shouldCreateConfigChangeListener() {
        ConfigChangeListener listener = injector.getInstance(ConfigChangeListener.class);
        assertNotNull(listener);
    }

    @Test
    void shouldCreateUploadServiceAsRetryAware() {
        UploadService uploadService = injector.getInstance(UploadService.class);
        assertNotNull(uploadService);
        assertInstanceOf(RetryAwareUploader.class, uploadService);
    }

    @Test
    void shouldCreateBatchTaskScheduler() {
        BatchTaskSchedulerUploader scheduler = injector.getInstance(BatchTaskSchedulerUploader.class);
        assertNotNull(scheduler);
    }

    @Test
    void shouldCreateFileBatchTracker() {
        FileBatchCompletionTracker tracker = injector.getInstance(FileBatchCompletionTracker.class);
        assertNotNull(tracker);
    }

    @Test
    void shouldCreateProgressReporter() {
        com.cq.agent.batch.report.ProgressReporter reporter = injector.getInstance(com.cq.agent.batch.report.ProgressReporter.class);
        assertNotNull(reporter);
    }

    @Test
    void shouldCreateFallbackPersistenceService() {
        FallbackPersistenceService service = injector.getInstance(FallbackPersistenceService.class);
        assertNotNull(service);
    }

    @Test
    void shouldCreateHttpServer() {
        HttpServer server = injector.getInstance(HttpServer.class);
        assertNotNull(server);
    }

    @Test
    void shouldCreateAgentRegistryService() {
        AgentRegistryService service = injector.getInstance(AgentRegistryService.class);
        assertNotNull(service);
    }

    @Test
    void shouldCreateAgentBootstrap() {
        AgentBootstrap bootstrap = injector.getInstance(AgentBootstrap.class);
        assertNotNull(bootstrap);
    }

    @Test
    void shouldReturnSameSingletonForAgentConfig() {
        AgentConfig config1 = injector.getInstance(AgentConfig.class);
        AgentConfig config2 = injector.getInstance(AgentConfig.class);
        assertSame(config1, config2);
    }

    @Test
    void shouldReturnSameSingletonForRetryAwareUploader() {
        RetryAwareUploader instance1 = injector.getInstance(RetryAwareUploader.class);
        RetryAwareUploader instance2 = injector.getInstance(RetryAwareUploader.class);
        assertSame(instance1, instance2);
    }

    @Test
    void shouldReturnSameSingletonForBatchTaskScheduler() {
        BatchTaskSchedulerUploader instance1 = injector.getInstance(BatchTaskSchedulerUploader.class);
        BatchTaskSchedulerUploader instance2 = injector.getInstance(BatchTaskSchedulerUploader.class);
        assertSame(instance1, instance2);
    }
}
