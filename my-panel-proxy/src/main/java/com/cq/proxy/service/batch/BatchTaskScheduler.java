package com.cq.proxy.service.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BatchTaskScheduler
{
    private static final Logger logger = LoggerFactory.getLogger(BatchTaskScheduler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RoutingScheduler routingScheduler;
    private final JdbcTemplate jdbcTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    public BatchTaskScheduler(RoutingScheduler routingScheduler, JdbcTemplate jdbcTemplate)
    {
        this.routingScheduler = routingScheduler;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public StartResult startTask(Long taskId, String sourceAgentApiUrl, Object scanRequest,
                                  List<String> targetAgents, String transferMode,
                                  String routingStrategy, String routingConfig,
                                  Long maxBandwidthBytesPerSec,
                                  String targetDirs, Integer preserveDirStructure)
    {
        try
        {
            Map<String, Object> scanResponse = postToAgent(sourceAgentApiUrl + "/api/internal/batch/scan", scanRequest);
            if (!Boolean.TRUE.equals(scanResponse.get("success")))
            {
                return new StartResult(false, "扫描失败", scanResponse);
            }
            Map<String, Object> scanResult = asMap(scanResponse.get("result"));
            List<Map<String, Object>> files = asListOfMap(scanResult.get("files"));
            int totalFiles = files.size();
            long totalSizeBytes = files.stream()
                    .mapToLong(f -> f.get("sizeBytes") instanceof Number ? ((Number) f.get("sizeBytes")).longValue() : 0L)
                    .sum();

            if (files.isEmpty())
            {
                jdbcTemplate.update(
                        "UPDATE batch_transfer_task SET total_files = 0, total_size_bytes = 0, status = 'COMPLETED', completed_at = NOW() WHERE id = ?",
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
                    "UPDATE batch_transfer_task SET total_files = ?, total_size_bytes = ?, status = 'TRANSFERRING' WHERE id = ?",
                    totalFiles, totalSizeBytes, taskId);

            Map<String, Object> dispatchRequest = buildDispatchRequest(taskId, subtasks, maxBandwidthBytesPerSec, agentDirMap, preserveDirStructure);
            Map<String, Object> dispatchResult = postToAgent(sourceAgentApiUrl + "/api/internal/batch/dispatch", dispatchRequest);

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
            if (e instanceof java.net.ConnectException)
            {
                errorMsg = "无法连接到源Agent(" + sourceAgentApiUrl + "): " + errorMsg;
            }
            logger.error("Failed to start task {}: {}", taskId, errorMsg, e);
            return new StartResult(false, errorMsg, null);
        }
    }

    public void pauseTask(Long taskId, String sourceAgentApiUrl)
    {
        logger.info("Pause task {} on agent {}", taskId, sourceAgentApiUrl);
    }

    public void resumeTask(Long taskId, String sourceAgentApiUrl)
    {
        logger.info("Resume task {} on agent {}", taskId, sourceAgentApiUrl);
    }

    public void cancelTask(Long taskId, String sourceAgentApiUrl)
    {
        logger.info("Cancel task {} on agent {}", taskId, sourceAgentApiUrl);
    }

    private void persistSubtasks(Long taskId, List<Map<String, Object>> subtasks)
    {
        for (Map<String, Object> subtask : subtasks)
        {
            String filePath = (String) subtask.get("filePath");
            String fileName = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf("/") + 1) : filePath;
            long fileSizeBytes = subtask.get("fileSizeBytes") instanceof Number ? ((Number) subtask.get("fileSizeBytes")).longValue() : 0L;
            String targetAgentId = (String) subtask.get("targetAgentId");

            jdbcTemplate.update(
                    "INSERT INTO batch_transfer_subtask (task_id, file_path, file_name, file_size_bytes, " +
                            "target_agent_id, status, transferred_chunks, total_chunks, transferred_bytes, " +
                            "retry_count, proxy_retry_count, create_time, update_time) " +
                            "VALUES (?, ?, ?, ?, ?, 'QUEUED', 0, 0, 0, 0, 0, NOW(), NOW())",
                    taskId, filePath, fileName, fileSizeBytes, targetAgentId);
        }
        logger.info("Persisted {} subtasks for task {}", subtasks.size(), taskId);
    }

    private Map<String, Object> buildDispatchRequest(Long taskId,
                                                     List<Map<String, Object>> subtasks,
                                                     Long maxBandwidthBytesPerSec,
                                                     Map<String, String> agentDirMap,
                                                     Integer preserveDirStructure)
    {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("dispatchId", "disp-" + UUID.randomUUID());
        request.put("taskId", taskId);
        request.put("maxBandwidthBytesPerSec", maxBandwidthBytesPerSec);
        request.put("agentTargetDirs", agentDirMap);
        request.put("preserveDirStructure", preserveDirStructure != null && preserveDirStructure == 1);
        request.put("subtasks", subtasks);
        return request;
    }

    private List<Map<String, Object>> generateSubtasks(Long taskId,
                                                       List<Map<String, Object>> scannedFiles,
                                                       Map<String, List<String>> resolvedTargets,
                                                       Map<String, String> agentDirMap)
    {
        List<Map<String, Object>> subtasks = new ArrayList<>();
        for (Map<String, Object> file : scannedFiles)
        {
            String relativePath = String.valueOf(file.get("relativePath"));
            List<String> targets = resolvedTargets.getOrDefault(relativePath, List.of());
            for (String targetAgentId : targets)
            {
                Map<String, Object> subtask = new LinkedHashMap<>();
                subtask.put("taskId", taskId);
                subtask.put("filePath", relativePath);
                subtask.put("fileName", relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf("/") + 1) : relativePath);
                subtask.put("fileSizeBytes", file.get("sizeBytes"));
                subtask.put("targetAgentId", targetAgentId);
                subtask.put("targetDir", agentDirMap.getOrDefault(targetAgentId, "/tmp"));
                subtask.put("priority", 5);
                subtasks.add(subtask);
            }
        }
        return subtasks;
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

    private Map<String, Object> postToAgent(String url, Object body) throws Exception
    {
        String json = OBJECT_MAPPER.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400)
        {
            throw new IllegalStateException("Agent接口调用失败, status=" + response.statusCode() + ", body=" + response.body());
        }
        return OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
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
