package com.cq.agent.batch.config;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.RetryConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
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
        AgentTaskConfig config = createTestConfig(1L);
        ScanConfig scanConfig = new ScanConfig();
        scanConfig.setCronExpression("0 */5 * * * ?");
        config.setScanConfig(scanConfig);
        config.setVersion(String.valueOf(1));
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(1L, 1L);

        AtomicBoolean schedulerRebuilt = new AtomicBoolean(false);
        configChangeListener.onCronChange(taskId -> {
            schedulerRebuilt.set(true);
            assertEquals(1L, taskId.longValue());
        });

        AgentTaskConfig updatedConfig = createTestConfig(1L);
        ScanConfig updatedScanConfig = new ScanConfig();
        updatedScanConfig.setCronExpression("0 */10 * * * ?");
        updatedConfig.setScanConfig(updatedScanConfig);
        updatedConfig.setVersion(String.valueOf(2));

        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);

        assertTrue(changed, "应检测到Cron变更");
        assertTrue(schedulerRebuilt.get(), "调度器应被重建");

        System.out.println("✅ Cron变更触发调度器重建");
    }

    // ==================== 2. 文件模式变更 ====================

    @Test
    @DisplayName("2. 模式变更 - 下次扫描生效")
    void testPatternsChanged_nextScanEffective() throws Exception {
        AgentTaskConfig config = createTestConfig(2L);
        config.setIncludePatterns(List.of("*.log"));
        config.setVersion(String.valueOf(1));
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(2L, 1L);

        AtomicBoolean patternsUpdated = new AtomicBoolean(false);
        configChangeListener.onPatternChange(context -> {
            patternsUpdated.set(true);
            assertEquals(2L, context.taskId.longValue());
            assertNotNull(context.patterns);
        });

        AgentTaskConfig updatedConfig = createTestConfig(2L);
        updatedConfig.setIncludePatterns(List.of("*.log", "*.txt"));
        updatedConfig.setVersion(String.valueOf(2));

        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);

        assertTrue(changed, "应检测到模式变更");
        assertTrue(patternsUpdated.get(), "模式应更新");

        System.out.println("✅ 模式变更将在下次扫描生效");
    }

    // ==================== 3. 重试配置变更 ====================

    @Test
    @DisplayName("3. 重试配置变更 - 立即应用")
    void testRetryConfigChanged_immediateApply() throws Exception {
        AgentTaskConfig config = createTestConfig(3L);
        RetryConfig retryConfig = new RetryConfig();
        retryConfig.setMaxRetryCount(Integer.valueOf(3));
        config.setRetryConfig(retryConfig);
        config.setVersion(String.valueOf(1));
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(3L, 1L);

        AtomicBoolean retryConfigUpdated = new AtomicBoolean(false);
        configChangeListener.onRetryConfigChange(context -> {
            retryConfigUpdated.set(true);
            assertEquals(3L, context.taskId.longValue());
            assertEquals(5, context.maxRetries);
        });

        AgentTaskConfig updatedConfig = createTestConfig(3L);
        RetryConfig updatedRetryConfig = new RetryConfig();
        updatedRetryConfig.setMaxRetryCount(5);
        updatedConfig.setRetryConfig(updatedRetryConfig);
        updatedConfig.setVersion(String.valueOf(2));

        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);

        assertTrue(changed, "应检测到重试配置变更");
        assertTrue(retryConfigUpdated.get(), "重试配置应立即应用");

        System.out.println("✅ 重试配置已立即应用");
    }

    // ==================== 4. 目标Agent变更 ====================

    @Test
    @DisplayName("4. 目标Agent变更 - 子任务重新生成")
    void testTargetAgentsChanged_subtasksRegenerated() throws Exception {
        AgentTaskConfig config = createTestConfig(4L);
        config.setVersion(String.valueOf(1));
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(4L, 1L);

        AtomicBoolean subtasksRegenerated = new AtomicBoolean(false);
        configChangeListener.onTargetAgentsChange(context -> {
            subtasksRegenerated.set(true);
            assertEquals(4L, context.taskId.longValue());
            assertNotNull(context.agents);
            assertEquals(2, context.agents.size());
        });

        AgentTaskConfig updatedConfig = createTestConfig(4L);
        List<TargetAgentInfo> targets = new ArrayList<>();
        TargetAgentInfo info1 = new TargetAgentInfo();
        info1.setAgentId("agent-001");
        targets.add(info1);
        TargetAgentInfo info2 = new TargetAgentInfo();
        info2.setAgentId("agent-002");
        targets.add(info2);
        updatedConfig.setTargetAgents(targets);
        updatedConfig.setVersion(String.valueOf(2));

        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);

        assertTrue(changed, "应检测到目标Agent变更");
        assertTrue(subtasksRegenerated.get(), "子任务应重新生成");

        System.out.println("✅ 目标Agent变更触发子任务重新生成");
    }

    // ==================== 5. 运行中任务热更新 ====================

    @Test
    @DisplayName("5. 运行中任务 - 热更新不中断")
    void testRunningTask_hotUpdateWithoutInterrupt() throws Exception {
        AgentTaskConfig config = createTestConfig(5L);
        config.setStatus("RUNNING");
        config.setVersion(String.valueOf(1));
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(5L, 1L);

        AtomicInteger updateCount = new AtomicInteger(0);

        configChangeListener.onAnyChange(context -> {
            updateCount.incrementAndGet();
        });

        AgentTaskConfig updatedConfig = createTestConfig(5L);
        updatedConfig.setStatus("RUNNING");
        updatedConfig.setIncludePatterns(List.of("*.new"));
        updatedConfig.setVersion(String.valueOf(2));

        boolean changed = configChangeListener.detectAndApplyChange(updatedConfig);

        assertTrue(changed, "运行中任务也应支持热更新");

        System.out.println("✅ 运行中任务热更新成功（不中断）");
    }

    // ==================== 6. 无变化检测 ====================

    @Test
    @DisplayName("6. 无变更 - 返回false")
    void testNoChange_returnsFalse() throws Exception {
        AgentTaskConfig config = createTestConfig(6L);
        config.setVersion(String.valueOf(100));
        configFileManager.saveTaskConfig(config);
        versionManager.acceptVersion(6L, 100L);

        AgentTaskConfig sameConfig = createTestConfig(6L);
        sameConfig.setVersion(String.valueOf(100));

        boolean changed = configChangeListener.detectAndApplyChange(sameConfig);

        assertFalse(changed, "无变更应返回false");

        System.out.println("✅ 无变更正确识别");
    }

    // ==================== 辅助方法 ====================

    private AgentTaskConfig createTestConfig(Long taskId) {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(taskId);
        config.setTaskName("测试任务" + taskId);
        config.setSourceAgentId("agent-001");
        config.setSourceDir("/var/log/app");

        List<TargetAgentInfo> targets = new ArrayList<>();
        TargetAgentInfo target = new TargetAgentInfo();
        target.setAgentId("agent-002");
        target.setTargetDir("/backup/test");
        targets.add(target);
        config.setTargetAgents(targets);

        config.setIncludePatterns(List.of("*.log", "*.txt"));
        config.setExcludePatterns(List.of("debug*"));
        config.setStatus("READY");
        return config;
    }
}
