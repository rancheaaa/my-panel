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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentModuleTest {

    private AppContext ctx;

    @BeforeEach
    void setUp() {
        AppContext.resetForTest();
        ctx = AppContext.getInstance();
    }

    @Test
    void shouldCreateAgentConfig() {
        AgentConfig config = ctx.getAgentConfig();
        assertNotNull(config);
        assertNotNull(config.getAgentId());
    }

    @Test
    void shouldCreateCommandExecutor() {
        CommandExecutor executor = ctx.getCommandExecutor();
        assertNotNull(executor);
    }

    @Test
    void shouldCreateFileService() {
        FileService fileService = ctx.getFileService();
        assertNotNull(fileService);
    }

    @Test
    void shouldCreateChunkedTransferService() {
        ChunkedTransferService service = ctx.getChunkedTransferService();
        assertNotNull(service);
    }

    @Test
    void shouldCreateConfigFileManager() {
        ConfigFileManager manager = ctx.getConfigFileManager();
        assertNotNull(manager);
    }

    @Test
    void shouldCreateConfigChangeListener() {
        ConfigChangeListener listener = ctx.getConfigChangeListener();
        assertNotNull(listener);
    }

    @Test
    void shouldCreateUploadServiceAsRetryAware() {
        UploadService uploadService = ctx.getUploadService();
        assertNotNull(uploadService);
        assertInstanceOf(RetryAwareUploader.class, uploadService);
    }

    @Test
    void shouldCreateBatchTaskScheduler() {
        BatchTaskSchedulerUploader scheduler = ctx.getBatchTaskSchedulerUploader();
        assertNotNull(scheduler);
    }

    @Test
    void shouldCreateFileBatchTracker() {
        FileBatchCompletionTracker tracker = ctx.getFileBatchCompletionTracker();
        assertNotNull(tracker);
    }

    @Test
    void shouldCreateProgressReporter() {
        com.cq.agent.batch.report.ProgressReporter reporter = ctx.getProgressReporter();
        assertNotNull(reporter);
    }

    @Test
    void shouldCreateFallbackPersistenceService() {
        FallbackPersistenceService service = ctx.getFallbackPersistenceService();
        assertNotNull(service);
    }

    @Test
    void shouldCreateHttpServer() {
        HttpServer server = ctx.getHttpServer();
        assertNotNull(server);
    }

    @Test
    void shouldCreateAgentRegistryService() {
        AgentRegistryService service = ctx.getAgentRegistryService();
        assertNotNull(service);
    }

    @Test
    void shouldCreateAgentBootstrap() {
        AgentBootstrap bootstrap = ctx.getAgentBootstrap();
        assertNotNull(bootstrap);
    }

    @Test
    void shouldReturnSameSingletonForAgentConfig() {
        AgentConfig config1 = ctx.getAgentConfig();
        AgentConfig config2 = ctx.getAgentConfig();
        assertSame(config1, config2);
    }

    @Test
    void shouldReturnSameSingletonForRetryAwareUploader() {
        RetryAwareUploader instance1 = ctx.getRetryAwareUploader();
        RetryAwareUploader instance2 = ctx.getRetryAwareUploader();
        assertSame(instance1, instance2);
    }

    @Test
    void shouldReturnSameSingletonForBatchTaskScheduler() {
        BatchTaskSchedulerUploader instance1 = ctx.getBatchTaskSchedulerUploader();
        BatchTaskSchedulerUploader instance2 = ctx.getBatchTaskSchedulerUploader();
        assertSame(instance1, instance2);
    }
}
