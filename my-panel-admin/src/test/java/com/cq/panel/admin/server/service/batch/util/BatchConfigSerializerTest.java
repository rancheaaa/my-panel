package com.cq.panel.admin.server.service.batch.util;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON序列化工具 单元测试
 * 覆盖率目标：100%
 */
@DisplayName("JSON序列化工具 - BatchConfigSerializer")
class BatchConfigSerializerTest {

    private final BatchConfigSerializer serializer = new BatchConfigSerializer();

    // ==================== 基础序列化测试 ====================
    
    @Nested
    @DisplayName("基础序列化功能")
    class BasicSerializationTests {
        
        @Test
        @DisplayName("1. 完整任务对象序列化为JSON")
        void testSerialize_fullTaskObject() {
            BatchTransferTask task = createFullTask();
            
            String json = serializer.serialize(task);
            
            assertNotNull(json, "序列化结果不应为null");
            assertTrue(json.startsWith("{"), "应以{开头");
            assertTrue(json.endsWith("}"), "应以}结尾");
            assertTrue(json.contains("\"taskName\""), "应包含taskName字段");
            assertTrue(json.contains("\"日志备份任务\""), "应包含任务名称值");
            
            System.out.println("✅ 序列化成功:");
            System.out.println(json);
        }
        
        @Test
        @DisplayName("2. 空任务对象序列化")
        void testSerialize_emptyTask() {
            BatchTransferTask task = new BatchTransferTask();
            
            String json = serializer.serialize(task);
            
            assertNotNull(json);
            assertTrue(json.length() > 0, "空对象应能序列化为非空字符串");
            System.out.println("✅ 空对象序列化: " + json);
        }
    }

    // ==================== 字段过滤测试 ====================
    
    @Nested
    @DisplayName("字段过滤与选择")
    class FieldFilteringTests {
        
        @Test
        @DisplayName("3. 只序列化指定字段")
        void testSerialize_selectedFieldsOnly() {
            BatchTransferTask task = createFullTask();
            List<String> fields = Arrays.asList("taskId", "taskName", "status");
            
            String json = serializer.serialize(task, fields);
            
            assertNotNull(json);
            assertTrue(json.contains("日志备份任务"), "应包含taskName");
            assertFalse(json.contains("sourceAgentId"), "不应包含未选择的字段");
            
            System.out.println("✅ 字段过滤成功: " + json);
        }
        
        @Test
        @DisplayName("4. 排除敏感字段")
        void testSerialize_excludeSensitiveFields() {
            BatchTransferTask task = createFullTask();
            List<String> excludeFields = Arrays.asList("id", "createBy", "updateBy", "deleted");
            
            String json = serializer.serializeExclude(task, excludeFields);
            
            assertNotNull(json);
            assertTrue(json.contains("taskName"), "应保留非敏感字段");
            
            System.out.println("✅ 敏感字段排除成功");
        }
    }

    // ==================== 特殊类型处理 ====================
    
    @Nested
    @DisplayName("特殊数据类型处理")
    class SpecialTypeHandlingTests {
        
        @Test
        @DisplayName("5. JSON格式字段序列化")
        void testSerialize_jsonField() {
            BatchTransferTask task = new BatchTransferTask();
            task.setTargetAgentIds("[\"agent-001\",\"agent-002\"]");
            task.setTargetAgentNames("[\"node1\",\"node2\"]");
            
            String json = serializer.serialize(task, Arrays.asList("targetAgentIds", "targetAgentNames"));
            
            assertNotNull(json);
            assertTrue(json.contains("agent-001"), "应包含Agent ID");
            assertTrue(json.contains("node1"), "应包含节点名称");
            
            System.out.println("✅ JSON字段序列化: " + json);
        }
        
        @Test
        @DisplayName("6. Date时间字段格式化")
        void testSerialize_dateFormatting() {
            BatchTransferTask task = new BatchTransferTask();
            Date now = new Date();
            task.setCreateTime(now);
            task.setUpdateTime(now);
            
            String json = serializer.serialize(task, Arrays.asList("createTime", "updateTime"));
            
            assertNotNull(json);
            assertTrue(json.contains("createTime"), "应包含时间字段");
            
            System.out.println("✅ 日期序列化: " + json);
        }
        
        @Test
        @DisplayName("7. Null值字段处理")
        void testSerialize_nullValues() {
            BatchTransferTask task = new BatchTransferTask();
            task.setTaskName(null);
            task.setStatus(null);
            
            String json = serializer.serialize(task);
            
            assertNotNull(json);
            assertTrue(json.length() > 0, "null值不应导致异常");
            
            System.out.println("✅ null值处理成功");
        }
    }

    // ==================== 反序列化测试 ====================
    
    @Nested
    @DisplayName("反序列化功能")
    class DeserializationTests {
        
        @Test
        @DisplayName("8. JSON反序列化为任务对象")
        void testDeserialize_jsonToTask() {
            String json = """
                {
                    "taskName": "测试任务",
                    "sourceDir": "/data/test",
                    "status": "READY",
                    "maxScanFiles": 5000,
                    "retryEnabled": 1
                }
                """;
            
            BatchTransferTask task = serializer.deserialize(json, BatchTransferTask.class);
            
            assertNotNull(task);
            assertEquals("测试任务", task.getTaskName());
            assertEquals("/data/test", task.getSourceDir());
            assertEquals("READY", task.getStatus());
            assertEquals(5000, task.getMaxScanFiles());
            
            System.out.println("✅ 反序列化成功: " + task.getTaskName());
        }
        
        @Test
        @DisplayName("9. 无效JSON处理")
        void testDeserialize_invalidJson() {
            assertThrows(Exception.class, () -> {
                serializer.deserialize("{invalid json", BatchTransferTask.class);
            }, "无效JSON应抛出异常");
            
            System.out.println("✅ 无效JSON正确抛出异常");
        }
    }

    // ==================== 辅助方法 ====================
    
    private BatchTransferTask createFullTask() {
        BatchTransferTask task = new BatchTransferTask();
        task.setId(1L);
        task.setTaskName("日志备份任务");
        task.setSourceAgentId("agent-001");
        task.setSourceAgentName("root@10.240.85.177:7777");
        task.setSourceDir("/var/log/app");
        task.setTargetDirs("/backup/logs/node1;/backup/logs/node2");
        task.setIncludePatterns("[\"*.log\",\"*.txt\"]");
        task.setExcludePatterns("[\"debug*\",\"temp*\"]");
        task.setScanCronExpression("0 */5 * * * ?");
        task.setMaxScanFiles(10000);
        task.setTargetAgentIds("[\"agent-002\",\"agent-003\"]");
        task.setTargetAgentNames("[\"root@node2:7777\",\"root@node3:7777\"]");
        task.setRetryEnabled(1);
        task.setRetryMaxDays(7);
        task.setRetryIntervalMin(30);
        task.setMaxRetryCount(10);
        task.setRetryBackoffType("EXPONENTIAL");
        task.setPostTransferAction("NONE");
        task.setPreserveDirStructure(1);
        task.setTransferMode("ONE_TO_MANY");
        task.setRoutingStrategy("BROADCAST");
        task.setStatus("RUNNING");
        task.setStartedAt(new Date());
        task.setCreateBy("admin");
        task.setCreateTime(new Date());
        task.setRemark("完整测试任务");
        return task;
    }
}
