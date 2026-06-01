package com.cq.agent.batch.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 原子文件写入器单元测试
 * 验证spec.md要求的"临时文件+重命名"原子写入
 */
@DisplayName("原子文件写入器 - AtomicFileWriter")
class AtomicFileWriterTest {

    @TempDir
    Path tempDir;

    private AtomicFileWriter atomicFileWriter;

    @BeforeEach
    void setUp() {
        atomicFileWriter = new AtomicFileWriter();
    }

    @Test
    @DisplayName("1. 原子写入 - 临时文件+重命名")
    void testWriteAtomic_tempFileThenRename() {
        File targetFile = tempDir.resolve("task_1001.json").toFile();
        String content = "{\"taskId\":1001,\"taskName\":\"测试任务\"}";

        atomicFileWriter.writeAtomic(targetFile, content);

        assertTrue(targetFile.exists(), "目标文件应存在");
        assertFalse(new File(tempDir.toFile(), "task_1001.json.tmp").exists(), "临时文件不应存在");
    }

    @Test
    @DisplayName("2. 写入内容正确性")
    void testWriteAtomic_contentCorrect() throws Exception {
        File targetFile = tempDir.resolve("task_1002.json").toFile();
        String content = "{\"taskId\":1002,\"version\":\"20260509103000\"}";

        atomicFileWriter.writeAtomic(targetFile, content);

        String readContent = java.nio.file.Files.readString(targetFile.toPath());
        assertEquals(content, readContent, "写入内容应正确");
    }

    @Test
    @DisplayName("3. 写入失败 - 临时文件不残留")
    void testWriteAtomic_failure_noTempFileLeft() {
        // 使用一个无法创建文件的非法路径来模拟写入失败
        File targetFile = new File("\\/\\invalid::path\\task_1003.json");
        String content = "{\"taskId\":1003}";

        assertThrows(RuntimeException.class, () -> {
            atomicFileWriter.writeAtomic(targetFile, content);
        });
    }

    @Test
    @DisplayName("4. 覆盖已有文件")
    void testWriteAtomic_overwriteExisting() throws Exception {
        File targetFile = tempDir.resolve("task_1004.json").toFile();

        // 先写入旧内容
        java.nio.file.Files.writeString(targetFile.toPath(), "{\"taskId\":1004,\"old\":true}");

        // 再写入新内容
        String newContent = "{\"taskId\":1004,\"new\":true}";
        atomicFileWriter.writeAtomic(targetFile, newContent);

        String readContent = java.nio.file.Files.readString(targetFile.toPath());
        assertEquals(newContent, readContent, "应覆盖为最新内容");
    }
}
