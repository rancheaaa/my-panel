package com.cq.agent.batch.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigFileManager单元测试
 * 验证tasks.meta.json持久化和任务配置管理
 */
@DisplayName("配置文件管理器 - ConfigFileManager")
class ConfigFileManagerTest {

    @TempDir
    Path tempDir;

    private ConfigFileManager configFileManager;

    @BeforeEach
    void setUp() {
        configFileManager = new ConfigFileManager(tempDir.toString());
    }

    @Test
    @DisplayName("1. 保存任务配置 - 同时更新tasks.meta.json")
    void testSaveTaskConfig() {
        BatchTransferTaskConfig config = createTestConfig(1001L, "日志备份");

        configFileManager.saveTaskConfig(config);

        // 验证task_1001.json文件存在
        File taskFile = new File(tempDir.toFile(), "task_1001.json");
        assertTrue(taskFile.exists(), "task_1001.json应存在");

        // 验证tasks.meta.json存在
        File metaFile = new File(tempDir.toFile(), "tasks.meta.json");
        assertTrue(metaFile.exists(), "tasks.meta.json应存在");

        System.out.println("✅ 保存任务配置: task_1001.json和tasks.meta.json都已创建");
    }

    @Test
    @DisplayName("2. tasks.meta.json - 包含正确的元数据")
    void testTasksMetaJson() {
        BatchTransferTaskConfig config1 = createTestConfig(1001L, "日志备份");
        BatchTransferTaskConfig config2 = createTestConfig(1002L, "数据同步");

        configFileManager.saveTaskConfig(config1);
        configFileManager.saveTaskConfig(config2);

        // 验证meta文件内容
        TaskMetaInfo meta = configFileManager.loadMetaInfo();
        assertNotNull(meta, "meta信息不应为null");
        assertEquals(2, meta.getTasks().size(), "应有2个任务");

        TaskMetaInfo.TaskMeta task1 = findTaskMeta(meta, 1001L);
        assertNotNull(task1, "应找到task 1001");
        assertEquals("日志备份", task1.getTaskName());
        assertEquals("RUNNING", task1.getStatus());

        TaskMetaInfo.TaskMeta task2 = findTaskMeta(meta, 1002L);
        assertNotNull(task2, "应找到task 1002");
        assertEquals("数据同步", task2.getTaskName());

        System.out.println("✅ tasks.meta.json验证: 2个任务, 状态正确");
    }

    @Test
    @DisplayName("3. 加载所有任务配置")
    void testLoadAllTaskConfigs() {
        configFileManager.saveTaskConfig(createTestConfig(1001L, "任务1"));
        configFileManager.saveTaskConfig(createTestConfig(1002L, "任务2"));

        List<BatchTransferTaskConfig> configs = configFileManager.loadAllTaskConfigs();

        assertEquals(2, configs.size(), "应加载2个任务");

        BatchTransferTaskConfig config1 = findConfig(configs, 1001L);
        assertNotNull(config1);
        assertEquals("任务1", config1.getTaskName());

        System.out.println("✅ 加载所有任务: count=2");
    }

    @Test
    @DisplayName("4. 删除任务配置 - 同时更新meta")
    void testDeleteTaskConfig() {
        configFileManager.saveTaskConfig(createTestConfig(1001L, "任务1"));
        configFileManager.saveTaskConfig(createTestConfig(1002L, "任务2"));

        configFileManager.deleteTaskConfig(1001L);

        List<BatchTransferTaskConfig> configs = configFileManager.loadAllTaskConfigs();
        assertEquals(1, configs.size(), "应只剩1个任务");

        TaskMetaInfo meta = configFileManager.loadMetaInfo();
        assertEquals(1, meta.getTasks().size(), "meta中应只剩1个任务");

        System.out.println("✅ 删除任务: task_1001已删除");
    }

    @Test
    @DisplayName("5. 更新任务状态 - 同步更新meta")
    void testUpdateTaskStatus() {
        configFileManager.saveTaskConfig(createTestConfig(1001L, "任务1"));

        configFileManager.updateTaskStatus(1001L, "PAUSED");

        BatchTransferTaskConfig config = configFileManager.loadTaskConfig(1001L);
        assertEquals("PAUSED", config.getStatus(), "状态应更新为PAUSED");

        TaskMetaInfo meta = configFileManager.loadMetaInfo();
        TaskMetaInfo.TaskMeta taskMeta = findTaskMeta(meta, 1001L);
        assertEquals("PAUSED", taskMeta.getStatus(), "meta中状态也应更新");

        System.out.println("✅ 更新任务状态: RUNNING→PAUSED");
    }

    private BatchTransferTaskConfig createTestConfig(Long taskId, String taskName) {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName(taskName);
        config.setStatus("RUNNING");
        config.setVersion(System.currentTimeMillis());
        config.setReceivedAt("2026-05-09T10:30:00Z");
        config.setPersistedAt("2026-05-09T10:30:01Z");
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");
        config.setTargetAgentIds(List.of("agent-002"));
        config.setIncludePatterns(List.of("*.log"));
        return config;
    }

    private TaskMetaInfo.TaskMeta findTaskMeta(TaskMetaInfo meta, Long taskId) {
        return meta.getTasks().stream()
            .filter(t -> t.getTaskId().equals(taskId))
            .findFirst()
            .orElse(null);
    }

    private BatchTransferTaskConfig findConfig(List<BatchTransferTaskConfig> configs, Long taskId) {
        return configs.stream()
            .filter(c -> c.getTaskId().equals(taskId))
            .findFirst()
            .orElse(null);
    }
}
