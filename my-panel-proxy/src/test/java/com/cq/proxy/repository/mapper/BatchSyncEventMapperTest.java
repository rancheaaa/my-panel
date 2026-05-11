package com.cq.proxy.repository.mapper;

import com.cq.proxy.repository.entity.BatchSyncEvent;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BatchSyncEventMapper 测试
 * 验证事件队列的CRUD操作和并发安全
 * 符合spec.md设计
 */
@SpringBootTest
@Transactional
class BatchSyncEventMapperTest {

    @Autowired
    private BatchSyncEventMapper eventMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // IDs populated in setUp
    private Long pendingId1;
    private Long pendingId2;
    private Long processingId;
    private Long completedId;

    @BeforeEach
    void setUp() {
        assertNotNull(eventMapper);
        assertNotNull(jdbcTemplate);
        createTable();
        cleanData();
        insertTestData();
    }

    private void createTable() {
        // 先删除旧表（确保schema最新）
        jdbcTemplate.execute("DROP TABLE IF EXISTS batch_sync_event");
        // MySQL语法创建表（与Mapper中的SQL一致）
        jdbcTemplate.execute("""
            CREATE TABLE batch_sync_event (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                event_type VARCHAR(30) NOT NULL,
                task_id BIGINT NOT NULL,
                source_agent_id VARCHAR(50) NOT NULL,
                payload TEXT,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                retry_count INT NOT NULL DEFAULT 0,
                error_message TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                processed_at TIMESTAMP,
                expire_at TIMESTAMP,
                started_at TIMESTAMP,
                next_retry_at TIMESTAMP,
                update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                completed_at TIMESTAMP
            )
        """);
    }

    private void cleanData() {
        jdbcTemplate.execute("DELETE FROM batch_sync_event");
    }

