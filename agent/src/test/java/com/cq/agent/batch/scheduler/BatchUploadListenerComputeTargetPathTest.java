package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BatchUploadListener computeTargetPath 单元测试
 * 验证 preserveDirStructure 配置在不同场景下的行为
 */
@DisplayName("BatchUploadListener - computeTargetPath 路径计算")
class BatchUploadListenerComputeTargetPathTest {

    @Test
    @DisplayName("1. Windows源目录 - 保留目录结构 - 子目录嵌套")
    void testWindowsSourceDir_preserveDirStructure_subDirectory() {
        ScannedFile scannedFile = createScannedFile("a9.txt", 77, "E:\\tmp\\aaa\\a9.txt");
        AgentTaskConfig config = createConfig("E:\\tmp", true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/tmp");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/tmp/aaa/a9.txt", targetPath);
    }

    @Test
    @DisplayName("2. Windows源目录 - 保留目录结构 - 深层子目录")
    void testWindowsSourceDir_preserveDirStructure_deepSubDirectory() {
        ScannedFile scannedFile = createScannedFile("error.log", 1024, "E:\\var\\log\\app\\2026\\05\\15\\error.log");
        AgentTaskConfig config = createConfig("E:\\var\\log\\app", true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/data/backup");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/data/backup/2026/05/15/error.log", targetPath);
    }

    @Test
    @DisplayName("3. Windows源目录 - 保留目录结构 - 根目录文件")
    void testWindowsSourceDir_preserveDirStructure_rootFile() {
        ScannedFile scannedFile = createScannedFile("test.txt", 100, "E:\\tmp\\test.txt");
        AgentTaskConfig config = createConfig("E:\\tmp", true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/backup");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/backup/test.txt", targetPath);
    }

    @Test
    @DisplayName("4. Windows源目录 - 扁平化模式")
    void testWindowsSourceDir_flattenMode() {
        ScannedFile scannedFile = createScannedFile("a9.txt", 77, "E:\\tmp\\aaa\\a9.txt");
        AgentTaskConfig config = createConfig("E:\\tmp", false);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/tmp");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/tmp/a9.txt", targetPath);
    }

    @Test
    @DisplayName("5. Linux源目录 - 保留目录结构")
    void testLinuxSourceDir_preserveDirStructure() {
        ScannedFile scannedFile = createScannedFile("app.log", 2048, "/var/log/myapp/subdir/app.log");
        AgentTaskConfig config = createConfig("/var/log/myapp", true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/data/backup");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/data/backup/subdir/app.log", targetPath);
    }

    @Test
    @DisplayName("6. sourceDir不匹配时退化为文件名")
    void testSourceDirMismatch_fallbackToFileName() {
        ScannedFile scannedFile = createScannedFile("a9.txt", 77, "D:\\other\\aaa\\a9.txt");
        AgentTaskConfig config = createConfig("E:\\tmp", true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/tmp");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/tmp/a9.txt", targetPath);
    }

    @Test
    @DisplayName("7. sourceDir为空时使用文件名")
    void testEmptySourceDir_useFileName() {
        ScannedFile scannedFile = createScannedFile("a9.txt", 77, "E:\\tmp\\aaa\\a9.txt");
        AgentTaskConfig config = createConfig(null, true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/tmp");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/tmp/a9.txt", targetPath);
    }

    @Test
    @DisplayName("8. targetAgent为null时返回null")
    void testNullTargetAgent_returnsNull() {
        ScannedFile scannedFile = createScannedFile("a9.txt", 77, "E:\\tmp\\aaa\\a9.txt");
        AgentTaskConfig config = createConfig("E:\\tmp", true);

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, null);
        String targetPath = listener.computeTargetPathForTest();

        assertNull(targetPath);
    }

    @Test
    @DisplayName("9. targetDir为null时返回null")
    void testNullTargetDir_returnsNull() {
        ScannedFile scannedFile = createScannedFile("a9.txt", 77, "E:\\tmp\\aaa\\a9.txt");
        AgentTaskConfig config = createConfig("E:\\tmp", true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", null);

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertNull(targetPath);
    }

    @Test
    @DisplayName("10. 目标目录末尾已有分隔符不重复添加")
    void testTargetDirWithTrailingSlash() {
        ScannedFile scannedFile = createScannedFile("a9.txt", 77, "E:\\tmp\\aaa\\a9.txt");
        AgentTaskConfig config = createConfig("E:\\tmp", true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/tmp/");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/tmp/aaa/a9.txt", targetPath);
    }

    @Test
    @DisplayName("11. Windows源目录带尾部反斜杠")
    void testWindowsSourceDirWithTrailingBackslash() {
        ScannedFile scannedFile = createScannedFile("a9.txt", 77, "E:\\tmp\\aaa\\a9.txt");
        AgentTaskConfig config = createConfig("E:\\tmp\\", true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/tmp");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/tmp/aaa/a9.txt", targetPath);
    }

    @Test
    @DisplayName("12. 混合路径分隔符兼容")
    void testMixedPathSeparators() {
        ScannedFile scannedFile = createScannedFile("test.log", 512, "E:/tmp/sub/test.log");
        AgentTaskConfig config = createConfig("E:\\tmp", true);
        TargetAgentInfo targetAgent = createTargetAgent("agent-002", "/backup");

        BatchUploadListener listener = createListenerForTesting(config, scannedFile, targetAgent);
        String targetPath = listener.computeTargetPathForTest();

        assertEquals("/backup/sub/test.log", targetPath);
    }

    // ==================== 辅助方法 ====================

    private ScannedFile createScannedFile(String fileName, long fileSize, String absolutePath) {
        ScannedFile file = new ScannedFile();
        file.setFileName(fileName);
        file.setFileSize(fileSize);
        file.setAbsolutePath(absolutePath);
        file.setLastModified(System.currentTimeMillis());
        return file;
    }

    private AgentTaskConfig createConfig(String sourceDir, boolean preserveDirStructure) {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(1L);
        config.setTaskName("test-task");
        config.setSourceDir(sourceDir);

        TransferConfig transferConfig = new TransferConfig();
        transferConfig.setPreserveDirStructure(preserveDirStructure);
        transferConfig.setTransferMode("ONE_TO_ONE");
        config.setTransferConfig(transferConfig);

        return config;
    }

    private TargetAgentInfo createTargetAgent(String agentId, String targetDir) {
        TargetAgentInfo info = new TargetAgentInfo();
        info.setAgentId(agentId);
        info.setAgentName("test-agent");
        info.setTargetDir(targetDir);
        return info;
    }

    private BatchUploadListener createListenerForTesting(AgentTaskConfig config,
            ScannedFile scannedFile, TargetAgentInfo targetAgent) {
        return new BatchUploadListener(1L, scannedFile, config, targetAgent);
    }
}
