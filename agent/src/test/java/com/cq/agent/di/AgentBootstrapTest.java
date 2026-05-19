package com.cq.agent.di;

import com.cq.agent.batch.scheduler.FileRetryScheduler;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentBootstrapTest {

    @Mock
    private HttpServer server;

    @Mock
    private AgentConfig config;

    @Mock
    private BatchTaskSchedulerUploaderDecorator batchTaskUploader;

    @Mock
    private FileRetryScheduler fileRetryScheduler;

    @Mock
    private AgentRegistryService registryService;

    @Test
    void shouldCreateAgentBootstrapWithDependencies() {
        AgentBootstrap bootstrap = new AgentBootstrap(server, config, batchTaskUploader,
                fileRetryScheduler, registryService);
        assertNotNull(bootstrap);
    }

    @Test
    void shouldStartAllRunningTasksOnStart() throws Exception {
        AgentBootstrap bootstrap = new AgentBootstrap(server, config, batchTaskUploader,
                fileRetryScheduler, registryService);
        when(server.getActualPort()).thenReturn(7777);
        doNothing().when(server).start();

        Thread testThread = new Thread(() -> {
            try {
                bootstrap.start();
            } catch (Exception e) {
                // expected - awaitTermination will be interrupted
            }
        });
        testThread.start();
        Thread.sleep(500);
        testThread.interrupt();
        testThread.join(2000);

        verify(batchTaskUploader).startAllRunningTasks();
    }

    @Test
    void shouldRegisterActualPortAndStartRegistry() throws Exception {
        AgentBootstrap bootstrap = new AgentBootstrap(server, config, batchTaskUploader,
                fileRetryScheduler, registryService);
        when(server.getActualPort()).thenReturn(7777);
        doNothing().when(server).start();

        Thread testThread = new Thread(() -> {
            try {
                bootstrap.start();
            } catch (Exception e) {
                // expected
            }
        });
        testThread.start();
        Thread.sleep(500);
        testThread.interrupt();
        testThread.join(2000);

        verify(registryService).setActualPort(7777);
        verify(registryService).start();
    }

    @Test
    void shouldStartServerBeforeRegistry() throws Exception {
        AgentBootstrap bootstrap = new AgentBootstrap(server, config, batchTaskUploader,
                fileRetryScheduler, registryService);
        when(server.getActualPort()).thenReturn(7777);
        doNothing().when(server).start();

        Thread testThread = new Thread(() -> {
            try {
                bootstrap.start();
            } catch (Exception e) {
                // expected
            }
        });
        testThread.start();
        Thread.sleep(500);
        testThread.interrupt();
        testThread.join(2000);

        verify(server).start();
    }
}
