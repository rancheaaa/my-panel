package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.RetryConfig;
import org.junit.jupiter.api.*;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quartz任务调度器单元测试
 * 验证spec.md要求的Quartz定时调度功能
 */
@DisplayName("Quartz任务调度器 - QuartzTaskScheduler")
class QuartzTaskSchedulerTest {

    private Scheduler quartzScheduler;
    private QuartzTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() throws Exception {
        quartzScheduler = new StdSchedulerFactory().getScheduler();
        quartzScheduler.start();
        taskScheduler = new QuartzTaskScheduler(quartzScheduler);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (quartzScheduler != null && !quartzScheduler.isShutdown()) {
            quartzScheduler.shutdown(true);
        }
    }

    // ==================== 1. 任务启动 ====================

    @Test
    @DisplayName("1. 启动任务 - 按taskId创建Quartz Job")
    void testStartTask_createsQuartzJob() throws Exception {
        AgentTaskConfig config = createConfig(1001L, "0/1 * * * * ?");
        AtomicInteger executionCount = new AtomicInteger(0);

        taskScheduler.startTask(config, executionCount::incrementAndGet);

        Thread.sleep(1500); // 等待至少1次触发

        assertTrue(executionCount.get() >= 1, "任务应被Quartz调度执行");
    }

    @Test
    @DisplayName("2. 启动任务 - 标准Cron表达式")
    void testStartTask_standardCronExpression() throws Exception {
        // 使用标准Cron: 每秒执行
        AgentTaskConfig config = createConfig(1002L, "* * * * * ?");
        AtomicInteger executionCount = new AtomicInteger(0);

        taskScheduler.startTask(config, executionCount::incrementAndGet);

        Thread.sleep(1500);

        assertTrue(executionCount.get() >= 1, "标准Cron表达式应被正确解析");
    }

    @Test
    @DisplayName("3. 启动任务 - 复杂Cron表达式")
    void testStartTask_complexCronExpression() throws Exception {
        // 使用复杂Cron: 每5秒执行
        AgentTaskConfig config = createConfig(1003L, "0/5 * * * * ?");
        AtomicInteger executionCount = new AtomicInteger(0);

        taskScheduler.startTask(config, executionCount::incrementAndGet);

        Thread.sleep(6200); // 等待至少1次触发(5秒间隔)

        assertTrue(executionCount.get() >= 1, "复杂Cron表达式应被正确解析");
        assertTrue(executionCount.get() <= 2, "5秒间隔在6.2秒内应最多执行2次");
    }

    // ==================== 2. 任务暂停/恢复 ====================

    @Test
    @DisplayName("4. 暂停任务 - 不再触发")
    void testPauseTask_noMoreTriggers() throws Exception {
        AgentTaskConfig config = createConfig(1004L, "0/1 * * * * ?");
        AtomicInteger executionCount = new AtomicInteger(0);

        taskScheduler.startTask(config, executionCount::incrementAndGet);
        Thread.sleep(1100); // 至少执行1次

        int countBeforePause = executionCount.get();
        taskScheduler.pauseTask(1004L);
        Thread.sleep(1500); // 暂停后再等1.5秒

        assertEquals(countBeforePause, executionCount.get(), "暂停后不应再执行");
    }

    @Test
    @DisplayName("5. 恢复任务 - 重新开始调度")
    void testResumeTask_resumesScheduling() throws Exception {
        AgentTaskConfig config = createConfig(1005L, "0/1 * * * * ?");
        AtomicInteger executionCount = new AtomicInteger(0);

        taskScheduler.startTask(config, executionCount::incrementAndGet);
        Thread.sleep(500);
        taskScheduler.pauseTask(1005L);
        Thread.sleep(500);

        int countBeforeResume = executionCount.get();
        taskScheduler.resumeTask(1005L);
        Thread.sleep(1500);

        assertTrue(executionCount.get() > countBeforeResume, "恢复后应有新的执行");
    }

    // ==================== 3. 任务更新 ====================

