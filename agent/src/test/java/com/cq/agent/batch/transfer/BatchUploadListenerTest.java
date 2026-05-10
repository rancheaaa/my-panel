package com.cq.agent.batch.transfer;

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 上传监听器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("上传监听器 - BatchUploadListener")
class BatchUploadListenerTest {

    private BatchUploadListener uploadListener;

    @BeforeEach
    void setUp() {
        uploadListener = new BatchUploadListener();
    }

    // ==================== 1. 上传成功回调 ====================

    @Test
    @DisplayName("1. 上传成功 - 触发onSuccess回调")
    void testOnSuccess_callbackTriggered() {
        AtomicBoolean successCalled = new AtomicBoolean(false);
        
        uploadListener.onSuccess((subtaskId, targetPath) -> {
            successCalled.set(true);
            assertEquals(1L, subtaskId.longValue());
            assertEquals("/backup/file.txt", targetPath);
        });
        
        uploadListener.notifySuccess(1L, "/backup/file.txt");
        
        assertTrue(successCalled.get(), "onSuccess回调应被触发");
        
        System.out.println("✅ 上传成功回调: subtask=1, path=/backup/file.txt");
    }

    // ==================== 2. 上传失败回调 ====================

    @Test
    @DisplayName("2. 上传失败 - 触发onFailure回调")
    void testOnFailure_callbackTriggered() {
        AtomicBoolean failureCalled = new AtomicBoolean(false);
        
        uploadListener.onFailure((subtaskId, error) -> {
            failureCalled.set(true);
            assertEquals(2L, subtaskId.longValue());
            assertNotNull(error);
            assertTrue(error.contains("连接超时"));
        });
        
        uploadListener.notifyFailure(2L, "连接超时");
        
        assertTrue(failureCalled.get(), "onFailure回调应被触发");
        
        System.out.println("✅ 上传失败回调: subtask=2, error=连接超时");
    }

    // ==================== 3. 进度更新回调 ====================

    @Test
    @DisplayName("3. 进度更新 - 触发onProgress回调")
    void testOnProgress_callbackTriggered() {
        AtomicInteger lastProgress = new AtomicInteger(-1);
        
        uploadListener.onProgress((subtaskId, transferred, total) -> {
            lastProgress.set((int) transferred);
            assertEquals(3L, subtaskId.longValue());
            assertEquals(1024L, total);
        });
        
        uploadListener.notifyProgress(3L, 512L, 1024L);
        
        assertEquals(512, lastProgress.get(), "进度应更新为512");
        
        System.out.println("✅ 进度更新: subtask=3, progress=512/1024");
    }

    // ==================== 4. 多监听器支持 ====================

    @Test
    @DisplayName("4. 多监听器 - 所有都收到通知")
    void testMultipleListeners_allNotified() {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);
        
        uploadListener.onProgress((id, t, total) -> listener1Count.incrementAndGet());
        uploadListener.onProgress((id, t, total) -> listener2Count.incrementAndGet());
        
        for (int i = 0; i < 5; i++) {
            uploadListener.notifyProgress((long) i, (long) (i * 100), 1000L);
        }
        
        assertEquals(5, listener1Count.get(), "监听器1应收到5次通知");
        assertEquals(5, listener2Count.get(), "监听器2应收到5次通知");
        
        System.out.println("✅ 多监听器支持: listener1=5, listener2=5");
    }
}
