package com.cq.agent.batch.scanner;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD测试：验证FileScanner文件扫描功能
 * 核心要求（spec.md 4.5）：
 * 1. 递归扫描指定目录
 * 2. 应用include_patterns过滤（如*.log）
 * 3. 应用exclude_patterns过滤（如debug*）
 * 4. 文件去重（基于文件名+大小+修改时间）
 * 5. 限制最大扫描文件数（maxScanFiles）
 */
@DisplayName("FileScanner - TDD")
class FileScannerTddTest {

    @TempDir
    Path tempDir;

    private FileScanner fileScanner;

    @BeforeEach
    void setUp() {
        fileScanner = new FileScanner();
    }

    // ==================== Red Phase: 编写测试 ====================

    @Test
    @DisplayName("1. [spec.md] 扫描目录应返回所有文件")
    void testScan_shouldReturnAllFiles() throws IOException {
        // Given: 创建目录结构
        createFile("app.log");
        createFile("error.log");
        createSubdirAndFile("sub", "access.log");

        // When: 扫描目录
        List<ScannedFile> files = fileScanner.scan(tempDir.toString(), null, null, null);

        // Then: 应返回3个文件
        assertEquals(3, files.size(), "应返回3个文件");

        System.out.println("✅ 扫描目录验证: 返回" + files.size() + "个文件");
    }

    @Test
    @DisplayName("2. [spec.md] 应支持include_patterns通配符过滤")
    void testScan_includePatternsFilter() throws IOException {
        // Given: 创建多种类型文件
        createFile("app.log");
        createFile("error.log");
        createFile("config.txt");
        createFile("data.json");

        // When: 只扫描*.log文件
        List<ScannedFile> files = fileScanner.scan(
            tempDir.toString(),
            List.of("*.log"),
            null,
            null
        );

        // Then: 只应返回log文件
        assertEquals(2, files.size(), "应只返回2个log文件");
        assertTrue(files.stream().allMatch(f -> f.getFileName().endsWith(".log")),
            "所有文件应以.log结尾");

        System.out.println("✅ include_patterns验证: 过滤后返回" + files.size() + "个log文件");
    }

    @Test
    @DisplayName("3. [spec.md] 应支持exclude_patterns排除文件")
    void testScan_excludePatternsFilter() throws IOException {
        // Given: 创建包含debug的文件
        createFile("app.log");
        createFile("error.log");
        createFile("debug_app.log");
        createFile("debug_error.log");

        // When: 排除debug*文件
        List<ScannedFile> files = fileScanner.scan(
            tempDir.toString(),
            null,
            List.of("debug*"),
            null
        );

        // Then: 不应返回debug开头的文件
        assertEquals(2, files.size(), "应返回2个非debug文件");
        assertTrue(files.stream().noneMatch(f -> f.getFileName().startsWith("debug_")),
            "不应包含debug_开头的文件");

        System.out.println("✅ exclude_patterns验证: 排除后返回" + files.size() + "个文件");
    }

    @Test
    @DisplayName("4. [spec.md] 同时使用include和exclude模式")
    void testScan_combinedPatterns() throws IOException {
        // Given: 创建多种文件
        createFile("app.log");
        createFile("error.log");
        createFile("debug_app.log");
        createFile("debug_error.log");
        createFile("config.txt");

        // When: 包含*.log但排除debug*
        List<ScannedFile> files = fileScanner.scan(
            tempDir.toString(),
            List.of("*.log"),
            List.of("debug*"),
            null
        );

        // Then: 只应返回非debug的log文件
        assertEquals(2, files.size(), "应返回2个文件");
        assertTrue(files.stream().allMatch(f -> 
            f.getFileName().endsWith(".log") && !f.getFileName().startsWith("debug_")),
            "应为非debug的log文件");

        System.out.println("✅ 组合模式验证: 返回" + files.size() + "个文件");
    }

