package com.cq.agent.batch.scheduler;

import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileRetryScheduler 单元测试")
class FileRetrySchedulerTest {

    @Mock
    private RetryAwareUploaderDecorator retryAwareUploader;

    private FileRetryScheduler fileRetryScheduler;

    @BeforeEach
    void setUp() throws Exception {
        // 使用较短的扫描间隔用于测试（1小时 = 3600000ms）
        fileRetryScheduler = new FileRetryScheduler(retryAwareUploader, 3600000L);
    }

    @Test
    @DisplayName("1.1 应成功创建 FileRetryScheduler 实例")
    void shouldCreateInstanceSuccessfully() {
        assertNotNull(fileRetryScheduler, "FileRetryScheduler 应该被成功创建");
    }

    @Test
    @DisplayName("1.2 构造函数应接收正确的参数")
    void shouldAcceptCorrectParameters() {
        // 验证实例化时不抛出异常即表示参数正确
        assertDoesNotThrow(() -> new FileRetryScheduler(retryAwareUploader, 60000L));
    }

    @Test
    @DisplayName("1.3 shutdown() 方法不应抛出异常")
    void shutdownShouldNotThrowException() {
        assertDoesNotThrow(() -> fileRetryScheduler.shutdown(), "shutdown() 应正常执行");
    }
}
