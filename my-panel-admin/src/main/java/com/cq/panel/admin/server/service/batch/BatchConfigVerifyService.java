package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.web.domain.vo.batch.ConfigVerifyResultVO;
import com.cq.panel.admin.server.web.domain.vo.batch.ConfigVerifyResultVO.FieldDiff;
import com.cq.panel.admin.server.web.domain.vo.batch.ConfigVerifyResultVO.TaskVerifyResult;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.admin.server.service.ProxyClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 配置校验服务
 * 对比服务端生成的配置与 Agent 端持久化的配置，按 taskId 逐字段对比
 */
@Service
public class BatchConfigVerifyService {

    private static final Logger log = LoggerFactory.getLogger(BatchConfigVerifyService.class);

    private final BatchTransferTaskMapper batchTransferTaskMapper;
    private final AgentRegistryMapper agentRegistryMapper;
    private final BatchConfigSerializer batchConfigSerializer;
    private final ProxyClientService proxyClientService;
    private final ObjectMapper objectMapper;

    public BatchConfigVerifyService(BatchTransferTaskMapper batchTransferTaskMapper,
                                     AgentRegistryMapper agentRegistryMapper,
                                     BatchConfigSerializer batchConfigSerializer,
                                     ProxyClientService proxyClientService,
                                     ObjectMapper objectMapper) {
        this.batchTransferTaskMapper = batchTransferTaskMapper;
        this.agentRegistryMapper = agentRegistryMapper;
        this.batchConfigSerializer = batchConfigSerializer;
        this.proxyClientService = proxyClientService;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验 Agent 端配置与服务端生成的是否一致
     * 按 taskId 逐字段对比，忽略时间戳字段
     */
    public ConfigVerifyResultVO verifyConfig(String sourceAgentId) {
        AgentRegistry agent = agentRegistryMapper.selectAgentRegistryById(sourceAgentId);
        if (agent == null) {
            throw new RuntimeException("节点不存在: " + sourceAgentId);
        }

        ConfigVerifyResultVO result = new ConfigVerifyResultVO();
        result.setSourceAgentId(sourceAgentId);
        result.setSourceAgentName(agent.getNodeName() != null ? agent.getNodeName()
                : agent.getAgentIp() + ":" + agent.getAgentPort());

        // 1. 生成服务端配置
        Map<Long, AgentTaskConfig> serverConfigs = generateServerConfigs(sourceAgentId);

        // 2. 调用 Agent 获取当前配置
        Map<Long, AgentTaskConfig> agentConfigs = fetchAgentConfigs(agent);

        // 3. 按 taskId 对比
        List<TaskVerifyResult> taskResults = new ArrayList<>();
        Set<Long> allTaskIds = new TreeSet<>(serverConfigs.keySet());
        allTaskIds.addAll(agentConfigs.keySet());

        boolean allMatch = true;
        for (Long taskId : allTaskIds) {
            TaskVerifyResult tr = new TaskVerifyResult();
            tr.setTaskId(taskId);

            AgentTaskConfig serverConfig = serverConfigs.get(taskId);
            AgentTaskConfig agentConfig = agentConfigs.get(taskId);

            if (serverConfig != null) {
                tr.setTaskName(serverConfig.getTaskName());
            } else if (agentConfig != null) {
                tr.setTaskName(agentConfig.getTaskName());
            }

            if (serverConfig == null && agentConfig != null) {
                tr.setExists(false);
                tr.setMatch(false);
                tr.setDiffs(Collections.singletonList(createFieldDiff("task", "服务端不存在", "Agent端存在")));
                allMatch = false;
            } else if (serverConfig != null && agentConfig == null) {
                tr.setExists(true);
                tr.setMatch(false);
                tr.setDiffs(Collections.singletonList(createFieldDiff("task", "Agent端不存在", "服务端存在")));
                allMatch = false;
            } else {
                // 两边都有，逐字段对比
                tr.setExists(true);
                List<FieldDiff> diffs = compareFields(serverConfig, agentConfig);
                tr.setDiffs(diffs);
                if (!diffs.isEmpty()) {
                    tr.setMatch(false);
                    allMatch = false;
                } else {
                    tr.setMatch(true);
                }
            }
            taskResults.add(tr);
        }

        result.setAllMatch(allMatch);
        result.setTasks(taskResults);
        return result;
    }

    /**
     * 强制推送配置到 Agent
     */
    public int pushConfig(String sourceAgentId) throws Exception {
        AgentRegistry agent = agentRegistryMapper.selectAgentRegistryById(sourceAgentId);
        if (agent == null) {
            throw new RuntimeException("节点不存在: " + sourceAgentId);
        }

        Map<String, String> serverConfigs = generateServerConfigJsons(sourceAgentId);
        if (serverConfigs.isEmpty()) {
            throw new RuntimeException("该节点没有传输任务配置");
        }

        byte[] zipData = batchConfigSerializer.generateConfigZip(serverConfigs);
        if (zipData == null || zipData.length == 0) {
            throw new RuntimeException("生成配置包失败");
        }

        final int CHUNK_SIZE = 1024 * 1024; // 1MB
        int totalChunks = (int) Math.ceil((double) zipData.length / CHUNK_SIZE);

        // 初始化推送会话
        Map<String, Object> initBody = new LinkedHashMap<>();
        initBody.put("totalSize", zipData.length);
        initBody.put("totalChunks", totalChunks);
        initBody.put("chunkSize", CHUNK_SIZE);

        String initBodyJson = objectMapper.writeValueAsString(initBody);
        String initProxyResponse = proxyClientService.configPushInit(sourceAgentId, initBodyJson);
        JsonNode initDataNode = proxyClientService.extractData(initProxyResponse);

        if (initDataNode == null) {
            throw new RuntimeException("初始化推送会话失败: Proxy转发失败");
        }

        String sessionId;
        try {
            // initDataNode是Agent原始响应的JSON字符串
            String initAgentResponse = initDataNode.asText();
            JsonNode initJson = objectMapper.readTree(initAgentResponse);
            sessionId = initJson.path("data").path("sessionId").asText();
        } catch (Exception e) {
            throw new RuntimeException("解析推送会话响应失败: " + e.getMessage());
        }

        // 逐片上传
        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, zipData.length);
            byte[] chunkData = Arrays.copyOfRange(zipData, start, end);
            String base64Chunk = Base64.getEncoder().encodeToString(chunkData);

            Map<String, Object> chunkBody = new LinkedHashMap<>();
            chunkBody.put("sessionId", sessionId);
            chunkBody.put("chunkIndex", i);
            chunkBody.put("data", base64Chunk);

            String chunkBodyJson = objectMapper.writeValueAsString(chunkBody);
            proxyClientService.configPushChunk(sourceAgentId, chunkBodyJson);
        }

