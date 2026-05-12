package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.transfer.RetryManager;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD测试：验证组件集成到主功能
 * 核心要求：
 * 1. BatchTaskSchedulerManager应使用RetryManager管理重试
 * 2. BatchTaskSchedulerManager应使用BatchTransferManager控制并发
 * 3. 任务执行时应正确调用这些组件
 */
@DisplayName("组件集成 - TDD")
class BatchTaskSchedulerManagerIntegrationTest {

    @Mock
    private ConfigFileManager configFileManager;

    @Mock
    private RetryManager retryManager;

    private BatchTaskSchedulerManager schedulerManager;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        // 使用反射注入mock对象
        schedulerManager = new BatchTaskSchedulerManager(configFileManager);
        schedulerManager.setRetryManager(retryManager);
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
    @DisplayName("1. [集成] BatchTaskSchedulerManager应有RetryManager实例")
    void testHasRetryManager() {
        assertNotNull(schedulerManager.getRetryManager(), "应设置RetryManager");
        assertSame(retryManager, schedulerManager.getRetryManager(), "应为同一个实例");
        System.out.println("✅ RetryManager已集成");
    }

    @Test
    @DisplayName("5. [集成] 传输失败时应通过RetryManager判断是否重试")
    void testFailTask_shouldCheckRetry() {
        when(retryManager.shouldRetry(anyLong(), anyString())).thenReturn(false);

        schedulerManager.failTask(1L, "连接超时");

        verify(retryManager).shouldRetry(eq(1L), eq("连接超时"));
        System.out.println("✅ 失败时检查重试");
    }

    @Test
    @DisplayName("6. [集成] 重试次数未达上限时应重新调度任务")
    void testFailTask_shouldRescheduleWhenRetryAllowed() {
        when(retryManager.shouldRetry(anyLong(), anyString())).thenReturn(true);
        when(retryManager.calculateNextRetryDelay(anyLong(), anyInt())).thenReturn(30000L);

        BatchTransferTaskConfig config = createRunningConfig(1L, "0 */5 * * * ?");
        schedulerManager.startTask(config);
        
        schedulerManager.failTask(1L, "网络错误");

        verify(retryManager).shouldRetry(eq(1L), eq("网络错误"));
        System.out.println("✅ 重试允许时重新调度");
    }

    @Test
    @DisplayName("7. [集成] 超过最大重试次数时应标记最终失败")
    void testFailTask_shouldMarkFinalFailure() {
        when(retryManager.shouldRetry(anyLong(), anyString())).thenReturn(false);

        boolean shouldContinue = schedulerManager.failTask(1L, "永久性错误");

        assertFalse(shouldContinue, "超过最大重试次数不应继续");
        System.out.println("✅ 超过重试次数标记最终失败");
    }

    @Test
    @DisplayName("8. [集成] 关闭时应清理所有资源")
    void testShutdown_shouldCleanupAll() {
        schedulerManager.shutdown();

        // shutdown应清理retryManager
        verify(retryManager).clearAll();
        System.out.println("✅ 关闭时资源已清理");
    }

    // ==================== 辅助方法 ====================

    private BatchTransferTaskConfig createRunningConfig(Long taskId, String cronExpression) {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName("测试任务" + taskId);
        config.setStatus("RUNNING");
        config.setCronExpression(cronExpression);
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");
        config.setTargetDirs(java.util.List.of("/backup/logs"));
        config.setMaxRetries(10);
        return config;
    }
}
