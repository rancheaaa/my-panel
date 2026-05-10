package com.cq.agent.batch.config;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 版本管理器单元测试
 * 覆盖率目标：100%
 */
@DisplayName("版本管理器 - VersionManager")
class VersionManagerTest {

    private VersionManager versionManager;

    @BeforeEach
    void setUp() {
        versionManager = new VersionManager();
    }

    // ==================== 1. 首次接收新版本 ====================

    @Test
    @DisplayName("1. 首次接收 - 创建新版本")
    void testFirstReceive_newVersion_created() {
        long newVersion = 1L;
        boolean result = versionManager.acceptVersion(100L, newVersion);
        
        assertTrue(result, "首次接收应返回true");
        assertEquals(newVersion, versionManager.getCurrentVersion(100L));
        
        System.out.println("✅ 首次接收创建版本: taskId=100, version=" + newVersion);
    }

    // ==================== 2. 更高版本更新 ====================

    @Test
    @DisplayName("2. 更高版本 - 应用更新")
    void testNewerVersion_updateApplied() {
        versionManager.acceptVersion(101L, 1L);
        
        boolean result = versionManager.acceptVersion(101L, 2L);
        
        assertTrue(result, "更高版本应被接受");
        assertEquals(2L, versionManager.getCurrentVersion(101L));
        
        System.out.println("✅ 版本更新: 1 → 2");
    }

    // ==================== 3. 相同版本幂等性 ====================

    @Test
    @DisplayName("3. 相同版本 - 幂等返回false")
    void testSameVersion_idempotentReturn() {
        versionManager.acceptVersion(102L, 5L);
        
        boolean result = versionManager.acceptVersion(102L, 5L);
        
        assertFalse(result, "相同版本应返回false（幂等）");
        assertEquals(5L, versionManager.getCurrentVersion(102L));
        
        System.out.println("✅ 相同版本幂等处理: version=5");
    }

    // ==================== 4. 低版本忽略 ====================

    @Test
    @DisplayName("4. 低版本 - 忽略并警告")
    void testOlderVersion_ignoredWithWarning() {
        versionManager.acceptVersion(103L, 10L);
        
        boolean result = versionManager.acceptVersion(103L, 5L);
        
        assertFalse(result, "低版本应被忽略");
        assertEquals(10L, versionManager.getCurrentVersion(103L));
        
        System.out.println("⚠️  低版本被忽略: current=10, received=5");
    }

    // ==================== 5. 基于时间戳的比较 ====================

    @Test
    @DisplayName("5. 版本比较 - 基于时间戳")
    void testVersionComparison_timestampBased() throws InterruptedException {
        long baseTime = System.currentTimeMillis();
        
        versionManager.acceptVersion(104L, baseTime);
        Thread.sleep(10); // 确保时间戳不同
        
        long newerTimestamp = System.currentTimeMillis();
        boolean result = versionManager.acceptVersion(104L, newerTimestamp);
        
        assertTrue(result, "更新的时间戳应被接受");
        assertEquals(newerTimestamp, versionManager.getCurrentVersion(104L));
        
        System.out.println("✅ 时间戳比较: base=" + baseTime + ", newer=" + newerTimestamp);
    }

    // ==================== 6. 多任务独立版本管理 ====================

    @Test
    @DisplayName("6. 多任务 - 独立版本管理")
    void testMultipleTasks_independentVersions() {
        versionManager.acceptVersion(200L, 1L);
        versionManager.acceptVersion(201L, 5L);
        versionManager.acceptVersion(202L, 3L);
        
        assertEquals(1L, versionManager.getCurrentVersion(200L));
        assertEquals(5L, versionManager.getCurrentVersion(201L));
        assertEquals(3L, versionManager.getCurrentVersion(202L));
        
        System.out.println("✅ 多任务独立版本: task200=1, task201=5, task202=3");
    }

    // ==================== 7. 不存在任务的版本查询 ====================

    @Test
    @DisplayName("7. 不存在的任务 - 返回-1")
    void testNonExistentTask_returnsNegativeOne() {
        long version = versionManager.getCurrentVersion(99999L);
        
        assertEquals(-1L, version, "不存在的任务应返回-1");
        
        System.out.println("✅ 不存在任务返回: " + version);
    }

    // ==================== 8. 清理操作 ====================

    @Test
    @DisplayName("8. clear() - 清除所有版本")
    void testClear_removesAllVersions() {
        versionManager.acceptVersion(300L, 1L);
        versionManager.acceptVersion(301L, 2L);
        
        versionManager.clear();
        
        assertEquals(-1L, versionManager.getCurrentVersion(300L));
        assertEquals(-1L, versionManager.getCurrentVersion(301L));
        
        System.out.println("✅ 所有版本已清除");
    }
}
