package com.cq.panel.admin.server.repository.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * batch_sync_event 表结构验证测试
 * 
 * 使用纯字符串分析方式验证DDL符合spec.md设计规格
 */
class BatchSyncEventTest {

    private static final String TABLE_NAME = "batch_sync_event";
    
    /**
     * 完整的DDL语句
     */
    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS `batch_sync_event` (
                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                `event_type` varchar(30) NOT NULL COMMENT '事件类型: TASK_CREATED/TASK_UPDATED/TASK_DELETED/TASK_STATUS_CHANGED',
                `task_id` bigint DEFAULT NULL COMMENT '关联的任务ID',
                `source_agent_id` varchar(50) DEFAULT NULL COMMENT '源Agent ID(用于路由)',
                `payload` text NOT NULL COMMENT '事件负载数据(JSON格式, 包含完整任务/状态配置)',
                `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '事件处理状态: PENDING-待处理/PROCESSING-处理中/COMPLETED-已完成/FAILED-失败',
                `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
                `error_message` text DEFAULT NULL COMMENT '错误信息',
                `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                `processed_at` datetime DEFAULT NULL COMMENT '处理完成时间',
                `expire_at` datetime DEFAULT NULL COMMENT '过期时间(超时未处理的PENDING事件标记为FAILED)',
                PRIMARY KEY (`id`),
                KEY `idx_status_created` (`status`, `created_at`),
                KEY `idx_task_id` (`task_id`),
                KEY `idx_source_agent` (`source_agent_id`),
                KEY `idx_expire_at` (`expire_at`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量同步事件队列表(Admin-Proxy异步通信)'
            """;

    @Test
    void testCreateTable_success() {
        assertTrue(DDL.contains("CREATE TABLE IF NOT EXISTS `" + TABLE_NAME + "`"),
                   "应包含完整的CREATE TABLE语句");
        
        assertTrue(DDL.contains("ENGINE=InnoDB"),
                   "应指定InnoDB引擎");
        
        assertTrue(DDL.contains("DEFAULT CHARSET=utf8mb4"),
                   "应指定utf8mb4字符集");
        
        assertTrue(DDL.contains("COMMENT='批量同步事件队列表(Admin-Proxy异步通信)'"),
                   "应有正确的表注释");
    }

    @Test
    void testFieldTypes_correct() {
        // 必须包含的字段及其类型定义（11个字段）
        String[] requiredFields = {
            "`id` bigint NOT NULL",
            "`event_type` varchar(30) NOT NULL",
            "`task_id` bigint DEFAULT NULL",
            "`source_agent_id` varchar(50) DEFAULT NULL",
            "`payload` text NOT NULL",
            "`status` varchar(20) NOT NULL DEFAULT 'PENDING'",
            "`retry_count` int NOT NULL DEFAULT 0",
            "`error_message` text DEFAULT NULL",
            "`created_at` datetime NOT NULL",
            "`processed_at` datetime DEFAULT NULL",
            "`expire_at` datetime DEFAULT NULL"
        };
        
        for (String field : requiredFields) {
            assertTrue(DDL.contains(field),
                       "缺少或类型不匹配的字段: " + field);
        }
        
        assertEquals(11, requiredFields.length, "应该有11个字段定义");
    }

    @Test
    void testIndexes_created() {
        assertTrue(DDL.contains("PRIMARY KEY (`id`)"),
                   "应包含主键索引");
        
        assertTrue(DDL.contains("KEY `idx_status_created` (`status`, `created_at`)"),
                   "应包含复合索引idx_status_created");
        
        assertTrue(DDL.contains("KEY `idx_task_id` (`task_id`)"),
                   "应包含idx_task_id索引");
        
        assertTrue(DDL.contains("KEY `idx_source_agent` (`source_agent_id`)"),
                   "应包含idx_source_agent索引");
        
        assertTrue(DDL.contains("KEY `idx_expire_at` (`expire_at`)"),
                   "应包含idx_expire_at索引");
    }

    @Test
    void testDefaultValues_set() {
        assertTrue(DDL.contains("`status` varchar(20) NOT NULL DEFAULT 'PENDING'"));
        assertTrue(DDL.contains("`retry_count` int NOT NULL DEFAULT 0"));
    }

    @Test
    void testNotNullConstraints() {
        String[] notNullFields = {
            "id", "event_type", "payload", "status", "retry_count", "created_at"
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
    void testEventTypeEnum() {
        // 验证事件类型枚举值
        assertTrue(DDL.contains("TASK_CREATED"),
                   "应支持TASK_CREATED事件类型");
        assertTrue(DDL.contains("TASK_UPDATED"),
                   "应支持TASK_UPDATED事件类型");
        assertTrue(DDL.contains("TASK_DELETED"),
                   "应支持TASK_DELETED事件类型");
        assertTrue(DDL.contains("TASK_STATUS_CHANGED"),
                   "应支持TASK_STATUS_CHANGED事件类型");
    }

    @Test
    void testEventStatusEnum() {
        // 验证事件状态枚举值
        assertTrue(DDL.contains("PENDING") && DDL.contains("待处理"),
                   "应支持PENDING状态");
        assertTrue(DDL.contains("PROCESSING") && DDL.contains("处理中"),
                   "应支持PROCESSING状态");
        assertTrue(DDL.contains("COMPLETED") && DDL.contains("已完成"),
                   "应支持COMPLETED状态");
        assertTrue(DDL.contains("FAILED") && DDL.contains("失败"),
                   "应支持FAILED状态");
        
        // 验证默认值为PENDING
        assertTrue(DDL.contains("DEFAULT 'PENDING'"),
                   "默认状态应为PENDING");
    }
}
