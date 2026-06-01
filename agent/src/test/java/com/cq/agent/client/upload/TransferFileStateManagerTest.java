package com.cq.agent.client.upload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransferFileStateManager - 传输文件状态管理器")
class TransferFileStateManagerTest {

    @TempDir
    Path tempDir;

    private Path testFile;
    private Path expectedHiddenFile;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("report.csv");
        Files.writeString(testFile, "test data content");
        expectedHiddenFile = tempDir.resolve(".report.csv.transferring");
    }

    @AfterEach
    void tearDown() {
        // 清理可能残留的文件
        try {
            Files.deleteIfExists(testFile);
            Files.deleteIfExists(expectedHiddenFile);
        } catch (IOException ignored) {
        }
    }

    // ==================== hideFile 测试 ====================

    @Test
    @DisplayName("hideFile - 应该将原始文件重命名为隐藏文件")
    void hideFileShouldRenameToHiddenFile() throws IOException {
        Path result = TransferFileStateManager.hideFile(testFile);

        assertEquals(expectedHiddenFile, result, "返回的隐藏路径应该正确");
        assertFalse(Files.exists(testFile), "原始文件不应该存在");
        assertTrue(Files.exists(expectedHiddenFile), "隐藏文件应该存在");
        assertEquals("test data content", Files.readString(expectedHiddenFile), "文件内容应该保持不变");
    }

    @Test
    @DisplayName("hideFile - 文件不存在时应该抛出IOException")
    void hideFileShouldThrowWhenFileNotExists() {
        Path nonExistent = tempDir.resolve("nonexistent.txt");

        IOException exception = assertThrows(IOException.class,
                () -> TransferFileStateManager.hideFile(nonExistent));
        assertTrue(exception.getMessage().contains("文件不存在"));
    }

    @Test
    @DisplayName("hideFile - 参数为null时应该抛出IllegalArgumentException")
    void hideFileShouldThrowWhenNull() {
        assertThrows(IllegalArgumentException.class,
                () -> TransferFileStateManager.hideFile(null));
    }

    @Test
    @DisplayName("hideFile - 隐藏文件已存在时应该抛出IOException")
    void hideFileShouldThrowWhenHiddenFileExists() throws IOException {
        // 先创建隐藏文件
        Files.writeString(expectedHiddenFile, "existing hidden");

        IOException exception = assertThrows(IOException.class,
                () -> TransferFileStateManager.hideFile(testFile));
        assertTrue(exception.getMessage().contains("隐藏文件已存在"));
    }

    // ==================== unhideFile 测试 ====================

    @Test
    @DisplayName("unhideFile - 应该将隐藏文件恢复为原始文件")
    void unhideFileShouldRestoreOriginalFile() throws IOException {
        // 先隐藏
        TransferFileStateManager.hideFile(testFile);

        Path result = TransferFileStateManager.unhideFile(expectedHiddenFile);

        assertEquals(testFile, result, "恢复的路径应该是原始路径");
        assertTrue(Files.exists(testFile), "原始文件应该恢复");
        assertFalse(Files.exists(expectedHiddenFile), "隐藏文件不应该存在");
        assertEquals("test data content", Files.readString(testFile), "文件内容应该保持不变");
    }

    @Test
    @DisplayName("unhideFile - 隐藏文件不存在时应该抛出IOException")
    void unhideFileShouldThrowWhenFileNotExists() {
        Path nonExistent = tempDir.resolve(".nonexistent.txt.transferring");

        IOException exception = assertThrows(IOException.class,
                () -> TransferFileStateManager.unhideFile(nonExistent));
        assertTrue(exception.getMessage().contains("传输中文件不存在"));
    }

    @Test
    @DisplayName("unhideFile - 参数为null时应该抛出IllegalArgumentException")
    void unhideFileShouldThrowWhenNull() {
        assertThrows(IllegalArgumentException.class,
                () -> TransferFileStateManager.unhideFile(null));
    }

    // ==================== isTransferring 测试 ====================

    @Test
    @DisplayName("isTransferring - 原始文件未隐藏时应该返回false")
    void isTransferringShouldReturnFalseWhenNotHidden() {
        assertFalse(TransferFileStateManager.isTransferring(testFile));
    }

    @Test
    @DisplayName("isTransferring - 原始文件已隐藏时应该返回true")
    void isTransferringShouldReturnTrueWhenHidden() throws IOException {
        TransferFileStateManager.hideFile(testFile);

        assertTrue(TransferFileStateManager.isTransferring(testFile));
    }

    @Test
    @DisplayName("isTransferring - 参数为null时应该返回false")
    void isTransferringShouldReturnFalseWhenNull() {
        assertFalse(TransferFileStateManager.isTransferring(null));
    }

    @Test
    @DisplayName("isTransferring - 原始文件不存在且无隐藏文件时应该返回false")
    void isTransferringShouldReturnFalseWhenFileNotExists() {
        Path nonExistent = tempDir.resolve("nonexistent.txt");
        assertFalse(TransferFileStateManager.isTransferring(nonExistent));
    }

    // ==================== isTransferringFile 测试 ====================

    @Test
    @DisplayName("isTransferringFile - 传输中文件名应该返回true")
    void isTransferringFileShouldReturnTrueForTransferringName() {
        Path transferringPath = tempDir.resolve(".report.csv.transferring");
        assertTrue(TransferFileStateManager.isTransferringFile(transferringPath));
    }

    @Test
    @DisplayName("isTransferringFile - 普通文件名应该返回false")
    void isTransferringFileShouldReturnFalseForNormalName() {
        assertFalse(TransferFileStateManager.isTransferringFile(testFile));
    }

    @Test
    @DisplayName("isTransferringFile - 参数为null时应该返回false")
    void isTransferringFileShouldReturnFalseWhenNull() {
        assertFalse(TransferFileStateManager.isTransferringFile(null));
    }

    @Test
    @DisplayName("isTransferringFile - 只有后缀没有前缀点的文件应该返回false")
    void isTransferringFileShouldReturnFalseForOnlySuffix() {
        Path wrongName = tempDir.resolve("report.csv.transferring");
        assertFalse(TransferFileStateManager.isTransferringFile(wrongName));
    }

    @Test
    @DisplayName("isTransferringFile - 只有前缀点没有后缀的文件应该返回false")
    void isTransferringFileShouldReturnFalseForOnlyPrefix() {
        Path wrongName = tempDir.resolve(".report.csv");
        assertFalse(TransferFileStateManager.isTransferringFile(wrongName));
    }

    // ==================== getTransferringPath 测试 ====================

    @Test
    @DisplayName("getTransferringPath - 应该正确计算隐藏文件路径")
    void getTransferringPathShouldComputeCorrectly() {
        Path original = tempDir.resolve("data.txt");
        Path expected = tempDir.resolve(".data.txt.transferring");

        assertEquals(expected, TransferFileStateManager.getTransferringPath(original));
    }

    @Test
    @DisplayName("getTransferringPath - 带子目录的路径应该正确计算")
    void getTransferringPathShouldHandleSubdirectory() {
        Path original = tempDir.resolve("sub").resolve("dir").resolve("file.log");
        Path expected = tempDir.resolve("sub").resolve("dir").resolve(".file.log.transferring");

        assertEquals(expected, TransferFileStateManager.getTransferringPath(original));
    }

    @Test
    @DisplayName("getTransferringPath - 参数为null时应该返回null")
    void getTransferringPathShouldReturnNullWhenNull() {
        assertNull(TransferFileStateManager.getTransferringPath(null));
    }

    // ==================== getOriginalPath 测试 ====================

    @Test
    @DisplayName("getOriginalPath - 应该从隐藏文件路径推算原始路径")
    void getOriginalPathShouldComputeCorrectly() {
        Path hidden = tempDir.resolve(".data.txt.transferring");
        Path expected = tempDir.resolve("data.txt");

        assertEquals(expected, TransferFileStateManager.getOriginalPath(hidden));
    }

    @Test
    @DisplayName("getOriginalPath - 带子目录的路径应该正确推算")
    void getOriginalPathShouldHandleSubdirectory() {
        Path hidden = tempDir.resolve("sub").resolve("dir").resolve(".file.log.transferring");
        Path expected = tempDir.resolve("sub").resolve("dir").resolve("file.log");

        assertEquals(expected, TransferFileStateManager.getOriginalPath(hidden));
    }

    @Test
    @DisplayName("getOriginalPath - 非隐藏文件格式应该返回原路径")
    void getOriginalPathShouldReturnOriginalForNonTransferringFormat() {
        Path normal = tempDir.resolve("normal.txt");
        assertEquals(normal, TransferFileStateManager.getOriginalPath(normal));
    }

    @Test
    @DisplayName("getOriginalPath - 参数为null时应该返回null")
    void getOriginalPathShouldReturnNullWhenNull() {
        assertNull(TransferFileStateManager.getOriginalPath(null));
    }

    // ==================== hideFileSafely 测试 ====================

    @Test
    @DisplayName("hideFileSafely - 正常情况应该隐藏文件")
    void hideFileSafelyShouldHideFile() throws IOException {
        Path result = TransferFileStateManager.hideFileSafely(testFile);

        assertEquals(expectedHiddenFile, result);
        assertFalse(Files.exists(testFile));
        assertTrue(Files.exists(expectedHiddenFile));
    }

    @Test
    @DisplayName("hideFileSafely - 文件已在传输中时应该返回已有隐藏路径而不抛异常")
    void hideFileSafelyShouldReturnExistingPathWhenAlreadyTransferring() throws IOException {
        // 先隐藏文件
        TransferFileStateManager.hideFile(testFile);

        // 再次调用hideFileSafely不应该抛异常
        Path result = TransferFileStateManager.hideFileSafely(testFile);

        assertEquals(expectedHiddenFile, result);
        assertTrue(Files.exists(expectedHiddenFile));
    }

    @Test
    @DisplayName("hideFileSafely - 文件不存在时应该抛出IOException")
    void hideFileSafelyShouldThrowWhenFileNotExists() {
        Path nonExistent = tempDir.resolve("nonexistent.txt");
        assertThrows(IOException.class, () -> TransferFileStateManager.hideFileSafely(nonExistent));
    }

    // ==================== unhideFileSafely 测试 ====================

    @Test
    @DisplayName("unhideFileSafely - 正常情况应该恢复文件")
    void unhideFileSafelyShouldUnhideFile() throws IOException {
        TransferFileStateManager.hideFile(testFile);

        Path result = TransferFileStateManager.unhideFileSafely(expectedHiddenFile);

        assertEquals(testFile, result);
        assertTrue(Files.exists(testFile));
        assertFalse(Files.exists(expectedHiddenFile));
    }

    @Test
    @DisplayName("unhideFileSafely - 文件不存在时应该返回null而不抛异常")
    void unhideFileSafelyShouldReturnNullWhenFileNotExists() {
        Path nonExistent = tempDir.resolve(".nonexistent.txt.transferring");
        assertNull(TransferFileStateManager.unhideFileSafely(nonExistent));
    }

    @Test
    @DisplayName("unhideFileSafely - 参数为null时应该返回null")
    void unhideFileSafelyShouldReturnNullWhenNull() {
        assertNull(TransferFileStateManager.unhideFileSafely(null));
    }

    @Test
    @DisplayName("unhideFileSafely - 非传输中文件格式应该返回null")
    void unhideFileSafelyShouldReturnNullForNonTransferringFormat() throws IOException {
        Path normalFile = tempDir.resolve("normal.txt");
        Files.writeString(normalFile, "test");

        assertNull(TransferFileStateManager.unhideFileSafely(normalFile));
    }

    // ==================== 端到端场景测试 ====================

    @Test
    @DisplayName("端到端 - 隐藏→判断→恢复 完整流程")
    void endToEndHideCheckRestoreFlow() throws IOException {
        // 1. 初始状态：文件可见，不在传输中
        assertFalse(TransferFileStateManager.isTransferring(testFile));
        assertTrue(Files.exists(testFile));

        // 2. 隐藏文件
        Path hiddenPath = TransferFileStateManager.hideFile(testFile);
        assertTrue(TransferFileStateManager.isTransferring(testFile));
        assertFalse(Files.exists(testFile));
        assertTrue(Files.exists(hiddenPath));

        // 3. 恢复文件
        Path restoredPath = TransferFileStateManager.unhideFile(hiddenPath);
        assertFalse(TransferFileStateManager.isTransferring(testFile));
        assertTrue(Files.exists(testFile));
        assertFalse(Files.exists(hiddenPath));
        assertEquals("test data content", Files.readString(testFile));
    }

    @Test
    @DisplayName("端到端 - 隐藏→删除（模拟DELETE操作）")
    void endToEndHideThenDelete() throws IOException {
        Path hiddenPath = TransferFileStateManager.hideFile(testFile);
        assertTrue(Files.exists(hiddenPath));

        // 模拟DELETE操作：直接删除隐藏文件
        Files.delete(hiddenPath);
        assertFalse(Files.exists(hiddenPath));
        assertFalse(Files.exists(testFile));
    }

    @Test
    @DisplayName("端到端 - 隐藏→备份（模拟BACKUP操作）")
    void endToEndHideThenBackup() throws IOException {
        Path hiddenPath = TransferFileStateManager.hideFile(testFile);
        assertTrue(Files.exists(hiddenPath));

        // 模拟BACKUP操作：复制隐藏文件到备份目录（使用原始文件名）
        Path backupDir = tempDir.resolve("backup");
        Files.createDirectories(backupDir);
        Path originalPath = TransferFileStateManager.getOriginalPath(hiddenPath);
        Path backupTarget = backupDir.resolve(originalPath.getFileName());
        Files.copy(hiddenPath, backupTarget);

        // 删除隐藏文件（COPY模式下的清理）
        Files.delete(hiddenPath);

        assertTrue(Files.exists(backupTarget));
        assertEquals("test data content", Files.readString(backupTarget));
        assertFalse(Files.exists(hiddenPath));
    }

    @Test
    @DisplayName("端到端 - 一对多场景：隐藏一次，多次判断传输中状态")
    void endToEndOneToManyScenario() throws IOException {
        // 模拟一对多：文件隐藏一次，多个目标判断是否在传输中
        Path hiddenPath = TransferFileStateManager.hideFile(testFile);

        // 多个目标都判断文件在传输中
        assertTrue(TransferFileStateManager.isTransferring(testFile));
        assertTrue(TransferFileStateManager.isTransferring(testFile));
        assertTrue(TransferFileStateManager.isTransferring(testFile));

        // 隐藏文件仍然可以被读取
        assertEquals("test data content", Files.readString(hiddenPath));

        // 所有目标完成后恢复
        TransferFileStateManager.unhideFile(hiddenPath);
        assertFalse(TransferFileStateManager.isTransferring(testFile));
    }

    @Test
    @DisplayName("端到端 - hideFileSafely在并发场景下不会重复隐藏")
    void endToEndConcurrentHideSafely() throws IOException {
        // 第一次隐藏成功
        Path result1 = TransferFileStateManager.hideFileSafely(testFile);
        assertEquals(expectedHiddenFile, result1);

        // 第二次调用hideFileSafely不会抛异常，返回已有隐藏路径
        Path result2 = TransferFileStateManager.hideFileSafely(testFile);
        assertEquals(expectedHiddenFile, result2);

        // 文件内容不变
        assertEquals("test data content", Files.readString(expectedHiddenFile));
    }

    @Test
    @DisplayName("路径计算 - 包含特殊字符的文件名")
    void pathComputationWithSpecialCharacters() {
        Path original = tempDir.resolve("data-2026_05.tar.gz");
        Path hidden = TransferFileStateManager.getTransferringPath(original);
        Path restored = TransferFileStateManager.getOriginalPath(hidden);

        assertEquals(tempDir.resolve(".data-2026_05.tar.gz.transferring"), hidden);
        assertEquals(original, restored);
    }

    @Test
    @DisplayName("路径计算 - 中文文件名")
    void pathComputationWithChineseFileName() {
        Path original = tempDir.resolve("数据报告.csv");
        Path hidden = TransferFileStateManager.getTransferringPath(original);
        Path restored = TransferFileStateManager.getOriginalPath(hidden);

        assertEquals(tempDir.resolve(".数据报告.csv.transferring"), hidden);
        assertEquals(original, restored);
    }

    @Test
    @DisplayName("路径计算 - 深层嵌套目录")
    void pathComputationWithDeepNesting() {
        Path original = Path.of("/a/b/c/d/e/file.txt");
        Path hidden = TransferFileStateManager.getTransferringPath(original);
        Path restored = TransferFileStateManager.getOriginalPath(hidden);

        assertEquals(Path.of("/a/b/c/d/e/.file.txt.transferring"), hidden);
        assertEquals(original, restored);
    }
}
