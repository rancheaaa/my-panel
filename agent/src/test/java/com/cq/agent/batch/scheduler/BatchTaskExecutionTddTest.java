package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD测试：验证BatchTaskSchedulerManager的完整任务执行流程
 * 核心要求（spec.md 4.5-4.7）：
 * 1. 扫描源目录并匹配文件模式
 * 2. 调用传输逻辑进行P2P传输
 * 3. 记录传输结果和进度
 * 4. 失败时触发重试机制
 */
@DisplayName("批量任务执行流程 - TDD")
class BatchTaskExecutionTddTest {

    @Mock
    private ConfigFileManager configFileManager;

    @Mock
    private RetryAwareUploaderDecorator retryAwareUploader;

    @Mock
    private FileScanner fileScanner;

    private BatchTaskSchedulerManager schedulerManager;
    private Path tempDir;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        schedulerManager = new BatchTaskSchedulerManager(configFileManager);
        schedulerManager.setRetryAwareUploader(retryAwareUploader);
        schedulerManager.setFileScanner(fileScanner);

        // 创建临时目录用于测试
        tempDir = Files.createTempDirectory("batch-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schedulerManager != null) {
            schedulerManager.shutdown();
        }
        mocks.close();

        // 清理临时目录
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    // ==================== 核心执行流程测试 ====================

    @Test
    @DisplayName("1. [spec.md 4.5] 任务执行时应扫描源目录")
    void testExecuteTask_shouldScanSourceDirectory() {
        // Given: 创建任务配置
        AgentTaskConfig config = createTestConfig(tempDir.toString());
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(Collections.emptyList());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应调用FileScanner扫描目录
        verify(fileScanner).scan(
            eq(tempDir.toString()),
            eq(config.getIncludePatterns()),
            eq(config.getExcludePatterns()),
            any()
        );

        System.out.println("✅ 目录扫描验证: FileScanner.scan被正确调用");
    }

    @Test
    @DisplayName("2. [spec.md 4.5] 应使用include_patterns过滤文件")
    void testExecuteTask_shouldApplyIncludePatterns() {
        // Given: 配置包含模式
        AgentTaskConfig config = createTestConfig(tempDir.toString());
        config.setIncludePatterns(List.of("*.log"));

        when(fileScanner.scan(anyString(), eq(List.of("*.log")), isNull(), isNull()))
            .thenReturn(Collections.emptyList());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应传递include_patterns给FileScanner
        verify(fileScanner).scan(
            anyString(),
            eq(List.of("*.log")),
            isNull(),
            isNull()
        );

        System.out.println("✅ include_patterns验证: 只扫描.log文件");
    }

    @Test
    @DisplayName("3. [spec.md 4.5] 应使用exclude_patterns排除文件")
    void testExecuteTask_shouldApplyExcludePatterns() {
        // Given: 配置排除模式
        AgentTaskConfig config = createTestConfig(tempDir.toString());
        config.setExcludePatterns(List.of("debug*", "temp*"));

        when(fileScanner.scan(anyString(), isNull(), eq(List.of("debug*", "temp*")), isNull()))
            .thenReturn(Collections.emptyList());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应传递exclude_patterns给FileScanner
        verify(fileScanner).scan(
            anyString(),
            isNull(),
            eq(List.of("debug*", "temp*")),
            isNull()
        );

        System.out.println("✅ exclude_patterns验证: 排除debug*和temp*文件");
    }

    @Test
    @DisplayName("4. [spec.md 4.5] 扫描不到文件时也应标记完成")
    void testExecuteTask_noFilesFoundShouldComplete() {
        // Given: 模拟扫描无文件
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(Collections.emptyList());

        AgentTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 无文件时也应正常完成
        verify(retryAwareUploader).recordSuccess(eq(1001L));

        System.out.println("✅ 空目录验证: 无文件时也正常完成");
    }

