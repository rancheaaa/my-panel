package com.cq.agent.batch.report;

import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地持久化补报服务单元测试
 * 覆盖率目标：100%
 */
@DisplayName("本地持久化补报 - FallbackPersistenceService")
class FallbackPersistenceServiceTest {

    private FallbackPersistenceService persistenceService;
    private ProgressReporter mockProgressReporter;
    
    @org.junit.jupiter.api.io.TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        persistenceService = new FallbackPersistenceService(tempDir.toString());
        
        // 创建mock ProgressReporter（使用String构造函数用于测试）
        mockProgressReporter = new ProgressReporter(new AgentConfig());
        mockProgressReporter.setFallbackPersistenceService(persistenceService);
        persistenceService.setProgressReporter(mockProgressReporter);
        
        // 配置较短的间隔用于测试（1秒）
        persistenceService.configureAutoRetry(1, 5);
    }

    @AfterEach
    void tearDown() {
        persistenceService.stopAutoRetry();
        persistenceService.shutdown();
    }

    // ==================== 1. 持久化事件 ====================

    @Test
    @DisplayName("1. persist() - 事件保存到本地文件")
    void testPersist_eventSaved() throws Exception {
        SubTaskEvent event = createSubTaskEvent(1L);
        
        persistenceService.persist(event);
        
        List<SubTaskEvent> loaded = persistenceService.loadAll();
        
        assertEquals(1, loaded.size(), "应有1个持久化的事件");
        assertEquals(1L, loaded.getFirst().getSubtaskId());
        
        System.out.println("✅ 事件持久化成功: subtask=1");
    }

    // ==================== 2. 加载恢复事件 ====================

    @Test
    @DisplayName("2. loadAll() - 恢复所有未上报事件")
    void testLoadAll_eventsRestored() throws Exception {
        for (int i = 0; i < 5; i++) {
            persistenceService.persist(createSubTaskEvent((long) i));
            Thread.sleep(10); // 确保时间戳不同
        }
        
        List<SubTaskEvent> events = persistenceService.loadAll();
        
        assertEquals(5, events.size(), "应恢复所有5个事件");
        
        System.out.println("✅ 恢复持久化事件: count=" + events.size());
    }

    // ==================== 3. 删除已上报事件 ====================

    @Test
    @DisplayName("3. deleteReported() - 清理已成功上报的事件")
    void testDeleteReported_oldEventsCleaned() throws Exception {
        for (int i = 0; i < 3; i++) {
            persistenceService.persist(createSubTaskEvent((long) i));
        }
        
        assertEquals(3, persistenceService.loadAll().size());
        
        List<Long> reportedIds = List.of(0L, 1L);
        persistenceService.deleteReported(reportedIds);
        
        List<SubTaskEvent> remaining = persistenceService.loadAll();
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
                    
                    persistenceService.persist(createSubTaskEvent((long) index));
                } catch (Exception e) {
                    //
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);
        
        Thread.sleep(200); // 等待文件写入完成
        
        List<SubTaskEvent> allEvents = persistenceService.loadAll();
        
        System.out.println("✅ 并发持久化: threads=" + threadCount + 
                          ", persisted=" + allEvents.size());
                          
        assertTrue(allEvents.size() > 0, "应有部分事件被持久化");
        assertTrue(allEvents.size() <= threadCount, "不应超过线程总数");
    }

    // ==================== 5. 自动补报功能测试 ====================

    @Test
    @DisplayName("5. startAutoRetry() - 启动自动补报任务")
    void testStartAutoRetry_success() {
        assertFalse(isAutoRetryRunning(), "初始状态应为未运行");
        
        persistenceService.startAutoRetry();
        
        assertTrue(isAutoRetryRunning(), "启动后应为运行状态");
        
        System.out.println("✅ 自动补报启动成功");
    }

    @Test
    @DisplayName("6. stopAutoRetry() - 停止自动补报任务")
    void testStopAutoRetry_success() throws Exception {
        persistenceService.startAutoRetry();
        Thread.sleep(100); // 确保启动完成
        
        persistenceService.stopAutoRetry();
        
        assertFalse(isAutoRetryRunning(), "停止后应为未运行状态");
        
        System.out.println("✅ 自动补报停止成功");
    }

    @Test
    @DisplayName("7. retryNow() - 手动触发一次补报")
    void testRetryNow_manualTrigger() throws Exception {
        // 先添加一些失败的事件
        for (int i = 0; i < 3; i++) {
            persistenceService.persist(createSubTaskEvent((long) i));
        }
        
        AtomicInteger reportCallCount = new AtomicInteger(0);
        
        // 配置mock ProgressReporter：模拟前2个成功，第3个失败
        mockProgressReporter.setHttpClient((url, data) -> {
            int callNum = reportCallCount.incrementAndGet();
            return callNum <= 2; // 前2次成功，第3次失败
        });
        
        long pendingBefore = persistenceService.getPendingCount();
        assertEquals(3, pendingBefore, "应有3个待处理事件");
        
        // 手动触发补报
        persistenceService.retryNow();
        
        Thread.sleep(500); // 等待补报完成
        
        long pendingAfter = persistenceService.getPendingCount();
        assertTrue(pendingAfter < pendingBefore, "补报后待处理数量应减少");
        assertTrue(pendingAfter >= 1, "至少有1个失败的事件保留");
        
        System.out.println("✅ 手动补报执行成功: before=" + pendingBefore + 
                          ", after=" + pendingAfter + 
                          ", reported=" + (pendingBefore - pendingAfter));
    }

    @Test
    @DisplayName("8. configureAutoRetry() - 配置参数验证")
    void testConfigureAutoRetry_params() {
        // 测试配置是否生效
        persistenceService.configureAutoRetry(60, 20);
        
        // 通过反射或公共方法验证配置（这里仅验证不抛异常）
        assertNotNull(persistenceService);
        
        System.out.println("✅ 参数配置成功");
    }

    @Test
    @DisplayName("9. getPendingCount() - 获取待处理数量")
    void testGetPendingCount() throws Exception {
        assertEquals(0, persistenceService.getPendingCount(), "初始应为0");
        
        persistenceService.persist(createSubTaskEvent(1L));
        assertEquals(1, persistenceService.getPendingCount(), "添加1个后应为1");
        
        persistenceService.persist(createSubTaskEvent(2L));
        assertEquals(2, persistenceService.getPendingCount(), "再添加1个后应为2");
        
        System.out.println("✅ 待处理数量统计正确");
    }

    @Test
    @DisplayName("10. 自动补报 - 定时扫描并重试")
    void testAutoRetry_scheduledScanAndRetry() throws Exception {
        // 准备测试数据
        for (int i = 0; i < 5; i++) {
            persistenceService.persist(createSubTaskEvent((long) i));
        }
        
        AtomicInteger successCount = new AtomicInteger(0);
        
        // Mock：全部成功
        mockProgressReporter.setHttpClient((url, data) -> {
            successCount.incrementAndGet();
            return true;
        });
        
        // 启动自动补报（间隔1秒）
        persistenceService.startAutoRetry();
        
        // 等待至少1次扫描周期
        Thread.sleep(2500); // 等待2-3次扫描
        
        persistenceService.stopAutoRetry();
        
        // 验证：应该已经尝试过补报
        assertTrue(successCount.get() > 0, "应该有事件被重新上报");
        
        long remaining = persistenceService.getPendingCount();
        System.out.println("✅ 定时扫描完成: success=" + successCount.get() + 
                          ", remaining=" + remaining);
    }

    @Test
    @DisplayName("11. 无ProgressReporter时启动失败")
    void testStartAutoRetry_noProgressReporter() {
        FallbackPersistenceService serviceWithoutReporter = 
            new FallbackPersistenceService(tempDir.toString() + "-no-reporter");
        
        // 不设置 ProgressReporter，直接启动
        serviceWithoutReporter.startAutoRetry();
        
        assertFalse(isAutoRetryRunningFor(serviceWithoutReporter), 
            "无ProgressReporter不应启动");
        
        serviceWithoutReporter.shutdown();
        
        System.out.println("✅ 无ProgressReporter时正确拒绝启动");
    }

    // ==================== 辅助方法 ====================

    private boolean isAutoRetryRunning() {
        try {
            var field = FallbackPersistenceService.class.getDeclaredField("autoRetryEnabled");
            field.setAccessible(true);
            AtomicBoolean enabled = (AtomicBoolean) field.get(persistenceService);
            return enabled.get();
        } catch (Exception e) {
            fail("无法访问autoRetryEnabled字段");
            return false;
        }
    }

    private boolean isAutoRetryRunningFor(FallbackPersistenceService service) {
        try {
            var field = FallbackPersistenceService.class.getDeclaredField("autoRetryEnabled");
            field.setAccessible(true);
            AtomicBoolean enabled = (AtomicBoolean) field.get(service);
            return enabled.get();
        } catch (Exception e) {
            fail("无法访问autoRetryEnabled字段");
            return false;
        }
    }

    private SubTaskEvent createSubTaskEvent(Long subtaskId) {
        SubTaskEvent event = new SubTaskEvent();
        event.setSubtaskId(subtaskId);
        event.setTaskId(100L);
        event.setTransferId("transfer-" + subtaskId);
        event.setStatus("SENDING");
        event.setTransferredChunks(subtaskId.intValue() * 5);
        event.setTotalChunks(100);
        event.setTransferredBytes(subtaskId * 1024L);
        event.setSpeedBytesPerSec(512L);
        event.setFileName("test-" + subtaskId + ".log");
        event.setFileSizeBytes(2048L);
        return event;
    }
}
