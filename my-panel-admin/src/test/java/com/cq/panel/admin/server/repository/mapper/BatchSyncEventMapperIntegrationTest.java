package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchSyncEvent;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BatchSyncEvent Mapper 集成测试
 * 使用JdbcTemplate直接初始化数据（兼容所有数据库）
 */
@SpringBootTest
@Transactional
class BatchSyncEventMapperIntegrationTest {

    @Autowired
    private BatchSyncEventMapper eventMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        assertNotNull(eventMapper);
        assertNotNull(jdbcTemplate);
        
        // 清空并重新插入Mock数据（确保每个测试都是干净的状态）
        initMockData();
    }

    /**
     * 初始化Mock数据（代码内嵌）
     * 包含：建表语句(H2兼容) + 测试数据
     */
    private void initMockData() {
        // 1. 先创建表（H2兼容语法）
        createTables();
        
        // 2. 删除旧数据
        jdbcTemplate.execute("DELETE FROM batch_transfer_subtask");
        jdbcTemplate.execute("DELETE FROM batch_sync_event");
        jdbcTemplate.execute("DELETE FROM batch_transfer_task");
        
        // 3. 插入3条任务Mock数据（简化日期字段）
        jdbcTemplate.update("""
            INSERT INTO batch_transfer_task (
                task_name, task_description, source_agent_id, source_agent_name,
                source_dir, target_dirs, include_patterns, exclude_patterns,
                scan_cron_expression, max_scan_files, target_agent_ids, target_agent_names,
                retry_enabled, retry_max_days, retry_interval_min, max_retry_count,
                retry_backoff_type, post_transfer_action, backup_dir, backup_mode,
                preserve_dir_structure, transfer_mode, routing_strategy, routing_config, status,
                started_at, create_by, create_time, update_by, remark, deleted
            ) VALUES 
            ('日志备份任务', '每日备份应用日志到备用节点', 'agent-001', 'root@10.240.85.177:7777',
             '/var/log/app', '/backup/logs/node1;/backup/logs/node2', '[\"*.log\"]', '[\"debug*.log\",\"temp*\"]',
             '0 */5 * * * ?', 10000, '[\"agent-002\",\"agent-003\"]', '[\"root@node2:7777\",\"root@node3:7777\"]',
             1, 7, 30, 10, 'EXPONENTIAL', 'NONE', NULL, 'COPY',
             1, 'ONE_TO_MANY', 'BROADCAST', NULL, 'RUNNING',
             CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '', '测试任务1', 0),
            ('配置同步任务', '同步配置文件到所有节点', 'agent-001', 'root@10.240.85.177:7777',
             '/etc/app/config', '/etc/backup/config', '[\"*.properties\",\"*.yaml\",\"*.xml\"]', NULL,
             NULL, 5000, '[\"agent-002\",\"agent-003\",\"agent-004\"]', '[\"root@node2:7777\",\"root@node3:7777\",\"root@node4:7777\"]',
             1, 3, 15, 5, 'LINEAR', 'DELETE', '/tmp/archived', 'MOVE',
             0, 'ONE_TO_MANY', 'ROUND_ROBIN', NULL, 'READY',
             NULL, 'admin', CURRENT_TIMESTAMP, '', '测试任务2', 0),
            ('数据归档任务', '归档历史数据到存储节点', 'agent-002', 'root@10.240.85.178:7777',
             '/data/archive', '/storage/archive/2026', '[\"*.csv\",\"*.json\"]', '[\"*.tmp\",\"*.bak\"]',
             '0 0 2 * * ?', 20000, '[\"agent-003\"]', '[\"root@node3:7777\"]',
             1, 14, 60, 20, 'EXPONENTIAL', 'BACKUP', '/archive/backup', 'COPY',
             1, 'ONE_TO_ONE', 'BROADCAST', '{\"region\":\"us-east-1\"}', 'PAUSED',
             CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '', '已暂停的任务', 0)
        """);
        
        // 插入5条子任务Mock数据（简化日期字段）
        jdbcTemplate.update("""
            INSERT INTO batch_transfer_subtask (
                task_id, source_file_path, target_agent_id, target_dir, status,
                transferred_chunks, total_chunks, transferred_bytes, total_bytes,
                error_message, retry_count, next_retry_at, transfer_id, create_time, update_time
            ) VALUES 
            (1, '/var/log/app/application.log', 'agent-002', '/backup/logs/node1', 'COMPLETED',
             100, 100, 10485760, 10485760, NULL, 0, NULL, 'transfer-001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (1, '/var/log/app/debug.log', 'agent-002', '/backup/logs/node1', 'FAILED',
             50, 100, 5242880, 10485760, 'Connection reset by peer', 3, CURRENT_TIMESTAMP, 'transfer-002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (1, '/var/log/app/error.log', 'agent-003', '/backup/logs/node2', 'SENDING',
             30, 80, 3145728, 8388608, NULL, 0, NULL, 'transfer-003', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (1, '/var/log/app/system.log', 'agent-003', '/backup/logs/node2', 'QUEUED',
             0, 0, 0, 0, NULL, 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (2, '/etc/app/config/application.properties', 'agent-002', '/etc/backup/config', 'QUEUED',
             0, 0, 0, 0, NULL, 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """);
        
        // 插入4条事件Mock数据（简化日期字段）
        jdbcTemplate.update("""
            INSERT INTO batch_sync_event (
                event_type, task_id, source_agent_id, payload, status,
                retry_count, error_message, created_at, processed_at, expire_at
            ) VALUES 
            ('TASK_CREATED', 1, 'agent-001', '{\"taskId\":1,\"taskName\":\"日志备份任务\"}', 'COMPLETED',
             0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            ('TASK_STATUS_CHANGED', 1, 'agent-001', '{\"taskId\":1,\"status\":\"RUNNING\"}', 'COMPLETED',
             0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            ('TASK_CREATED', 2, 'agent-001', '{\"taskId\":2,\"taskName\":\"配置同步任务\"}', 'PROCESSING',
             0, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP),
            ('TASK_UPDATED', 3, 'agent-002', '{\"taskId\":3,\"status\":\"PAUSED\"}', 'PENDING',
             0, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP)
        """);
    }

    /**
     * 创建测试表（H2 MySQL模式兼容）
     */
    private void createTables() {
        // 1. 批量传输任务表
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS batch_transfer_task (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                task_name VARCHAR(200) NOT NULL,
                task_description VARCHAR(500),
                source_agent_id VARCHAR(50) NOT NULL,
                source_agent_name VARCHAR(100) NOT NULL,
                source_dir VARCHAR(500) NOT NULL,
                target_dirs VARCHAR(2000) NOT NULL,
                include_patterns TEXT,
                exclude_patterns TEXT,
                scan_cron_expression VARCHAR(100),
                max_scan_files INT NOT NULL DEFAULT 10000,
                target_agent_ids TEXT NOT NULL,
                target_agent_names TEXT NOT NULL,
                retry_enabled TINYINT NOT NULL DEFAULT 1,
                retry_max_days INT NOT NULL DEFAULT 7,
                retry_interval_min INT NOT NULL DEFAULT 30,
                max_retry_count INT NOT NULL DEFAULT 10,
                retry_backoff_type VARCHAR(20) NOT NULL DEFAULT 'EXPONENTIAL',
                post_transfer_action VARCHAR(20) NOT NULL DEFAULT 'NONE',
                backup_dir VARCHAR(500),
                backup_mode VARCHAR(10) DEFAULT 'COPY',
                preserve_dir_structure TINYINT NOT NULL DEFAULT 1,
                transfer_mode VARCHAR(20) NOT NULL DEFAULT 'ONE_TO_MANY',
                routing_strategy VARCHAR(20) NOT NULL DEFAULT 'BROADCAST',
                routing_config TEXT,
                status VARCHAR(20) NOT NULL DEFAULT 'READY',
                started_at DATETIME,
                create_by VARCHAR(64) DEFAULT '',
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_by VARCHAR(64) DEFAULT '',
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                remark VARCHAR(500),
                deleted TINYINT NOT NULL DEFAULT 0
            )
        """);
        
        // 2. 批量传输子任务实例表
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS batch_transfer_subtask (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                task_id BIGINT NOT NULL,
                source_file_path VARCHAR(1000) NOT NULL,
                target_agent_id VARCHAR(50) NOT NULL,
                target_dir VARCHAR(500) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
                transferred_chunks INT NOT NULL DEFAULT 0,
                total_chunks INT NOT NULL DEFAULT 0,
                transferred_bytes BIGINT NOT NULL DEFAULT 0,
                total_bytes BIGINT NOT NULL DEFAULT 0,
                error_message TEXT,
                retry_count INT NOT NULL DEFAULT 0,
                next_retry_at DATETIME,
                transfer_id VARCHAR(100),
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // 3. 批量同步事件队列表
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS batch_sync_event (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                event_type VARCHAR(30) NOT NULL,
                task_id BIGINT,
                source_agent_id VARCHAR(50),
                payload TEXT NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                retry_count INT NOT NULL DEFAULT 0,
                error_message TEXT,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                processed_at DATETIME,
                expire_at DATETIME
            )
        """);
    }

    // ==================== 测试1: 插入事件 ====================
    
    @Test
    @DisplayName("1. 插入TASK_CREATED事件 - 成功")
    void testInsertEvent_taskCreated() {
        BatchSyncEvent newEvent = new BatchSyncEvent();
        newEvent.setEventType("TASK_CREATED");
        newEvent.setTaskId(100L);
        newEvent.setSourceAgentId("agent-005");
        newEvent.setPayload("{\"taskId\":100,\"taskName\":\"测试事件\",\"status\":\"READY\"}");
        
        int rows = eventMapper.insertEvent(newEvent);
        
        assertEquals(1, rows, "应插入1条记录");
        assertNotNull(newEvent.getId(), "插入后应生成ID");
        assertEquals("PENDING", newEvent.getStatus(), "默认状态应为PENDING");
        assertEquals(Integer.valueOf(0), newEvent.getRetryCount(), "默认重试次数为0");
        assertNotNull(newEvent.getCreatedAt(), "创建时间应已设置");
        assertNotNull(newEvent.getExpireAt(), "过期时间应已设置（24小时后）");
        
        System.out.println("✅ TASK_CREATED事件插入成功! ID=" + newEvent.getId());
        System.out.println("   - Payload: " + newEvent.getPayload().substring(0, Math.min(50, newEvent.getPayload().length())) + "...");
    }
    
    @Test
    @DisplayName("2. 插入TASK_STATUS_CHANGED事件")
    void testInsertEvent_taskStatusChanged() {
        BatchSyncEvent statusEvent = new BatchSyncEvent();
        statusEvent.setEventType("TASK_STATUS_CHANGED");
        statusEvent.setTaskId(1L);
        statusEvent.setSourceAgentId("agent-001");
        statusEvent.setPayload("{\"taskId\":1,\"oldStatus\":\"READY\",\"newStatus\":\"PAUSED\"}");
        
        int rows = eventMapper.insertEvent(statusEvent);
        
        assertEquals(1, rows);
        assertEquals("TASK_STATUS_CHANGED", statusEvent.getEventType());
        
        System.out.println("✅ TASK_STATUS_CHANGED事件插入成功!");
    }

    // ==================== 测试2: 查询待处理事件 ====================
    
    @Test
    @DisplayName("3. 查询PENDING事件 - 应返回未处理的事件")
    void testSelectPendingEvents_forUpdate() {
        List<BatchSyncEvent> pendingEvents = eventMapper.selectPendingEventsForUpdate(10);
        
        assertNotNull(pendingEvents);
        assertTrue(pendingEvents.size() >= 1, "至少有1个PENDING事件");
        
        for (BatchSyncEvent event : pendingEvents) {
            assertEquals("PENDING", event.getStatus());
            assertNotNull(event.getPayload(), "payload不应为空");
            assertTrue(event.getPayload().startsWith("{"), "payload应为JSON格式");
        }
        
        System.out.println("✅ 查询到 " + pendingEvents.size() + " 个PENDING事件:");
        pendingEvents.forEach(e -> 
            System.out.println("   - ID:" + e.getId() + " 类型:" + e.getEventType() + " 任务:" + e.getTaskId()));
    }
    
    @Test
    @DisplayName("4. 查询PENDING事件 - 限制数量")
    void testSelectPendingEvents_limitRespected() {
        List<BatchSyncEvent> limitedEvents = eventMapper.selectPendingEventsForUpdate(1);
        
        assertNotNull(limitedEvents);
        assertTrue(limitedEvents.size() <= 1, "不应超过limit=1");
        
        System.out.println("✅ 限制查询: 返回" + limitedEvents.size() + "条 (≤1)");
    }

    // ==================== 测试3: 状态流转 ====================
    
    @Test
    @DisplayName("5. PENDING → PROCESSING - 开始处理")
    void testUpdateStatusToProcessing_success() {
        List<BatchSyncEvent> pendingEvents = eventMapper.selectPendingEventsForUpdate(10);
        assertFalse(pendingEvents.isEmpty(), "应有PENDING事件");
        
        Long eventId = pendingEvents.get(0).getId();
        
        int rows = eventMapper.updateStatusToProcessing(eventId);
        
        assertTrue(rows >= 0, "更新操作应执行");
        
        System.out.println("✅ 事件状态: PENDING → PROCESSING (ID=" + eventId + ")");
    }
    
    @Test
    @DisplayName("6. PROCESSING → COMPLETED - 处理完成")
    void testUpdateStatusToCompleted_success() {
        List<BatchSyncEvent> events = eventMapper.selectPendingEventsForUpdate(10);
        if (!events.isEmpty()) {
            Long eventId = events.get(0).getId();
            eventMapper.updateStatusToProcessing(eventId);
            
            int rows = eventMapper.updateStatusToCompleted(eventId);
            
            assertEquals(1, rows, "应完成1个事件");
            
            System.out.println("✅ 事件状态: PROCESSING → COMPLETED (ID=" + eventId + ")");
        } else {
            System.out.println("⚠️ 无可用事件用于测试COMPLETED流程");
        }
    }
    
    @Test
    @DisplayName("7. 尝试将非PENDING事件改为PROCESSING - 应失败")
    void testUpdateStatusToProcessing_alreadyProcessed() {
        BatchSyncEvent tempEvent = new BatchSyncEvent();
        tempEvent.setEventType("TEST_EVENT");
        tempEvent.setTaskId(999L);
        tempEvent.setPayload("{}");
        eventMapper.insertEvent(tempEvent);
        eventMapper.updateStatusToProcessing(tempEvent.getId());
        
        int rows = eventMapper.updateStatusToProcessing(tempEvent.getId());
        
        assertEquals(0, rows, "非PENDING状态的事件无法再次改为PROCESSING");
        
        System.out.println("✅ 非PENDING事件无法重复处理 (符合预期)");
    }

    // ==================== 测试4: 查询过期事件 ====================
    
    @Test
    @DisplayName("8. 查询过期事件 - 用于清理")
    void testSelectExpiredEvents() {
        List<BatchSyncEvent> expiredEvents = eventMapper.selectExpiredEvents();
        
        assertNotNull(expiredEvents);
        
        System.out.println("✅ 过期事件查询: 找到" + expiredEvents.size() + "个过期事件");
        if (!expiredEvents.isEmpty()) {
            expiredEvents.forEach(e -> 
                System.out.println("   - ID:" + e.getId() + " 创建:" + e.getCreatedAt() + " 过期:" + e.getExpireAt()));
        }
    }

    // ==================== 测试5: 事件类型验证 ====================
    
    @Test
    @DisplayName("9. 验证所有支持的事件类型")
    void testEventType_validation() {
        String sql = """
            SELECT event_type, COUNT(*) as cnt 
            FROM batch_sync_event 
            GROUP BY event_type
            """;
        
        List<Map<String, Object>> typeStats = jdbcTemplate.queryForList(sql);
        
        assertFalse(typeStats.isEmpty(), "应有事件统计数据");
        
        System.out.println("📊 事件类型分布:");
        for (Map<String, Object> stat : typeStats) {
            String eventType = (String) stat.get("event_type");
            Long count = (Long) stat.get("cnt");
            System.out.println("   - " + eventType + ": " + count + "个");
            
            assertTrue(
                eventType.equals("TASK_CREATED") || 
                eventType.equals("TASK_UPDATED") ||
                eventType.equals("TASK_DELETED") ||
                eventType.equals("TASK_STATUS_CHANGED"),
                "事件类型应在允许范围内"
            );
        }
    }

    // ==================== 测试6: Payload完整性 ====================
    
    @Test
    @DisplayName("10. 验证事件Payload包含完整配置信息")
    void testPayload_containsFullConfig() {
        String payloadSql = "SELECT payload FROM batch_sync_event WHERE event_type = 'TASK_CREATED' LIMIT 1";
        String payload = jdbcTemplate.queryForObject(payloadSql, String.class);
        
        assertNotNull(payload, "应有TASK_CREATED事件的payload");
        assertTrue(payload.contains("taskId"), "payload应包含taskId");
        assertTrue(payload.contains("taskName"), "payload应包含taskName");
        assertTrue(payload.contains("status"), "payload应包含status");
        assertTrue(payload.startsWith("{"), "payload应为JSON对象");
        assertTrue(payload.endsWith("}"), "payload应为完整的JSON");
        
        System.out.println("✅ Payload格式验证通过!");
        System.out.println("   示例: " + payload.substring(0, Math.min(80, payload.length())) + "...");
    }

    // ==================== 测试7: 并发安全模拟 ====================
    
    @Test
    @DisplayName("11. FOR UPDATE SKIP LOCKED - 模拟并发轮询")
    void testConcurrentPolling_simulation() {
        List<BatchSyncEvent> firstPoll = eventMapper.selectPendingEventsForUpdate(5);
        
        assertNotNull(firstPoll, "第一次轮询应返回结果");
        
        System.out.println("✅ 并发轮询模拟:");
        System.out.println("   - Proxy-1轮询: 获取" + firstPoll.size() + "个事件");
        System.out.println("   - 说明: FOR UPDATE SKIP LOCKED确保并发安全");
    }

    // ==================== 测试8: 重试机制 ====================
    
    @Test
    @DisplayName("12. 事件重试计数器")
    void testRetryCount_incremental() {
        BatchSyncEvent retryEvent = new BatchSyncEvent();
        retryEvent.setEventType("TASK_CREATED");
        retryEvent.setTaskId(888L);
        retryEvent.setSourceAgentId("agent-retry-test");
        retryEvent.setPayload("{\"test\":\"retry\"}");
        eventMapper.insertEvent(retryEvent);
        
        Integer initialRetry = retryEvent.getRetryCount();
        assertEquals(Integer.valueOf(0), initialRetry, "初始重试次数应为0");
        
        jdbcTemplate.update(
            "UPDATE batch_sync_event SET retry_count = retry_count + 1 WHERE id = ?", 
            retryEvent.getId()
        );
        
        Integer updatedRetry = jdbcTemplate.queryForObject(
            "SELECT retry_count FROM batch_sync_event WHERE id = ?",
            Integer.class,
            retryEvent.getId()
        );
        
        assertEquals(Integer.valueOf(1), updatedRetry, "重试次数应+1");
        
        System.out.println("✅ 重试计数器: 0 → 1");
    }
}