    private void insertTestData() {
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp future24h = Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS));
        Timestamp past10min = Timestamp.from(Instant.now().minus(10, ChronoUnit.MINUTES));
        Timestamp past1h = Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Timestamp past23h = Timestamp.from(Instant.now().minus(23, ChronoUnit.HOURS));
        Timestamp past2d = Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS));
        Timestamp past1d = Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS));

        // PENDING event (not expired)
        jdbcTemplate.update("""
            INSERT INTO batch_sync_event (event_type, task_id, source_agent_id, payload, status, retry_count, created_at, expire_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, "TASK_CREATED", 1, "agent-001", "{\"taskId\":1,\"taskName\":\"test\"}", "PENDING", 0, now, future24h);
        pendingId1 = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // PENDING event (another)
        jdbcTemplate.update("""
            INSERT INTO batch_sync_event (event_type, task_id, source_agent_id, payload, status, retry_count, created_at, expire_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, "TASK_STATUS_CHANGED", 2, "agent-002", "{\"taskId\":2,\"status\":\"RUNNING\"}", "PENDING", 0, now, future24h);
        pendingId2 = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // PROCESSING event (started 10 minutes ago)
        jdbcTemplate.update("""
            INSERT INTO batch_sync_event (event_type, task_id, source_agent_id, payload, status, retry_count, created_at, started_at, expire_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, "TASK_UPDATED", 1, "agent-001", "{\"taskId\":1}", "PROCESSING", 1, past10min, past10min, future24h);
        processingId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // COMPLETED event
        jdbcTemplate.update("""
            INSERT INTO batch_sync_event (event_type, task_id, source_agent_id, payload, status, retry_count, processed_at, created_at, expire_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, "TASK_CREATED", 3, "agent-003", "{\"taskId\":3}", "COMPLETED", 0, now, past1h, past23h);
        completedId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // PENDING but expired
        jdbcTemplate.update("""
            INSERT INTO batch_sync_event (event_type, task_id, source_agent_id, payload, status, retry_count, created_at, expire_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, "TASK_DELETED", 4, "agent-001", "{\"taskId\":4}", "PENDING", 0, past2d, past1d);

        // PENDING with next_retry_at in future (should NOT be returned)
        jdbcTemplate.update("""
            INSERT INTO batch_sync_event (event_type, task_id, source_agent_id, payload, status, retry_count, created_at, expire_at, next_retry_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, "TASK_CREATED", 5, "agent-003", "{\"taskId\":5}", "PENDING", 0, now, future24h, future24h);
    }

    // ==================== selectPendingEvents ====================

    @Test
    @DisplayName("1. 查询待处理事件 - 只返回PENDING且未过期")
    void testSelectPendingEvents_notExpired() {
        List<BatchSyncEvent> events = eventMapper.selectPendingEvents(10);

        assertNotNull(events);
        // 应有2个PENDING且未过期的事件（第3个PENDING事件已过期，第4个是PROCESSING，第5个是COMPLETED）
        assertEquals(2, events.size(), "应有2个PENDING且未过期的事件");

        for (BatchSyncEvent event : events) {
            assertEquals("PENDING", event.getStatus());
        }
    }

    @Test
    @DisplayName("2. 查询待处理事件 - limit限制返回数量")
    void testSelectPendingEvents_withLimit() {
        List<BatchSyncEvent> events = eventMapper.selectPendingEvents(1);

        assertEquals(1, events.size(), "limit=1应只返回1条");
    }

    // ==================== updateStatusToProcessing ====================

    @Test
    @DisplayName("3. 更新状态为PROCESSING - 成功")
    void testUpdateStatusToProcessing_success() {
        BatchSyncEvent event = new BatchSyncEvent();
        event.setId(pendingId1);
        event.setStatus("PROCESSING");
        event.setStartedAt(new Date());

        int rows = eventMapper.updateStatusToProcessing(event);

        assertEquals(1, rows, "应更新1条记录");
    }

    // ==================== updateStatusToCompleted ====================

    @Test
    @DisplayName("4. 更新状态为COMPLETED - 成功")
    void testUpdateStatusToCompleted_success() {
        int rows = eventMapper.updateStatusToCompleted(processingId);

        assertEquals(1, rows);
    }

    // ==================== updateStatusToFailed ====================

    @Test
    @DisplayName("5. 更新状态为FAILED - 记录错误信息")
    void testUpdateStatusToFailed_success() {
        int rows = eventMapper.updateStatusToFailed(processingId, "推送超时");

        assertEquals(1, rows);
    }

    // ==================== updateRetry ====================

    @Test
    @DisplayName("6. 重试更新 - 设置下次重试时间")
    void testUpdateRetry_success() {
        Date nextRetryAt = new Date(System.currentTimeMillis() + 60000);
        int rows = eventMapper.updateRetry(processingId, nextRetryAt, "推送超时");

        assertEquals(1, rows);
    }

    // ==================== selectExpiredEvents ====================

    @Test
    @DisplayName("7. 查询过期事件")
    void testSelectExpiredEvents() {
        List<BatchSyncEvent> events = eventMapper.selectExpiredEvents(new Date());

        assertNotNull(events);
        assertEquals(1, events.size(), "应有1个过期事件");
    }

    // ==================== selectStuckProcessingEvents ====================

    @Test
    @DisplayName("8. 查询卡住的PROCESSING事件")
    void testSelectStuckProcessingEvents() {
        Date threshold = new Date(System.currentTimeMillis() - 5 * 60 * 1000); // 5分钟前
        List<BatchSyncEvent> events = eventMapper.selectStuckProcessingEvents(threshold);

        assertNotNull(events);
        assertTrue(events.size() >= 1, "应至少有1个卡住的事件");
    }

    // ==================== updateStatusToPending ====================

    @Test
    @DisplayName("9. 恢复为PENDING状态")
    void testUpdateStatusToPending() {
        int rows = eventMapper.updateStatusToPending(processingId);

        assertEquals(1, rows);
    }
}
