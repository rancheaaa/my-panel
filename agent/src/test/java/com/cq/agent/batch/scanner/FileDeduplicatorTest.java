package com.cq.agent.batch.scanner;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件去重器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("文件去重器 - FileDeduplicator")
class FileDeduplicatorTest {

    private FileDeduplicator fileDeduplicator;

    @BeforeEach
    void setUp() {
        fileDeduplicator = new FileDeduplicator();
    }

    // ==================== 1. 相同文件跳过 ====================

    @Test
    @DisplayName("1. 相同文件(名称+大小+修改时间) - 跳过")
    void testDedup_sameNameSizeModTime_skip() {
        FileInfo file1 = createFileInfo("app.log", 1024, 1000L);
        FileInfo file2 = createFileInfo("app.log", 1024, 1000L);
        
        fileDeduplicator.recordProcessed(file1);
        
        boolean shouldInclude = fileDeduplicator.shouldInclude(file2);
        
        assertFalse(shouldInclude, "完全相同的文件应被跳过");
        
        System.out.println("✅ 相同文件已去重: app.log (1024bytes, ts=1000)");
    }

    // ==================== 2. 不同文件包含 ====================

    @Test
    @DisplayName("2. 不同文件(名称或大小或时间) - 包含")
    void testDedup_differentFile_include() {
        FileInfo file1 = createFileInfo("app.log", 1024, 1000L);
        FileInfo file2 = createFileInfo("data.txt", 2048, 2000L);
        
        fileDeduplicator.recordProcessed(file1);
        
        boolean shouldInclude = fileDeduplicator.shouldInclude(file2);
        
        assertTrue(shouldInclude, "不同文件应被包含");
        
        System.out.println("✅ 不同文件已包含: data.txt");
    }

    // ==================== 3. 修改后文件重新包含 ====================

    @Test
    @DisplayName("3. 修改后文件(大小/时间变化) - 重新包含")
    void testDedup_modifiedFile_reinclude() {
        FileInfo original = createFileInfo("config.xml", 512, 3000L);
        FileInfo modified = createFileInfo("config.xml", 600, 3500L);
        
        fileDeduplicator.recordProcessed(original);
        
        boolean shouldInclude = fileDeduplicator.shouldInclude(modified);
        
        assertTrue(shouldInclude, "修改后的文件应重新包含");
        
        System.out.println("✅ 修改后文件重新包含: config.xml (512→600 bytes)");
    }

    // ==================== 4. 同日规则应用 ====================

    @Test
    @DisplayName("4. 同日规则 - 当天文件不重复传输")
    void testDedup_sameDayRule_applied() {
        long todayMorning = System.currentTimeMillis();
        long todayEvening = todayMorning + (8 * 60 * 60 * 1000); // 8小时后
        
        FileInfo morningFile = createFileInfo("daily.log", 2048, todayMorning);
        FileInfo eveningFile = createFileInfo("daily.log", 2048, todayEvening);
        
        fileDeduplicator.recordProcessed(morningFile);
        
        boolean shouldInclude = fileDeduplicator.shouldInclude(eveningFile);
        
        assertFalse(shouldInclude, "同日同文件不应重复传输");
        
        System.out.println("✅ 同日规则生效: daily.log 不重复传输");
    }

    // ==================== 辅助方法 ====================

    private FileInfo createFileInfo(String fileName, long size, long lastModified) {
        return new FileInfo(
            java.nio.file.Paths.get("/test/" + fileName),
            fileName,
            size,
            lastModified
        );
    }
}
