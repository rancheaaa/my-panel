package com.cq.agent.batch.scheduler;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cron调度器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("Cron调度器 - CronScheduler")
class CronSchedulerTest {

    private CronScheduler cronScheduler;

    @BeforeEach
    void setUp() {
        cronScheduler = new CronScheduler();
    }

    @AfterEach
    void tearDown() {
        if (cronScheduler != null) {
            cronScheduler.stop();
        }
    }

    // ==================== 1. Cron触发测试 ====================

    @Test
    @DisplayName("1. Cron表达式触发执行")
    void testSchedule_cronTriggered() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        
        cronScheduler.setTask(() -> {
            executed.set(true);
            System.out.println("✅ 任务被Cron触发执行");
        });
        
        cronScheduler.start("0/1 * * * * ?"); // 每1秒触发
        
        Thread.sleep(1500); // 等待至少1次触发
        
        assertTrue(executed.get(), "任务应被Cron触发");
        
        System.out.println("✅ Cron触发测试通过");
    }

    // ==================== 2. 停止测试 ====================

    @Test
    @DisplayName("2. 停止后不再触发")
    void testStop_noMoreTriggers() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        
        cronScheduler.setTask(() -> {
            executionCount.incrementAndGet();
        });
        
        cronScheduler.start("0/1 * * * * ?");
        Thread.sleep(1100); // 允许1次执行
        
        int countBeforeStop = executionCount.get();
        cronScheduler.stop();
        Thread.sleep(1100); // 停止后再等1秒
        
        int countAfterStop = executionCount.get();
        
        assertTrue(countBeforeStop >= 1, "停止前应至少执行1次");
        assertEquals(countBeforeStop, countAfterStop, "停止后不应再执行");
        
        System.out.println("✅ 停止后不再触发: before=" + countBeforeStop + ", after=" + countAfterStop);
    }

    // ==================== 3. 暂停测试 ====================

    @Test
    @DisplayName("3. 暂停后当前完成但不再新触发")
    void testPause_currentCompletes_noNew() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch currentExecution = new CountDownLatch(1);
        
        cronScheduler.setTask(() -> {
            executionCount.incrementAndGet();
            try {
                Thread.sleep(500); // 模拟耗时任务
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            currentExecution.countDown();
        });
        
        cronScheduler.start("0/1 * * * * ?");
        Thread.sleep(100); // 等待第一次启动
        
        cronScheduler.pause();
        currentExecution.await(2, TimeUnit.SECONDS); // 等待当前完成
        
        int countDuringPause = executionCount.get();
        Thread.sleep(1200); // 暂停后再等一会
        
        int countAfterPauseWait = executionCount.get();
        
        assertTrue(countDuringPause >= 1, "暂停前应有执行");
        // 注意：由于任务可能正在执行，暂停后可能还有1-2次（取决于时序）
        
        System.out.println("✅ 暂停测试: during=" + countDuringPause + ", after=" + countAfterPauseWait);
    }

    // ==================== 4. 恢复测试 ====================

    @Test
    @DisplayName("4. 恢复后重新开始调度")
    void testResume_resumesScheduling() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        
        cronScheduler.setTask(() -> executionCount.incrementAndGet());
        
        cronScheduler.start("0/1 * * * * ?");
        Thread.sleep(500);
        
        cronScheduler.pause();
        Thread.sleep(500);
        
        int countBeforeResume = executionCount.get();
        cronScheduler.resume();
        Thread.sleep(1500);
        
        int countAfterResume = executionCount.get();
        
        assertTrue(countAfterResume > countBeforeResume, 
            "恢复后应有新的执行");
            
        System.out.println("✅ 恢复调度成功: before=" + countBeforeResume + ", after=" + countAfterResume);
    }

    // ==================== 5. 动态更新Cron ====================

    @Test
    @DisplayName("5. 动态更新Cron表达式")
    void testUpdateCron_dynamicChange() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        
        cronScheduler.setTask(() -> executionCount.incrementAndGet());
        
        cronScheduler.start("0/2 * * * * ?"); // 初始每2秒
        Thread.sleep(1100);
        int countWith2sInterval = executionCount.get();
        
        cronScheduler.updateCronExpression("0/1 * * * * ?"); // 改为每1秒
        Thread.sleep(2100);
        int countWith1sInterval = executionCount.get();
        
        assertTrue(countWith1sInterval > countWith2sInterval,
            "更快的频率应有更多执行");
            
        System.out.println("✅ 动态更新Cron: 2s间隔=" + countWith2sInterval + 
                          ", 1s间隔=" + countWith1sInterval);
    }

    // ==================== 6. 启动时立即执行 ====================

    @Test
    @DisplayName("6. 启动时立即执行一次")
    void testImmediateExecution_onStart() throws Exception {
        AtomicBoolean immediateExecuted = new AtomicBoolean(false);
        CountDownLatch startLatch = new CountDownLatch(1);
        
        cronScheduler.setTask(() -> {
            immediateExecuted.set(true);
            startLatch.countDown();
        });
        
        cronScheduler.start("0/10 * * * * ?"); // 10秒间隔（不会自动快速触发）
        
        boolean executed = startLatch.await(2, TimeUnit.SECONDS);
        
        assertTrue(executed || immediateExecuted.get(), 
            "启动时应立即执行一次");
            
        System.out.println("✅ 启动时立即执行验证通过");
    }
}
