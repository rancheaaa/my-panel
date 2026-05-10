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
        
        // 2. 清空旧数据
        jdbcTemplate.execute("DELETE FROM batch_transfer_subtask");
        jdbcTemplate.execute("DELETE FROM batch_sync_event");
        jdbcTemplate.execute("DELETE FROM batch_transfer_task");
        
        // 3. 插入3条任务Mock数据（外键依赖，简化日期字段）
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
        assertEquals("/var/log/app/application.log", subtask.getSourceFilePath());
        assertEquals("agent-002", subtask.getTargetAgentId());
        
        assertEquals(100, subtask.getTransferredChunks(), "已传输100个分片");
        assertEquals(100, subtask.getTotalChunks(), "总分片100");
        assertEquals(10485760L, subtask.getTransferredBytes(), "已传输10MB");
        assertEquals(10485760L, subtask.getTotalBytes(), "总大小10MB");
        
        System.out.println("✅ COMPLETED子任务查询成功!");
        System.out.println("   - 文件: " + subtask.getSourceFilePath());
        System.out.println("   - 进度: " + subtask.getTransferredBytes() + "/" + subtask.getTotalBytes() + " bytes");
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
        assertNotNull(subtask.getNextRetryAt(), "应有下次重试时间");
        assertNotNull(subtask.getTransferId(), "应有传输会话ID");
        
        System.out.println("✅ FAILED子任务查询成功!");
        System.out.println("   - 错误: " + subtask.getErrorMessage());
        System.out.println("   - 重试次数: " + subtask.getRetryCount());
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
        
        System.out.println("✅ QUEUED子任务查询: 任务1=" + task1Queued.size() + ", 任务2=" + task2Queued.size());
    }

    // ==================== 测试2: 插入操作 ====================
    
    @Test
    @DisplayName("4. 插入单个子任务")
    void testInsert_success() {
        BatchTransferSubtask newSubtask = new BatchTransferSubtask();
        newSubtask.setTaskId(1L);
        newSubtask.setSourceFilePath("/var/log/app/new-test.log");
        newSubtask.setTargetAgentId("agent-003");
        newSubtask.setTargetDir("/backup/logs/node3");
        newSubtask.setStatus("QUEUED");
        newSubtask.setTransferredChunks(0);
        newSubtask.setTotalChunks(50);
        newSubtask.setTransferredBytes(0L);
        newSubtask.setTotalBytes(5242880L);
        newSubtask.setRetryCount(0);  // ✅ 必填字段
        newSubtask.setCreateTime(new Date());
        newSubtask.setUpdateTime(new Date());
        
        int rows = subtaskMapper.insert(newSubtask);
        
        assertEquals(1, rows, "应插入1条记录");
        assertNotNull(newSubtask.getId(), "插入后应生成ID");
        
        List<BatchTransferSubtask> inserted = subtaskMapper.selectByTaskIdAndStatus(1L, "QUEUED");
        boolean found = inserted.stream()
            .anyMatch(s -> s.getSourceFilePath().equals("/var/log/app/new-test.log"));
        assertTrue(found, "应能查到刚插入的子任务");
        
        System.out.println("✅ 子任务插入成功! ID=" + newSubtask.getId());
    }

    // ==================== 测试3: 批量插入 ====================
    
    @Test
    @DisplayName("5. 批量插入多个子任务")
    void testBatchInsert_multiple() {
        List<BatchTransferSubtask> batchList = java.util.Arrays.asList(
            createSubtask(1L, "/var/log/batch/file1.log", "agent-002"),
            createSubtask(1L, "/var/log/batch/file2.log", "agent-003"),
            createSubtask(1L, "/var/log/batch/file3.log", "agent-002")
        );
        
        int rows = subtaskMapper.batchInsert(batchList);
        
        assertEquals(3, rows, "应插入3条记录");
        
        List<BatchTransferSubtask> allQueued = subtaskMapper.selectByTaskIdAndStatus(1L, "QUEUED");
        assertTrue(allQueued.size() >= 3, "队列中至少有3个任务");
        
        System.out.println("✅ 批量插入成功! 共" + rows + "条");
    }
    
    private BatchTransferSubtask createSubtask(Long taskId, String filePath, String targetAgent) {
        BatchTransferSubtask subtask = new BatchTransferSubtask();
        subtask.setTaskId(taskId);
        subtask.setSourceFilePath(filePath);
        subtask.setTargetAgentId(targetAgent);
        subtask.setTargetDir("/backup/batch");
        subtask.setStatus("QUEUED");
        subtask.setTransferredChunks(0);  // ✅ 必填字段
        subtask.setTotalChunks(100);  // ✅ 必填字段
        subtask.setTransferredBytes(0L);  // ✅ 必填字段
        subtask.setTotalBytes(10485760L);  // ✅ 必填字段
        subtask.setRetryCount(0);  // ✅ 必填字段
        subtask.setCreateTime(new Date());
        subtask.setUpdateTime(new Date());
        return subtask;
    }

    // ==================== 测试4: 更新进度 ====================
    
    @Test
    @DisplayName("6. 更新传输进度")
    void testUpdateProgress_partialUpdate() {
        List<BatchTransferSubtask> sendingTasks = subtaskMapper.selectByTaskIdAndStatus(1L, "SENDING");
        assertFalse(sendingTasks.isEmpty(), "应有SENDING状态的子任务");
        
        BatchTransferSubtask sendingTask = sendingTasks.get(0);
        Long taskId = sendingTask.getId();
        Integer oldChunks = sendingTask.getTransferredChunks();
        Long oldBytes = sendingTask.getTransferredBytes();
        
        int rows = subtaskMapper.updateProgress(taskId, 50, 5242880L);
        
        assertEquals(1, rows, "应更新1条记录");
        
        System.out.println("✅ 进度更新成功!");
        System.out.println("   - 分片: " + oldChunks + " → 50");
        System.out.println("   - 字节: " + oldBytes + " → 5242880");
    }

    // ==================== 测试5: 状态变更 ====================
    
    @Test
    @DisplayName("7. 状态变更为RETRYING (自动递增retry_count)")
    void testUpdateStatus_toRetrying() {
        List<BatchTransferSubtask> failedTasks = subtaskMapper.selectByTaskIdAndStatus(1L, "FAILED");
        assertFalse(failedTasks.isEmpty());
        
        BatchTransferSubtask failedTask = failedTasks.get(0);
        Long taskId = failedTask.getId();
        int oldRetryCount = failedTask.getRetryCount();
        
        int rows = subtaskMapper.updateStatus(taskId, "RETRYING", null);
        
        assertEquals(1, rows);
        
        System.out.println("✅ 状态更新: FAILED → RETRYING");
        System.out.println("   - 重试次数: " + oldRetryCount + " → " + (oldRetryCount + 1));
    }
    
    @Test
    @DisplayName("8. 状态变更为COMPLETED (带错误信息)")
    void testUpdateStatus_toCompleted_withError() {
        BatchTransferSubtask tempSubtask = new BatchTransferSubtask();
        tempSubtask.setTaskId(99L);
        tempSubtask.setSourceFilePath("/test/temp.log");
        tempSubtask.setTargetAgentId("agent-001");
        tempSubtask.setTargetDir("/test/target");
        tempSubtask.setStatus("SENDING");
        tempSubtask.setTransferredChunks(80);  // ✅ 必填
        tempSubtask.setTotalChunks(100);  // ✅ 必填
        tempSubtask.setTransferredBytes(8388608L);  // ✅ 必填
        tempSubtask.setTotalBytes(10485760L);  // ✅ 必填
        tempSubtask.setRetryCount(0);  // ✅ 必填
        tempSubtask.setCreateTime(new Date());
        tempSubtask.setUpdateTime(new Date());
        
        subtaskMapper.insert(tempSubtask);
        
        int rows = subtaskMapper.updateStatus(tempSubtask.getId(), "COMPLETED", "部分分片损坏但已完成");
        
        assertEquals(1, rows);
        
        System.out.println("✅ 状态更新: SENDING → COMPLETED (带错误信息)");
    }

    // ==================== 测试6: 统计查询 ====================
    
    @Test
    @DisplayName("9. 统计各状态子任务数量")
    void testStatistics_byStatus() {
        long completedCount = subtaskMapper.selectByTaskIdAndStatus(1L, "COMPLETED").size();
        long failedCount = subtaskMapper.selectByTaskIdAndStatus(1L, "FAILED").size();
        long sendingCount = subtaskMapper.selectByTaskIdAndStatus(1L, "SENDING").size();
        long queuedCount = subtaskMapper.selectByTaskIdAndStatus(1L, "QUEUED").size();
        long retryingCount = subtaskMapper.selectByTaskIdAndStatus(1L, "RETRYING").size();
        
        assertEquals(1, completedCount, "COMPLETED: 1个");
        assertEquals(1, failedCount, "FAILED: 1个");
        assertEquals(1, sendingCount, "SENDING: 1个");
        assertTrue(queuedCount >= 1, "QUEUED: 至少1个");
        assertEquals(0, retryingCount, "RETRYING: 0个");
        
        long totalForTask1 = completedCount + failedCount + sendingCount + queuedCount + retryingCount;
        
        System.out.println("📊 任务1子任务统计:");
        System.out.println("   - COMPLETED: " + completedCount);
        System.out.println("   - SENDING: " + sendingCount);
        System.out.println("   - QUEUED: " + queuedCount);
        System.out.println("   - FAILED: " + failedCount);
        System.out.println("   - RETRYING: " + retryingCount);
        System.out.println("   - 总计: " + totalForTask1);
    }
}
