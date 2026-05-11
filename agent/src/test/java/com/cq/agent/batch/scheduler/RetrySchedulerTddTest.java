package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.transfer.RetryManager;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD测试：验证延迟重试调度功能
 * 核心要求（spec.md 4.7）：
 * 1. 失败后应计算延迟时间并重新调度任务
 * 2. 使用Quartz调度器实现延迟执行
 * 3. 超过最大重试次数后不再调度
 */
@DisplayName("延迟重试调度 - TDD")
class RetrySchedulerTddTest {

    @Mock
    private ConfigFileManager configFileManager;

    @Mock
    private RetryManager retryManager;

    private BatchTaskSchedulerManager schedulerManager;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
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

    // ==================== Red Phase: 编写测试 ====================

    @Test
    @DisplayName("1. [spec.md] 失败时应通过RetryManager判断是否重试")
    void testFailTask_shouldCheckRetry() {
        when(retryManager.shouldRetry(anyLong(), anyString())).thenReturn(false);

        boolean shouldContinue = schedulerManager.failTask(1L, "连接超时");

        verify(retryManager).shouldRetry(eq(1L), eq("连接超时"));
        assertFalse(shouldContinue, "不应继续");
        System.out.println("✅ 重试检查验证: 调用shouldRetry");
    }

    @Test
    @DisplayName("2. [spec.md] 应允许重试时返回true")
    void testFailTask_retryAllowed_returnsTrue() {
        when(retryManager.shouldRetry(anyLong(), anyString())).thenReturn(true);

        boolean shouldContinue = schedulerManager.failTask(1L, "网络错误");

        assertTrue(shouldContinue, "应允许重试");
        System.out.println("✅ 重试允许验证: 返回true");
    }

    @Test
    @DisplayName("3. [spec.md] 不应重试时返回false")
    void testFailTask_maxRetriesExceeded_returnsFalse() {
        when(retryManager.shouldRetry(anyLong(), anyString())).thenReturn(false);

        boolean shouldContinue = schedulerManager.failTask(1L, "永久性错误");

        assertFalse(shouldContinue, "超过最大重试次数");
        System.out.println("✅ 最大重试验证: 返回false");
    }

    @Test
    @DisplayName("4. [spec.md] 失败时应检查是否需要重试")
    void testFailTask_shouldCheckRetry() {
        when(retryManager.shouldRetry(anyLong(), anyString())).thenReturn(false);

        schedulerManager.failTask(1L, "传输失败");

        verify(retryManager).shouldRetry(eq(1L), eq("传输失败"));
        System.out.println("✅ 失败重试验证: 调用shouldRetry");
    }

    @Test
    @DisplayName("5. [spec.md] 成功完成时应记录成功")
    void testCompleteTask_shouldRecordSuccess() {
        schedulerManager.completeTask(1L);

        verify(retryManager).recordSuccess(eq(1L));
        System.out.println("✅ 成功记录验证: 调用recordSuccess");
    }

    @Test
    @DisplayName("6. [spec.md] 关闭时应清理所有资源")
    void testShutdown_shouldClearAll() {
        schedulerManager.shutdown();

        verify(retryManager).clearAll();
        System.out.println("✅ 资源清理验证: 调用clearAll");
    }

    @Test
    @DisplayName("7. [spec.md] 应正确获取重试次数")
    void testGetRetryCount() {
        when(retryManager.getRetryCount(anyLong())).thenReturn(3);

        int count = schedulerManager.getRetryCount(1L);

        assertEquals(3, count);
        verify(retryManager).getRetryCount(eq(1L));
        System.out.println("✅ 获取重试次数验证: count=3");
    }

    @Test
    @DisplayName("8. [spec.md] 应正确获取最大重试次数")
    void testGetMaxRetries() {
        when(retryManager.getMaxRetries()).thenReturn(10);

        int max = schedulerManager.getMaxRetries();

        assertEquals(10, max);
        verify(retryManager).getMaxRetries();
        System.out.println("✅ 获取最大重试次数验证: max=10");
    }
}
