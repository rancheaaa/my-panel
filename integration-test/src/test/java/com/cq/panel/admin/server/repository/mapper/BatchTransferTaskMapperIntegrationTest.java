package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
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
 * BatchTransferTask Mapper 集成测试
 * 使用JdbcTemplate直接初始化数据（兼容所有数据库）
 */
@SpringBootTest
@Transactional
class BatchTransferTaskMapperIntegrationTest {

    @Autowired
    private BatchTransferTaskMapper taskMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        assertNotNull(taskMapper);
        assertNotNull(jdbcTemplate);
        
        // 清空并重新插入Mock数据（确保每个测试都是干净的状态）
        initMockData();
    }

    /**
     * 初始化Mock数据（代码内嵌）
     * 包含：建表语句(H2兼容) + 测试数据
     */
    private void initMockData() {
        // 1. 先创建表（H2兼容语法，去掉MySQL特定语法）
        createTables();
        
        // 2. 清空旧数据
        jdbcTemplate.execute("DELETE FROM batch_transfer_subtask");
        jdbcTemplate.execute("DELETE FROM batch_sync_event");
        jdbcTemplate.execute("DELETE FROM batch_transfer_task");
        
        // 3. 插入3条任务Mock数据（简化日期字段，避免DATEADD兼容问题）
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

    // ==================== 测试1: 查询所有任务 ====================
    
    @Test
    @DisplayName("1. 查询所有任务 - 应返回3条记录")
    void testSelectList_allTasks() {
        BatchTransferTask query = new BatchTransferTask();
        List<BatchTransferTask> tasks = taskMapper.selectList(query);
        
        assertNotNull(tasks);
        assertEquals(3, tasks.size(), "应返回3条任务记录");
        
        System.out.println("✅ 查询到 " + tasks.size() + " 条任务记录");
        tasks.forEach(t -> System.out.println("   - ID:" + t.getId() + " 名称:" + t.getTaskName()));
    }

    // ==================== 测试2: 根据ID查询 ====================
    
    @Test
    @DisplayName("2. 根据ID查询 - 存在的任务")
    void testSelectById_found() {
        // 先从列表中获取第一个任务的ID（不假设ID=1）
        List<BatchTransferTask> allTasks = taskMapper.selectList(new BatchTransferTask());
        assertFalse(allTasks.isEmpty(), "应有任务数据");
        
        Long firstTaskId = allTasks.get(0).getId();
        BatchTransferTask task = taskMapper.selectById(firstTaskId);
        
        assertNotNull(task, "任务不应为null");
        assertEquals(firstTaskId, task.getId());
        assertNotNull(task.getTaskName());
        assertNotNull(task.getSourceAgentId());
        assertNotNull(task.getSourceAgentName());  // ⭐ Agent Name
        assertEquals("/var/log/app", task.getSourceDir());  // 第一条数据的固定值
        
        System.out.println("✅ 成功查询到任务: " + task.getTaskName() + " (ID=" + firstTaskId + ")");
        System.out.println("   - 状态: " + task.getStatus());
        System.out.println("   - 源Agent: " + task.getSourceAgentName());
    }
    
    @Test
    @DisplayName("3. 根据ID查询 - 不存在的任务")
    void testSelectById_notFound() {
        BatchTransferTask task = taskMapper.selectById(9999L);
        assertNull(task, "不存在的任务应返回null");
        System.out.println("✅ 不存在的任务返回null (符合预期)");
    }
    
    @Test
    @DisplayName("4. 逻辑删除后查询不到")
    void testSelectById_deleted() {
        List<BatchTransferTask> allTasks = taskMapper.selectList(new BatchTransferTask());
        assertFalse(allTasks.isEmpty(), "应有任务数据可删除");
        
        Long taskIdToDelete = allTasks.get(0).getId();
        int rows = taskMapper.deleteById(taskIdToDelete);
        assertEquals(1, rows, "应删除1条记录");
        
        assertNull(taskMapper.selectById(taskIdToDelete), "删除后不应再查到");
        System.out.println("✅ 已删除的任务(ID=" + taskIdToDelete + ")无法查询到 (符合预期)");
    }

    // ==================== 测试3: 条件查询 ====================
    
    @Test
    @DisplayName("5. 按状态过滤 - RUNNING状态")
    void testSelectList_filterByStatusRunning() {
        BatchTransferTask query = new BatchTransferTask();
        query.setStatus("RUNNING");
        List<BatchTransferTask> tasks = taskMapper.selectList(query);
        
        assertEquals(1, tasks.size(), "只有1个RUNNING状态的任务");
        assertEquals("日志备份任务", tasks.get(0).getTaskName());
        System.out.println("✅ RUNNING状态查询: 找到 " + tasks.size() + " 条");
    }
    
    @Test
    @DisplayName("6. 按源Agent过滤")
    void testSelectList_filterBySourceAgent() {
        BatchTransferTask query = new BatchTransferTask();
        query.setSourceAgentId("agent-001");
        List<BatchTransferTask> tasks = taskMapper.selectList(query);
        
        assertEquals(2, tasks.size(), "agent-001应有2个任务");
        System.out.println("✅ agent-001查询: 找到 " + tasks.size() + " 条任务");
    }

    // ==================== 测试4: 插入操作 ====================
    
    @Test
    @DisplayName("7. 插入新任务成功")
    void testInsert_success() {
        BatchTransferTask newTask = new BatchTransferTask();
        newTask.setTaskName("集成测试任务");
        newTask.setSourceAgentId("agent-005");
        newTask.setSourceAgentName("root@10.240.85.180:7777");  // ⭐ Agent Name
        newTask.setSourceDir("/data/test");
        newTask.setTargetDirs("/backup/test");  // ✅ 必填字段
        newTask.setTargetAgentIds("[\"agent-006\"]");  // ✅ 必填字段
        newTask.setTargetAgentNames("[\"root@node6:7777\"]");  // ✅ 必填字段
        newTask.setStatus("READY");
        newTask.setMaxScanFiles(5000);
        newTask.setRetryEnabled(1);  // ✅ Integer类型
        newTask.setRetryMaxDays(7);  // ✅ 必填字段
        newTask.setRetryIntervalMin(30);  // ✅ 必填字段
        newTask.setMaxRetryCount(10);  // ✅ 必填字段
        newTask.setRetryBackoffType("EXPONENTIAL");  // ✅ 必填字段
        newTask.setPreserveDirStructure(1);  // ✅ Integer类型
        newTask.setTransferMode("ONE_TO_MANY");  // ✅ 必填字段
        newTask.setRoutingStrategy("BROADCAST");  // ✅ 必填字段
        newTask.setPostTransferAction("NONE");  // ✅ 必填字段
        newTask.setCreateBy("test-user");
        newTask.setCreateTime(new Date());
        newTask.setUpdateTime(new Date());
        
        int rows = taskMapper.insert(newTask);
        assertEquals(1, rows, "应插入1条记录");
        assertNotNull(newTask.getId(), "插入后应生成ID");
        
        BatchTransferTask inserted = taskMapper.selectById(newTask.getId());
        assertNotNull(inserted);
        assertEquals("集成测试任务", inserted.getTaskName());
        assertEquals("root@10.240.85.180:7777", inserted.getSourceAgentName());  // ⭐ 验证
        
        System.out.println("✅ 插入成功! 新任务ID: " + inserted.getId());
    }

    // ==================== 测试5: 更新操作 ====================
    
    @Test
    @DisplayName("8. 更新任务状态")
    void testUpdate_statusChange() {
        List<BatchTransferTask> allTasks = taskMapper.selectList(new BatchTransferTask());
        BatchTransferTask original = allTasks.stream()
            .filter(t -> "READY".equals(t.getStatus()) || "PAUSED".equals(t.getStatus()))
            .findFirst()
            .orElseGet(() -> allTasks.isEmpty() ? null : allTasks.get(0));
        assertNotNull(original, "应有可更新的任务");
        
        String originalStatus = original.getStatus();
        original.setStatus("RUNNING");
        original.setStartedAt(new Date());
        original.setUpdateBy("system-test");
        int rows = taskMapper.updateById(original);
        
        assertEquals(1, rows);
        System.out.println("✅ 更新成功! 任务状态: " + originalStatus + " → RUNNING (ID=" + original.getId() + ")");
    }

    // ==================== 测试6: 字段完整性验证 ====================
    
    @Test
    @DisplayName("9. 验证Agent Name字段映射正确")
    void testAgentNameFieldMapping() {
        List<BatchTransferTask> tasks = taskMapper.selectList(new BatchTransferTask());
        assertFalse(tasks.isEmpty(), "应有任务数据");
        
        BatchTransferTask task = tasks.get(0);
        
        assertNotNull(task.getSourceAgentName(), "source_agent_name字段应存在");
        assertTrue(task.getSourceAgentName().contains("@"), "格式应为user@ip:port");
        
        assertNotNull(task.getTargetAgentNames(), "target_agent_names字段应存在");
        assertTrue(task.getTargetAgentNames().contains("["), "应为JSON数组格式");
        
        System.out.println("✅ Agent Name字段验证:");
        System.out.println("   - 源节点: " + task.getSourceAgentName());
        System.out.println("   - 目标列表: " + task.getTargetAgentNames());
    }
    
    @Test
    @DisplayName("10. 验证废弃字段不存在")
    void testDeprecatedField_notExist() {
        try {
            jdbcTemplate.queryForMap("SELECT scan_frequency_sec FROM batch_transfer_task LIMIT 1");
            fail("应该抛出异常，因为scan_frequency_sec字段不存在");
        } catch (Exception e) {
            String errorMsg = e.getMessage().toLowerCase();
            System.out.println("实际异常消息: " + errorMsg);
            
            assertTrue(errorMsg.contains("column") || 
                       errorMsg.contains("not found") ||
                       errorMsg.contains("doesn't exist") ||
                       errorMsg.contains("unknown column") ||
                       errorMsg.contains("bad sql grammar"),
                       "异常消息应包含字段不存在信息。实际消息: " + errorMsg);
        }
        System.out.println("✅ 废弃字段scan_frequency_sec确实不存在");
    }

    // ==================== 测试7: 统计与复杂查询 ====================
    
    @Test
    @DisplayName("11. 组合条件查询")
    void testSelectList_combinedConditions() {
        BatchTransferTask query = new BatchTransferTask();
        query.setSourceAgentId("agent-001");
        query.setStatus("RUNNING");
        List<BatchTransferTask> tasks = taskMapper.selectList(query);
        
        assertEquals(1, tasks.size(), "只有1个任务同时满足两个条件");
        System.out.println("✅ 组合查询: agent-001 + RUNNING → " + tasks.size() + " 条");
    }
    
    @Test
    @DisplayName("12. 通过JdbcTemplate统计各状态数量")
    void testStatistics_viaJdbc() {
        List<Map<String, Object>> stats = jdbcTemplate.queryForList(
            "SELECT status, COUNT(*) as cnt FROM batch_transfer_task GROUP BY status"
        );
        
        assertFalse(stats.isEmpty());
        
        long totalCount = 0;
        for (Map<String, Object> stat : stats) {
            String status = (String) stat.get("status");
            Long count = (Long) stat.get("cnt");
            totalCount += count;
            System.out.println("   - " + status + ": " + count);
        }
        
        assertEquals(3, totalCount, "总计应有3条");
        System.out.println("📊 总计: " + totalCount + " 条任务");
    }
}