        // 完成推送
        Map<String, Object> completeBody = new LinkedHashMap<>();
        completeBody.put("sessionId", sessionId);

        String completeBodyJson = objectMapper.writeValueAsString(completeBody);
        String completeProxyResponse = proxyClientService.configPushComplete(sourceAgentId, completeBodyJson);
        JsonNode completeDataNode = proxyClientService.extractData(completeProxyResponse);

        if (completeDataNode == null) {
            throw new RuntimeException("完成推送失败: Proxy转发失败");
        }

        try {
            String completeAgentResponse = completeDataNode.asText();
            JsonNode completeJson = objectMapper.readTree(completeAgentResponse);
            return completeJson.path("data").path("filesCount").asInt();
        } catch (Exception e) {
            throw new RuntimeException("解析推送完成响应失败: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 生成服务端配置对象 Map (taskId -> AgentTaskConfig)
     */
    private Map<Long, AgentTaskConfig> generateServerConfigs(String sourceAgentId) {
        BatchTransferTask query = new BatchTransferTask();
        query.setSourceAgentId(sourceAgentId);
        query.setDeleted(0);
        List<BatchTransferTask> tasks = batchTransferTaskMapper.selectList(query);

        Map<Long, AgentTaskConfig> configs = new LinkedHashMap<>();
        if (tasks == null || tasks.isEmpty()) {
            return configs;
        }

        for (BatchTransferTask task : tasks) {
            try {
                String json = batchConfigSerializer.serializeForAgent(task);
                AgentTaskConfig config = objectMapper.readValue(json, AgentTaskConfig.class);
                configs.put(task.getId(), config);
            } catch (Exception e) {
                log.warn("序列化任务配置失败: taskId={}", task.getId(), e);
            }
        }
        return configs;
    }

    /**
     * 生成服务端配置 JSON Map (fileName -> jsonContent)
     * 包含 task_*.json 和 tasks.meta.json
     */
    private Map<String, String> generateServerConfigJsons(String sourceAgentId) {
        BatchTransferTask query = new BatchTransferTask();
        query.setSourceAgentId(sourceAgentId);
        query.setDeleted(0);
        List<BatchTransferTask> tasks = batchTransferTaskMapper.selectList(query);

        Map<String, String> configs = new LinkedHashMap<>();
        if (tasks == null || tasks.isEmpty()) {
            return configs;
        }

        long now = System.currentTimeMillis();
        String isoTime = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date(now));
        List<Map<String, Object>> metaTasks = new ArrayList<>();

        for (BatchTransferTask task : tasks) {
            try {
                String json = batchConfigSerializer.serializeForAgent(task);
                String fileName = "task_" + task.getId() + ".json";
                configs.put(fileName, json);

                // 构建 tasks.meta.json 中的任务元数据
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("taskId", task.getId());
                meta.put("taskName", task.getTaskName());
                meta.put("status", task.getStatus());
                meta.put("version", task.getUpdateTime() != null
                        ? Long.parseLong(new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(task.getUpdateTime()))
                        : 0L);
                meta.put("receivedAt", isoTime);
                meta.put("persistedAt", isoTime);
                meta.put("lastModified", now);
                metaTasks.add(meta);
            } catch (Exception e) {
                log.warn("序列化任务配置失败: taskId={}", task.getId(), e);
            }
        }

        // 生成 tasks.meta.json
        if (!metaTasks.isEmpty()) {
            Map<String, Object> metaData = new LinkedHashMap<>();
            metaData.put("tasks", metaTasks);
            try {
                String metaJson = batchConfigSerializer.serialize(metaData);
                configs.put("tasks.meta.json", metaJson);
            } catch (Exception e) {
                log.warn("序列化meta配置失败", e);
            }
        }

        return configs;
    }

    /**
     * 从 Agent 获取配置对象 Map (taskId -> AgentTaskConfig)
     */
    private Map<Long, AgentTaskConfig> fetchAgentConfigs(AgentRegistry agent) {
        Map<Long, AgentTaskConfig> result = new LinkedHashMap<>();
        try {
            String proxyResponse = proxyClientService.configVerify(agent.getId());
            JsonNode dataNode = proxyClientService.extractData(proxyResponse);

            if (dataNode == null) {
                return result;
            }

            // dataNode是Agent原始响应的JSON字符串
            String agentResponseStr = dataNode.asText();
            JsonNode agentRoot = objectMapper.readTree(agentResponseStr);
            JsonNode tasksNode = agentRoot.path("data").path("tasks");
            if (tasksNode.isArray()) {
                for (JsonNode taskNode : tasksNode) {
                    String content = taskNode.path("content").asText();
                    Long taskId = taskNode.path("taskId").asLong(-1);
                    if (taskId > 0 && content != null && !content.isEmpty()) {
                        try {
                            AgentTaskConfig config = objectMapper.readValue(content, AgentTaskConfig.class);
                            result.put(taskId, config);
                        } catch (Exception e) {
                            log.warn("反序列化 Agent 配置失败: taskId={}, agentId={}", taskId, agent.getId(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取Agent配置失败: agentId={}", agent.getId(), e);
        }
        return result;
    }

    /**
     * 逐字段对比两个 AgentTaskConfig，返回差异列表
     * 忽略时间戳字段：receivedAt, persistedAt, startedAt, version
     */
    private List<FieldDiff> compareFields(AgentTaskConfig server, AgentTaskConfig agent) {
        List<FieldDiff> diffs = new ArrayList<>();

        compareField(diffs, "taskName", server.getTaskName(), agent.getTaskName());
        compareField(diffs, "taskDescription", server.getTaskDescription(), agent.getTaskDescription());
        compareField(diffs, "status", server.getStatus(), agent.getStatus());
        compareField(diffs, "sourceAgentId", server.getSourceAgentId(), agent.getSourceAgentId());
        compareField(diffs, "sourceAgentName", server.getSourceAgentName(), agent.getSourceAgentName());
        compareField(diffs, "sourceDir", server.getSourceDir(), agent.getSourceDir());
        compareField(diffs, "taskPriority", server.getTaskPriority(), agent.getTaskPriority());
        compareField(diffs, "targetAgents", targetAgentsToString(server), targetAgentsToString(agent));

        // scanConfig
        if (server.getScanConfig() != null || agent.getScanConfig() != null) {
            var ss = server.getScanConfig();
            var as = agent.getScanConfig();
            compareField(diffs, "scanConfig.cronExpression", ss != null ? ss.getCronExpression() : null,
                    as != null ? as.getCronExpression() : null);
            compareField(diffs, "scanConfig.maxScanFiles", ss != null ? ss.getMaxScanFiles() : null,
                    as != null ? as.getMaxScanFiles() : null);
            compareField(diffs, "scanConfig.scheduledEnabled", ss != null ? ss.getScheduledEnabled() : null,
                    as != null ? as.getScheduledEnabled() : null);
            compareField(diffs, "scanConfig.scheduledStartTime", ss != null ? ss.getScheduledStartTime() : null,
                    as != null ? as.getScheduledStartTime() : null);
            compareField(diffs, "scanConfig.scheduledEndTime", ss != null ? ss.getScheduledEndTime() : null,
                    as != null ? as.getScheduledEndTime() : null);
        }

        // transferConfig
        if (server.getTransferConfig() != null || agent.getTransferConfig() != null) {
            var st = server.getTransferConfig();
            var at = agent.getTransferConfig();
            compareField(diffs, "transferConfig.transferMode", st != null ? st.getTransferMode() : null,
                    at != null ? at.getTransferMode() : null);
            compareField(diffs, "transferConfig.preserveDirStructure",
                    st != null ? st.isPreserveDirStructure() : null,
                    at != null ? at.isPreserveDirStructure() : null);
            compareField(diffs, "transferConfig.routingStrategy", st != null ? st.getRoutingStrategy() : null,
                    at != null ? at.getRoutingStrategy() : null);
            compareField(diffs, "transferConfig.routingConfig", st != null ? st.getRoutingConfig() : null,
                    at != null ? at.getRoutingConfig() : null);
            compareField(diffs, "transferConfig.maxBandwidthKbS", st != null ? st.getMaxBandwidthKbS() : null,
                    at != null ? at.getMaxBandwidthKbS() : null);
            compareField(diffs, "transferConfig.postTransferAction", st != null ? st.getPostTransferAction() : null,
                    at != null ? at.getPostTransferAction() : null);
            compareField(diffs, "transferConfig.backupDir", st != null ? st.getBackupDir() : null,
                    at != null ? at.getBackupDir() : null);
            compareField(diffs, "transferConfig.backupMode", st != null ? st.getBackupMode() : null,
                    at != null ? at.getBackupMode() : null);
        }

        // retryConfig
        if (server.getRetryConfig() != null || agent.getRetryConfig() != null) {
            var sr = server.getRetryConfig();
            var ar = agent.getRetryConfig();
            compareField(diffs, "retryConfig.enabled", sr != null ? sr.isEnabled() : null,
                    ar != null ? ar.isEnabled() : null);
            compareField(diffs, "retryConfig.maxDays", sr != null ? sr.getMaxDays() : null,
                    ar != null ? ar.getMaxDays() : null);
            compareField(diffs, "retryConfig.intervalMin", sr != null ? sr.getIntervalMin() : null,
                    ar != null ? ar.getIntervalMin() : null);
            compareField(diffs, "retryConfig.maxRetryCount", sr != null ? sr.getMaxRetryCount() : null,
                    ar != null ? ar.getMaxRetryCount() : null);
            compareField(diffs, "retryConfig.backoffType", sr != null ? sr.getBackoffType() : null,
                    ar != null ? ar.getBackoffType() : null);
        }

        // includePatterns / excludePatterns
        compareField(diffs, "includePatterns",
                listToString(server.getIncludePatterns()),
                listToString(agent.getIncludePatterns()));
        compareField(diffs, "excludePatterns",
                listToString(server.getExcludePatterns()),
                listToString(agent.getExcludePatterns()));

        return diffs;
    }

    private void compareField(List<FieldDiff> diffs, String fieldPath, Object serverVal, Object agentVal) {
        String sv = formatValue(serverVal);
        String av = formatValue(agentVal);
        if (!Objects.equals(sv, av)) {
            diffs.add(createFieldDiff(fieldPath, sv, av));
        }
    }

    private FieldDiff createFieldDiff(String fieldPath, String serverValue, String agentValue) {
        FieldDiff fd = new FieldDiff();
        fd.setFieldPath(fieldPath);
        fd.setServerValue(serverValue);
        fd.setAgentValue(agentValue);
        return fd;
    }

    private String formatValue(Object val) {
        if (val == null) return null;
        return val.toString();
    }

    private String targetAgentsToString(AgentTaskConfig config) {
        if (config == null || config.getTargetAgents() == null || config.getTargetAgents().isEmpty()) return null;
        return config.getTargetAgents().toString();
    }

    private String listToString(List<?> list) {
        if (list == null || list.isEmpty()) return null;
        return list.toString();
    }
}
