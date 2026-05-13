package com.cq.agent.batch.scheduler;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.UploadListener;
import com.cq.agent.client.upload.UploadTask;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD测试：验证P2P文件传输功能（spec.md 4.6）
 * 核心要求：
 * 1. 使用AgentUploader进行真实的P2P传输
 * 2. 通过UploadListener回调捕获进度
 * 3. 正确构建remoteTargetInfo格式
 * 4. 传输失败时触发重试机制
 */
@DisplayName("P2P文件传输 - TDD")
class P2PFileTransferTddTest {

    @Mock
    private ConfigFileManager configFileManager;

    @Mock
    private RetryAwareUploaderDecorator retryAwareUploader;

    @Mock
    private FileScanner fileScanner;

    @Mock
    private AgentUploader agentUploader;

    private BatchTaskSchedulerManager schedulerManager;
    private Path tempDir;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        schedulerManager = new BatchTaskSchedulerManager(configFileManager);
        schedulerManager.setRetryAwareUploader(retryAwareUploader);
        schedulerManager.setFileScanner(fileScanner);

        // 注入AgentUploader
        schedulerManager.setAgentUploader(agentUploader);

        // 创建临时目录用于测试
        tempDir = Files.createTempDirectory("p2p-test");
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

    // ==================== Red Phase: P2P传输核心逻辑测试 ====================

    @Test
    @DisplayName("1. [spec.md 4.6] 应调用AgentUploader.uploadFile进行P2P传输")
    void testProcessScannedFiles_shouldCallAgentUploader() {
        // Given: 模拟扫描到文件
        FileScanner.ScannedFile scannedFile = createScannedFile("test.log", 1024L);
        
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(List.of(scannedFile));
        
        when(agentUploader.uploadFile(anyString(), anyString(), any(UploadListener.class)))
            .thenReturn(true);

        AgentTaskConfig config = createTestConfigWithTargets();

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应调用AgentUploader进行上传
        verify(agentUploader).uploadFile(
            contains("test.log"),  // localPath包含文件名
            contains("192.168.1.100"),  // remoteTargetInfo包含目标IP
            any(UploadListener.class)   // 传入监听器
        );

        System.out.println("✅ P2P传输验证: AgentUploader.uploadFile被调用");
    }

    @Test
    @DisplayName("2. [spec.md 4.6] 应构建正确的remoteTargetInfo格式")
    void testProcessScannedFiles_shouldBuildCorrectRemoteTargetInfo() {
        // Given: 配置目标Agent信息
        FileScanner.ScannedFile scannedFile = createScannedFile("app.log", 2048L);
        
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(List.of(scannedFile));
        
        when(agentUploader.uploadFile(anyString(), anyString(), any(UploadListener.class)))
            .thenReturn(true);

        AgentTaskConfig config = createTestConfigWithTargets();

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: remoteTargetInfo应符合格式 "ip:port@username:destPath"
        ArgumentCaptor<String> remoteTargetInfoCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentUploader).uploadFile(
            anyString(),
            remoteTargetInfoCaptor.capture(),
            any(UploadListener.class)
        );

        String remoteTargetInfo = remoteTargetInfoCaptor.getValue();
        assertTrue(remoteTargetInfo.contains("192.168.1.100"), "应包含目标IP");
        assertTrue(remoteTargetInfo.contains("7777"), "应包含目标端口");
        assertTrue(remoteTargetInfo.contains("root"), "应包含用户名");
        assertTrue(remoteTargetInfo.contains("/remote/target"), "应包含目标路径");
        assertTrue(remoteTargetInfo.contains("app.log"), "应包含文件名");

