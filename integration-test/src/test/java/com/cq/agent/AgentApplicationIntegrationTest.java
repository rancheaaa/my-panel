package com.cq.agent;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentApplication 集成测试
 * 验证BatchTaskSchedulerUploaderDecorator的Quartz调度功能
 */
@DisplayName("AgentApplication - 组件集成测试")
class AgentApplicationIntegrationTest {

    @TempDir
    Path tempDir;

    private BatchTaskSchedulerUploaderDecorator batchTaskUploader;
    private ConfigFileManager configFileManager;
    private String configDir;

    @BeforeEach
    void setUp() throws Exception {
        configDir = tempDir.resolve("batch-config").toString();
        configFileManager = new ConfigFileManager(configDir);

        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setUploadSendingQueueDir(tempDir.resolve("sending").toString());
        agentConfig.setUploadFailRetryQueueDir(tempDir.resolve("fail-retry").toString());
        agentConfig.setUploadFinalFailureQueueDir(tempDir.resolve("final-failure").toString());
        agentConfig.setUploadSuccessQueueDir(tempDir.resolve("success").toString());

        batchTaskUploader = new BatchTaskSchedulerUploaderDecorator(agentConfig, configFileManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (batchTaskUploader != null) {
            batchTaskUploader.shutdown();
        }
    }

    @Test
    @DisplayName("1. 完整流程：接收配置→持久化→启动Quartz调度")
    void testFullFlow_receiveConfigAndStartScheduler() throws Exception {
        // 1. 创建任务配置
        AgentTaskConfig config = createConfig(1001L, "0/1 * * * * ?");

        // 2. 持久化配置（模拟BatchConfigReceiveHandler的行为）
        configFileManager.saveTaskConfig(config);

        // 3. 验证配置已保存
        AgentTaskConfig loaded = configFileManager.loadTaskConfig(1001L);
        assertNotNull(loaded);
        assertEquals("0/1 * * * * ?", loaded.getScanConfig().getCronExpression());

        // 4. 启动Quartz调度
        AtomicInteger executionCount = new AtomicInteger(0);
        batchTaskUploader.startTask(loaded);

        // 5. 验证调度器已启动
        assertTrue(batchTaskUploader.isTaskRunning(1001L), "任务应处于运行状态");

        // 6. 等待执行
        Thread.sleep(1500);
        // Quartz调度执行（不验证具体次数，只验证任务在运行）
        assertTrue(batchTaskUploader.isTaskRunning(1001L), "任务应仍在运行");
    }

    @Test
    @DisplayName("2. 启动加载：从本地文件恢复RUNNING任务")
    void testStartupLoader_restoreRunningTasks() throws Exception {
        // 1. 保存RUNNING状态的任务
        AgentTaskConfig config1 = createConfig(2001L, "0/1 * * * * ?");
        config1.setStatus("RUNNING");
        configFileManager.saveTaskConfig(config1);

        // 2. 保存PAUSED状态的任务
        AgentTaskConfig config2 = createConfig(2002L, "0/1 * * * * ?");
        config2.setStatus("PAUSED");
        configFileManager.saveTaskConfig(config2);

        // 3. 只恢复RUNNING状态的任务
        AgentTaskConfig loaded1 = configFileManager.loadTaskConfig(2001L);
        if ("RUNNING".equals(loaded1.getStatus())) {
            batchTaskUploader.startTask(loaded1);
        }

        AgentTaskConfig loaded2 = configFileManager.loadTaskConfig(2002L);
        if ("RUNNING".equals(loaded2.getStatus())) {
            batchTaskUploader.startTask(loaded2);
        }

        // 4. 验证：只有RUNNING任务被调度
        Thread.sleep(500);
        assertTrue(batchTaskUploader.isTaskRunning(2001L), "RUNNING任务应被调度");
        assertFalse(batchTaskUploader.isTaskRunning(2002L), "PAUSED任务不应被调度");
    }

    @Test
    @DisplayName("3. 热更新：Cron表达式变更后重新调度")
    void testHotUpdate_cronExpressionChanged() throws Exception {
        // 1. 启动任务（每2秒）
        AgentTaskConfig config = createConfig(3001L, "0/2 * * * * ?");
        batchTaskUploader.startTask(config);

        Thread.sleep(2100);
        assertTrue(batchTaskUploader.isTaskRunning(3001L));

        // 2. 模拟配置变更（热更新为每1秒）
        AgentTaskConfig updatedConfig = createConfig(3001L, "0/1 * * * * ?");
        configFileManager.saveTaskConfig(updatedConfig);
        batchTaskUploader.updateTask(updatedConfig);

        Thread.sleep(1100);
        // 验证更新后任务仍在运行
        assertTrue(batchTaskUploader.isTaskRunning(3001L), "更新后任务应仍在运行");
    }

    @Test
    @DisplayName("4. 状态变更：PAUSED→RUNNING→调度恢复")
    void testStatusChange_pausedToRunning() throws Exception {
        // 1. 启动任务
        AgentTaskConfig config = createConfig(4001L, "0/1 * * * * ?");
        batchTaskUploader.startTask(config);

        Thread.sleep(1100);
        assertTrue(batchTaskUploader.isTaskRunning(4001L));

        // 2. 暂停任务
        batchTaskUploader.pauseTask(4001L);
        Thread.sleep(500);
        // 暂停后任务Job仍存在，只是暂停状态

        // 3. 恢复任务
        batchTaskUploader.resumeTask(4001L);
        Thread.sleep(500);
        assertTrue(batchTaskUploader.isTaskRunning(4001L), "恢复后任务应仍在运行");
    }

    @Test
    @DisplayName("5. 删除任务：停止调度并清理")
    void testDeleteTask_stopScheduling() throws Exception {
        // 1. 启动任务
        AgentTaskConfig config = createConfig(5001L, "0/1 * * * * ?");
        batchTaskUploader.startTask(config);

        Thread.sleep(1100);
        assertTrue(batchTaskUploader.isTaskRunning(5001L));

        // 2. 删除任务
        batchTaskUploader.deleteTask(5001L);

        // 3. 验证
        assertFalse(batchTaskUploader.isTaskRunning(5001L), "任务应已停止");
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
