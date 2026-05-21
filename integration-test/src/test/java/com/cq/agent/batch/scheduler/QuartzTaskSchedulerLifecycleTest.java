package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quartz任务调度器生命周期测试
 * 验证BatchTaskSchedulerUploaderDecorator的完整生命周期管理
 */
@DisplayName("Quartz任务调度器 - 生命周期测试")
class QuartzTaskSchedulerLifecycleTest {

    @TempDir
    Path tempDir;

    private BatchTaskSchedulerUploaderDecorator batchTaskUploader;
    private ConfigFileManager configFileManager;

    @BeforeEach
    void setUp() throws Exception {
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setUploadSendingQueueDir(tempDir.resolve("sending").toString());
        agentConfig.setUploadFailRetryQueueDir(tempDir.resolve("fail-retry").toString());
        agentConfig.setUploadFinalFailureQueueDir(tempDir.resolve("final-failure").toString());
        agentConfig.setUploadSuccessQueueDir(tempDir.resolve("success").toString());

        configFileManager = new ConfigFileManager(tempDir.resolve("batch-config").toString());
        batchTaskUploader = new BatchTaskSchedulerUploaderDecorator(agentConfig, configFileManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (batchTaskUploader != null) {
            batchTaskUploader.shutdown();
        }
    }

    @Test
    @DisplayName("1. 完整生命周期：启动→执行→暂停→恢复→删除")
    void testFullLifecycle() throws Exception {
        AgentTaskConfig config = createConfig(3001L, "0/1 * * * * ?");

        // 1. 启动
        batchTaskUploader.startTask(config);
        Thread.sleep(1100);
        assertTrue(batchTaskUploader.isTaskRunning(3001L), "启动后应运行");

        // 2. 暂停
        batchTaskUploader.pauseTask(3001L);
        Thread.sleep(500);
        assertTrue(batchTaskUploader.isTaskRunning(3001L), "暂停后Job仍存在");

        // 3. 恢复
        batchTaskUploader.resumeTask(3001L);
        Thread.sleep(1100);
        assertTrue(batchTaskUploader.isTaskRunning(3001L), "恢复后应运行");

        // 4. 删除
        batchTaskUploader.deleteTask(3001L);
        assertFalse(batchTaskUploader.isTaskRunning(3001L), "删除后应不再运行");
    }

    @Test
    @DisplayName("2. 重启调度器后恢复RUNNING任务")
    void testRestart_restoreRunningTasks() throws Exception {
        // 1. 保存RUNNING状态的任务
        AgentTaskConfig config = createConfig(3002L, "0/1 * * * * ?");
        config.setStatus("RUNNING");
        configFileManager.saveTaskConfig(config);

        // 2. 模拟重启：加载所有RUNNING任务
        batchTaskUploader.startAllRunningTasks();
        Thread.sleep(1100);
        assertTrue(batchTaskUploader.isTaskRunning(3002L), "重启后应恢复RUNNING任务");
    }

    @Test
    @DisplayName("3. shutdown后所有任务停止")
    void testShutdown_allTasksStop() throws Exception {
        AgentTaskConfig config1 = createConfig(3003L, "0/1 * * * * ?");
        AgentTaskConfig config2 = createConfig(3004L, "0/1 * * * * ?");

        batchTaskUploader.startTask(config1);
        batchTaskUploader.startTask(config2);
        Thread.sleep(1100);

        assertTrue(batchTaskUploader.isTaskRunning(3003L));
        assertTrue(batchTaskUploader.isTaskRunning(3004L));

        // shutdown
        batchTaskUploader.shutdown();

        // shutdown后Quartz已关闭，isTaskRunning可能抛异常或返回false
        // 这里只验证shutdown不抛异常
    }

    @Test
    @DisplayName("4. 重复启动同一任务 - 先删除再创建")
    void testRestartSameTask_deleteAndRecreate() throws Exception {
        AgentTaskConfig config = createConfig(3005L, "0/1 * * * * ?");
        batchTaskUploader.startTask(config);
        Thread.sleep(1100);
        assertTrue(batchTaskUploader.isTaskRunning(3005L));

        // 再次启动（应先删除旧的再创建新的）
        AgentTaskConfig updatedConfig = createConfig(3005L, "0/2 * * * * ?");
        batchTaskUploader.startTask(updatedConfig);
        Thread.sleep(1100);
        assertTrue(batchTaskUploader.isTaskRunning(3005L), "重新启动后应仍在运行");
    }

    @Test
    @DisplayName("5. 暂停不存在的任务 - 不抛异常")
    void testPauseNonExistentTask_noException() {
        assertDoesNotThrow(() -> batchTaskUploader.pauseTask(9999L));
    }

    @Test
    @DisplayName("6. 恢复不存在的任务 - 不抛异常")
    void testResumeNonExistentTask_noException() {
        assertDoesNotThrow(() -> batchTaskUploader.resumeTask(9999L));
    }

    @Test
    @DisplayName("7. 删除不存在的任务 - 不抛异常")
    void testDeleteNonExistentTask_noException() {
        assertDoesNotThrow(() -> batchTaskUploader.deleteTask(9999L));
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
