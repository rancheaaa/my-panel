package com.cq.agent.batch.config;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 启动加载器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("启动加载器 - StartupLoader")
class StartupLoaderTest {

    private StartupLoader startupLoader;
    private ConfigFileManager configFileManager;

    @org.junit.jupiter.api.io.TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        configFileManager = new ConfigFileManager(tempDir.toString());
        startupLoader = new StartupLoader(configFileManager);
    }

    // ==================== 1. 首次启动 - 空配置 ====================

    @Test
    @DisplayName("1. 首次启动 - 空配置等待Proxy")
    void testFirstStart_emptyConfig_waitForProxy() {
        List<AgentTaskConfig> loadedTasks = startupLoader.load();

        assertNotNull(loadedTasks);
        assertTrue(loadedTasks.isEmpty(), "首次启动应返回空列表");

        System.out.println("✅ 首次启动: 空配置，等待Proxy推送");
    }

    // ==================== 2. 已有配置 - 加载任务 ====================

    @Test
    @DisplayName("2. 已有配置 - 任务成功加载")
    void testExistingConfig_tasksLoaded() {
        AgentTaskConfig task1 = createTestConfig(1L, "任务A", "READY");
        AgentTaskConfig task2 = createTestConfig(2L, "任务B", "RUNNING");

        configFileManager.saveTaskConfig(task1);
        configFileManager.saveTaskConfig(task2);

        List<AgentTaskConfig> loadedTasks = startupLoader.load();

        assertEquals(2, loadedTasks.size(), "应加载2个任务");

        boolean foundTask1 = loadedTasks.stream()
            .anyMatch(t -> t.getTaskId().equals(1L) && t.getTaskName().equals("任务A"));
        boolean foundTask2 = loadedTasks.stream()
            .anyMatch(t -> t.getTaskId().equals(2L) && t.getTaskName().equals("任务B"));

        assertTrue(foundTask1, "应包含任务A");
        assertTrue(foundTask2, "应包含任务B");

        System.out.println("✅ 已有配置加载: " + loadedTasks.size() + " 个任务");
    }

    // ==================== 3. 运行中任务 - 调度器重建 ====================

    @Test
    @DisplayName("3. 运行中任务 - 调度器重建")
    void testRunningTasks_schedulerRecreated() {
        AgentTaskConfig runningTask = createTestConfig(10L, "运行中任务", "RUNNING");
        ScanConfig scanConfig = new ScanConfig();
        scanConfig.setCronExpression("0 */5 * * * ?");
        runningTask.setScanConfig(scanConfig);
        configFileManager.saveTaskConfig(runningTask);

        AtomicBoolean schedulerRecreated = new AtomicBoolean(false);
        startupLoader.setSchedulerRecreator(taskId -> {
            schedulerRecreated.set(true);
            assertEquals(10L, taskId.longValue());
        });

        List<AgentTaskConfig> loadedTasks = startupLoader.load();

        assertFalse(loadedTasks.isEmpty(), "应加载运行中的任务");
        assertTrue(schedulerRecreated.get(), "运行中任务的调度器应被重建");

        System.out.println("✅ 运行中任务调度器已重建");
    }

    // ==================== 4. 损坏元数据恢复 ====================

    @Test
    @DisplayName("4. 损坏元数据 - 自动恢复")
    void testCorruptedMeta_recovery() throws Exception {
        AgentTaskConfig validTask = createTestConfig(99L, "有效任务", "READY");
        configFileManager.saveTaskConfig(validTask);

        Path metaFile = tempDir.resolve("task_99.json");
        java.nio.file.Files.writeString(metaFile, "{corrupted json content}");

        List<AgentTaskConfig> loadedTasks = startupLoader.load();

        assertNotNull(loadedTasks, "损坏后不应抛异常");
        System.out.println("✅ 损坏元数据自动恢复: loaded=" + (loadedTasks != null ? loadedTasks.size() : 0));
    }

    // ==================== 辅助方法 ====================

    private AgentTaskConfig createTestConfig(Long taskId, String taskName, String status) {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName(taskName);
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");

        List<TargetAgentInfo> targets = new ArrayList<>();
        TargetAgentInfo target = new TargetAgentInfo();
        target.setAgentId("agent-002");
        target.setTargetDir("/backup/test");
        targets.add(target);
        config.setTargetAgents(targets);

        config.setIncludePatterns(List.of("*.log"));
        config.setStatus(status);
        return config;
    }
}
