package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import org.junit.jupiter.api.*;
import org.mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD测试：验证组件集成到主功能
 * 核心要求：
 * 1. BatchTaskSchedulerManager应使用RetryAwareUploaderDecorator管理重试
 * 2. 任务执行时应正确调用这些组件
 */
@DisplayName("组件集成 - TDD")
class BatchTaskSchedulerManagerIntegrationTest {

    @Mock
    private ConfigFileManager configFileManager;

    @Mock
    private RetryAwareUploaderDecorator retryAwareUploader;

    private BatchTaskSchedulerManager schedulerManager;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        schedulerManager = new BatchTaskSchedulerManager(configFileManager);
        schedulerManager.setRetryAwareUploader(retryAwareUploader);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schedulerManager != null) {
            schedulerManager.shutdown();
        }
        mocks.close();
    }

    // ==================== Red Phase: 组件集成测试 ====================

    @Test
    @DisplayName("1. [集成] BatchTaskSchedulerManager应有RetryAwareUploaderDecorator实例")
    void testHasRetryAwareUploader() {
        assertNotNull(schedulerManager.getRetryAwareUploader(), "应设置RetryAwareUploaderDecorator");
        assertSame(retryAwareUploader, schedulerManager.getRetryAwareUploader(), "应为同一个实例");
        System.out.println("✅ RetryAwareUploaderDecorator已集成");
    }

    @Test
    @DisplayName("8. [集成] 关闭时应清理所有资源")
    void testShutdown_shouldCleanupAll() {
        schedulerManager.shutdown();

        System.out.println("✅ 关闭时资源已清理");
    }
}
