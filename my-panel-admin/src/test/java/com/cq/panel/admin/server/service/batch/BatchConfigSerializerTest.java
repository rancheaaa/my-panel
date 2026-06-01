package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BatchConfigSerializer单元测试
 * 验证Admin实体到Agent公共Bean的序列化转换
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
        @DisplayName("1. 完整任务对象基础序列化")
        void testSerialize_basicObject() {
            BatchTransferTask task = new BatchTransferTask();
            task.setTaskName("日志备份任务");
            task.setStatus("RUNNING");

            String json = serializer.serialize(task);

            assertNotNull(json, "序列化结果不应为null");
            assertTrue(json.startsWith("{"), "应以{开头");
            assertTrue(json.endsWith("}"), "应以}结尾");
            assertTrue(json.contains("\"taskName\""), "应包含taskName字段");
            assertTrue(json.contains("\"日志备份任务\""), "应包含任务名称值");

            System.out.println("✅ 基础序列化成功:");
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

    // ==================== Agent格式序列化测试 (核心功能) ====================

    @Nested
    @DisplayName("Agent格式转换与序列化")
    class AgentFormatSerializationTests {

        @Test
        @DisplayName("3. 完整任务转换为Agent格式")
        void testSerializeForAgent_fullConversion() throws Exception {
            BatchTransferTask task = createFullTask();

            String json = serializer.serializeForAgent(task);

            assertNotNull(json, "Agent格式序列化结果不应为null");

            // 验证基本字段
            assertTrue(json.contains("\"taskId\":1") || json.contains("\"taskId\": 1"), "应包含taskId");
            assertTrue(json.contains("\"日志备份任务\""), "应包含任务名称");
            assertTrue(json.contains("\"RUNNING\""), "应包含状态");

            // 验证嵌套配置对象
            assertTrue(json.contains("\"scanConfig\""), "应包含scanConfig对象");
            assertTrue(json.contains("\"transferConfig\""), "应包含transferConfig对象");
            assertTrue(json.contains("\"retryConfig\""), "应包含retryConfig对象");
            assertTrue(json.contains("\"targetAgents\""), "应包含targetAgents数组");

            // 验证嵌套配置内容
            assertTrue(json.contains("\"cronExpression\""), "scanConfig应包含cronExpression");
            assertTrue(json.contains("\"routingStrategy\""), "transferConfig应包含routingStrategy");
            assertTrue(json.contains("\"enabled\""), "retryConfig应包含enabled");
            assertTrue(json.contains("\"agentId\""), "targetAgents应包含agentId");

            System.out.println("✅ Agent格式转换成功:");
            System.out.println(json);
        }

        @Test
        @DisplayName("4. Agent格式 - ScanConfig正确构建")
        void testSerializeForAgent_scanConfig() throws Exception {
            BatchTransferTask task = createFullTask();
            task.setScanCronExpression("0 */10 * * * ?");
            task.setMaxScanFiles(50000);

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config.getScanConfig());
            assertEquals("0 */10 * * * ?", config.getScanConfig().getCronExpression());
            assertEquals(50000, config.getScanConfig().getMaxScanFiles());

            System.out.println("✅ ScanConfig构建验证通过");
        }

        @Test
        @DisplayName("5. Agent格式 - TransferConfig正确构建")
        void testSerializeForAgent_transferConfig() throws Exception {
            BatchTransferTask task = createFullTask();
            task.setRoutingStrategy("ROUND_ROBIN");
            task.setPreserveDirStructure(1);
            task.setPostTransferAction("DELETE_SOURCE");

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config.getTransferConfig());
            assertEquals("ROUND_ROBIN", config.getTransferConfig().getRoutingStrategy());
            assertTrue(config.getTransferConfig().isPreserveDirStructure());
            assertEquals("DELETE_SOURCE", config.getTransferConfig().getPostTransferAction());

            System.out.println("✅ TransferConfig构建验证通过");
        }

        @Test
        @DisplayName("6. Agent格式 - RetryConfig正确构建")
        void testSerializeForAgent_retryConfig() throws Exception {
            BatchTransferTask task = createFullTask();
            task.setRetryEnabled(1);
            task.setRetryMaxDays(14);
            task.setRetryIntervalMin(60);
            task.setMaxRetryCount(20);
            task.setRetryBackoffType("LINEAR");

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config.getRetryConfig());
            assertTrue(config.getRetryConfig().isEnabled());
            assertEquals(14, config.getRetryConfig().getMaxDays());
            assertEquals(60, config.getRetryConfig().getIntervalMin());
            assertEquals(20, config.getRetryConfig().getMaxRetryCount());
            assertEquals("LINEAR", config.getRetryConfig().getBackoffType());

            System.out.println("✅ RetryConfig构建验证通过");
        }

        @Test
        @DisplayName("7. Agent格式 - TargetAgents正确构建")
        void testSerializeForAgent_targetAgents() throws Exception {
            BatchTransferTask task = createFullTask();
            task.setTargetDirs("/data/node1;/data/node2;/data/node3");
            task.setTargetAgentIds("[\"agent-002\",\"agent-003\",\"agent-004\"]");
            task.setTargetAgentNames("[\"root@node2:7777\",\"root@node3:7777\",\"root@node4:7777\"]");

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config.getTargetAgents());
            assertEquals(3, config.getTargetAgents().size(), "应有3个目标Agent");

            // 验证第一个目标
            assertEquals("agent-002", config.getTargetAgents().get(0).getAgentId());
            assertEquals("root@node2:7777", config.getTargetAgents().get(0).getAgentName());
            assertEquals("/data/node1", config.getTargetAgents().get(0).getTargetDir());

            // 验证第二个目标
            assertEquals("agent-003", config.getTargetAgents().get(1).getAgentId());

            System.out.println("✅ TargetAgents构建验证通过: count=" + config.getTargetAgents().size());
        }

        @Test
        @DisplayName("8. Agent格式 - 文件模式解析")
        void testSerializeForAgent_filePatterns() throws Exception {
            BatchTransferTask task = createFullTask();
            task.setIncludePatterns("[\"*.log\",\"*.txt\",\"*.csv\"]");
            task.setExcludePatterns("[\"debug*\",\"temp*\",\"*.bak\"]");

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config.getIncludePatterns());
            assertNotNull(config.getExcludePatterns());
            assertEquals(3, config.getIncludePatterns().size(), "应有3个包含模式");
            assertEquals(3, config.getExcludePatterns().size(), "应有3个排除模式");
            assertTrue(config.getIncludePatterns().contains("*.log"));
            assertTrue(config.getExcludePatterns().contains("debug*"));

            System.out.println("✅ 文件模式解析验证通过");
        }
    }

    // ==================== 特殊类型处理测试 ====================

    @Nested
    @DisplayName("特殊数据类型处理")
    class SpecialTypeHandlingTests {

        @Test
        @DisplayName("9. Date时间字段格式化")
        void testSerializeForAgent_dateFormatting() throws Exception {
            BatchTransferTask task = createFullTask();
            Date startedAt = new Date();
            task.setStartedAt(startedAt);
            task.setUpdateTime(new Date());

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config.getStartedAt(), "startedAt不应为null");
            assertTrue(config.getStartedAt().contains("T"), "startedAt应为ISO-8601格式");
            assertNotNull(config.getVersion(), "version不应为null");
            assertTrue(config.getVersion().matches("\\d{14}"), "version应为yyyyMMddHHmmss格式的时间戳");

            System.out.println("✅ 日期格式化验证通过: startedAt=" + config.getStartedAt() + ", version=" + config.getVersion());
        }

        @Test
        @DisplayName("10. Null值字段处理")
        void testSerializeForAgent_nullValues() throws Exception {
            BatchTransferTask task = new BatchTransferTask();
            task.setId(999L);
            task.setTaskName(null);
            task.setStatus(null);
            task.setSourceDir(null);
            task.setUpdateTime(new Date());

            String json = serializer.serializeForAgent(task);

            assertNotNull(json);
            assertTrue(json.length() > 0, "null值不应导致异常");

            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);
            assertEquals(999L, config.getTaskId());

            System.out.println("✅ null值处理成功");
        }

        @Test
        @DisplayName("11. 禁用重试配置")
        void testSerializeForAgent_retryDisabled() throws Exception {
            BatchTransferTask task = createFullTask();
            task.setRetryEnabled(0);

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertFalse(config.getRetryConfig().isEnabled(), "重试应为禁用状态");

            System.out.println("✅ 禁用重试配置验证通过");
        }

        @Test
        @DisplayName("12. 禁用目录结构保留")
        void testSerializeForAgent_noPreserveDirStructure() throws Exception {
            BatchTransferTask task = createFullTask();
            task.setPreserveDirStructure(0);

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertFalse(config.getTransferConfig().isPreserveDirStructure(), "不应保留目录结构");

            System.out.println("✅ 目录结构保留禁用验证通过");
        }
    }

    // ==================== 反序列化测试 ====================

    @Nested
    @DisplayName("反序列化功能")
    class DeserializationTests {

        @Test
        @DisplayName("13. JSON反序列化为AgentTaskConfig")
        void testDeserialize_toAgentTaskConfig() {
            String json = """
                    {
                        "taskId": 1001,
                        "taskName": "测试任务",
                        "status": "READY",
                        "sourceDir": "/data/test",
                        "scanConfig": {
                            "cronExpression": "0 */5 * * * ?",
                            "maxScanFiles": 5000
                        },
                        "transferConfig": {
                            "routingStrategy": "BROADCAST"
                        },
                        "retryConfig": {
                            "enabled": true,
                            "maxRetryCount": 10
                        }
                    }
                    """;

            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config);
            assertEquals(1001L, config.getTaskId());
            assertEquals("测试任务", config.getTaskName());
            assertEquals("READY", config.getStatus());
            assertEquals("/data/test", config.getSourceDir());
            assertNotNull(config.getScanConfig());
            assertEquals("0 */5 * * * ?", config.getScanConfig().getCronExpression());
            assertEquals(5000, config.getScanConfig().getMaxScanFiles());

            System.out.println("✅ 反序列化为AgentTaskConfig成功");
        }

        @Test
        @DisplayName("14. 无效JSON处理")
        void testDeserialize_invalidJson() {
            assertThrows(BatchConfigSerializer.DeserializationException.class, () -> {
                serializer.deserialize("{invalid json", AgentTaskConfig.class);
            }, "无效JSON应抛出DeserializationException");

            System.out.println("✅ 无效JSON正确抛出异常");
        }

        @Test
        @DisplayName("15. 空JSON处理")
        void testDeserialize_emptyJson() {
            assertThrows(BatchConfigSerializer.DeserializationException.class, () -> {
                serializer.deserialize("", AgentTaskConfig.class);
            }, "空字符串应抛出异常");

            System.out.println("✅ 空JSON正确抛出异常");
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件处理")
    class EdgeCaseTests {

        @Test
        @DisplayName("16. 目标Agent数量不匹配时处理")
        void testSerializeForAgent_mismatchedTargetCounts() throws Exception {
            BatchTransferTask task = createFullTask();
            task.setTargetDirs("/dir1;/dir2"); // 2个目录
            task.setTargetAgentIds("[\"agent-001\"]"); // 1个ID
            task.setTargetAgentNames("[\"name1\",\"name2\",\"name3\"]"); // 3个名称

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config.getTargetAgents());
            assertEquals(3, config.getTargetAgents().size(), "应以最大数量为准");

            System.out.println("✅ 数量不匹配边界情况处理通过");
        }

        @Test
        @DisplayName("17. 空的目标列表处理")
        void testSerializeForAgent_emptyTargets() throws Exception {
            BatchTransferTask task = createFullTask();
            task.setTargetDirs("");
            task.setTargetAgentIds("");
            task.setTargetAgentNames("");

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config.getTargetAgents());
            assertTrue(config.getTargetAgents().isEmpty() || config.getTargetAgents().get(0).getAgentId().isEmpty(),
                    "空目标应返回空列表或空元素");

            System.out.println("✅ 空目标列表处理通过");
        }

        @Test
        @DisplayName("18. 版本号时间戳格式验证")
        void testSerializeForAgent_versionFormat() throws Exception {
            BatchTransferTask task = createFullTask();
            Date updateTime = new Date(); // 当前时间
            task.setUpdateTime(updateTime);

            String json = serializer.serializeForAgent(task);
            AgentTaskConfig config = serializer.deserialize(json, AgentTaskConfig.class);

            assertNotNull(config.getVersion());
            assertEquals(14, config.getVersion().length(), "版本号应为14位数字(yyyyMMddHHmmss)");

            System.out.println("✅ 版本号格式验证通过: version=" + config.getVersion());
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
        task.setUpdateTime(new Date());
        task.setCreateBy("admin");
        task.setCreateTime(new Date());
        task.setRemark("完整测试任务");
        return task;
    }
}
