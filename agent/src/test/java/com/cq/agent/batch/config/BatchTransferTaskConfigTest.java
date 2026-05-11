package com.cq.agent.batch.config;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BatchTransferTaskConfig单元测试
 * 验证符合spec.md的完整数据结构
 */
@DisplayName("批量传输任务配置 - BatchTransferTaskConfig")
class BatchTransferTaskConfigTest {

    @Test
    @DisplayName("1. 完整配置结构 - 包含所有嵌套配置对象")
    void testFullConfigStructure() {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(1001L);
        config.setTaskName("日志备份");
        config.setStatus("RUNNING");
        config.setVersion(20260509103000L);
        config.setReceivedAt("2026-05-09T10:30:00Z");
        config.setPersistedAt("2026-05-09T10:30:01Z");
        config.setStartedAt("2026-05-09T10:30:00Z");

        // ScanConfig
        BatchTransferTaskConfig.ScanConfig scanConfig = new BatchTransferTaskConfig.ScanConfig();
        scanConfig.setCronExpression("0 */5 * * * ?");
        scanConfig.setMaxScanFiles(10000);
        config.setScanConfig(scanConfig);

        // TransferConfig
        BatchTransferTaskConfig.TransferConfig transferConfig = new BatchTransferTaskConfig.TransferConfig();
        transferConfig.setRoutingStrategy("BROADCAST");
        transferConfig.setMaxBandwidthKbS(10240);
        transferConfig.setPreserveDirStructure(true);
        transferConfig.setPostTransferAction("NONE");
        config.setTransferConfig(transferConfig);

        // RetryConfig
        BatchTransferTaskConfig.RetryConfig retryConfig = new BatchTransferTaskConfig.RetryConfig();
        retryConfig.setEnabled(true);
        retryConfig.setMaxDays(7);
        retryConfig.setIntervalMin(30);
        retryConfig.setMaxRetryCount(10);
        retryConfig.setBackoffType("EXPONENTIAL");
        config.setRetryConfig(retryConfig);

        assertEquals(1001L, config.getTaskId());
        assertEquals("日志备份", config.getTaskName());
        assertEquals("RUNNING", config.getStatus());
        assertEquals(20260509103000L, config.getVersion());

        assertNotNull(config.getScanConfig());
        assertEquals("0 */5 * * * ?", config.getScanConfig().getCronExpression());
        assertEquals(10000, config.getScanConfig().getMaxScanFiles());

        assertNotNull(config.getTransferConfig());
        assertEquals("BROADCAST", config.getTransferConfig().getRoutingStrategy());
        assertEquals(10240, config.getTransferConfig().getMaxBandwidthKbS());
        assertTrue(config.getTransferConfig().isPreserveDirStructure());
        assertEquals("NONE", config.getTransferConfig().getPostTransferAction());

        assertNotNull(config.getRetryConfig());
        assertTrue(config.getRetryConfig().isEnabled());
        assertEquals(7, config.getRetryConfig().getMaxDays());
        assertEquals(30, config.getRetryConfig().getIntervalMin());
        assertEquals(10, config.getRetryConfig().getMaxRetryCount());
        assertEquals("EXPONENTIAL", config.getRetryConfig().getBackoffType());

        System.out.println("✅ 完整配置结构验证通过");
    }

    @Test
    @DisplayName("2. 向后兼容 - 旧版maxRetries字段仍可用")
    void testBackwardCompatibility() {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(1001L);
        config.setMaxRetries(5);

        assertEquals(5, config.getMaxRetries());

        // 当retryConfig存在时，maxRetries应返回retryConfig的值
        BatchTransferTaskConfig.RetryConfig retryConfig = new BatchTransferTaskConfig.RetryConfig();
        retryConfig.setMaxRetryCount(10);
        config.setRetryConfig(retryConfig);

        assertEquals(10, config.getMaxRetries());

        System.out.println("✅ 向后兼容验证通过");
    }

    @Test
    @DisplayName("3. JSON序列化 - 包含所有嵌套对象")
    void testJsonSerialization() {
        BatchTransferTaskConfig config = createFullConfig();

        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(config);

        assertTrue(json.contains("\"taskId\":1001") || json.contains("\"taskId\": 1001"));
        assertTrue(json.contains("\"scanConfig\""));
        assertTrue(json.contains("\"transferConfig\""));
        assertTrue(json.contains("\"retryConfig\""));
        assertTrue(json.contains("\"cronExpression\":\"0 */5 * * * ?\"") || json.contains("\"cronExpression\": \"0 */5 * * * ?\""));
        assertTrue(json.contains("\"routingStrategy\":\"BROADCAST\"") || json.contains("\"routingStrategy\": \"BROADCAST\""));
        assertTrue(json.contains("\"backoffType\":\"EXPONENTIAL\"") || json.contains("\"backoffType\": \"EXPONENTIAL\""));

        System.out.println("✅ JSON序列化验证通过");
    }

