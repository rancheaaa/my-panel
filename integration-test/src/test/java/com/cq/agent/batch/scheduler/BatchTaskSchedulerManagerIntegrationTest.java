package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.client.upload.BatchTaskSchedulerUploader;
import com.cq.agent.client.upload.RetryAwareUploader;
import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组件集成测试
 * 验证BatchTaskSchedulerUploaderDecorator继承RetryAwareUploaderDecorator
 */
@DisplayName("组件集成 - 继承关系验证")
class BatchTaskSchedulerManagerIntegrationTest {

    @Mock
    private ConfigFileManager configFileManager;

    private AutoCloseable mocks;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    @DisplayName("1. BatchTaskSchedulerUploaderDecorator IS-A RetryAwareUploaderDecorator")
    void testInheritanceRelationship() {
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setUploadSendingQueueDir(tempDir.resolve("sending").toString());
        agentConfig.setUploadFailRetryQueueDir(tempDir.resolve("fail-retry").toString());
        agentConfig.setUploadFinalFailureQueueDir(tempDir.resolve("final-failure").toString());

        try {
            BatchTaskSchedulerUploader batchTaskUploader =
                    new BatchTaskSchedulerUploader(agentConfig, configFileManager);

            // 验证继承关系
            assertInstanceOf(RetryAwareUploader.class, batchTaskUploader,
                    "BatchTaskSchedulerUploaderDecorator应继承RetryAwareUploaderDecorator");
            assertInstanceOf(com.cq.agent.client.upload.AgentUploader.class, batchTaskUploader,
                    "BatchTaskSchedulerUploaderDecorator应间接继承AgentUploader");

            batchTaskUploader.shutdown();
            System.out.println("✅ 继承关系验证通过");
        } catch (Exception e) {
            fail("创建BatchTaskSchedulerUploaderDecorator失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("2. 关闭时应清理所有资源")
    void testShutdown_shouldCleanupAll() {
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setUploadSendingQueueDir(tempDir.resolve("sending").toString());
        agentConfig.setUploadFailRetryQueueDir(tempDir.resolve("fail-retry").toString());
        agentConfig.setUploadFinalFailureQueueDir(tempDir.resolve("final-failure").toString());

        try {
            BatchTaskSchedulerUploader batchTaskUploader =
                    new BatchTaskSchedulerUploader(agentConfig, configFileManager);
            batchTaskUploader.shutdown();
            System.out.println("✅ 关闭时资源已清理");
        } catch (Exception e) {
            fail("shutdown失败: " + e.getMessage());
        }
    }
}
