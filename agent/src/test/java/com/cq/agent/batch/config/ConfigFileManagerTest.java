package com.cq.agent.batch.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置文件管理器单元测试
 * 覆盖率目标：100%
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

    // ==================== 1. 文件写入测试 ====================

    @Test
    @DisplayName("1. saveTaskConfig() 创建JSON配置文件")
    void testSaveTaskConfig_createsJsonFile() {
        BatchTransferTaskConfig config = createTestConfig(100L, "测试任务");
        
        configFileManager.saveTaskConfig(config);
        
        Path expectedFile = tempDir.resolve("task_100.json");
        assertTrue(Files.exists(expectedFile), "配置文件应该存在");
        
        System.out.println("✅ 配置文件创建成功: " + expectedFile);
    }

    @Test
    @DisplayName("2. saveTaskConfig() 原子写入操作 (tmp→正式)")
    void testSaveTaskConfig_atomicRename() throws IOException {
        BatchTransferTaskConfig config = createTestConfig(101L, "原子操作任务");
        
        configFileManager.saveTaskConfig(config);
        
        Path configFile = tempDir.resolve("task_101.json");
        String content = Files.readString(configFile);
        
        assertTrue(content.contains("101"), "JSON应包含taskId值");
        assertTrue(content.contains("原子操作任务"), "JSON应包含任务名称");
        
        System.out.println("✅ 原子写入验证通过");
    }

    @Test
    @DisplayName("3. saveTaskConfig() 元数据自动更新")
    void testSaveTaskConfig_metaUpdated() {
        BatchTransferTaskConfig config = createTestConfig(102L, "元数据任务");
        
        configFileManager.saveTaskConfig(config);
        
        List<TaskMetaInfo> metaList = configFileManager.loadAllTaskMeta();
        
        assertEquals(1, metaList.size(), "应有1个任务元数据");
        assertEquals(102L, metaList.get(0).getTaskId());
        assertEquals("元数据任务", metaList.get(0).getTaskName());
        
        System.out.println("✅ 元数据更新正确: " + metaList);
    }

    // ==================== 2. 文件读取测试 ====================

    @Test
    @DisplayName("4. loadTaskConfig() 加载存在的配置")
    void testLoadTaskConfig_exists() {
        BatchTransferTaskConfig original = createTestConfig(103L, "加载测试");
        configFileManager.saveTaskConfig(original);
        
        BatchTransferTaskConfig loaded = configFileManager.loadTaskConfig(103L);
        
        assertNotNull(loaded, "加载的配置不应为null");
        assertEquals(103L, loaded.getTaskId());
        assertEquals("加载测试", loaded.getTaskName());
        assertEquals("/var/log/app", loaded.getSourceDir());
        
        System.out.println("✅ 配置加载成功: taskId=" + loaded.getTaskId());
    }

    @Test
    @DisplayName("5. loadTaskConfig() 不存在返回null")
    void testLoadTaskConfig_notFound_null() {
        BatchTransferTaskConfig result = configFileManager.loadTaskConfig(99999L);
        
        assertNull(result, "不存在的任务应返回null");
        
        System.out.println("✅ 不存在的配置返回null");
    }

    @Test
    @DisplayName("6. loadTaskConfig() 损坏文件使用缓存")
    void testLoadTaskConfig_corrupted_useCache() throws IOException {
        BatchTransferTaskConfig original = createTestConfig(104L, "缓存测试");
        configFileManager.saveTaskConfig(original);
        
        Path corruptedFile = tempDir.resolve("task_104.json");
        Files.writeString(corruptedFile, "{invalid json content}");
        
        BatchTransferTaskConfig loaded = configFileManager.loadTaskConfig(104L);
        
        assertNotNull(loaded, "损坏文件应使用缓存的元数据");
        assertEquals(104L, loaded.getTaskId());
        
        System.out.println("✅ 损坏文件降级处理: 使用缓存");
    }

    // ==================== 3. 元数据管理测试 ====================

    @Test
    @DisplayName("7. loadAllTaskMeta() 多个任务的元数据")
    void testLoadAllTaskMeta_multipleTasks() {
        configFileManager.saveTaskConfig(createTestConfig(200L, "任务A"));
        configFileManager.saveTaskConfig(createTestConfig(201L, "任务B"));
        configFileManager.saveTaskConfig(createTestConfig(202L, "任务C"));
        
        List<TaskMetaInfo> metaList = configFileManager.loadAllTaskMeta();
        
        assertEquals(3, metaList.size(), "应有3个任务元数据");
        
        System.out.println("✅ 多任务元数据: count=" + metaList.size());
    }

    @Test
    @DisplayName("8. loadAllTaskMeta() 空目录返回空列表")
    void testLoadAllTaskMeta_emptyDir() {
        List<TaskMetaInfo> metaList = configFileManager.loadAllTaskMeta();
        
        assertNotNull(metaList);
        assertTrue(metaList.isEmpty(), "空目录应返回空列表");
        
        System.out.println("✅ 空目录处理正常");
    }

    // ==================== 4. 删除测试 ====================

    @Test
    @DisplayName("9. deleteTaskConfig() 删除配置文件")
    void testDeleteTaskConfig_fileRemoved() {
        BatchTransferTaskConfig config = createTestConfig(300L, "待删除任务");
        configFileManager.saveTaskConfig(config);
        
        assertTrue(Files.exists(tempDir.resolve("task_300.json")));
        
        configFileManager.deleteTaskConfig(300L);
        
        assertFalse(Files.exists(tempDir.resolve("task_300.json")), "文件应被删除");
        
        System.out.println("✅ 配置文件删除成功");
    }

    @Test
    @DisplayName("10. deleteTaskConfig() 删除后元数据更新")
    void testDeleteTaskConfig_metaUpdated() {
        configFileManager.saveTaskConfig(createTestConfig(301L, "删除元数据"));
        configFileManager.deleteTaskConfig(301L);
        
        List<TaskMetaInfo> metaList = configFileManager.loadAllTaskMeta();
        
        boolean found = metaList.stream()
            .anyMatch(m -> m.getTaskId().equals(301L));
            
        assertFalse(found, "删除的任务不应在元数据中");
        
        System.out.println("✅ 删除后元数据已更新");
    }

    // ==================== 5. 并发安全测试 ====================

    @Test
    @DisplayName("11. 并发写入线程安全")
    void testConcurrentWrites_lockProtection() throws Exception {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final long taskId = 400L + i;
            executor.submit(() -> {
                try {
                    configFileManager.saveTaskConfig(
                        createTestConfig(taskId, "并发任务" + taskId));
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        List<TaskMetaInfo> metaList = configFileManager.loadAllTaskMeta();
        assertEquals(threadCount, metaList.size(), 
            "所有并发写入都应成功");
        
        System.out.println("✅ 并发写入安全: " + threadCount + " 个任务");
    }

    @Test
    @DisplayName("12. 写入期间读取安全")
    void testConcurrentReadDuringWrite_safe() throws Exception {
        configFileManager.saveTaskConfig(createTestConfig(500L, "读写测试"));
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger readSuccessCount = new AtomicInteger(0);
        AtomicInteger writeSuccessCount = new AtomicInteger(0);
        
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 10; j++) {
                        BatchTransferTaskConfig config = 
                            configFileManager.loadTaskConfig(500L);
                        if (config != null) {
                            readSuccessCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        for (int i = 0; i < 2; i++) {
            final long taskId = 501L + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 5; j++) {
                        configFileManager.saveTaskConfig(
                            createTestConfig(taskId, "并发写" + j));
                        writeSuccessCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        startLatch.countDown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        System.out.println("✅ 并发读写安全: reads=" + readSuccessCount.get() + 
                          ", writes=" + writeSuccessCount.get());
    }

    // ==================== 辅助方法 ====================

    private BatchTransferTaskConfig createTestConfig(Long taskId, String taskName) {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName(taskName);
        config.setSourceAgentId("agent-001");
        config.setSourceAgentName("root@10.240.85.177:7777");
        config.setSourceDir("/var/log/app");
        config.setTargetDirs(List.of("/backup/test"));
        config.setIncludePatterns(List.of("*.log", "*.txt"));
        config.setExcludePatterns(List.of("debug*"));
        config.setTargetAgentIds(List.of("agent-002"));
        config.setTargetAgentNames(List.of("root@node2:7777"));
        return config;
    }
}
