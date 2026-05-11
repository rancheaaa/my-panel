package com.cq.agent;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.scheduler.QuartzTaskScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentApplication 集成测试
 * 验证所有组件是否正确连接和工作
 */
@DisplayName("AgentApplication - 组件集成测试")
class AgentApplicationIntegrationTest {

    @TempDir
    Path tempDir;

    private Scheduler quartzScheduler;
    private QuartzTaskScheduler taskScheduler;
    private ConfigFileManager configFileManager;
    private String configDir;

    @BeforeEach
    void setUp() throws Exception {
        quartzScheduler = new StdSchedulerFactory().getScheduler();
        quartzScheduler.start();
        taskScheduler = new QuartzTaskScheduler(quartzScheduler);

        configDir = tempDir.resolve("batch-config").toString();
        configFileManager = new ConfigFileManager(configDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        if (quartzScheduler != null && !quartzScheduler.isShutdown()) {
            quartzScheduler.shutdown(true);
        }
    }

    @Test
    @DisplayName("1. 完整流程：接收配置→持久化→启动Quartz调度")
    void testFullFlow_receiveConfigAndStartScheduler() throws Exception {
        // 1. 创建任务配置
        BatchTransferTaskConfig config = createConfig(1001L, "0/1 * * * * ?");

        // 2. 持久化配置（模拟BatchConfigReceiveHandler的行为）
        configFileManager.saveTaskConfig(config);

        // 3. 验证配置已保存
        BatchTransferTaskConfig loaded = configFileManager.loadTaskConfig(1001L);
        assertNotNull(loaded);
        assertEquals("0/1 * * * * ?", loaded.getCronExpression());

        // 4. 启动Quartz调度（模拟AgentApplication连接后的行为）
        AtomicInteger executionCount = new AtomicInteger(0);
        taskScheduler.startTask(loaded, executionCount::incrementAndGet);

        // 5. 验证调度器已启动
        assertTrue(taskScheduler.isTaskRunning(1001L), "任务应处于运行状态");

        // 6. 等待执行
        Thread.sleep(1500);
        assertTrue(executionCount.get() >= 1, "任务应被Quartz调度执行");
    }

    @Test
    @DisplayName("2. 启动加载：从本地文件恢复RUNNING任务")
    void testStartupLoader_restoreRunningTasks() throws Exception {
        // 1. 保存RUNNING状态的任务
        BatchTransferTaskConfig config1 = createConfig(2001L, "0/1 * * * * ?");
        config1.setStatus("RUNNING");
        configFileManager.saveTaskConfig(config1);

        // 2. 保存PAUSED状态的任务
        BatchTransferTaskConfig config2 = createConfig(2002L, "0/1 * * * * ?");
        config2.setStatus("PAUSED");
        configFileManager.saveTaskConfig(config2);

        // 3. 模拟StartupLoader加载
        AtomicInteger task1Count = new AtomicInteger(0);
        AtomicInteger task2Count = new AtomicInteger(0);

        // 4. 只恢复RUNNING状态的任务
        BatchTransferTaskConfig loaded1 = configFileManager.loadTaskConfig(2001L);
        if ("RUNNING".equals(loaded1.getStatus())) {
            taskScheduler.startTask(loaded1, task1Count::incrementAndGet);
        }

        BatchTransferTaskConfig loaded2 = configFileManager.loadTaskConfig(2002L);
        if ("RUNNING".equals(loaded2.getStatus())) {
            taskScheduler.startTask(loaded2, task2Count::incrementAndGet);
        }

        // 5. 验证：只有RUNNING任务被调度
        Thread.sleep(1500);
        assertTrue(task1Count.get() >= 1, "RUNNING任务应被调度");
        assertEquals(0, task2Count.get(), "PAUSED任务不应被调度");
    }

    @Test
    @DisplayName("3. 热更新：Cron表达式变更后重新调度")
    void testHotUpdate_cronExpressionChanged() throws Exception {
        // 1. 启动任务（每2秒）
        BatchTransferTaskConfig config = createConfig(3001L, "0/2 * * * * ?");
        AtomicInteger executionCount = new AtomicInteger(0);
        taskScheduler.startTask(config, executionCount::incrementAndGet);

        Thread.sleep(2100);
        int countWith2s = executionCount.get();

        // 2. 模拟配置变更（热更新为每1秒）
        BatchTransferTaskConfig updatedConfig = createConfig(3001L, "0/1 * * * * ?");
        configFileManager.saveTaskConfig(updatedConfig);
        taskScheduler.updateTask(updatedConfig);

        Thread.sleep(2100);
        int countWith1s = executionCount.get();

        // 3. 验证频率变快
        assertTrue(countWith1s > countWith2s, "更新后执行频率应增加");
    }

    @Test
    @DisplayName("4. 状态变更：PAUSED→RUNNING→调度恢复")
    void testStatusChange_pausedToRunning() throws Exception {
        // 1. 启动任务
        BatchTransferTaskConfig config = createConfig(4001L, "0/1 * * * * ?");
        AtomicInteger executionCount = new AtomicInteger(0);
        taskScheduler.startTask(config, executionCount::incrementAndGet);

        Thread.sleep(1100);
        int countBeforePause = executionCount.get();

        // 2. 暂停任务
        taskScheduler.pauseTask(4001L);
        Thread.sleep(1500);
        int countAfterPause = executionCount.get();

        // 3. 恢复任务
        taskScheduler.resumeTask(4001L);
        Thread.sleep(1500);
        int countAfterResume = executionCount.get();

        // 4. 验证
        assertTrue(countBeforePause >= 1, "启动后应有执行");
        assertEquals(countBeforePause, countAfterPause, "暂停后不应增加");
        assertTrue(countAfterResume > countAfterPause, "恢复后应继续执行");
    }

    @Test
    @DisplayName("5. 删除任务：停止调度并清理")
    void testDeleteTask_stopScheduling() throws Exception {
        // 1. 启动任务
        BatchTransferTaskConfig config = createConfig(5001L, "0/1 * * * * ?");
        AtomicInteger executionCount = new AtomicInteger(0);
        taskScheduler.startTask(config, executionCount::incrementAndGet);

        Thread.sleep(1100);
        int countBeforeDelete = executionCount.get();
        assertTrue(countBeforeDelete >= 1);

        // 2. 删除任务
        taskScheduler.deleteTask(5001L);

        // 3. 验证
        assertFalse(taskScheduler.isTaskRunning(5001L), "任务应已停止");
        Thread.sleep(1500);
        assertEquals(countBeforeDelete, executionCount.get(), "删除后不应再执行");
    }

    // ==================== 辅助方法 ====================

    private BatchTransferTaskConfig createConfig(Long taskId, String cron) {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName("测试任务-" + taskId);
        config.setSourceAgentId("agent-001");
        config.setSourceDir(tempDir.resolve("source").toString());
        config.setTargetDirs(List.of(tempDir.resolve("target").toString()));
        config.setIncludePatterns(List.of("*.log"));
        config.setStatus("RUNNING");
        config.setCronExpression(cron);
        config.setMaxRetries(10);
        config.setVersion(System.currentTimeMillis());
        return config;
    }
}
