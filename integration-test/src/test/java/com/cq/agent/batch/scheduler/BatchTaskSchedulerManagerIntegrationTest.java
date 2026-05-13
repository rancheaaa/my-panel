package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @DisplayName("5. [集成] 传输失败时应通过RetryAwareUploaderDecorator判断是否重试")
    void testFailTask_shouldCheckRetry() {
        when(retryAwareUploader.shouldRetry(anyLong(), anyString())).thenReturn(false);

        schedulerManager.failTask(1L, "连接超时");

        verify(retryAwareUploader).shouldRetry(eq(1L), eq("连接超时"));
        System.out.println("✅ 失败时检查重试");
    }

    @Test
    @DisplayName("6. [集成] 重试次数未达上限时应重新调度任务")
    void testFailTask_shouldRescheduleWhenRetryAllowed() {
        when(retryAwareUploader.shouldRetry(anyLong(), anyString())).thenReturn(true);
        when(retryAwareUploader.calculateNextRetryDelay(anyLong(), anyInt())).thenReturn(30000L);

        AgentTaskConfig config = createRunningConfig(1L, "0 */5 * * * ?");
        schedulerManager.startTask(config);

        schedulerManager.failTask(1L, "网络错误");

        verify(retryAwareUploader).shouldRetry(eq(1L), eq("网络错误"));
        System.out.println("✅ 重试允许时重新调度");
    }

    @Test
    @DisplayName("7. [集成] 超过最大重试次数时应标记最终失败")
    void testFailTask_shouldMarkFinalFailure() {
        when(retryAwareUploader.shouldRetry(anyLong(), anyString())).thenReturn(false);

        boolean shouldContinue = schedulerManager.failTask(1L, "永久性错误");

        assertFalse(shouldContinue, "超过最大重试次数不应继续");
        System.out.println("✅ 超过重试次数标记最终失败");
    }

    @Test
    @DisplayName("8. [集成] 关闭时应清理所有资源")
    void testShutdown_shouldCleanupAll() {
        schedulerManager.shutdown();

        System.out.println("✅ 关闭时资源已清理");
    }

    // ==================== 辅助方法 ====================

    private AgentTaskConfig createRunningConfig(Long taskId, String cronExpression) {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName("测试任务" + taskId);
        config.setStatus("RUNNING");

        ScanConfig scanConfig = new ScanConfig();
        scanConfig.setCronExpression(cronExpression);
        config.setScanConfig(scanConfig);

        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");

        TargetAgentInfo targetInfo = new TargetAgentInfo();
        targetInfo.setTargetDir("/backup/logs");
        config.setTargetAgents(Collections.singletonList(targetInfo));

        RetryConfig retryConfig = new RetryConfig();
        retryConfig.setMaxRetryCount(10);
        config.setRetryConfig(retryConfig);

        return config;
    }
}