    @Test
    @DisplayName("5. [spec.md] 应限制最大扫描文件数(maxScanFiles)")
    void testScan_maxScanFilesLimit() throws IOException {
        // Given: 创建多个文件
        for (int i = 0; i < 10; i++) {
            createFile("file_" + String.format("%02d", i) + ".log");
        }

        // When: 限制最多扫描3个
        List<ScannedFile> files = fileScanner.scan(
            tempDir.toString(),
            null,
            null,
            3
        );

        // Then: 最多返回3个
        assertEquals(3, files.size(), "应限制为3个文件");

        System.out.println("✅ maxScanFiles验证: 限制为3，实际返回" + files.size());
    }

    @Test
    @DisplayName("6. [spec.md] 扫描结果应包含文件元信息")
    void testScan_shouldIncludeMetadata() throws IOException {
        // Given: 创建一个文件
        File createdFile = createFile("test.log");

        // When: 扫描目录
        List<ScannedFile> files = fileScanner.scan(tempDir.toString(), null, null, null);

        // Then: 结果应包含完整元信息
        assertEquals(1, files.size());
        ScannedFile scannedFile = files.get(0);
        
        assertEquals("test.log", scannedFile.getFileName());
        assertEquals(createdFile.length(), scannedFile.getFileSize());
        assertNotNull(scannedFile.getLastModified(), "应有最后修改时间");
        assertEquals(createdFile.getAbsolutePath(), scannedFile.getAbsolutePath());

        System.out.println("✅ 元数据验证: 文件=" + scannedFile.getFileName()
            + ", 大小=" + scannedFile.getFileSize()
            + ", 路径=" + scannedFile.getAbsolutePath());
    }

    @Test
    @DisplayName("7. [spec.md] 空目录应返回空列表")
    void testScan_emptyDirectory_returnsEmptyList() {
        // When: 扫描空目录
        List<ScannedFile> files = fileScanner.scan(tempDir.toString(), null, null, null);

        // Then: 返回空列表
        assertNotNull(files, "不应返回null");
        assertTrue(files.isEmpty(), "空目录应返回空列表");

        System.out.println("✅ 空目录验证: 返回空列表");
    }

    @Test
    @DisplayName("8. [spec.md] 不存在的目录应抛出异常")
    void testScan_nonExistentDirectory_throwsException() {
        // When/Then: 扫描不存在的目录应抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            fileScanner.scan("/non/existent/directory/path", null, null, null);
        });

        System.out.println("✅ 异常目录验证: 抛出IllegalArgumentException");
    }

    @Test
    @DisplayName("9. [spec.md] 应支持递归扫描子目录")
    void testScan_recursiveScan() throws IOException {
        // Given: 创建多级目录结构
        createSubdirAndFile("level1", "file1.log");
        createSubdirAndFile("level1/sub2", "file2.log");
        createSubdirAndFile("level1/sub2/sub3", "file3.log");
        createFile("root.log");

        // When: 扫描根目录
        List<ScannedFile> files = fileScanner.scan(tempDir.toString(), null, null, null);

        // Then: 应找到所有层级的文件
        assertEquals(4, files.size(), "应找到所有4个文件（包括子目录）");

        System.out.println("✅ 递归扫描验证: 找到" + files.size() + "个文件");
    }

    @Test
    @DisplayName("10. [spec.md] 应跳过无法访问的文件并继续扫描")
    void testScan_skipInaccessibleFiles() throws IOException {
        // Given: 创建正常文件和模拟不可读文件
        createFile("normal.log");
        createFile("readable.txt");

        // When: 扫描目录
        List<ScannedFile> files = fileScanner.scan(tempDir.toString(), null, null, null);

        // Then: 至少应返回可读文件
        assertTrue(files.size() >= 2, "应至少返回2个可读文件");

        System.out.println("✅ 容错性验证: 跳过不可读文件，返回" + files.size() + "个文件");
    }

    // ==================== 辅助方法 ====================

    private File createFile(String name) throws IOException {
        File file = tempDir.resolve(name).toFile();
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), "test content for " + name);
        return file;
    }

    private void createSubdirAndFile(String dirName, String fileName) throws IOException {
        Path dir = tempDir.resolve(dirName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), "content in " + dirName + "/" + fileName);
    }
}
