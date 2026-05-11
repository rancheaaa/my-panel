package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import org.junit.jupiter.api.*;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD测试：验证QuartzTaskScheduler生命周期管理
 * 核心要求：
 * 1. 任务与Quartz Job/Trigger是一对一关系
 * 2. 删除任务时清理Quartz Job和Runnable缓存
 * 3. 多任务并行时互不干扰
 * 4. 符合spec.md"调度器按任务ID隔离"设计
 */
@DisplayName("QuartzTaskScheduler生命周期 - 符合spec.md设计")
class QuartzTaskSchedulerLifecycleTest {

    private QuartzTaskScheduler scheduler;
    private Scheduler quartzScheduler;

    @BeforeEach
    void setUp() throws SchedulerException {
        quartzScheduler = new StdSchedulerFactory().getScheduler();
        quartzScheduler.start();
        scheduler = new QuartzTaskScheduler(quartzScheduler);
    }

    @AfterEach
    void tearDown() throws SchedulerException {
        if (quartzScheduler != null && !quartzScheduler.isShutdown()) {
            quartzScheduler.shutdown();
        }
    }

    // ==================== Red Phase: 生命周期验证 ====================

    @Test
    @DisplayName("1. [spec.md] 每个任务应有独立的Quartz Job")
    void testStartTask_eachTaskHasIndependentJob() throws SchedulerException {
        // Given: 启动两个任务
        BatchTransferTaskConfig config1 = createConfig(1L, "0 */5 * * * ?");
        BatchTransferTaskConfig config2 = createConfig(2L, "0 */10 * * * ?");

        AtomicInteger counter1 = new AtomicInteger(0);
        AtomicInteger counter2 = new AtomicInteger(0);

        scheduler.startTask(config1, counter1::incrementAndGet);
        scheduler.startTask(config2, counter2::incrementAndGet);

        // Then: Quartz中应有两个独立的Job
        assertTrue(scheduler.isTaskRunning(1L), "任务1应存在");
        assertTrue(scheduler.isTaskRunning(2L), "任务2应存在");

        // 验证JobKey独立
        JobKey jobKey1 = new JobKey("batch-task-1", "batch-transfer");
        JobKey jobKey2 = new JobKey("batch-task-2", "batch-transfer");
        assertTrue(quartzScheduler.checkExists(jobKey1), "Job1应存在");
        assertTrue(quartzScheduler.checkExists(jobKey2), "Job2应存在");

        System.out.println("✅ 独立Job验证: 2个任务各有独立Quartz Job");
    }

    @Test
    @DisplayName("2. [spec.md] 删除任务时应清理Quartz Job")
    void testDeleteTask_shouldRemoveQuartzJob() throws SchedulerException {
        // Given: 启动任务
        BatchTransferTaskConfig config = createConfig(1L, "0 */5 * * * ?");
        scheduler.startTask(config, () -> {});
        assertTrue(scheduler.isTaskRunning(1L), "任务应存在");

        // When: 删除任务
        scheduler.deleteTask(1L);

        // Then: Quartz Job应被删除
        assertFalse(scheduler.isTaskRunning(1L), "任务应不存在");
        JobKey jobKey = new JobKey("batch-task-1", "batch-transfer");
        assertFalse(quartzScheduler.checkExists(jobKey), "Quartz Job应被删除");

        System.out.println("✅ 删除清理验证: Quartz Job已删除");
    }

    @Test
    @DisplayName("3. [spec.md] 删除任务时应清理Runnable缓存")
    void testDeleteTask_shouldClearRunnableCache() {
        // Given: 启动任务
        BatchTransferTaskConfig config = createConfig(1L, "0 */5 * * * ?");
        AtomicInteger counter = new AtomicInteger(0);
        scheduler.startTask(config, counter::incrementAndGet);

        // When: 删除任务
        scheduler.deleteTask(1L);

        // Then: 重新启动同名任务，应使用新的Runnable
        AtomicInteger counter2 = new AtomicInteger(0);
        scheduler.startTask(config, counter2::incrementAndGet);

        // 触发Job执行
        try {
            quartzScheduler.triggerJob(new JobKey("batch-task-1", "batch-transfer"));
            Thread.sleep(100); // 等待执行
        } catch (Exception e) {
            // ignore
        }

        // counter2应被增加（说明使用了新的Runnable）
        assertTrue(counter2.get() >= 1, "应使用新的Runnable");

        System.out.println("✅ Runnable缓存验证: 删除后重新启动使用新Runnable");
    }

