package com.cq.agent.batch.scheduler;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 批次ID生成逻辑单元测试
 */
@DisplayName("批次ID生成逻辑")
class BatchIdGenerationTest {

    @Test
    @DisplayName("1. scanBatchId - 始终为正数")
    void testScanBatchId_positive() {
        for (int i = 0; i < 100; i++) {
            Long id = generateScanBatchId((long) i);
            assertTrue(id > 0, "scanBatchId应为正数: " + id);
        }
    }

    @Test
    @DisplayName("2. fileBatchId - 始终为正数")
    void testFileBatchId_positive() {
        Long scanBatchId = generateScanBatchId(1L);
        for (int i = 0; i < 100; i++) {
            Long id = generateFileBatchId(1L, "file" + i + ".txt", scanBatchId);
            assertTrue(id > 0, "fileBatchId应为正数: " + id);
        }
    }

    @Test
    @DisplayName("3. scanBatchId - 不同时间生成不同ID")
    void testScanBatchId_differentTimes() throws Exception {
        Long id1 = generateScanBatchId(1L);
        Thread.sleep(15);
        Long id2 = generateScanBatchId(1L);

        assertNotEquals(id1, id2, "不同时间应生成不同ID");
    }

    @Test
    @DisplayName("4. fileBatchId - 不同scanBatch生成不同fileBatchId")
    void testFileBatchId_differentScanBatches() throws Exception {
        Long scanBatchId1 = generateScanBatchId(1L);
        Thread.sleep(15);
        Long scanBatchId2 = generateScanBatchId(1L);

        Long fileBatchId1 = generateFileBatchId(1L, "test.txt", scanBatchId1);
        Long fileBatchId2 = generateFileBatchId(1L, "test.txt", scanBatchId2);

        assertNotEquals(fileBatchId1, fileBatchId2, "不同scanBatch应生成不同fileBatchId");
    }

    @Test
    @DisplayName("5. fileBatchId - 不同文件名哈希不同")
    void testFileBatchId_differentFileNameHash() {
        Long scanBatchId = generateScanBatchId(1L);
        Long id1 = generateFileBatchId(1L, "file1.txt", scanBatchId);
        Long id2 = generateFileBatchId(1L, "file2.txt", scanBatchId);

        long hash1 = (id1 / 100000) % 10000;
        long hash2 = (id2 / 100000) % 10000;

        assertNotEquals(hash1, hash2, "不同文件名应有不同哈希");
    }

    @Test
    @DisplayName("6. scanBatchId - 5次有间隔生成无碰撞")
    void testScanBatchId_noCollision_withDelay() throws Exception {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (int i = 0; i < 5; i++) {
            Long id = generateScanBatchId(1L);
            assertFalse(ids.contains(id), "重复的scanBatchId: " + id);
            ids.add(id);
            Thread.sleep(15);
        }
    }

    // ==================== 复制ID生成逻辑 ====================

    private Long generateScanBatchId(Long taskId) {
        long timePart = System.currentTimeMillis() % 100000000000L;
        long taskPart = (taskId != null ? taskId : 0L) % 10000;
        long randomPart = java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 10000);
        return timePart * 1000000 + taskPart * 100 + randomPart;
    }

    private Long generateFileBatchId(Long taskId, String fileName, Long scanBatchId) {
        long scanBatchPrefix = scanBatchId != null ? scanBatchId / 1000000 : System.currentTimeMillis() % 100000000000L;
        int nameHash = Math.abs(fileName.hashCode());
        long taskPart = (taskId != null ? taskId : 0L) % 10000;
        long randomPart = java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1000);
        long fileHashPart = (nameHash % 10000L) * 100000L + taskPart * 1000L + randomPart;
        return scanBatchPrefix * 100000000L + fileHashPart;
    }
}