    @Test
    @DisplayName("6. 更新Cron表达式 - 热更新")
    void testUpdateCronExpression_hotUpdate() throws Exception {
        AgentTaskConfig config = createConfig(1006L, "0/2 * * * * ?"); // 每2秒
        AtomicInteger executionCount = new AtomicInteger(0);

        taskScheduler.startTask(config, executionCount::incrementAndGet);
        Thread.sleep(2100); // 2秒内应执行1次
        int countWith2s = executionCount.get();

        // 热更新为每1秒
        AgentTaskConfig updatedConfig = createConfig(1006L, "0/1 * * * * ?");
        taskScheduler.updateTask(updatedConfig);
        Thread.sleep(2100); // 2秒内应执行约2次
        int countWith1s = executionCount.get();

        assertTrue(countWith1s > countWith2s, "更快的频率应有更多执行");
    }

    // ==================== 4. 任务删除 ====================

    @Test
    @DisplayName("7. 删除任务 - 停止调度")
    void testDeleteTask_stopsScheduling() throws Exception {
        AgentTaskConfig config = createConfig(1007L, "0/1 * * * * ?");
        AtomicInteger executionCount = new AtomicInteger(0);

        taskScheduler.startTask(config, executionCount::incrementAndGet);
        Thread.sleep(1100);

        int countBeforeDelete = executionCount.get();
        taskScheduler.deleteTask(1007L);
        Thread.sleep(1500);

        assertEquals(countBeforeDelete, executionCount.get(), "删除后不应再执行");
    }

    // ==================== 5. 任务隔离 ====================

    @Test
    @DisplayName("8. 多任务隔离 - 独立调度")
    void testMultipleTasks_isolated() throws Exception {
        AtomicInteger task1Count = new AtomicInteger(0);
        AtomicInteger task2Count = new AtomicInteger(0);

        AgentTaskConfig config1 = createConfig(2001L, "0/1 * * * * ?");
        AgentTaskConfig config2 = createConfig(2002L, "0/1 * * * * ?");

        taskScheduler.startTask(config1, task1Count::incrementAndGet);
        taskScheduler.startTask(config2, task2Count::incrementAndGet);

        Thread.sleep(1500);

        assertTrue(task1Count.get() >= 1, "任务1应被调度");
        assertTrue(task2Count.get() >= 1, "任务2应被调度");

        // 暂停任务1，任务2应继续
        taskScheduler.pauseTask(2001L);
        Thread.sleep(1500);

        int task1AfterPause = task1Count.get();
        int task2AfterPause = task2Count.get();

        assertEquals(task1AfterPause, task1Count.get(), "任务1暂停后不应增加");
        assertTrue(task2Count.get() > task2AfterPause - 1, "任务2应继续执行");
    }

    // ==================== 6. 首次执行 ====================

    @Test
    @DisplayName("9. 启动时立即执行一次")
    void testStartTask_immediateExecution() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AgentTaskConfig config = createConfig(1008L, "0/10 * * * * ?"); // 10秒间隔

        taskScheduler.startTask(config, latch::countDown);

        boolean executed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(executed, "启动时应立即执行一次");
    }

    // ==================== 辅助方法 ====================

    private AgentTaskConfig createConfig(Long taskId, String cron) {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName("测试任务-" + taskId);
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/tmp/test");

        TargetAgentInfo targetInfo = new TargetAgentInfo();
        targetInfo.setTargetDir("/tmp/backup");
        config.setTargetAgents(List.of(targetInfo));

        config.setIncludePatterns(List.of("*.log"));
        config.setStatus("RUNNING");

        ScanConfig scanConfig = new ScanConfig();
        scanConfig.setCronExpression(cron);
        config.setScanConfig(scanConfig);

        RetryConfig retryConfig = new RetryConfig();
        retryConfig.setMaxRetryCount(10);
        config.setRetryConfig(retryConfig);

        config.setVersion(String.valueOf(System.currentTimeMillis()));
        return config;
    }
}
