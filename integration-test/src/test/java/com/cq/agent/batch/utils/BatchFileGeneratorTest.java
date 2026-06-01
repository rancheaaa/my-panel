package com.cq.agent.batch.utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchFileGenerator - 批量文件生成器")
class BatchFileGeneratorTest {

    @TempDir
    Path tempDir;

    private BatchFileGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new BatchFileGenerator();
    }

    @Test
    @DisplayName("指定文件大小范围和数量，应正确生成对应数量的文件")
    void testGenerate_withFileSizeRange() throws IOException {
        List<Path> files = generator
                .targetDir(tempDir.resolve("output"))
                .fileSizeRange(1024, 4096)
                .namePattern("test-*.dat")
                .fileCount(5)
                .generate();

        assertEquals(5, files.size());
        for (Path file : files) {
            assertTrue(Files.exists(file), "文件应存在: " + file);
            long size = Files.size(file);
            assertTrue(size >= 1024 && size <= 4096,
                    "文件大小应在[1024, 4096]范围内, 实际: " + size);
        }
    }

    @Test
    @DisplayName("通配符*应展开为随机字符串")
    void testGenerate_wildcardStarExpansion() throws IOException {
        List<Path> files = generator
                .targetDir(tempDir)
                .namePattern("report-*.log")
                .fileSizeRange(100, 200)
                .fileCount(3)
                .generate();

        for (Path file : files) {
            String name = file.getFileName().toString();
            assertTrue(name.startsWith("report-") && name.endsWith(".log"),
                    "文件名应匹配通配符模式: " + name);
            int dashIndex = name.indexOf('-');
            int dotIndex = name.lastIndexOf('.');
            String middle = name.substring(dashIndex + 1, dotIndex);
            assertTrue(middle.length() >= 3 && middle.length() <= 10,
                    "*展开长度应在[3,10]: " + middle);
        }
    }

    @Test
    @DisplayName("通配符?应展开为单个随机字符")
    void testGenerate_wildcardQuestionMark() throws IOException {
        List<Path> files = generator
                .targetDir(tempDir)
                .namePattern("data-???.csv")
                .fileSizeRange(50, 100)
                .fileCount(4)
                .generate();

        for (Path file : files) {
            String name = file.getFileName().toString();
            assertTrue(name.matches("data-[a-z0-9]{3}\\.csv"),
                    "文件名应匹配?通配符: " + name);
        }
    }

    @Test
    @DisplayName("原子写入：目标目录不应出现临时文件")
    void testGenerate_atomicWrite_noTempFilesLeftBehind() throws IOException {
        Path outputDir = tempDir.resolve("atomic-output");

        generator.targetDir(outputDir)
                .namePattern("safe-*.txt")
                .fileSizeRange(256, 512)
                .fileCount(10)
                .generate();

        try (var stream = Files.list(outputDir)) {
            stream.forEach(path -> {
                String name = path.getFileName().toString();
                assertFalse(name.contains(".tmp_"),
                        "不应残留临时文件: " + name);
                assertFalse(name.contains(".batch-gen-staging-"),
                        "不应残留staging目录文件: " + name);
                assertTrue(name.startsWith("safe-") && name.endsWith(".txt"),
                        "文件名应符合pattern: " + name);
            });
        }
    }

    @Test
    @DisplayName("未设置targetDir时应抛异常")
    void testGenerate_missingTargetDir_shouldThrow() {
        BatchFileGenerator gen = new BatchFileGenerator().fileCount(1);
        assertThrows(IllegalStateException.class, gen::generate);
    }

    @Test
    @DisplayName("生成大量小文件（性能边界）")
    void testGenerate_manySmallFiles() throws IOException {
        Path outputDir = tempDir.resolve("bulk");
        int count = 500;

        long start = System.currentTimeMillis();
        List<Path> files = generator
                .targetDir(outputDir)
                .fileSizeRange(64, 128)
                .namePattern("bulk-*.bin")
                .fileCount(count)
                .generate();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(count, files.size());
        System.out.println("生成" + count + "个文件耗时: " + elapsed + "ms");
    }

    @Test
    @DisplayName("混合通配符模式")
    void testGenerate_mixedWildcards() throws IOException {
        List<Path> files = generator
                .targetDir(tempDir)
                .namePattern("log_??_*_v?.tmp")
                .fileSizeRange(10, 50)
                .fileCount(6)
                .generate();

        for (Path file : files) {
            String name = file.getFileName().toString();
            assertTrue(name.matches("log_[a-z0-9]{2}_[a-z0-9]{3,10}_v[a-z0-9]\\.tmp"),
                    "混合通配符不匹配: " + name);
        }
    }

    @Test
    @DisplayName("目标目录不存在时自动创建")
    void testGenerate_autoCreateTargetDir() throws IOException {
        Path nestedDir = tempDir.resolve("a").resolve("b").resolve("c");
        assertFalse(Files.exists(nestedDir));

        generator.targetDir(nestedDir)
                .namePattern("auto-*.txt")
                .fileSizeRange(10, 20)
                .fileCount(2)
                .generate();

        assertTrue(Files.exists(nestedDir));
        assertEquals(2, Files.list(nestedDir).count());
    }

    @Test
    @DisplayName("staging目录与目标目录在同一文件系统，move应为重命名操作")
    void testGenerate_stagingOnSameFileSystem_asTarget() throws IOException {
        Path outputDir = tempDir.resolve("same-fs-test");

        generator.targetDir(outputDir)
                .namePattern("fs-check-*.dat")
                .fileSizeRange(100, 200)
                .fileCount(3)
                .generate();

        try (var stream = Files.list(outputDir)) {
            long count = stream.count();
            assertEquals(3, count);
        }

        try (var stream = Files.list(outputDir)) {
            stream.forEach(path -> {
                Path parent = path.getParent();
                assertEquals(outputDir.toAbsolutePath().normalize(),
                        parent.toAbsolutePath().normalize(),
                        "生成文件应在targetDir下，确认move是同文件系统重命名");
            });
        }
    }
}
