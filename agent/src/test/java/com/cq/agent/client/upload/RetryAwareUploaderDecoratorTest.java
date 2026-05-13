package com.cq.agent.client.upload;

import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryAwareUploaderDecorator - 委托方法测试（从 AgentUploader 迁移）")
class RetryAwareUploaderDecoratorTest {

    @Mock
    private AgentUploader delegate;

    @Mock
    private UploadTask mockUploadTask;

    private RetryAwareUploaderDecorator retryAwareUploader;

    @BeforeEach
    void setUp() {
        AgentConfig config = new AgentConfig();
        retryAwareUploader = new RetryAwareUploaderDecorator(delegate, 10, 30, 2);
    }

    // ==================== 任务管理查询方法测试 ====================

    @Test
    @DisplayName("9.1 [迁移] getInflightTasks 应委托给被装饰对象")
    void testGetInflightTasks_shouldDelegate() {
        List<UploadTask> mockTasks = Arrays.asList(mockUploadTask);
        when(delegate.getInflightTasks(1, 10)).thenReturn(mockTasks);

        List<UploadTask> result = retryAwareUploader.getInflightTasks(1, 10);

        assertEquals(mockTasks, result);
        verify(delegate).getInflightTasks(1, 10);
    }

    @Test
    @DisplayName("9.2 [迁移] getAllInflightTasks 应委托给被装饰对象")
    void testGetAllInflightTasks_shouldDelegate() {
        List<UploadTask> mockTasks = Arrays.asList(mockUploadTask);
        when(delegate.getAllInflightTasks()).thenReturn(mockTasks);

        List<UploadTask> result = retryAwareUploader.getAllInflightTasks();

        assertEquals(mockTasks, result);
        verify(delegate).getAllInflightTasks();
    }

    @Test
    @DisplayName("9.3 [迁移] getInflightTasksCount 应委托给被装饰对象")
    void testGetInflightTasksCount_shouldDelegate() {
        when(delegate.getInflightTasksCount()).thenReturn(5);

        int count = retryAwareUploader.getInflightTasksCount();

        assertEquals(5, count);
        verify(delegate).getInflightTasksCount();
    }

    @Test
    @DisplayName("9.4 [迁移] isInflightTasksEmpty 应委托给被装饰对象")
    void testIsInflightTasksEmpty_shouldDelegate() {
        when(delegate.isInflightTasksEmpty()).thenReturn(true);

        boolean isEmpty = retryAwareUploader.isInflightTasksEmpty();

        assertTrue(isEmpty);
        verify(delegate).isInflightTasksEmpty();
    }

    @Test
    @DisplayName("9.5 [迁移] clearAllInflightTasks 应委托给被装饰对象")
    void testClearAllInflightTasks_shouldDelegate() {
        retryAwareUploader.clearAllInflightTasks();

        verify(delegate).clearAllInflightTasks();
    }

    // ==================== 重试相关方法测试 ====================

    @Test
    @DisplayName("9.6 [迁移] resubmitTask 应委托给被装饰对象")
    void testResubmitTask_shouldDelegate() {
        UploadTask task = mock(UploadTask.class);
        when(task.getTransferId()).thenReturn("test-transfer-id");

        retryAwareUploader.resubmitTask(task);

        verify(delegate).resubmitTask(task);
    }

    @Test
    @DisplayName("9.7 [迁移] getFailedQueueDir 应委托给被装饰对象")
    void testGetFailedQueueDir_shouldDelegate() {
        Path mockPath = Paths.get("/tmp/failed");
        when(delegate.getFailedQueueDir()).thenReturn(mockPath);

        Path result = retryAwareUploader.getFailedQueueDir();

        assertEquals(mockPath, result);
        verify(delegate).getFailedQueueDir();
    }
}
