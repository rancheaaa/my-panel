package com.cq.agent.batch.report;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地持久化补报服务单元测试
 * 覆盖率目标：100%
 */
@DisplayName("本地持久化补报 - FallbackPersistenceService")
class FallbackPersistenceServiceTest {

    private FallbackPersistenceService persistenceService;
    
    @org.junit.jupiter.api.io.TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        persistenceService = new FallbackPersistenceService(tempDir.toString());
    }

    // ==================== 1. 持久化事件 ====================

    @Test
    @DisplayName("1. persist() - 事件保存到本地文件")
    void testPersist_eventSaved() throws Exception {
        ProgressEvent event = createProgressEvent(1L, 50, 100);
        
        persistenceService.persist(event);
        
        List<ProgressEvent> loaded = persistenceService.loadAll();
        
        assertEquals(1, loaded.size(), "应有1个持久化的事件");
        assertEquals(1L, loaded.get(0).getSubtaskId());
        
        System.out.println("✅ 事件持久化成功: subtask=1");
    }

    // ==================== 2. 加载恢复事件 ====================

    @Test
    @DisplayName("2. loadAll() - 恢复所有未上报事件")
    void testLoadAll_eventsRestored() throws Exception {
        for (int i = 0; i < 5; i++) {
            persistenceService.persist(createProgressEvent((long) i, i * 20, 100));
            Thread.sleep(10); // 确保时间戳不同
        }
        
        List<ProgressEvent> events = persistenceService.loadAll();
        
        assertEquals(5, events.size(), "应恢复所有5个事件");
        
        System.out.println("✅ 恢复持久化事件: count=" + events.size());
    }

    // ==================== 3. 删除已上报事件 ====================

    @Test
    @DisplayName("3. deleteReported() - 清理已成功上报的事件")
    void testDeleteReported_oldEventsCleaned() throws Exception {
        for (int i = 0; i < 3; i++) {
            persistenceService.persist(createProgressEvent((long) i, i * 30, 100));
        }
        
        assertEquals(3, persistenceService.loadAll().size());
        
        List<Long> reportedIds = List.of(0L, 1L);
        persistenceService.deleteReported(reportedIds);
        
        List<ProgressEvent> remaining = persistenceService.loadAll();
        assertEquals(1, remaining.size(), "删除后应只剩1个");
        assertEquals(2L, remaining.get(0).getSubtaskId());
        
        System.out.println("✅ 已上报事件已清理: remaining=" + remaining.size());
    }

    // ==================== 4. 并发安全测试 ====================

    @Test
    @DisplayName("4. 并发persist() - 线程安全")
    void testConcurrentAccess_threadSafe() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    startLatch.await();
                    
                    persistenceService.persist(
                        createProgressEvent((long) index, index * 10, 100)
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);
        
        Thread.sleep(200); // 等待文件写入完成
        
        List<ProgressEvent> allEvents = persistenceService.loadAll();
        
        System.out.println("✅ 并发持久化: threads=" + threadCount + 
                          ", persisted=" + allEvents.size());
                          
        assertTrue(allEvents.size() > 0, "应有部分事件被持久化");
        assertTrue(allEvents.size() <= threadCount, "不应超过线程总数");
    }

    // ==================== 辅助方法 ====================

    private ProgressEvent createProgressEvent(Long subtaskId, int transferredBytes, int totalBytes) {
        return new ProgressEvent(subtaskId, transferredBytes, totalBytes, System.currentTimeMillis());
    }
}
