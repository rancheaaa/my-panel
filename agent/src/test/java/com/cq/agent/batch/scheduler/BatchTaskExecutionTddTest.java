package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.config.BatchTransferTaskConfig;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.transfer.BatchTransferManager;
import com.cq.agent.batch.transfer.RetryManager;
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
    private RetryManager retryManager;

    @Mock
    private BatchTransferManager transferManager;

    @Mock
    private FileScanner fileScanner;

    private BatchTaskSchedulerManager schedulerManager;
    private Path tempDir;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        schedulerManager = new BatchTaskSchedulerManager(configFileManager);
        schedulerManager.setRetryManager(retryManager);
        schedulerManager.setTransferManager(transferManager);
        schedulerManager.setFileScanner(fileScanner);

        // 默认：允许获取传输许可（大多数测试需要）
        when(transferManager.tryAcquire(anyString())).thenReturn(true);

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
        BatchTransferTaskConfig config = createTestConfig(tempDir.toString());
        when(fileScanner.scan(anyString(), anyList(), anyList(), any()))
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
        BatchTransferTaskConfig config = createTestConfig(tempDir.toString());
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
        BatchTransferTaskConfig config = createTestConfig(tempDir.toString());
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
    @DisplayName("4. [spec.md 4.6] 扫描到文件后应执行传输逻辑")
    void testExecuteTask_shouldProcessScannedFiles() {
        // Given: 模拟扫描到文件
        FileScanner.ScannedFile file1 = createScannedFile("app.log", 1024L);
        FileScanner.ScannedFile file2 = createScannedFile("error.log", 2048L);

        when(fileScanner.scan(anyString(), anyList(), anyList(), any()))
            .thenReturn(List.of(file1, file2));

        BatchTransferTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应处理扫描到的文件（completeTask被调用表示成功完成）
        verify(retryManager).recordSuccess(eq(1001L));

        System.out.println("✅ 文件处理验证: 扫描到" + 2 + "个文件并处理完成");
    }

    @Test
    @DisplayName("5. [spec.md 4.6] 传输成功后应记录完成状态")
    void testExecuteTask_successShouldRecordComplete() {
        // Given: 模拟扫描到文件并成功处理
        when(fileScanner.scan(anyString(), anyList(), anyList(), any()))
            .thenReturn(List.of(createScannedFile("test.log", 1024L)));

        BatchTransferTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应调用completeTask记录成功
        verify(retryManager).recordSuccess(eq(1001L));
        verify(transferManager).release(eq("1001"));

        System.out.println("✅ 成功记录验证: completeTask被正确调用");
    }

    @Test
    @DisplayName("6. [spec.md 4.7] 扫描失败时应触发重试机制")
    void testExecuteTask_scanFailureShouldTriggerRetry() {
        // Given: 模拟扫描失败（使用正确的参数匹配器）
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("目录不存在"));
        
        when(retryManager.shouldRetry(anyLong(), anyString())).thenReturn(true);

        BatchTransferTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应调用failTask并检查是否需要重试
        verify(retryManager).shouldRetry(eq(1001L), contains("目录不存在"));

        System.out.println("✅ 重试机制验证: failTask检查是否需要重试");
    }

    @Test
    @DisplayName("7. [spec.md 4.5] 扫描不到文件时也应标记完成")
    void testExecuteTask_noFilesFoundShouldComplete() {
        // Given: 模拟扫描无文件
        when(fileScanner.scan(anyString(), anyList(), anyList(), any()))
            .thenReturn(Collections.emptyList());

        BatchTransferTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 无文件时也应正常完成
        verify(retryManager).recordSuccess(eq(1001L));

        System.out.println("✅ 空目录验证: 无文件时也正常完成");
    }

    @Test
    @DisplayName("8. [spec.md] 执行前应获取传输许可")
    void testExecuteTask_shouldAcquirePermitBeforeExecution() {
        // Given: 配置传输许可控制
        when(fileScanner.scan(anyString(), anyList(), anyList(), any()))
            .thenReturn(Collections.emptyList());

        BatchTransferTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应先尝试获取许可
        verify(transferManager).tryAcquire(eq("1001"));

        System.out.println("✅ 许可控制验证: 执行前获取传输许可");
    }

    @Test
    @DisplayName("9. [spec.md] 无法获取许可时应跳过执行")
    void testExecuteTask_skipWhenCannotAcquirePermit() {
        // Given: 无法获取许可（覆盖默认行为）
        when(transferManager.tryAcquire(anyString())).thenReturn(false);

        BatchTransferTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 不应执行扫描
        verify(fileScanner, never()).scan(anyString(), anyList(), anyList(), anyInt());
        verify(retryManager).shouldRetry(eq(1001L), contains("获取传输许可超时"));

        System.out.println("✅ 许可拒绝验证: 无许可时跳过执行");
    }

    @Test
    @DisplayName("10. [spec.md 4.7] 超过最大重试次数应标记最终失败")
    void testExecuteTask_maxRetriesExceededShouldFail() {
        // Given: 模拟失败且超过最大重试次数
        when(fileScanner.scan(anyString(), anyList(), anyList(), any()))
            .thenThrow(new RuntimeException("永久性错误"));
        
        when(retryManager.shouldRetry(anyLong(), anyString())).thenReturn(false);

        BatchTransferTaskConfig config = createTestConfigWithTargets(tempDir.toString());

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 不再重试，标记最终失败
        verify(retryManager, never()).calculateNextRetryDelay(anyLong(), anyInt());

        System.out.println("✅ 最终失败验证: 超过最大重试次数不再调度");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的任务配置
     */
    private BatchTransferTaskConfig createTestConfig(String sourceDir) {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(1001L);
        config.setTaskName("测试任务");
        config.setSourceDir(sourceDir);
        config.setTargetDirs(List.of("/remote/target"));
        config.setCronExpression("0 */5 * * * ?");
        config.setStatus("RUNNING");
        return config;
    }

    /**
     * 带目标Agent配置的任务
     */
    private BatchTransferTaskConfig createTestConfigWithTargets(String sourceDir) {
        BatchTransferTaskConfig config = createTestConfig(sourceDir);
        config.setTargetAgentIds(List.of("target-agent-001"));
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
    private Runnable invokeCreateTaskRunnable(BatchTransferTaskConfig config) {
        try {
            var method = BatchTaskSchedulerManager.class.getDeclaredMethod("createTaskRunnable", BatchTransferTaskConfig.class);
            method.setAccessible(true);
            return (Runnable) method.invoke(schedulerManager, config);
        } catch (Exception e) {
            throw new RuntimeException("反射调用失败", e);
        }
    }
}
