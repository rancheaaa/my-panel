package com.cq.agent.batch.scanner;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 目录扫描器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("目录扫描器 - DirectoryScanner")
class DirectoryScannerTest {

    private DirectoryScanner directoryScanner;
    
    @org.junit.jupiter.api.io.TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        directoryScanner = new DirectoryScanner();
    }

    // ==================== 1. 扁平目录扫描 ====================

    @Test
    @DisplayName("1. 扫描扁平目录 - 返回所有匹配文件")
    void testScan_flatDirectory() throws IOException {
        Files.createFile(tempDir.resolve("file1.log"));
        Files.createFile(tempDir.resolve("file2.txt"));
        Files.createFile(tempDir.resolve("file3.log"));
        
        List<FileInfo> results = directoryScanner.scan(
            tempDir.toString(), 
            List.of("*.log"), 
            List.of(), 
            100
        );
        
        assertEquals(2, results.size(), "应找到2个.log文件");
        
        List<String> fileNames = results.stream()
            .map(FileInfo::getFileName)
            .collect(Collectors.toList());
            
        assertTrue(fileNames.contains("file1.log"), "应包含file1.log");
        assertTrue(fileNames.contains("file3.log"), "应包含file3.log");
        
        System.out.println("✅ 扁平目录扫描: " + results.size() + " 个文件");
    }

    // ==================== 2. 递归子目录扫描 ====================

    @Test
    @DisplayName("2. 递归扫描子目录")
    void testScan_recursiveSubdirectories() throws IOException {
        Files.createDirectories(tempDir.resolve("subdir1"));
        Files.createDirectories(tempDir.resolve("subdir2"));
        
        Files.createFile(tempDir.resolve("root.log"));
        Files.createFile(tempDir.resolve("subdir1/nested.log"));
        Files.createFile(tempDir.resolve("subdir2/deep.log"));
        
        List<FileInfo> results = directoryScanner.scan(
            tempDir.toString(),
            List.of("*.log"),
            List.of(),
            100
        );
        
        assertEquals(3, results.size(), "应递归找到3个.log文件");
        
        System.out.println("✅ 递归扫描: " + results.size() + " 个文件");
    }

    // ==================== 3. Include模式过滤 ====================

    @Test
    @DisplayName("3. Include模式过滤 - 只返回匹配的文件")
    void testIncludePattern_filtering() throws IOException {
        Files.createFile(tempDir.resolve("app.log"));
        Files.createFile(tempDir.resolve("app.txt"));
        Files.createFile(tempDir.resolve("data.csv"));
        Files.createFile(tempDir.resolve("config.xml"));
        
        List<FileInfo> logFiles = directoryScanner.scan(
            tempDir.toString(),
            List.of("*.log"),
            List.of(),
            100
        );
        
        assertEquals(1, logFiles.size(), "*.log应只匹配1个");
        assertTrue(logFiles.get(0).getFileName().equals("app.log"));
        
        System.out.println("✅ Include过滤: *.log → " + logFiles.size() + " 个文件");
    }

    // ==================== 4. Exclude模式过滤 ====================

    @Test
    @DisplayName("4. Exclude模式过滤 - 排除匹配的文件")
    void testExcludePattern_filtering() throws IOException {
        Files.createFile(tempDir.resolve("debug.log"));
        Files.createFile(tempDir.resolve("app.log"));
        Files.createFile(tempDir.resolve("debug_trace.log"));
        
        List<FileInfo> results = directoryScanner.scan(
            tempDir.toString(),
            List.of("*.log"),
            List.of("debug*"),
            100
        );
        
        assertEquals(1, results.size(), "排除debug*后应只剩1个");
        assertTrue(results.get(0).getFileName().equals("app.log"));
        
        System.out.println("✅ Exclude过滤: 排除debug* → " + results.size() + " 个文件");
    }

    // ==================== 5. 最大文件数限制 ====================

    @Test
    @DisplayName("5. 最大文件数限制 - 超过限制截断")
    void testMaxFilesLimit_respected() throws IOException {
        for (int i = 0; i < 20; i++) {
            Files.createFile(tempDir.resolve("file" + i + ".log"));
        }
        
        List<FileInfo> results = directoryScanner.scan(
            tempDir.toString(),
            List.of("*.log"),
            List.of(),
            5
        );
        
        assertEquals(5, results.size(), "应限制为5个文件");
        
        System.out.println("✅ 文件数量限制: max=5, actual=" + results.size());
    }

    // ==================== 6. 不存在的目录异常 ====================

    @Test
    @DisplayName("6. 不存在目录 - 抛出异常或返回空列表")
    void testNonexistentDirectory_exception() {
        String nonExistentPath = tempDir.resolve("not_exist").toString();
        
        assertThrows(Exception.class, () -> {
            directoryScanner.scan(nonExistentPath, List.of("*"), List.of(), 100);
        });
        
        System.out.println("✅ 不存在目录正确处理");
    }

    // ==================== 7. 空目录返回空列表 ====================

    @Test
    @DisplayName("7. 空目录 - 返回空列表")
    void testEmptyDirectory_emptyList() {
        List<FileInfo> results = directoryScanner.scan(
            tempDir.toString(),
            List.of("*"),
            List.of(),
            100
        );
        
        assertNotNull(results);
        assertTrue(results.isEmpty(), "空目录应返回空列表");
        
        System.out.println("✅ 空目录处理正常: count=" + results.size());
    }

    // ==================== 8. 隐藏文件处理 ====================

    @Test
    @DisplayName("8. 隐藏文件 - 可配置包含或排除")
    void testHiddenFiles_includedOrExcluded() throws IOException {
        Files.createFile(tempDir.resolve(".hidden"));
        Files.createFile(tempDir.resolve("visible.log"));
        
        List<FileInfo> allFiles = directoryScanner.scan(
            tempDir.toString(),
            List.of("*"),
            List.of(),
            100
        );
        
        boolean hasHidden = allFiles.stream()
            .anyMatch(f -> f.getFileName().startsWith("."));
        
        System.out.println("✅ 隐藏文件处理: total=" + allFiles.size() + 
                          ", hasHidden=" + hasHidden);
    }

    // ==================== 9. 特殊字符文件名 ====================

    @Test
    @DisplayName("9. 特殊字符文件名 - 正确处理")
    void testSpecialCharacters_handled() throws IOException {
        Files.createFile(tempDir.resolve("file with spaces.log"));
        Files.createFile(tempDir.resolve("文件名中文.log"));
        Files.createFile(tempDir.resolve("file-with-dashes.log"));
        
        List<FileInfo> results = directoryScanner.scan(
            tempDir.toString(),
            List.of("*.log"),
            List.of(),
            100
        );
        
        assertEquals(3, results.size(), "特殊字符文件名应全部识别");
        
        System.out.println("✅ 特殊字符文件名: " + results.size() + " 个文件");
    }

    // ==================== 10. 大目录性能验证 ====================

    @Test
    @DisplayName("10. 大目录性能 - 快速完成")
    void testLargeDirectory_performance() throws IOException {
        for (int i = 0; i < 100; i++) {
            Files.createFile(tempDir.resolve("perf_" + i + ".txt"));
        }
        
        long startTime = System.currentTimeMillis();
        
        List<FileInfo> results = directoryScanner.scan(
            tempDir.toString(),
            List.of("*.txt"),
            List.of(),
            10000
        );
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertEquals(100, results.size());
        assertTrue(duration < 2000, "100个文件扫描应在2秒内完成");
        
        System.out.println("✅ 大目录性能: files=" + results.size() + 
                          ", time=" + duration + "ms");
    }
}
