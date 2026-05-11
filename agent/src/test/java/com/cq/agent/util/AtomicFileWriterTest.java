package com.cq.agent.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AtomicFileWriter - 原子性文件写入工具")
class AtomicFileWriterTest {

    @TempDir
    Path tempDir;

    private Path targetFile;

    @BeforeEach
    void setUp() {
        targetFile = tempDir.resolve("test-file.json");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(targetFile);
    }

    @Test
    @DisplayName("应该成功写入内容到目标文件")
    void shouldWriteContentToTargetFile() throws IOException {
        String content = "{\"key\": \"value\"}";

        AtomicFileWriter.writeAtomically(targetFile, content);

        assertTrue(Files.exists(targetFile), "目标文件应该存在");
        String actualContent = Files.readString(targetFile);
        assertEquals(content, actualContent, "文件内容应该匹配");
    }

    @Test
    @DisplayName("写入后不应该留下临时文件")
    void shouldNotLeaveTempFileAfterWrite() throws IOException {
        String content = "test content";

        AtomicFileWriter.writeAtomically(targetFile, content);

        long tempFileCount = Files.list(tempDir)
                .filter(path -> path.getFileName().toString().contains(".tmp_"))
                .count();

        assertEquals(0, tempFileCount, "不应该有残留的临时文件");
    }

    @Test
    @DisplayName("应该覆盖已存在的文件")
    void shouldOverwriteExistingFile() throws IOException {
        String originalContent = "original content";
        String newContent = "new content";

        Files.writeString(targetFile, originalContent);
        AtomicFileWriter.writeAtomically(targetFile, newContent);

        String actualContent = Files.readString(targetFile);
        assertEquals(newContent, actualContent, "应该被新内容覆盖");
    }

    @Test
    @DisplayName("当目标路径无效时应该抛出IOException")
    void shouldThrowExceptionForInvalidPath() {
        Path invalidPath = Path.of("/invalid/path/that/does/not/exist/file.txt");

        assertThrows(IOException.class, () ->
            AtomicFileWriter.writeAtomically(invalidPath, "content")
        );
    }

    @Test
    @DisplayName("应该正确处理空字符串内容")
    void shouldHandleEmptyContent() throws IOException {
        AtomicFileWriter.writeAtomically(targetFile, "");

        assertTrue(Files.exists(targetFile));
        String content = Files.readString(targetFile);
        assertEquals("", content, "空字符串应该被正确写入");
    }

    @Test
    @DisplayName("应该正确处理大文件内容")
    void shouldHandleLargeContent() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("{\"index\": ").append(i).append(", \"data\": \"test").append(i).append("\"}");
            if (i < 9999) sb.append("\n");
        }
        String largeContent = sb.toString();

        AtomicFileWriter.writeAtomically(targetFile, largeContent);

        String actualContent = Files.readString(targetFile);
        assertEquals(largeContent.length(), actualContent.length(), "大文件内容长度应该匹配");
    }

    @Test
    @DisplayName("deleteIfExists 应该删除存在的文件")
    void shouldDeleteExistingFile() throws IOException {
        Files.writeString(targetFile, "temporary");

        AtomicFileWriter.deleteIfExists(targetFile);

        assertFalse(Files.exists(targetFile), "文件应该被删除");
    }

    @Test
    @DisplayName("deleteIfExists 对不存在的文件应该静默成功")
    void shouldSucceedWhenDeletingNonExistentFile() {
        Path nonExistent = tempDir.resolve("non-existent.txt");

        assertDoesNotThrow(() ->
            AtomicFileWriter.deleteIfExists(nonExistent)
        );
    }

    @Test
    @DisplayName("并发写入不应该导致数据损坏")
    void shouldHandleConcurrentWrites() throws InterruptedException, IOException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    AtomicFileWriter.writeAtomically(targetFile,
                            "{\"thread\": " + index + ", \"data\": \"test\"}");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join(5000);
        }

        assertTrue(Files.exists(targetFile), "最终文件应该存在");
        String content = Files.readString(targetFile);
        assertNotNull(content, "内容不应为null");
        assertTrue(content.contains("\"thread\":"), "内容应该是有效的JSON");
    }
}
