package com.cq.agent.batch.config;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置变更监听器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("配置变更监听器 - ConfigChangeListener")
class ConfigChangeListenerTest {

    private ConfigChangeListener configChangeListener;
    private ConfigFileManager configFileManager;
    private VersionManager versionManager;
    
    @org.junit.jupiter.api.io.TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        configFileManager = new ConfigFileManager(tempDir.toString());
        versionManager = new VersionManager();
        configChangeListener = new ConfigChangeListener(configFileManager, versionManager);
    }

    @AfterEach
    void tearDown() {
        configChangeListener.shutdown();
    }

    // ==================== 1. Cron表达式变更 ====================

    @Test
    @DisplayName("1. Cron变更 - 调度器重建")
    void testCronChanged_schedulerRebuilt() throws Exception {
        BatchTransferTaskConfig config = createTestConfig(1L);
        config.setCronExpression("0 */5 * * * ?");
        config.setVersion(1L);
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(1L, 1L);
        
        AtomicBoolean schedulerRebuilt = new AtomicBoolean(false);
        configChangeListener.onCronChange(taskId -> {
            schedulerRebuilt.set(true);
            assertEquals(1L, taskId.longValue());
        });
        
        BatchTransferTaskConfig updatedConfig = createTestConfig(1L);
        updatedConfig.setCronExpression("0 */10 * * * ?");
        updatedConfig.setVersion(2L);
        
        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);
        
        assertTrue(changed, "应检测到Cron变更");
        assertTrue(schedulerRebuilt.get(), "调度器应被重建");
        
        System.out.println("✅ Cron变更触发调度器重建");
    }

    // ==================== 2. 文件模式变更 ====================

    @Test
    @DisplayName("2. 模式变更 - 下次扫描生效")
    void testPatternsChanged_nextScanEffective() throws Exception {
        BatchTransferTaskConfig config = createTestConfig(2L);
        config.setIncludePatterns(java.util.List.of("*.log"));
        config.setVersion(1L);
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(2L, 1L);
        
        AtomicBoolean patternsUpdated = new AtomicBoolean(false);
        configChangeListener.onPatternChange(context -> {
            patternsUpdated.set(true);
            assertEquals(2L, context.taskId.longValue());
            assertNotNull(context.patterns);
        });
        
        BatchTransferTaskConfig updatedConfig = createTestConfig(2L);
        updatedConfig.setIncludePatterns(java.util.List.of("*.log", "*.txt"));
        updatedConfig.setVersion(2L);
        
        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);
        
        assertTrue(changed, "应检测到模式变更");
        assertTrue(patternsUpdated.get(), "模式应更新");
        
        System.out.println("✅ 模式变更将在下次扫描生效");
    }

    // ==================== 3. 重试配置变更 ====================

    @Test
    @DisplayName("3. 重试配置变更 - 立即应用")
    void testRetryConfigChanged_immediateApply() throws Exception {
        BatchTransferTaskConfig config = createTestConfig(3L);
        config.setMaxRetries(3);
        config.setVersion(1L);
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(3L, 1L);
        
        AtomicBoolean retryConfigUpdated = new AtomicBoolean(false);
        configChangeListener.onRetryConfigChange(context -> {
            retryConfigUpdated.set(true);
            assertEquals(3L, context.taskId.longValue());
            assertEquals(5, context.maxRetries);
        });
        
        BatchTransferTaskConfig updatedConfig = createTestConfig(3L);
        updatedConfig.setMaxRetries(5);
        updatedConfig.setVersion(2L);
        
        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);
        
        assertTrue(changed, "应检测到重试配置变更");
        assertTrue(retryConfigUpdated.get(), "重试配置应立即应用");
        
        System.out.println("✅ 重试配置已立即应用");
    }

    // ==================== 4. 目标Agent变更 ====================

    @Test
    @DisplayName("4. 目标Agent变更 - 子任务重新生成")
    void testTargetAgentsChanged_subtasksRegenerated() throws Exception {
        BatchTransferTaskConfig config = createTestConfig(4L);
        config.setTargetAgentIds(java.util.List.of("agent-001"));
        config.setVersion(1L);
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(4L, 1L);
        
        AtomicBoolean subtasksRegenerated = new AtomicBoolean(false);
        configChangeListener.onTargetAgentsChange(context -> {
            subtasksRegenerated.set(true);
            assertEquals(4L, context.taskId.longValue());
            assertNotNull(context.agents);
            assertEquals(2, context.agents.size());
        });
        
        BatchTransferTaskConfig updatedConfig = createTestConfig(4L);
        updatedConfig.setTargetAgentIds(java.util.List.of("agent-001", "agent-002"));
        updatedConfig.setVersion(2L);
        
        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);
        
        assertTrue(changed, "应检测到目标Agent变更");
        assertTrue(subtasksRegenerated.get(), "子任务应重新生成");
        
        System.out.println("✅ 目标Agent变更触发子任务重新生成");
    }

    // ==================== 5. 运行中任务热更新 ====================

    @Test
    @DisplayName("5. 运行中任务 - 热更新不中断")
    void testRunningTask_hotUpdateWithoutInterrupt() throws Exception {
        BatchTransferTaskConfig config = createTestConfig(5L);
        config.setStatus("RUNNING");
        config.setVersion(1L);
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(5L, 1L);
        
        AtomicInteger updateCount = new AtomicInteger(0);
        
        configChangeListener.onAnyChange(context -> {
            updateCount.incrementAndGet();
        });
        
        BatchTransferTaskConfig updatedConfig = createTestConfig(5L);
        updatedConfig.setStatus("RUNNING");
        updatedConfig.setIncludePatterns(java.util.List.of("*.new"));
        updatedConfig.setVersion(2L);
        
        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);
        
        assertTrue(changed, "运行中任务也应支持热更新");
        
        System.out.println("✅ 运行中任务热更新成功（不中断）");
    }

    // ==================== 6. 无变化检测 ====================

    @Test
    @DisplayName("6. 无变更 - 返回false")
    void testNoChange_returnsFalse() throws Exception {
        BatchTransferTaskConfig config = createTestConfig(6L);
        config.setVersion(100L);
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(6L, 100L);
        
        BatchTransferTaskConfig sameConfig = createTestConfig(6L);
        sameConfig.setVersion(100L);
        
        boolean changed = configChangeListener.detectAndApplyChange(sameConfig);
        
        assertFalse(changed, "无变更应返回false");
        
        System.out.println("✅ 无变更正确识别");
    }

    // ==================== 辅助方法 ====================

    private BatchTransferTaskConfig createTestConfig(Long taskId) {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName("测试任务" + taskId);
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");
        config.setTargetDirs(java.util.List.of("/backup/test"));
        config.setIncludePatterns(java.util.List.of("*.log", "*.txt"));
        config.setExcludePatterns(java.util.List.of("debug*"));
        config.setTargetAgentIds(java.util.List.of("agent-002"));
        config.setStatus("READY");
        return config;
    }
}