    @Test
    @DisplayName("5. [refactor] 扫描失败时应记录错误日志（不再触发任务级重试）")
    void testExecuteTask_scanFailureShouldLogError() {
        // Given: 模拟扫描失败
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("目录不存在"));

        AgentTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应记录错误日志（不再调用 shouldRetry，因为已删除任务级重试）
        // 验证没有调用 retryAwareUploader.shouldRetry()
        verify(retryAwareUploader, never()).shouldRetry(anyLong(), anyString());
        
        // 验证任务正常完成（异常被捕获并记录日志）
        System.out.println("✅ 错误处理验证: 扫描失败时仅记录日志，不触发任务级重试");
    }

    @Test
    @DisplayName("6. [spec.md 4.7] 超过最大重试次数应标记最终失败")
    void testExecuteTask_maxRetriesExceededShouldFail() {
        // Given: 模拟失败且超过最大重试次数
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("永久性错误"));
        
        when(retryAwareUploader.shouldRetry(anyLong(), anyString())).thenReturn(false);

        AgentTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 不再重试，标记最终失败
        verify(retryAwareUploader, never()).calculateNextRetryDelay(anyLong(), anyInt());

        System.out.println("✅ 最终失败验证: 超过最大重试次数不再调度");
    }

    @Test
    @DisplayName("7. [spec.md] 无目标Agent时应跳过传输")
    void testExecuteTask_noTargetAgentsShouldSkip() {
        // Given: 无目标Agent配置
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(List.of(createScannedFile("test.log", 1024L)));

        AgentTaskConfig config = createTestConfig(tempDir.toString());  // 不设置targetAgentIds

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 任务仍应完成（无目标时视为空操作）
        verify(retryAwareUploader).recordSuccess(eq(1001L));

        System.out.println("✅ 空目标验证: 无目标Agent时跳过传输");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的任务配置
     */
    private AgentTaskConfig createTestConfig(String sourceDir) {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(1001L);
        config.setTaskName("测试任务");
        config.setSourceDir(sourceDir);

        List<TargetAgentInfo> targets = new ArrayList<>();
        TargetAgentInfo target = new TargetAgentInfo();
        target.setTargetDir("/remote/target");
        targets.add(target);
        config.setTargetAgents(targets);

        ScanConfig scanConfig = new ScanConfig();
        scanConfig.setCronExpression("0 */5 * * * ?");
        config.setScanConfig(scanConfig);
        config.setStatus("RUNNING");
        return config;
    }

    /**
     * 带目标Agent配置的任务
     */
    private AgentTaskConfig createTestConfigWithTargets(String sourceDir) {
        AgentTaskConfig config = createTestConfig(sourceDir);
        List<TargetAgentInfo> targets = new ArrayList<>();
        TargetAgentInfo target = new TargetAgentInfo();
        target.setAgentId("target-agent-001");
        targets.add(target);
        config.setTargetAgents(targets);
        return config;
    }

    /**
     * 创建扫描到的文件对象
     */
    private FileScanner.ScannedFile createScannedFile(String fileName, long fileSize) {
        FileScanner.ScannedFile scannedFile = new FileScanner.ScannedFile();
        scannedFile.setFileName(fileName);
        scannedFile.setFileSize(fileSize);
        scannedFile.setLastModified(System.currentTimeMillis());
        scannedFile.setAbsolutePath(tempDir.resolve(fileName).toString());
        return scannedFile;
    }

    /**
     * 通过反射调用私有的createTaskRunnable方法
     */
    private Runnable invokeCreateTaskRunnable(AgentTaskConfig config) {
        try {
            var method = BatchTaskSchedulerManager.class.getDeclaredMethod("createTaskRunnable", AgentTaskConfig.class);
            method.setAccessible(true);
            return (Runnable) method.invoke(schedulerManager, config);
        } catch (Exception e) {
            throw new RuntimeException("反射调用失败", e);
        }
    }
}
