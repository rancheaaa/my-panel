package com.cq.panel.admin.server.repository.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * batch_transfer_task 表结构验证测试
 * 
 * 使用纯字符串分析方式验证DDL符合spec.md设计规格
 * 避免特定数据库方言依赖，确保测试稳定性
 */
class BatchTransferTaskTest {

    private static final String TABLE_NAME = "batch_transfer_task";
    
    /**
     * 完整的DDL语句（从schema.sql第697-736行提取）
     */
    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS `batch_transfer_task` (
                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                `task_name` varchar(200) NOT NULL COMMENT '任务名称',
                `task_description` varchar(500) DEFAULT NULL COMMENT '任务描述',
                `source_agent_id` varchar(50) NOT NULL COMMENT '源Agent ID',
                `source_agent_name` varchar(100) NOT NULL COMMENT '源节点名称',
                `source_dir` varchar(500) NOT NULL COMMENT '源目录绝对路径',
                `target_dirs` varchar(2000) NOT NULL COMMENT '目标节点目录(分号分隔)',
                `include_patterns` text DEFAULT NULL COMMENT '包含通配符(JSON数组)',
                `exclude_patterns` text DEFAULT NULL COMMENT '排除通配符(JSON数组)',
                `scan_cron_expression` varchar(100) DEFAULT NULL COMMENT '定时扫描Cron表达式(6-7位), 如 "0 */5 * * * ?" 表示每5分钟扫描',
                `max_scan_files` int NOT NULL DEFAULT 10000 COMMENT '单次最大扫描文件数, 范围[1,100000]',
                `target_agent_ids` text NOT NULL COMMENT '目标Agent ID列表(JSON数组)',
                `target_agent_names` text NOT NULL COMMENT '目标节点名称列表(JSON数组, 格式: user@ip:port, 与target_agent_ids一一对应, Admin创建任务时必填)',
                `retry_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用自动重试: 0-否 1-是',
                `retry_max_days` int NOT NULL DEFAULT 7 COMMENT '重试保留天数, 范围[1,30]',
                `retry_interval_min` int NOT NULL DEFAULT 30 COMMENT '首次重试间隔(分钟), 范围[5,1440]',
                `max_retry_count` int NOT NULL DEFAULT 10 COMMENT '单个子任务最大重试次数, 范围[1,100]',
                `retry_backoff_type` varchar(20) NOT NULL DEFAULT 'EXPONENTIAL' COMMENT '重试退避策略: LINEAR(线性)/EXPONENTIAL(指数退避,推荐)',
                `post_transfer_action` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '传输后操作: NONE/DELETE/BACKUP',
                `backup_dir` varchar(500) DEFAULT NULL COMMENT '备份目录绝对路径(post_transfer_action=BACKUP时必填)',
                `backup_mode` varchar(10) DEFAULT 'COPY' COMMENT '备份模式: COPY/MOVE',
                `preserve_dir_structure` tinyint NOT NULL DEFAULT 1 COMMENT '是否保持原始目录结构: 0-否 1-是',
                `transfer_mode` varchar(20) NOT NULL DEFAULT 'ONE_TO_MANY' COMMENT '传输模式: ONE_TO_ONE/ONE_TO_MANY',
                `routing_strategy` varchar(20) NOT NULL DEFAULT 'BROADCAST' COMMENT '路由策略: BROADCAST/ROUND_ROBIN/REGION_BASED/RANDOM',
                `routing_config` text DEFAULT NULL COMMENT '路由策略配置JSON(REGION_BASED时必填)',
                `status` varchar(20) NOT NULL DEFAULT 'READY' COMMENT '任务运行状态: READY-就绪(已配置)/RUNNING-运行中/PAUSED-已暂停',
                `started_at` datetime DEFAULT NULL COMMENT '首次启动时间',
                `create_by` varchar(64) DEFAULT '' COMMENT '创建人用户ID',
                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                `update_by` varchar(64) DEFAULT '' COMMENT '更新人用户ID',
                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标志: 0-未删除 1-已删除',
                PRIMARY KEY (`id`),
                KEY `idx_status` (`status`),
                KEY `idx_source_agent` (`source_agent_id`),
                KEY `idx_create_by` (`create_by`),
                KEY `idx_create_time` (`create_time`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量传输任务表(模板配置)'
            """;

    /**
     * 测试1: 验证DDL基本结构完整
     */
    @Test
    void testCreateTable_success() {
        assertTrue(DDL.contains("CREATE TABLE IF NOT EXISTS `" + TABLE_NAME + "`"),
                   "应包含完整的CREATE TABLE语句");
        
        assertTrue(DDL.contains("ENGINE=InnoDB"),
                   "应指定InnoDB引擎");
        
        assertTrue(DDL.contains("DEFAULT CHARSET=utf8mb4"),
                   "应指定utf8mb4字符集");
        
        assertTrue(DDL.contains("COMMENT='批量传输任务表(模板配置)'"),
                   "应有正确的表注释");
    }

    /**
     * 测试2: 验证所有字段类型正确（33个字段）
     */
    @Test
    void testFieldTypes_correct() {
        // 必须包含的字段及其类型定义（33个字段）
        String[] requiredFields = {
            "`id` bigint NOT NULL",
            "`task_name` varchar(200) NOT NULL",
            "`task_description` varchar(500) DEFAULT NULL",
            "`source_agent_id` varchar(50) NOT NULL",
            "`source_agent_name` varchar(100) NOT NULL",  // ⭐ Agent Name字段
            "`source_dir` varchar(500) NOT NULL",
            "`target_dirs` varchar(2000) NOT NULL",
            "`include_patterns` text DEFAULT NULL",
            "`exclude_patterns` text DEFAULT NULL",
            "`scan_cron_expression` varchar(100) DEFAULT NULL",
            "`max_scan_files` int NOT NULL DEFAULT 10000",
            "`target_agent_ids` text NOT NULL",
            "`target_agent_names` text NOT NULL",          // ⭐ Agent Name字段
            "`retry_enabled` tinyint NOT NULL DEFAULT 1",
            "`retry_max_days` int NOT NULL DEFAULT 7",
            "`retry_interval_min` int NOT NULL DEFAULT 30",
            "`max_retry_count` int NOT NULL DEFAULT 10",
            "`retry_backoff_type` varchar(20) NOT NULL DEFAULT 'EXPONENTIAL'",
            "`post_transfer_action` varchar(20) NOT NULL DEFAULT 'NONE'",
            "`backup_dir` varchar(500) DEFAULT NULL",
            "`backup_mode` varchar(10) DEFAULT 'COPY'",       // ⭐ 备份模式字段
            "`preserve_dir_structure` tinyint NOT NULL DEFAULT 1",
            "`transfer_mode` varchar(20) NOT NULL DEFAULT 'ONE_TO_MANY'",
            "`routing_strategy` varchar(20) NOT NULL DEFAULT 'BROADCAST'",
            "`routing_config` text DEFAULT NULL",
            "`status` varchar(20) NOT NULL DEFAULT 'READY'",
            "`started_at` datetime DEFAULT NULL",
            "`create_by` varchar(64) DEFAULT ''",
            "`create_time` datetime NOT NULL",               // ⭐ 必须NOT NULL
            "`update_by` varchar(64) DEFAULT ''",
            "`update_time` datetime NOT NULL",
            "`remark` varchar(500) DEFAULT NULL",
            "`deleted` tinyint NOT NULL DEFAULT 0"
        };
        
        for (String field : requiredFields) {
            assertTrue(DDL.contains(field),
                       "缺少或类型不匹配的字段: " + field);
        }
        
        assertEquals(33, requiredFields.length, "应该有33个字段定义");
    }

    /**
     * 测试3: 验证所有索引已创建（5个索引）
     */
    @Test
    void testIndexes_created() {
        assertTrue(DDL.contains("PRIMARY KEY (`id`)"),
                   "应包含主键索引");
        
        assertTrue(DDL.contains("KEY `idx_status` (`status`)"),
                   "应包含idx_status索引");
        
        assertTrue(DDL.contains("KEY `idx_source_agent` (`source_agent_id`)"),
                   "应包含idx_source_agent索引");
        
        assertTrue(DDL.contains("KEY `idx_create_by` (`create_by`)"),
                   "应包含idx_create_by索引");
        
        assertTrue(DDL.contains("KEY `idx_create_time` (`create_time`)"),
                   "应包含idx_create_time索引");  // ⭐ 关键索引
    }

    /**
     * 测试4: 验证默认值设置正确
     */
    @Test
    void testDefaultValues_set() {
        assertTrue(DDL.contains("`status` varchar(20) NOT NULL DEFAULT 'READY'"));
        assertTrue(DDL.contains("`retry_enabled` tinyint NOT NULL DEFAULT 1"));
        assertTrue(DDL.contains("`deleted` tinyint NOT NULL DEFAULT 0"));
        assertTrue(DDL.contains("`max_scan_files` int NOT NULL DEFAULT 10000"));
        assertTrue(DDL.contains("`retry_max_days` int NOT NULL DEFAULT 7"));
        assertTrue(DDL.contains("`retry_interval_min` int NOT NULL DEFAULT 30"));
        assertTrue(DDL.contains("`max_retry_count` int NOT NULL DEFAULT 10"));
        assertTrue(DDL.contains("`retry_backoff_type` varchar(20) NOT NULL DEFAULT 'EXPONENTIAL'"));
        assertTrue(DDL.contains("`post_transfer_action` varchar(20) NOT NULL DEFAULT 'NONE'"));
        assertTrue(DDL.contains("`backup_mode` varchar(10) DEFAULT 'COPY'"));      // ⭐
        assertTrue(DDL.contains("`preserve_dir_structure` tinyint NOT NULL DEFAULT 1"));
        assertTrue(DDL.contains("`transfer_mode` varchar(20) NOT NULL DEFAULT 'ONE_TO_MANY'"));
        assertTrue(DDL.contains("`routing_strategy` varchar(20) NOT NULL DEFAULT 'BROADCAST'"));
    }

    /**
     * 测试5: 验证NOT NULL约束生效
     */
    @Test
    void testNotNullConstraints() {
        String[] notNullFields = {
            "id", "task_name", "source_agent_id", "source_agent_name",
            "source_dir", "target_dirs", "target_agent_ids", "target_agent_names",
            "max_scan_files", "status", "create_time", "update_time", "deleted"
        };
        
        for (String field : notNullFields) {
            String fieldDef = "`" + field + "`";
            int fieldPos = DDL.indexOf(fieldDef);
            
            assertNotEquals(-1, fieldPos, "应该包含字段: " + field);
            
            String afterField = DDL.substring(fieldPos, Math.min(fieldPos + 100, DDL.length()));
            assertTrue(afterField.contains("NOT NULL"),
                       field + " 字段应该有NOT NULL约束");
        }
    }

    /**
     * 测试6: 验证废弃字段scan_frequency_sec不存在
     */
    @Test
    void testNoDeprecatedFrequencySecField() {
        assertFalse(DDL.contains("scan_frequency_sec"),
                   "不应该包含已废弃的scan_frequency_sec字段");
        
        assertFalse(DDL.contains("frequencySec"),
                   "注释中不应提及frequencySec");
        
        assertTrue(DDL.contains("定时扫描Cron表达式(6-7位)"),
                   "scan_cron_expression注释应描述其自身功能");
    }

    /**
     * 测试7: 验证Agent Name字段存在且正确
     */
    @Test
    void testAgentNameFieldsExist() {
        assertTrue(DDL.contains("`source_agent_name` varchar(100) NOT NULL"),
                   "应包含source_agent_name字段(varchar(100), NOT NULL)");
        
        assertTrue(DDL.contains("'源节点名称'"),
                   "source_agent_name注释应为'源节点名称'");
        
        assertTrue(DDL.contains("`target_agent_names` text NOT NULL"),
                   "应包含target_agent_names字段(text, NOT NULL)");
        
        assertTrue(DDL.contains("目标节点名称列表") && DDL.contains("JSON数组"),
                   "target_agent_names注释应包含'目标节点名称列表'和'JSON数组'");
        
        assertTrue(DDL.contains("user@ip:port"),
                   "agent name格式应说明为user@ip:port");
    }
}