    @Test
    @DisplayName("4. JSON反序列化 - 从spec示例JSON解析")
    void testJsonDeserialization() {
        String json = """
            {
              "taskId": 1001,
              "taskName": "日志备份",
              "status": "RUNNING",
              "version": 20260509103000,
              "receivedAt": "2026-05-09T10:30:00Z",
              "persistedAt": "2026-05-09T10:30:01Z",
              "sourceAgentId": "agent-001",
              "sourceAgentName": "root@10.240.85.177:7777",
              "sourceDir": "/var/log/app",
              "targetAgentIds": ["agent-002", "agent-003"],
              "targetAgentNames": ["root@node2:7777", "root@node3:7777"],
              "includePatterns": ["*.log"],
              "excludePatterns": ["debug*.log"],
              "scanConfig": {
                "cronExpression": "0 */5 * * * ?",
                "maxScanFiles": 10000
              },
              "transferConfig": {
                "routingStrategy": "BROADCAST",
                "maxBandwidthKbS": 10240,
                "preserveDirStructure": true,
                "postTransferAction": "NONE"
              },
              "retryConfig": {
                "enabled": true,
                "maxDays": 7,
                "intervalMin": 30,
                "maxRetryCount": 10,
                "backoffType": "EXPONENTIAL"
              }
            }
            """;

        com.google.gson.Gson gson = new com.google.gson.Gson();
        BatchTransferTaskConfig config = gson.fromJson(json, BatchTransferTaskConfig.class);

        assertEquals(1001L, config.getTaskId());
        assertEquals("日志备份", config.getTaskName());
        assertEquals("RUNNING", config.getStatus());
        assertEquals(20260509103000L, config.getVersion());

        assertNotNull(config.getScanConfig());
        assertEquals("0 */5 * * * ?", config.getScanConfig().getCronExpression());
        assertEquals(10000, config.getScanConfig().getMaxScanFiles());

        assertNotNull(config.getTransferConfig());
        assertEquals("BROADCAST", config.getTransferConfig().getRoutingStrategy());

        assertNotNull(config.getRetryConfig());
        assertTrue(config.getRetryConfig().isEnabled());
        assertEquals(7, config.getRetryConfig().getMaxDays());
        assertEquals(30, config.getRetryConfig().getIntervalMin());
        assertEquals(10, config.getRetryConfig().getMaxRetryCount());
        assertEquals("EXPONENTIAL", config.getRetryConfig().getBackoffType());

        System.out.println("✅ JSON反序列化验证通过");
    }

    private BatchTransferTaskConfig createFullConfig() {
        BatchTransferTaskConfig config = new BatchTransferTaskConfig();
        config.setTaskId(1001L);
        config.setTaskName("日志备份");
        config.setStatus("RUNNING");
        config.setVersion(20260509103000L);
        config.setReceivedAt("2026-05-09T10:30:00Z");
        config.setPersistedAt("2026-05-09T10:30:01Z");
        config.setStartedAt("2026-05-09T10:30:00Z");

        BatchTransferTaskConfig.ScanConfig scanConfig = new BatchTransferTaskConfig.ScanConfig();
        scanConfig.setCronExpression("0 */5 * * * ?");
        scanConfig.setMaxScanFiles(10000);
        config.setScanConfig(scanConfig);

        BatchTransferTaskConfig.TransferConfig transferConfig = new BatchTransferTaskConfig.TransferConfig();
        transferConfig.setRoutingStrategy("BROADCAST");
        transferConfig.setMaxBandwidthKbS(10240);
        transferConfig.setPreserveDirStructure(true);
        transferConfig.setPostTransferAction("NONE");
        config.setTransferConfig(transferConfig);

        BatchTransferTaskConfig.RetryConfig retryConfig = new BatchTransferTaskConfig.RetryConfig();
        retryConfig.setEnabled(true);
        retryConfig.setMaxDays(7);
        retryConfig.setIntervalMin(30);
        retryConfig.setMaxRetryCount(10);
        retryConfig.setBackoffType("EXPONENTIAL");
        config.setRetryConfig(retryConfig);

        return config;
    }
}