        System.out.println("✅ remoteTargetInfo格式验证: " + remoteTargetInfo);
    }

    @Test
    @DisplayName("3. [spec.md 4.6] 应传递正确的localFilePath给AgentUploader")
    void testProcessScannedFiles_shouldPassCorrectLocalPath() {
        // Given: 扫描到具体文件
        FileScanner.ScannedFile scannedFile = createScannedFile("data.json", 4096L);
        
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(List.of(scannedFile));
        
        when(agentUploader.uploadFile(anyString(), anyString(), any(UploadListener.class)))
            .thenReturn(true);

        AgentTaskConfig config = createTestConfigWithTargets();

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: localFilePath应为绝对路径
        ArgumentCaptor<String> localPathCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentUploader).uploadFile(
            localPathCaptor.capture(),
            anyString(),
            any(UploadListener.class)
        );

        String localPath = localPathCaptor.getValue();
        assertTrue(Paths.get(localPath).isAbsolute(), "应为绝对路径");
        assertTrue(localPath.endsWith("data.json"), "应以文件名结尾");

        System.out.println("✅ localFilePath验证: " + localPath);
    }

    @Test
    @DisplayName("4. [spec.md 4.6] 应传递UploadListener以捕获进度事件")
    void testProcessScannedFiles_shouldPassUploadListener() {
        // Given: 准备文件和配置
        FileScanner.ScannedFile scannedFile = createScannedFile("log.txt", 512L);
        
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(List.of(scannedFile));
        
        when(agentUploader.uploadFile(anyString(), anyString(), any(UploadListener.class)))
            .thenReturn(true);

        AgentTaskConfig config = createTestConfigWithTargets();

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应传递非null的UploadListener
        ArgumentCaptor<UploadListener> listenerCaptor = ArgumentCaptor.forClass(UploadListener.class);
        verify(agentUploader).uploadFile(
            anyString(),
            anyString(),
            listenerCaptor.capture()
        );

        UploadListener listener = listenerCaptor.getValue();
        assertNotNull(listener, "UploadListener不应为null");

        // 验证listener可以正常回调
        assertDoesNotThrow(() -> {
            listener.onProgress(10, 5, 50.0);
            listener.onComplete(mock(UploadTask.class));
        });

        System.out.println("✅ UploadListener验证: 监听器已正确传递并可回调");
    }

    @Test
    @DisplayName("5. [spec.md 4.6] 上传成功时应标记任务完成")
    void testProcessScannedFiles_uploadSuccessShouldComplete() {
        // Given: 模拟上传成功
        FileScanner.ScannedFile scannedFile = createScannedFile("success.log", 1024L);
        
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(List.of(scannedFile));
        
        when(agentUploader.uploadFile(anyString(), anyString(), any(UploadListener.class)))
            .thenReturn(true);

        AgentTaskConfig config = createTestConfigWithTargets();

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应调用completeTask记录成功
        verify(retryAwareUploader).recordSuccess(eq(1001L));

        System.out.println("✅ 成功完成验证: uploadFile返回true后任务完成");
    }

    @Test
    @DisplayName("6. [spec.md 4.7] 上传失败时应进入失败队列而非整体重试")
    void testProcessScannedFiles_uploadFailureShouldTriggerRetry() {
        // Given: 模拟上传失败
        FileScanner.ScannedFile scannedFile = createScannedFile("fail.log", 512L);

        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(List.of(scannedFile));

        when(agentUploader.uploadFile(anyString(), anyString(), any(UploadListener.class)))
            .thenReturn(false);  // 上传失败

        // 新行为：不再调用 shouldRetry()，而是让失败的文件进入失败队列
        // RetryManager 会定时扫描并重试

        AgentTaskConfig config = createTestConfigWithTargets();

        // When: 执行任务（不应该抛出异常）
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        
        // Then: 任务应该正常完成（不抛出异常），失败的文件已进入失败队列
        assertDoesNotThrow(() -> taskRunnable.run(),
            "部分文件失败不应导致整个任务失败");

        // 验证：不再调用旧的 shouldRetry 方法（因为改为异步重试机制）
        verify(retryAwareUploader, never()).shouldRetry(anyLong(), anyString());

        // 验证：任务完成时仍会记录成功（对于成功的文件）
        verify(retryAwareUploader).recordSuccess(1001L);

        System.out.println("✅ 失败重试验证: uploadFile返回false后不抛异常，失败文件进入队列等待定时重试");
    }

    @Test
    @DisplayName("7. [spec.md] 多个文件应逐个上传")
    void testProcessScannedFiles_shouldUploadAllFiles() {
        // Given: 模拟扫描到多个文件
        List<FileScanner.ScannedFile> files = List.of(
            createScannedFile("file1.log", 1024L),
            createScannedFile("file2.log", 2048L),
            createScannedFile("file3.txt", 3072L)
        );
        
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(files);
        
        when(agentUploader.uploadFile(anyString(), anyString(), any(UploadListener.class)))
            .thenReturn(true);

        AgentTaskConfig config = createTestConfigWithTargets();

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 应对每个文件调用一次uploadFile
        verify(agentUploader, times(3)).uploadFile(
            anyString(),
            anyString(),
            any(UploadListener.class)
        );

        System.out.println("✅ 批量上传验证: 3个文件全部上传");
    }

    @Test
    @DisplayName("8. [spec.md] 单个文件失败不应影响其他文件上传")
    void testProcessScannedFiles_singleFailureShouldNotBlockOthers() {
        // Given: 第2个文件上传失败
        List<FileScanner.ScannedFile> files = List.of(
            createScannedFile("success1.log", 1024L),
            createScannedFile("failed.log", 2048L),
            createScannedFile("success2.log", 3072L)
        );
        
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(files);
        
        when(agentUploader.uploadFile(anyString(), anyString(), any(UploadListener.class)))
            .thenAnswer(invocation -> {
                String path = invocation.getArgument(0, String.class);
                // 第2个文件返回false（失败）
                return !path.contains("failed.log");
            });

        AgentTaskConfig config = createTestConfigWithTargets();

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 所有3个文件都应该尝试上传
        verify(agentUploader, times(3)).uploadFile(
            anyString(),
            anyString(),
            any(UploadListener.class)
        );

        System.out.println("✅ 容错验证: 单个失败不阻塞其他文件");
    }

    @Test
    @DisplayName("10. [spec.md] 无目标Agent时应跳过传输")
    void testProcessScannedFiles_noTargetAgentsShouldSkip() {
        // Given: 无目标Agent配置
        FileScanner.ScannedFile scannedFile = createScannedFile("test.log", 1024L);
        
        when(fileScanner.scan(anyString(), any(), any(), any()))
            .thenReturn(List.of(scannedFile));

        AgentTaskConfig config = createTestConfig();  // 不设置targetAgentIds

        // When: 执行任务
        Runnable taskRunnable = invokeCreateTaskRunnable(config);
        taskRunnable.run();

        // Then: 不应调用AgentUploader
        verify(agentUploader, never()).uploadFile(
            anyString(),
            anyString(),
            any(UploadListener.class)
        );

        // 但任务仍应完成（无目标时视为空操作）
        verify(retryAwareUploader).recordSuccess(eq(1001L));

        System.out.println("✅ 空目标验证: 无目标Agent时跳过传输");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的任务配置
     */
    private AgentTaskConfig createTestConfig() {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(1001L);
        config.setTaskName("P2P测试任务");
        config.setSourceDir(tempDir.toString());

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
    private AgentTaskConfig createTestConfigWithTargets() {
        AgentTaskConfig config = createTestConfig();
        List<TargetAgentInfo> targets = new ArrayList<>();
        TargetAgentInfo target = new TargetAgentInfo();
        target.setAgentId("target-agent-001");
        target.setAgentName("root@192.168.1.100:7777");
        target.setTargetDir("/remote/target");
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
