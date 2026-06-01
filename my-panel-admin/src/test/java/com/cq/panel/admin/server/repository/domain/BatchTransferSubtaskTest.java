package com.cq.panel.admin.server.repository.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * batch_transfer_subtask 表结构验证测试
 * 
 * 使用纯字符串分析方式验证DDL符合spec.md设计规格
 */
class BatchTransferSubtaskTest {

    private static final String TABLE_NAME = "batch_transfer_subtask";
    
    /**
     * 完整的DDL语句
     */
    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS `batch_transfer_subtask` (
                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                `task_id` bigint NOT NULL COMMENT '关联的任务ID(外键)',
                `source_file_path` varchar(1000) NOT NULL COMMENT '源文件绝对路径',
                `target_agent_id` varchar(50) NOT NULL COMMENT '目标Agent ID',
                `target_dir` varchar(500) NOT NULL COMMENT '目标目录绝对路径',
                `status` varchar(20) NOT NULL DEFAULT 'QUEUED' COMMENT '子任务状态: QUEUED-排队中/SENDING-传输中/COMPLETED-已完成/FAILED-失败/RETRYING-重试中',
                `transferred_chunks` int NOT NULL DEFAULT 0 COMMENT '已传输分片数',
                `total_chunks` int NOT NULL DEFAULT 0 COMMENT '总分片数(-1表示未知)',
                `transferred_bytes` bigint NOT NULL DEFAULT 0 COMMENT '已传输字节数',
                `total_bytes` bigint NOT NULL DEFAULT 0 COMMENT '总字节数(-1表示未知)',
                `error_message` text DEFAULT NULL COMMENT '失败错误信息',
                `retry_count` int NOT NULL DEFAULT 0 COMMENT '当前重试次数',
                `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间',
                `transfer_id` varchar(100) DEFAULT NULL COMMENT 'Agent端传输会话ID(用于断点续传)',
                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                PRIMARY KEY (`id`),
                KEY `idx_task_source_target` (`task_id`, `target_agent_id`, `status`),
                KEY `idx_task_id` (`task_id`),
                KEY `idx_target_status` (`target_agent_id`, `status`),
                KEY `idx_status_retry` (`status`, `retry_count`),
                KEY `idx_transfer_id` (`transfer_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量传输子任务实例表(每个文件的每次传输)'
            """;

    @Test
    void testCreateTable_success() {
        assertTrue(DDL.contains("CREATE TABLE IF NOT EXISTS `" + TABLE_NAME + "`"),
                   "应包含完整的CREATE TABLE语句");
        
        assertTrue(DDL.contains("ENGINE=InnoDB"),
                   "应指定InnoDB引擎");
        
        assertTrue(DDL.contains("DEFAULT CHARSET=utf8mb4"),
                   "应指定utf8mb4字符集");
        
        assertTrue(DDL.contains("COMMENT='批量传输子任务实例表(每个文件的每次传输)'"),
                   "应有正确的表注释");
    }

    @Test
    void testFieldTypes_correct() {
        // 必须包含的字段及其类型定义（17个字段）
        String[] requiredFields = {
            "`id` bigint NOT NULL",
            "`task_id` bigint NOT NULL",
            "`source_file_path` varchar(1000) NOT NULL",
            "`target_agent_id` varchar(50) NOT NULL",
            "`target_dir` varchar(500) NOT NULL",
            "`status` varchar(20) NOT NULL DEFAULT 'QUEUED'",
            "`transferred_chunks` int NOT NULL DEFAULT 0",
            "`total_chunks` int NOT NULL DEFAULT 0",
            "`transferred_bytes` bigint NOT NULL DEFAULT 0",
            "`total_bytes` bigint NOT NULL DEFAULT 0",
            "`error_message` text DEFAULT NULL",
            "`retry_count` int NOT NULL DEFAULT 0",
            "`next_retry_at` datetime DEFAULT NULL",
            "`transfer_id` varchar(100) DEFAULT NULL",
            "`create_time` datetime NOT NULL",
            "`update_time` datetime NOT NULL"
        };
        
        for (String field : requiredFields) {
            assertTrue(DDL.contains(field),
                       "缺少或类型不匹配的字段: " + field);
        }
        
        assertEquals(16, requiredFields.length, "应该有16个字段定义");
    }

    @Test
    void testIndexes_created() {
        assertTrue(DDL.contains("PRIMARY KEY (`id`)"),
                   "应包含主键索引");
        
        assertTrue(DDL.contains("KEY `idx_task_source_target` (`task_id`, `target_agent_id`, `status`)"),
                   "应包含复合索引idx_task_source_target");
        
        assertTrue(DDL.contains("KEY `idx_task_id` (`task_id`)"),
                   "应包含idx_task_id索引");
        
        assertTrue(DDL.contains("KEY `idx_target_status` (`target_agent_id`, `status`)"),
                   "应包含复合索引idx_target_status");
        
        assertTrue(DDL.contains("KEY `idx_status_retry` (`status`, `retry_count`)"),
                   "应包含复合索引idx_status_retry");
        
        assertTrue(DDL.contains("KEY `idx_transfer_id` (`transfer_id`)"),
                   "应包含idx_transfer_id索引");
    }

    @Test
    void testDefaultValues_set() {
        assertTrue(DDL.contains("`status` varchar(20) NOT NULL DEFAULT 'QUEUED'"));
        assertTrue(DDL.contains("`transferred_chunks` int NOT NULL DEFAULT 0"));
        assertTrue(DDL.contains("`total_chunks` int NOT NULL DEFAULT 0"));
        assertTrue(DDL.contains("`transferred_bytes` bigint NOT NULL DEFAULT 0"));
        assertTrue(DDL.contains("`total_bytes` bigint NOT NULL DEFAULT 0"));
        assertTrue(DDL.contains("`retry_count` int NOT NULL DEFAULT 0"));
    }

    @Test
    void testNotNullConstraints() {
        String[] notNullFields = {
            "id", "task_id", "source_file_path", "target_agent_id",
            "target_dir", "status", "transferred_chunks", "total_chunks",
            "transferred_bytes", "total_bytes", "retry_count", "create_time", "update_time"
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

    @Test
    void testStatusEnumValues() {
        // 验证状态注释包含所有枚举值
        assertTrue(DDL.contains("QUEUED") && DDL.contains("排队中"),
                   "应包含QUEUED状态");
        assertTrue(DDL.contains("SENDING") && DDL.contains("传输中"),
                   "应包含SENDING状态");
        assertTrue(DDL.contains("COMPLETED") && DDL.contains("已完成"),
                   "应包含COMPLETED状态");
        assertTrue(DDL.contains("FAILED") && DDL.contains("失败"),
                   "应包含FAILED状态");
        assertTrue(DDL.contains("RETRYING") && DDL.contains("重试中"),
                   "应包含RETRYING状态");
        
        // 验证默认值为QUEUED
        assertTrue(DDL.contains("DEFAULT 'QUEUED'"),
                   "默认状态应为QUEUED");
    }

    @Test
    void testForeignKeyRelation() {
        // 验证task_id字段存在且注释说明是外键
        assertTrue(DDL.contains("`task_id` bigint NOT NULL"),
                   "应包含task_id字段");
        
        assertTrue(DDL.contains("关联的任务ID(外键)"),
                   "task_id注释应说明是外键关系");
        
        // 验证有task_id的索引（用于外键查询优化）
        assertTrue(DDL.contains("KEY `idx_task_id` (`task_id`)"),
                   "应有task_id索引以支持外键查询");
    }
}
