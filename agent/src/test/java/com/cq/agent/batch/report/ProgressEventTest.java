package com.cq.agent.batch.report;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProgressEvent单元测试
 * 验证符合spec.md的数据结构要求
 */
@DisplayName("进度事件 - ProgressEvent")
class ProgressEventTest {

    @Test
    @DisplayName("1. 默认构造函数 - 所有字段可设置")
    void testDefaultConstructor() {
        ProgressEvent event = new ProgressEvent();
        
        event.setSubtaskId(1L);
        event.setTaskId(100L);
        event.setTransferId("transfer-123");
        event.setStatus("SENDING");
        event.setTransferredChunks(15);
        event.setTotalChunks(20);
        event.setTransferredBytes(15728640L);
        event.setSpeedBytesPerSec(5242880L);
        event.setTimestamp(System.currentTimeMillis());
        event.setSequenceNumber(1);
        
        assertEquals(1L, event.getSubtaskId());
        assertEquals(100L, event.getTaskId());
        assertEquals("transfer-123", event.getTransferId());
        assertEquals("SENDING", event.getStatus());
        assertEquals(15, event.getTransferredChunks());
        assertEquals(20, event.getTotalChunks());
        assertEquals(15728640L, event.getTransferredBytes());
        assertEquals(5242880L, event.getSpeedBytesPerSec());
        assertEquals(1, event.getSequenceNumber());
    }

    @Test
    @DisplayName("2. 全参数构造函数 - 符合spec的数据结构")
    void testFullConstructor() {
        long timestamp = System.currentTimeMillis();
        
        ProgressEvent event = new ProgressEvent(
            1L,           // subtaskId
            100L,         // taskId
            "transfer-123", // transferId
            "SENDING",    // status
            15,           // transferredChunks
            20,           // totalChunks
            15728640L,    // transferredBytes
            5242880L,     // speedBytesPerSec
            timestamp,    // timestamp
            1             // sequenceNumber
        );
        
        assertEquals(1L, event.getSubtaskId());
        assertEquals(100L, event.getTaskId());
        assertEquals("transfer-123", event.getTransferId());
        assertEquals("SENDING", event.getStatus());
        assertEquals(15, event.getTransferredChunks());
        assertEquals(20, event.getTotalChunks());
        assertEquals(15728640L, event.getTransferredBytes());
        assertEquals(5242880L, event.getSpeedBytesPerSec());
        assertEquals(timestamp, event.getTimestamp());
        assertEquals(1, event.getSequenceNumber());
    }

    @Test
    @DisplayName("3. toString() - 包含关键字段信息")
    void testToString() {
        ProgressEvent event = new ProgressEvent(
            1L, 100L, "transfer-123", "SENDING",
            15, 20, 15728640L, 5242880L,
            System.currentTimeMillis(), 1
        );
        
        String str = event.toString();
        
        assertTrue(str.contains("subtask=1"));
        assertTrue(str.contains("task=100"));
        assertTrue(str.contains("status=SENDING"));
        assertTrue(str.contains("chunks=15/20"));
    }

    @Test
    @DisplayName("4. 兼容性 - 旧构造函数仍可用")
    void testBackwardCompatibility() {
        long timestamp = System.currentTimeMillis();
        
        ProgressEvent event = new ProgressEvent(1L, 15728640, 20971520, timestamp);
        
        assertEquals(1L, event.getSubtaskId());
        assertEquals(15728640, event.getTransferredBytes());
        assertEquals(20971520, event.getTotalBytes());
        assertEquals(timestamp, event.getTimestamp());
    }
}