    @Test
    @DisplayName("4. [spec.md] 删除任务A不应影响任务B")
    void testDeleteTask_shouldNotAffectOtherTasks() throws SchedulerException {
        // Given: 启动两个任务
        BatchTransferTaskConfig config1 = createConfig(1L, "0 */5 * * * ?");
        BatchTransferTaskConfig config2 = createConfig(2L, "0 */10 * * * ?");
        scheduler.startTask(config1, () -> {});
        scheduler.startTask(config2, () -> {});

        // When: 删除任务1
        scheduler.deleteTask(1L);

        // Then: 任务2应仍然存在
        assertFalse(scheduler.isTaskRunning(1L), "任务1应被删除");
        assertTrue(scheduler.isTaskRunning(2L), "任务2应仍然存在");

        JobKey jobKey2 = new JobKey("batch-task-2", "batch-transfer");
        assertTrue(quartzScheduler.checkExists(jobKey2), "任务2的Quartz Job应存在");

        System.out.println("✅ 隔离性验证: 删除任务1不影响任务2");
    }

    @Test
    @DisplayName("5. [spec.md] 重复启动同一任务应覆盖旧任务")
    void testStartTask_duplicate_shouldOverride() {
        // Given: 启动任务
        BatchTransferTaskConfig config = createConfig(1L, "0 */5 * * * ?");
        AtomicInteger counter1 = new AtomicInteger(0);
        scheduler.startTask(config, counter1::incrementAndGet);

        // When: 用不同的Runnable重新启动同一任务
        AtomicInteger counter2 = new AtomicInteger(0);
        scheduler.startTask(config, counter2::incrementAndGet);

        // Then: 触发执行，应使用新的Runnable
        try {
            quartzScheduler.triggerJob(new JobKey("batch-task-1", "batch-transfer"));
            Thread.sleep(100);
        } catch (Exception e) {
            // ignore
        }

        // counter2应被增加，counter1不应再增加
        assertTrue(counter2.get() >= 1, "应使用新的Runnable");

        System.out.println("✅ 覆盖验证: 重复启动同一任务覆盖旧Runnable");
    }

    @Test
    @DisplayName("6. [spec.md] shutdown应停止所有任务并清理资源")
    void testShutdown_shouldStopAllTasks() throws SchedulerException {
        // Given: 启动多个任务
        scheduler.startTask(createConfig(1L, "0 */5 * * * ?"), () -> {});
        scheduler.startTask(createConfig(2L, "0 */10 * * * ?"), () -> {});

        // When: shutdown
        scheduler.shutdown();

        // Then: 所有任务应被删除
        assertFalse(scheduler.isTaskRunning(1L), "任务1应被停止");
        assertFalse(scheduler.isTaskRunning(2L), "任务2应被停止");

        // Quartz中不应有batch-transfer组的Job
        var jobKeys = quartzScheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.jobGroupEquals("batch-transfer"));
        assertTrue(jobKeys.isEmpty(), "所有Job应被清理");

        System.out.println("✅ Shutdown验证: 所有任务和资源已清理");
    }

    @Test
    @DisplayName("7. [spec.md] 多任务并行执行互不干扰")
    void testMultipleTasks_parallelExecution() throws Exception {
        // Given: 启动两个任务，使用快速Cron（每秒执行）
        BatchTransferTaskConfig config1 = createConfig(1L, "0/1 * * * * ?");
        BatchTransferTaskConfig config2 = createConfig(2L, "0/1 * * * * ?");

        AtomicInteger counter1 = new AtomicInteger(0);
        AtomicInteger counter2 = new AtomicInteger(0);

        scheduler.startTask(config1, counter1::incrementAndGet);
        scheduler.startTask(config2, counter2::incrementAndGet);

        // When: 等待2秒让任务执行多次
        Thread.sleep(2000);

        // Then: 两个任务都应被执行多次
        assertTrue(counter1.get() >= 1, "任务1应被执行");
        assertTrue(counter2.get() >= 1, "任务2应被执行");

        System.out.println("✅ 并行执行验证: 任务1执行" + counter1.get() + "次, 任务2执行" + counter2.get() + "次");
    }

    @Test
    @DisplayName("8. [spec.md] 任务与Quartz是一对一关系")
    void testTaskToQuartzRelationship_oneToOne() throws SchedulerException {
        // Given: 一个任务
        BatchTransferTaskConfig config = createConfig(1L, "0 */5 * * * ?");
        scheduler.startTask(config, () -> {});

        // Then: 应只有一个Job和一个Trigger
        var jobKeys = quartzScheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.jobGroupEquals("batch-transfer"));
        var triggerKeys = quartzScheduler.getTriggerKeys(org.quartz.impl.matchers.GroupMatcher.triggerGroupEquals("batch-transfer-triggers"));

        assertEquals(1, jobKeys.size(), "应有1个Job");
        assertEquals(1, triggerKeys.size(), "应有1个Trigger");

        System.out.println("✅ 一对一验证: 1个任务对应1个Job和1个Trigger");
    }

    // ==================== 辅助方法 ====================

    private BatchTransferTaskConfig createConfig(Long taskId, String cronExpression) {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName("任务" + taskId);
        config.setStatus("RUNNING");
        config.setCronExpression(cronExpression);
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");
        config.setVersion(1L);
        return config;
    }
}
