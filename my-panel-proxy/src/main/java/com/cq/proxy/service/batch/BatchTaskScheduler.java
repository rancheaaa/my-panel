package com.cq.proxy.service.batch;

import com.cq.proxy.repository.entity.AgentRegistry;
import com.cq.proxy.repository.mapper.AgentRegistryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class BatchTaskScheduler
{
    private static final Logger logger = LoggerFactory.getLogger(BatchTaskScheduler.class);

    private final RoutingScheduler routingScheduler;
    private final JdbcTemplate jdbcTemplate;
    private final AgentRegistryMapper agentRegistryMapper;

    @Autowired
    public BatchTaskScheduler(RoutingScheduler routingScheduler, JdbcTemplate jdbcTemplate,
                              AgentRegistryMapper agentRegistryMapper)
    {
        this.routingScheduler = routingScheduler;
        this.jdbcTemplate = jdbcTemplate;
        this.agentRegistryMapper = agentRegistryMapper;
    }

    private String resolveAgentApiUrl(String sourceAgentId, String fallbackUrl)
    {
        if (sourceAgentId != null && !sourceAgentId.isEmpty())
        {
            try
            {
                AgentRegistry agent = agentRegistryMapper.selectById(sourceAgentId);
                if (agent != null)
                {
                    if (agent.getNodeStatus() != null && agent.getNodeStatus() == 1)
                    {
                        String url = "http://" + agent.getAgentIp() + ":" + agent.getAgentPort();
                        logger.info("Resolved agent[{}] URL from agent_registry: {}", sourceAgentId, url);
                        return url;
                    }
                    else
                    {
                        logger.warn("Agent[{}] found in registry but offline (nodeStatus={}), trying fallback",
                                sourceAgentId, agent.getNodeStatus());
                    }
                }
                else
                {
                    logger.warn("Agent[{}] not found in agent_registry, trying fallback URL", sourceAgentId);
                }
            }
            catch (Exception e)
            {
                logger.warn("Failed to resolve agent[{}] from agent_registry: {}", sourceAgentId, e.getMessage());
            }
        }

        if (fallbackUrl != null && !fallbackUrl.isEmpty())
        {
            logger.info("Using fallback agent URL: {}", fallbackUrl);
            return fallbackUrl;
        }

        throw new IllegalStateException("无法解析Agent地址: agentId=" + sourceAgentId + ", 无回退URL");
    }

    private RestClient createRestClientForUrl(String baseUrl)
    {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public StartResult startTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl,
                                  Object scanRequest, List<String> targetAgents,
                                  String transferMode, String routingStrategy,
                                  String routingConfig, Long maxBandwidthBytesPerSec,
                                  String targetDirs, Integer preserveDirStructure,
                                  String scanCronExpression)
    {
        String resolvedUrl = resolveAgentApiUrl(sourceAgentId, sourceAgentApiUrl);
        RestClient restClient = createRestClientForUrl(resolvedUrl);
        try
        {
            Map<String, Object> scanResponse = restClient.post()
                    .uri("/api/internal/batch/scan")
                    .body(scanRequest)
                    .retrieve()
                    .body(Map.class);
            if (scanResponse == null)
            {
                return new StartResult(false, "扫描请求无响应", null);
            }
            if (!Boolean.TRUE.equals(scanResponse.get("success")))
            {
                return new StartResult(false, "扫描失败", scanResponse);
            }
            Map<String, Object> data = asMap(scanResponse.get("data"));
            if (data == null)
            {
                return new StartResult(false, "扫描响应数据格式异常", scanResponse);
            }
            Map<String, Object> scanResult = asMap(data.get("result"));
            List<Map<String, Object>> files = asListOfMap(scanResult.get("files"));
            int totalFiles = files.size();
            long totalSizeBytes = files.stream()
                    .mapToLong(f -> f.get("sizeBytes") instanceof Number ? ((Number) f.get("sizeBytes")).longValue() : 0L)
                    .sum();

            if (files.isEmpty())
            {
                jdbcTemplate.update(
                        "UPDATE batch_transfer_task SET total_files = 0, total_size_bytes = 0, status = 'STOPPED', started_at = NOW() WHERE id = ?",
                        taskId);
                return new StartResult(true, "扫描完成，但没有匹配文件", Map.of(
                        "taskId", taskId, "scanResult", scanResult, "totalFiles", 0, "totalSizeBytes", 0L
                ));
            }

            List<String> filePaths = files.stream()
                    .map(item -> String.valueOf(item.get("relativePath")))
                    .toList();
            Map<String, List<String>> resolvedTargets = routingScheduler.resolveTargets(
                    filePaths, targetAgents, transferMode, routingStrategy, routingConfig);
            Map<String, String> agentDirMap = buildAgentDirMap(targetAgents, targetDirs);
            List<Map<String, Object>> subtasks = generateSubtasks(taskId, files, resolvedTargets, agentDirMap);

            persistSubtasks(taskId, subtasks);

            jdbcTemplate.update(
                    "UPDATE batch_transfer_task SET total_files = ?, total_size_bytes = ?, status = 'RUNNING', started_at = NOW() WHERE id = ?",
                    totalFiles, totalSizeBytes, taskId);

            Map<String, Object> dispatchRequest = buildDispatchRequest(taskId, subtasks, maxBandwidthBytesPerSec, agentDirMap, preserveDirStructure, scanRequest);
            Map<String, Object> dispatchResult = restClient.post()
                    .uri("/api/internal/batch/dispatch")
                    .body(dispatchRequest)
                    .retrieve()
                    .body(Map.class);

            if (scanCronExpression != null && !scanCronExpression.isBlank())
            {
                pushScanScheduleToAgent(resolvedUrl, taskId, scanCronExpression, scanRequest, targetAgents, targetDirs, preserveDirStructure, maxBandwidthBytesPerSec);
            }

            return new StartResult(true, "Task started", Map.of(
                    "taskId", taskId, "scanResult", scanResult,
                    "totalFiles", totalFiles, "totalSizeBytes", totalSizeBytes,
                    "subtaskCount", subtasks.size(), "dispatchResult", dispatchResult
            ));
        }
        catch (Exception e)
        {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isBlank())
            {
                errorMsg = e.getClass().getSimpleName();
            }
            logger.error("Failed to start task {}: {}", taskId, errorMsg, e);
            try
            {
                jdbcTemplate.update(
                        "UPDATE batch_transfer_task SET status = 'STOPPED' WHERE id = ?", taskId);
            }
            catch (Exception dbEx)
            {
                logger.error("Failed to update task {} status to FAILED: {}", taskId, dbEx.getMessage());
            }
            return new StartResult(false, errorMsg, null);
        }
    }

    public void pauseTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl)
    {
        String resolvedUrl = resolveAgentApiUrl(sourceAgentId, sourceAgentApiUrl);
        logger.info("Pause task {} on agent {}", taskId, resolvedUrl);
    }

    public void resumeTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl)
    {
        String resolvedUrl = resolveAgentApiUrl(sourceAgentId, sourceAgentApiUrl);
        logger.info("Resume task {} on agent {}", taskId, resolvedUrl);
    }

    public void cancelTask(Long taskId, String sourceAgentId, String sourceAgentApiUrl)
    {
        String resolvedUrl = resolveAgentApiUrl(sourceAgentId, sourceAgentApiUrl);
        logger.info("Cancel task {} on agent {}", taskId, resolvedUrl);
        removeScanScheduleFromAgent(resolvedUrl, taskId);
    }

    public void updateTaskConfig(Long taskId, String sourceAgentId, String sourceAgentApiUrl,
                                 Object scanRequest, List<String> targetAgents,
                                 String targetDirs, Integer preserveDirStructure,
                                 Long maxBandwidthBytesPerSec, String scanCronExpression)
    {
        String resolvedUrl = resolveAgentApiUrl(sourceAgentId, sourceAgentApiUrl);
        if (scanCronExpression != null && !scanCronExpression.isBlank()) {
            pushScanScheduleToAgent(resolvedUrl, taskId, scanCronExpression, scanRequest, targetAgents, targetDirs, preserveDirStructure, maxBandwidthBytesPerSec);
        } else {
            removeScanScheduleFromAgent(resolvedUrl, taskId);
        }
    }

    private void removeScanScheduleFromAgent(String agentUrl, Long taskId) {
        try {
            Map<String, Object> scheduleRequest = new java.util.LinkedHashMap<>();
            scheduleRequest.put("action", "remove");
            scheduleRequest.put("taskId", taskId);
            
            RestClient restClient = createRestClientForUrl(agentUrl);
            restClient.post()
                    .uri("/api/internal/batch/schedule")
                    .body(scheduleRequest)
                    .retrieve()
                    .body(Map.class);
            logger.info("Removed scan schedule from agent[{}] for task {}", agentUrl, taskId);
        } catch (Exception e) {
            logger.warn("Failed to remove scan schedule from agent for task {}: {}", taskId, e.getMessage());
        }
    }

    private void persistSubtasks(Long taskId, List<Map<String, Object>> subtasks)
    {
        int inserted = 0;
        for (Map<String, Object> subtask : subtasks)
        {
            String filePath = (String) subtask.get("filePath");
            String fileName = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf("/") + 1) : filePath;
            long fileSizeBytes = subtask.get("fileSizeBytes") instanceof Number ? ((Number) subtask.get("fileSizeBytes")).longValue() : 0L;
            String targetAgentId = (String) subtask.get("targetAgentId");
            Object md5Obj = subtask.get("fileMd5");
            String fileMd5 = md5Obj != null ? String.valueOf(md5Obj) : null;
            Object lastModObj = subtask.get("fileLastModified");
            java.sql.Timestamp fileLastModified = lastModObj instanceof Date ?
                    new java.sql.Timestamp(((Date) lastModObj).getTime()) :
                    (lastModObj instanceof Number ? new java.sql.Timestamp(((Number) lastModObj).longValue()) : null);

            if (fileMd5 != null || fileLastModified != null) {
                jdbcTemplate.update(
                        "INSERT INTO batch_transfer_subtask (task_id, file_path, file_name, file_size_bytes, " +
                                "target_agent_id, status, transferred_chunks, total_chunks, transferred_bytes, " +
                                "retry_count, proxy_retry_count, create_time, update_time, file_md5, file_last_modified) " +
                                "VALUES (?, ?, ?, ?, ?, 'QUEUED', 0, 0, 0, 0, 0, NOW(), NOW(), ?, ?)",
                        taskId, filePath, fileName, fileSizeBytes, targetAgentId, fileMd5, fileLastModified);
            } else {
                jdbcTemplate.update(
                        "INSERT INTO batch_transfer_subtask (task_id, file_path, file_name, file_size_bytes, " +
                                "target_agent_id, status, transferred_chunks, total_chunks, transferred_bytes, " +
                                "retry_count, proxy_retry_count, create_time, update_time) " +
                                "VALUES (?, ?, ?, ?, ?, 'QUEUED', 0, 0, 0, 0, 0, NOW(), NOW())",
                        taskId, filePath, fileName, fileSizeBytes, targetAgentId);
            }
            inserted++;

            try {
                Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                if (id != null && id > 0) {
                    subtask.put("subtaskId", id);
                    subtask.put("id", id);
                }
            } catch (Exception e) {
                logger.warn("Failed to get inserted subtask id for task {}, file {}, agent {}: {}", taskId, filePath, targetAgentId, e.getMessage());
            }
        }
        logger.info("Persisted subtasks for task {}: inserted={}", taskId, inserted);
    }

    private Map<String, Object> buildDispatchRequest(Long taskId,
                                                     List<Map<String, Object>> subtasks,
                                                     Long maxBandwidthBytesPerSec,
                                                     Map<String, String> agentDirMap,
                                                     Integer preserveDirStructure,
                                                     Object scanRequest)
    {
        String sourceBaseDir = null;
        if (scanRequest instanceof Map)
        {
            sourceBaseDir = (String) ((Map<?, ?>) scanRequest).get("baseDir");
        }
        Map<String, Object> taskConfig = null;
        try
        {
            taskConfig = jdbcTemplate.queryForMap(
                    "SELECT post_transfer_action, backup_dir, backup_mode FROM batch_transfer_task WHERE id = ?", taskId);
        }
        catch (Exception e)
        {
            logger.warn("Failed to query post-process config for task {}: {}", taskId, e.getMessage());
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("dispatchId", "disp-" + UUID.randomUUID());
        request.put("taskId", taskId);
        request.put("maxBandwidthBytesPerSec", maxBandwidthBytesPerSec);
        request.put("agentTargetDirs", agentDirMap);
        request.put("preserveDirStructure", preserveDirStructure != null && preserveDirStructure == 1);
        request.put("sourceBaseDir", sourceBaseDir);
        request.put("postAction", taskConfig != null ? taskConfig.get("post_transfer_action") : null);
        request.put("backupDir", taskConfig != null ? taskConfig.get("backup_dir") : null);
        request.put("backupMode", taskConfig != null ? taskConfig.getOrDefault("backup_mode", "COPY") : "COPY");
        request.put("subtasks", subtasks);
        return request;
    }

    private List<Map<String, Object>> generateSubtasks(Long taskId,
                                                       List<Map<String, Object>> scannedFiles,
                                                       Map<String, List<String>> resolvedTargets,
                                                       Map<String, String> agentDirMap)
    {
        List<Map<String, Object>> subtasks = new ArrayList<>();
        long subtaskSeq = 1;
        for (Map<String, Object> file : scannedFiles)
        {
            String relativePath = String.valueOf(file.get("relativePath"));
            List<String> targets = resolvedTargets.getOrDefault(relativePath, List.of());
            for (String targetAgentId : targets)
            {
                Map<String, Object> subtask = new LinkedHashMap<>();
                subtask.put("subtaskId", subtaskSeq++);
                subtask.put("taskId", taskId);
                subtask.put("filePath", relativePath);
                subtask.put("fileName", relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf("/") + 1) : relativePath);
                subtask.put("fileSizeBytes", file.get("sizeBytes"));
                if (file.get("md5") != null) subtask.put("fileMd5", String.valueOf(file.get("md5")));
                if (file.get("lastModified") != null) subtask.put("fileLastModified", file.get("lastModified"));
                subtask.put("targetAgentId", targetAgentId);
                subtask.put("targetDir", agentDirMap.getOrDefault(targetAgentId, "/tmp"));
                subtask.put("priority", 5);

                String targetApiUrl = resolveTargetAgentUrl(targetAgentId);
                if (targetApiUrl == null)
                {
                    logger.warn("Skipping subtask for file={}, targetAgent[{}] - agent URL could not be resolved or agent is offline",
                            relativePath, targetAgentId);
                    continue;
                }
                subtask.put("targetAgentApiUrl", targetApiUrl);

                subtasks.add(subtask);
            }
        }
        return subtasks;
    }

    private String resolveTargetAgentUrl(String agentId)
    {
        try
        {
            AgentRegistry registry = agentRegistryMapper.selectById(agentId);
            if (registry == null)
            {
                logger.warn("Target agent[{}] not found in agent_registry table", agentId);
                return null;
            }
            if (registry.getNodeStatus() == null || registry.getNodeStatus() != 1)
            {
                logger.warn("Target agent[{}] is offline (nodeStatus={}), ip={}, port={}",
                        agentId, registry.getNodeStatus(), registry.getAgentIp(), registry.getAgentPort());
                return null;
            }
            String url = "http://" + registry.getAgentIp() + ":" + registry.getAgentPort();
            logger.info("Resolved target agent[{}] URL: {}", agentId, url);
            return url;
        }
        catch (Exception e)
        {
            logger.warn("Failed to resolve API URL for agent {}: {}", agentId, e.getMessage());
            return null;
        }
    }

    private Map<String, String> buildAgentDirMap(List<String> targetAgents, String targetDirs)
    {
        Map<String, String> map = new LinkedHashMap<>();
        if (targetAgents == null || targetDirs == null) return map;
        String[] dirs = targetDirs.split(";");
        for (int i = 0; i < targetAgents.size() && i < dirs.length; i++)
        {
            map.put(targetAgents.get(i), dirs[i].trim());
        }
        return map;
    }

    private void pushScanScheduleToAgent(String agentUrl, Long taskId, String cronExpression,
                                          Object scanRequest, List<String> targetAgents,
                                          String targetDirs, Integer preserveDirStructure,
                                          Long maxBandwidthBytesPerSec)
    {
        try
        {
            List<Map<String, Object>> agentList = new java.util.ArrayList<>();
            for (String agentId : targetAgents)
            {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("agentId", agentId);
                a.put("apiUrl", resolveTargetAgentUrl(agentId));
                agentList.add(a);
            }

            Map<String, Object> taskConfig = null;
            try
            {
                taskConfig = jdbcTemplate.queryForMap(
                        "SELECT post_transfer_action, backup_dir, backup_mode FROM batch_transfer_task WHERE id = ?", taskId);
            }
            catch (Exception ignored) {}

            Map<String, Object> scheduleRequest = new LinkedHashMap<>();
            scheduleRequest.put("action", "upsert");
            scheduleRequest.put("taskId", taskId);
            scheduleRequest.put("cronExpression", cronExpression);
            scheduleRequest.put("scanConfig", scanRequest);
            scheduleRequest.put("proxyBaseUrl", "http://localhost:9876");
            scheduleRequest.put("targetAgents", agentList);
            scheduleRequest.put("targetDirs", buildAgentDirMap(targetAgents, targetDirs));
            scheduleRequest.put("preserveDirStructure", preserveDirStructure);
            scheduleRequest.put("maxBandwidthBytesPerSec", maxBandwidthBytesPerSec);
            scheduleRequest.put("postAction", taskConfig != null ? taskConfig.get("post_transfer_action") : null);
            scheduleRequest.put("backupDir", taskConfig != null ? taskConfig.get("backup_dir") : null);
            scheduleRequest.put("backupMode", taskConfig != null ? taskConfig.getOrDefault("backup_mode", "COPY") : "COPY");

            RestClient restClient = createRestClientForUrl(agentUrl);
            Map<String, Object> result = restClient.post()
                    .uri("/api/internal/batch/schedule")
                    .body(scheduleRequest)
                    .retrieve()
                    .body(Map.class);

            logger.info("Pushed scan schedule to agent[{}] for task {}: cron={}", agentUrl, taskId, cronExpression);
        }
        catch (Exception e)
        {
            logger.warn("Failed to push scan schedule to agent for task {}: {}", taskId, e.getMessage());
        }
    }

    private Map<String, Object> asMap(Object value)
    {
        if (value instanceof Map<?, ?> rawMap)
        {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet())
            {
                if (entry.getKey() != null)
                {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return map;
        }
        return Map.of();
    }

    private List<Map<String, Object>> asListOfMap(Object value)
    {
        if (!(value instanceof List<?> rawList))
        {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object item : rawList)
        {
            list.add(asMap(item));
        }
        return list;
    }

    public static class StartResult
    {
        public boolean success;
        public String message;
        public Object scanResult;

        public StartResult(boolean success, String message, Object scanResult)
        {
            this.success = success;
            this.message = message;
            this.scanResult = scanResult;
        }
    }
}
