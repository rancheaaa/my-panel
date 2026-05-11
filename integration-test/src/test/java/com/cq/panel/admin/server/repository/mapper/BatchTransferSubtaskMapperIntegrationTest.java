package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BatchTransferSubtask Mapper 集成测试
 * 使用JdbcTemplate直接初始化数据（兼容所有数据库）
 */
@SpringBootTest
@Transactional
class BatchTransferSubtaskMapperIntegrationTest {

    @Autowired
    private BatchTransferSubtaskMapper subtaskMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        assertNotNull(subtaskMapper);
        assertNotNull(jdbcTemplate);
        initMockData();
    }

    private void initMockData() {
        createTables();

        jdbcTemplate.execute("DELETE FROM batch_transfer_subtask");
        jdbcTemplate.execute("DELETE FROM batch_sync_event");
        jdbcTemplate.execute("DELETE FROM batch_transfer_task");

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
             '/var/log/app', '/backup/logs/node1;/backup/logs/node2', '["*.log"]', '["debug*.log","temp*"]',
             '0 */5 * * * ?', 10000, '["agent-002","agent-003"]', '["root@node2:7777","root@node3:7777"]',
             1, 7, 30, 10, 'EXPONENTIAL', 'NONE', NULL, 'COPY',
             1, 'ONE_TO_MANY', 'BROADCAST', NULL, 'RUNNING',
             CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, 'admin', '测试任务1', 0),
            ('配置同步任务', '同步配置文件到所有节点', 'agent-001', 'root@10.240.85.177:7777',
             '/etc/app/config', '/etc/backup/config', '["*.properties","*.yaml","*.xml"]', NULL,
             NULL, 5000, '["agent-002","agent-003","agent-004"]', '["root@node2:7777","root@node3:7777","root@node4:7777"]',
             1, 3, 15, 5, 'LINEAR', 'DELETE', '/tmp/archived', 'MOVE',
             0, 'ONE_TO_MANY', 'ROUND_ROBIN', NULL, 'READY',
             NULL, 'admin', CURRENT_TIMESTAMP, 'admin', '测试任务2', 0)
        """);

        jdbcTemplate.update("""
            INSERT INTO batch_transfer_subtask (
                task_id, source_agent_id, source_agent_name, target_agent_id, target_agent_name,
                source_path, target_path, file_name, file_size_bytes, file_last_modified,
                status, transfer_id, transferred_chunks, total_chunks, transferred_bytes,
                speed_bytes_per_sec, started_at, completed_at, duration_ms,
                error_code, error_message, error_stack_trace,
                retry_count, last_retry_at, next_retry_after,
                create_by, create_time, update_by, update_time, remark
            ) VALUES
            (1, 'agent-001', 'root@10.240.85.177:7777', 'agent-002', 'root@node2:7777',
             '/var/log/app/application.log', '/backup/logs/node1/application.log', 'application.log', 10485760, CURRENT_TIMESTAMP,
             'COMPLETED', 'transfer-001', 100, 100, 10485760,
             5242880, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 20000,
             NULL, NULL, NULL,
             0, NULL, NULL,
             'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, NULL),
            (1, 'agent-001', 'root@10.240.85.177:7777', 'agent-002', 'root@node2:7777',
             '/var/log/app/debug.log', '/backup/logs/node1/debug.log', 'debug.log', 10485760, CURRENT_TIMESTAMP,
             'FAILED', 'transfer-002', 50, 100, 5242880,
             NULL, CURRENT_TIMESTAMP, NULL, NULL,
             'CONN_RESET', 'Connection reset by peer', NULL,
             3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
             'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, NULL),
            (1, 'agent-001', 'root@10.240.85.177:7777', 'agent-003', 'root@node3:7777',
             '/var/log/app/error.log', '/backup/logs/node2/error.log', 'error.log', 8388608, CURRENT_TIMESTAMP,
             'SENDING', 'transfer-003', 30, 80, 3145728,
             1048576, CURRENT_TIMESTAMP, NULL, NULL,
             NULL, NULL, NULL,
             0, NULL, NULL,
             'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, NULL),
            (1, 'agent-001', 'root@10.240.85.177:7777', 'agent-003', 'root@node3:7777',
             '/var/log/app/system.log', '/backup/logs/node2/system.log', 'system.log', 2097152, CURRENT_TIMESTAMP,
             'QUEUED', NULL, 0, 0, 0,
             NULL, NULL, NULL, NULL,
             NULL, NULL, NULL,
             0, NULL, NULL,
             'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, NULL),
            (2, 'agent-001', 'root@10.240.85.177:7777', 'agent-002', 'root@node2:7777',
             '/etc/app/config/application.properties', '/etc/backup/config/application.properties', 'application.properties', 4096, CURRENT_TIMESTAMP,
             'QUEUED', NULL, 0, 0, 0,
             NULL, NULL, NULL, NULL,
             NULL, NULL, NULL,
             0, NULL, NULL,
             'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, NULL)
        """);

        jdbcTemplate.update("""
            INSERT INTO batch_sync_event (
                event_type, task_id, source_agent_id, payload, status,
                retry_count, error_message, created_at, processed_at, expire_at
            ) VALUES
            ('TASK_CREATED', 1, 'agent-001', '{"taskId":1,"taskName":"日志备份任务"}', 'COMPLETED',
             0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            ('TASK_STATUS_CHANGED', 1, 'agent-001', '{"taskId":1,"status":"RUNNING"}', 'COMPLETED',
             0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """);
    }

    private void createTables() {
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

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS batch_transfer_subtask (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                task_id BIGINT NOT NULL,
                source_agent_id VARCHAR(50) NOT NULL,
                source_agent_name VARCHAR(100),
                target_agent_id VARCHAR(50) NOT NULL,
                target_agent_name VARCHAR(100),
                source_path VARCHAR(1000) NOT NULL,
                target_path VARCHAR(1000) NOT NULL,
                file_name VARCHAR(255) NOT NULL,
                file_size_bytes BIGINT NOT NULL,
                file_last_modified DATETIME,
                status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
                transfer_id VARCHAR(100),
                transferred_chunks INT NOT NULL DEFAULT 0,
                total_chunks INT NOT NULL DEFAULT 0,
                transferred_bytes BIGINT NOT NULL DEFAULT 0,
                speed_bytes_per_sec BIGINT,
                started_at DATETIME,
                completed_at DATETIME,
                duration_ms BIGINT,
                error_code VARCHAR(50),
                error_message TEXT,
                error_stack_trace TEXT,
                retry_count INT NOT NULL DEFAULT 0,
                last_retry_at DATETIME,
                next_retry_after DATETIME,
                create_by VARCHAR(64) DEFAULT '',
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_by VARCHAR(64) DEFAULT '',
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                remark VARCHAR(500)
            )
        """);

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

    // ==================== 测试1: 查询子任务 ====================

    @Test
    @DisplayName("1. 根据任务ID和状态查询 - COMPLETED状态")
    void testSelectByTaskIdAndStatus_completed() {
        List<BatchTransferSubtask> subtasks = subtaskMapper.selectByTaskIdAndStatus(1L, "COMPLETED");

        assertNotNull(subtasks);
        assertEquals(1, subtasks.size(), "任务1应有1个COMPLETED子任务");

        BatchTransferSubtask subtask = subtasks.get(0);
        assertEquals(1L, subtask.getTaskId());
        assertEquals("COMPLETED", subtask.getStatus());
        assertEquals("/var/log/app/application.log", subtask.getSourcePath());
        assertEquals("agent-002", subtask.getTargetAgentId());

        assertEquals(100, subtask.getTransferredChunks(), "已传输100个分片");
        assertEquals(100, subtask.getTotalChunks(), "总分片100");
        assertEquals(10485760L, subtask.getTransferredBytes(), "已传输10MB");
        assertEquals(10485760L, subtask.getFileSizeBytes(), "文件大小10MB");
    }

    @Test
    @DisplayName("2. 根据任务ID和状态查询 - FAILED状态")
    void testSelectByTaskIdAndStatus_failed() {
        List<BatchTransferSubtask> subtasks = subtaskMapper.selectByTaskIdAndStatus(1L, "FAILED");

        assertNotNull(subtasks);
        assertEquals(1, subtasks.size());

        BatchTransferSubtask subtask = subtasks.get(0);
        assertEquals("FAILED", subtask.getStatus());
        assertNotNull(subtask.getErrorMessage(), "失败原因不应为空");
        assertEquals("Connection reset by peer", subtask.getErrorMessage());
        assertEquals(3, subtask.getRetryCount(), "已重试3次");
        assertNotNull(subtask.getTransferId(), "应有传输会话ID");
    }

    @Test
    @DisplayName("3. 根据任务ID和状态查询 - QUEUED状态")
    void testSelectByTaskIdAndStatus_queued() {
        List<BatchTransferSubtask> task1Queued = subtaskMapper.selectByTaskIdAndStatus(1L, "QUEUED");
        List<BatchTransferSubtask> task2Queued = subtaskMapper.selectByTaskIdAndStatus(2L, "QUEUED");

        assertEquals(1, task1Queued.size(), "任务1有1个排队中的子任务");
        assertEquals(1, task2Queued.size(), "任务2有1个排队中的子任务");

        for (BatchTransferSubtask subtask : task1Queued) {
            assertEquals(0, subtask.getTransferredChunks());
            assertEquals(0, subtask.getTotalChunks());
            assertNull(subtask.getTransferId(), "排队中无传输会话ID");
        }
    }

    // ==================== 测试2: 插入操作 ====================

    @Test
    @DisplayName("4. 插入单个子任务")
    void testInsert_success() {
        BatchTransferSubtask newSubtask = new BatchTransferSubtask();
        newSubtask.setTaskId(1L);
        newSubtask.setSourceAgentId("agent-001");
        newSubtask.setSourceAgentName("root@10.240.85.177:7777");
        newSubtask.setTargetAgentId("agent-003");
        newSubtask.setTargetAgentName("root@node3:7777");
        newSubtask.setSourcePath("/var/log/app/new-test.log");
        newSubtask.setTargetPath("/backup/logs/node3/new-test.log");
        newSubtask.setFileName("new-test.log");
        newSubtask.setFileSizeBytes(5242880L);
        newSubtask.setStatus("QUEUED");
        newSubtask.setTransferredChunks(0);
        newSubtask.setTotalChunks(50);
        newSubtask.setTransferredBytes(0L);
        newSubtask.setRetryCount(0);

        int rows = subtaskMapper.insert(newSubtask);

        assertEquals(1, rows, "应插入1条记录");
        assertNotNull(newSubtask.getId(), "插入后应生成ID");

        List<BatchTransferSubtask> inserted = subtaskMapper.selectByTaskIdAndStatus(1L, "QUEUED");
        boolean found = inserted.stream()
            .anyMatch(s -> "/var/log/app/new-test.log".equals(s.getSourcePath()));
        assertTrue(found, "应能查到刚插入的子任务");
    }

    // ==================== 测试3: 批量插入 ====================

    @Test
    @DisplayName("5. 批量插入多个子任务")
    void testBatchInsert_multiple() {
        List<BatchTransferSubtask> batchList = java.util.Arrays.asList(
            createSubtask(1L, "/var/log/batch/file1.log", "file1.log", "agent-002"),
            createSubtask(1L, "/var/log/batch/file2.log", "file2.log", "agent-003"),
            createSubtask(1L, "/var/log/batch/file3.log", "file3.log", "agent-002")
        );

        int rows = subtaskMapper.batchInsert(batchList);

        assertEquals(3, rows, "应插入3条记录");

        List<BatchTransferSubtask> allQueued = subtaskMapper.selectByTaskIdAndStatus(1L, "QUEUED");
        assertTrue(allQueued.size() >= 3, "队列中至少有3个任务");
    }

    private BatchTransferSubtask createSubtask(Long taskId, String sourcePath, String fileName, String targetAgent) {
        BatchTransferSubtask subtask = new BatchTransferSubtask();
        subtask.setTaskId(taskId);
        subtask.setSourceAgentId("agent-001");
        subtask.setSourceAgentName("root@10.240.85.177:7777");
        subtask.setTargetAgentId(targetAgent);
        subtask.setTargetAgentName("root@" + targetAgent + ":7777");
        subtask.setSourcePath(sourcePath);
        subtask.setTargetPath("/backup/batch/" + fileName);
        subtask.setFileName(fileName);
        subtask.setFileSizeBytes(10485760L);
        subtask.setStatus("QUEUED");
        subtask.setTransferredChunks(0);
        subtask.setTotalChunks(100);
        subtask.setTransferredBytes(0L);
        subtask.setRetryCount(0);
        return subtask;
    }

    // ==================== 测试4: 更新进度 ====================

    @Test
    @DisplayName("6. 更新传输进度")
    void testUpdateProgress_partialUpdate() {
        List<BatchTransferSubtask> sendingTasks = subtaskMapper.selectByTaskIdAndStatus(1L, "SENDING");
        assertFalse(sendingTasks.isEmpty(), "应有SENDING状态的子任务");

        BatchTransferSubtask sendingTask = sendingTasks.get(0);
        Long subtaskId = sendingTask.getId();

        int rows = subtaskMapper.updateProgress(subtaskId, 50, 5242880L, 1048576L);

        assertEquals(1, rows, "应更新1条记录");
    }

    // ==================== 测试5: 状态变更 ====================

    @Test
    @DisplayName("7. 状态变更为RETRYING (自动递增retry_count)")
    void testUpdateStatus_toRetrying() {
        List<BatchTransferSubtask> failedTasks = subtaskMapper.selectByTaskIdAndStatus(1L, "FAILED");
        assertFalse(failedTasks.isEmpty());

        BatchTransferSubtask failedTask = failedTasks.get(0);
        Long subtaskId = failedTask.getId();
        int oldRetryCount = failedTask.getRetryCount();

        int rows = subtaskMapper.updateStatus(subtaskId, "RETRYING", null, null, null, null,
            new Date(System.currentTimeMillis() + 3600000));

        assertEquals(1, rows);
    }

    @Test
    @DisplayName("8. 状态变更为COMPLETED")
    void testUpdateStatus_toCompleted() {
        List<BatchTransferSubtask> sendingTasks = subtaskMapper.selectByTaskIdAndStatus(1L, "SENDING");
        assertFalse(sendingTasks.isEmpty());

        BatchTransferSubtask sendingTask = sendingTasks.get(0);

        int rows = subtaskMapper.updateStatus(sendingTask.getId(), "COMPLETED", null, null, null, 20000L, null);

        assertEquals(1, rows);
    }

    // ==================== 测试6: 按任务ID统计 ====================

    @Test
    @DisplayName("9. 按任务ID统计各状态子任务数量")
    void testCountByTaskIdGroupByStatus() {
        List<java.util.Map<String, Object>> stats = subtaskMapper.countByTaskIdGroupByStatus(1L);

        assertNotNull(stats);
        assertFalse(stats.isEmpty(), "任务1应有子任务统计");

        long total = 0;
        for (java.util.Map<String, Object> row : stats) {
            total += ((Number) row.get("count")).longValue();
        }
        assertEquals(4, total, "任务1应有4个子任务");
    }
}
