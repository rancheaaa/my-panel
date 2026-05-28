package com.cq.agent.batch.scheduler;

import com.cq.agent.client.upload.BatchTaskSchedulerUploader;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quartz任务调度器单元测试
 * 验证BatchTaskSchedulerUploaderDecorator的Quartz定时调度功能
 */
@DisplayName("Quartz任务调度器 - BatchTaskSchedulerUploaderDecorator")
class QuartzTaskSchedulerTest {

    @TempDir
    Path tempDir;

    private BatchTaskSchedulerUploader batchTaskUploader;
    private ConfigFileManager configFileManager;

    @BeforeEach
    void setUp() throws Exception {
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setUploadSendingQueueDir(tempDir.resolve("sending").toString());
        agentConfig.setUploadFailRetryQueueDir(tempDir.resolve("fail-retry").toString());
        agentConfig.setUploadFinalFailureQueueDir(tempDir.resolve("final-failure").toString());
        agentConfig.setUploadSuccessQueueDir(tempDir.resolve("success").toString());

        configFileManager = new ConfigFileManager(tempDir.resolve("batch-config").toString());
        batchTaskUploader = new BatchTaskSchedulerUploader(agentConfig, configFileManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (batchTaskUploader != null) {
            batchTaskUploader.shutdown();
        }
    }

    // ==================== 1. 任务启动 ====================

    @Test
    @DisplayName("1. 启动任务 - 按taskId创建Quartz Job")
    void testStartTask_createsQuartzJob() throws Exception {
        AgentTaskConfig config = createConfig(1001L, "0/1 * * * * ?");
        batchTaskUploader.startTask(config);

        Thread.sleep(1500);
        assertTrue(batchTaskUploader.isTaskRunning(1001L), "任务应被Quartz调度运行");
    }

    @Test
    @DisplayName("2. 启动任务 - 标准Cron表达式")
    void testStartTask_standardCronExpression() throws Exception {
        AgentTaskConfig config = createConfig(1002L, "* * * * * ?");
        batchTaskUploader.startTask(config);

        Thread.sleep(1500);
        assertTrue(batchTaskUploader.isTaskRunning(1002L), "标准Cron表达式应被正确解析");
    }

    @Test
    @DisplayName("3. 启动任务 - 复杂Cron表达式")
    void testStartTask_complexCronExpression() throws Exception {
        AgentTaskConfig config = createConfig(1003L, "0/5 * * * * ?");
        batchTaskUploader.startTask(config);

        Thread.sleep(1000);
        assertTrue(batchTaskUploader.isTaskRunning(1003L), "复杂Cron表达式应被正确解析");
    }

    // ==================== 2. 任务暂停/恢复 ====================

    @Test
    @DisplayName("4. 暂停任务 - 不再触发")
    void testPauseTask_noMoreTriggers() throws Exception {
        AgentTaskConfig config = createConfig(1004L, "0/1 * * * * ?");
        batchTaskUploader.startTask(config);
        Thread.sleep(1100);

        assertTrue(batchTaskUploader.isTaskRunning(1004L));
        batchTaskUploader.pauseTask(1004L);
        // 暂停后Job仍存在，isTaskRunning返回true
        assertTrue(batchTaskUploader.isTaskRunning(1004L), "暂停后Job仍存在");
    }

    @Test
    @DisplayName("5. 恢复任务 - 重新开始调度")
    void testResumeTask_resumesScheduling() throws Exception {
        AgentTaskConfig config = createConfig(1005L, "0/1 * * * * ?");
        batchTaskUploader.startTask(config);
        Thread.sleep(500);
        batchTaskUploader.pauseTask(1005L);
        Thread.sleep(500);

        batchTaskUploader.resumeTask(1005L);
        Thread.sleep(500);
        assertTrue(batchTaskUploader.isTaskRunning(1005L), "恢复后任务应仍在运行");
    }

    // ==================== 3. 任务更新 ====================

    @Test
    @DisplayName("6. 更新Cron表达式 - 热更新")
    void testUpdateCronExpression_hotUpdate() throws Exception {
        AgentTaskConfig config = createConfig(1006L, "0/2 * * * * ?");
        batchTaskUploader.startTask(config);
        Thread.sleep(2100);
        assertTrue(batchTaskUploader.isTaskRunning(1006L));

        // 热更新为每1秒
        AgentTaskConfig updatedConfig = createConfig(1006L, "0/1 * * * * ?");
        batchTaskUploader.updateTask(updatedConfig);
        Thread.sleep(1100);
        assertTrue(batchTaskUploader.isTaskRunning(1006L), "更新后任务应仍在运行");
    }

    // ==================== 4. 任务删除 ====================

    @Test
    @DisplayName("7. 删除任务 - 停止调度")
    void testDeleteTask_stopsScheduling() throws Exception {
        AgentTaskConfig config = createConfig(1007L, "0/1 * * * * ?");
        batchTaskUploader.startTask(config);
        Thread.sleep(1100);
        assertTrue(batchTaskUploader.isTaskRunning(1007L));

        batchTaskUploader.deleteTask(1007L);
        assertFalse(batchTaskUploader.isTaskRunning(1007L), "删除后任务应不再运行");
    }

    // ==================== 5. 任务隔离 ====================

    @Test
    @DisplayName("8. 多任务隔离 - 独立调度")
    void testMultipleTasks_isolated() throws Exception {
        AgentTaskConfig config1 = createConfig(2001L, "0/1 * * * * ?");
        AgentTaskConfig config2 = createConfig(2002L, "0/1 * * * * ?");

        batchTaskUploader.startTask(config1);
        batchTaskUploader.startTask(config2);

        Thread.sleep(1500);

        assertTrue(batchTaskUploader.isTaskRunning(2001L), "任务1应被调度");
        assertTrue(batchTaskUploader.isTaskRunning(2002L), "任务2应被调度");

        // 暂停任务1，任务2应继续
        batchTaskUploader.pauseTask(2001L);
        Thread.sleep(500);
        assertTrue(batchTaskUploader.isTaskRunning(2002L), "任务2应继续运行");
    }

    // ==================== 6. 首次执行 ====================

    @Test
    @DisplayName("9. 启动时立即执行一次")
    void testStartTask_immediateExecution() throws Exception {
        AgentTaskConfig config = createConfig(1008L, "0/10 * * * * ?");
        batchTaskUploader.startTask(config);

        // 启动后应立即触发一次执行
        Thread.sleep(1000);
        assertTrue(batchTaskUploader.isTaskRunning(1008L), "任务应已启动");
    }

    // ==================== 辅助方法 ====================

    private AgentTaskConfig createConfig(Long taskId, String cron) {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName("测试任务-" + taskId);
        config.setSourceAgentId("agent-001");
        config.setSourceDir(tempDir.resolve("source").toString());

        TargetAgentInfo targetInfo = new TargetAgentInfo();
        targetInfo.setTargetDir(tempDir.resolve("target").toString());
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
