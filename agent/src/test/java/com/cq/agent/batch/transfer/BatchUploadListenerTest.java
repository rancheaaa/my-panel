package com.cq.agent.batch.transfer;

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

    // ==================== 3. 进度更新回调 - 旧版本兼容 ====================

    @Test
    @DisplayName("3. 进度更新(旧版) - 触发onProgress回调")
    void testOnProgress_callbackTriggered() {
        AtomicInteger lastProgress = new AtomicInteger(-1);

        uploadListener.onProgress((subtaskId, transferred, total, chunks, totalChunks, speed) -> {
            lastProgress.set((int) transferred);
            assertEquals(3L, subtaskId.longValue());
            assertEquals(1024L, total);
        });

        uploadListener.notifyProgress(3L, 512L, 1024L);

        assertEquals(512, lastProgress.get(), "进度应更新为512");

        System.out.println("✅ 进度更新(旧版): subtask=3, progress=512/1024");
    }

    // ==================== 4. 进度更新回调 - 完整版本 ====================

    @Test
    @DisplayName("4. 进度更新(完整版) - 包含chunks和speed")
    void testOnProgress_fullCallback() {
        AtomicLong capturedSpeed = new AtomicLong(-1);
        AtomicInteger capturedChunks = new AtomicInteger(-1);

        uploadListener.onProgress((subtaskId, transferred, total, chunks, totalChunks, speed) -> {
            capturedSpeed.set(speed);
            capturedChunks.set(chunks);
            assertEquals(4L, subtaskId.longValue());
            assertEquals(2048L, total);
            assertEquals(10, totalChunks);
        });

        uploadListener.notifyProgress(4L, 1024L, 2048L, 5, 10, 1048576L);

        assertEquals(5, capturedChunks.get(), "已传输chunks应为5");
        assertEquals(1048576L, capturedSpeed.get(), "速度应为1048576");

        System.out.println("✅ 进度更新(完整版): subtask=4, chunks=5/10, speed=1MB/s");
    }

    // ==================== 5. 多监听器支持 ====================

    @Test
    @DisplayName("5. 多监听器 - 所有都收到通知")
    void testMultipleListeners_allNotified() {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);

        uploadListener.onProgress((id, t, total, c, tc, s) -> listener1Count.incrementAndGet());
        uploadListener.onProgress((id, t, total, c, tc, s) -> listener2Count.incrementAndGet());

        for (int i = 0; i < 5; i++) {
            uploadListener.notifyProgress((long) i, (long) (i * 100), 1000L);
        }

        assertEquals(5, listener1Count.get(), "监听器1应收到5次通知");
        assertEquals(5, listener2Count.get(), "监听器2应收到5次通知");

        System.out.println("✅ 多监听器支持: listener1=5, listener2=5");
    }

    // ==================== 6. 清除监听器 ====================

    @Test
    @DisplayName("6. 清除监听器 - 不再收到通知")
    void testClearListeners() {
        AtomicInteger callCount = new AtomicInteger(0);

        uploadListener.onProgress((id, t, total, c, tc, s) -> callCount.incrementAndGet());
        uploadListener.notifyProgress(1L, 100L, 1000L);
        assertEquals(1, callCount.get());

        uploadListener.clear();

        uploadListener.notifyProgress(1L, 200L, 1000L);
        assertEquals(1, callCount.get(), "清除后不应再收到通知");

        System.out.println("✅ 清除监听器: 清除后不再收到通知");
    }
}
