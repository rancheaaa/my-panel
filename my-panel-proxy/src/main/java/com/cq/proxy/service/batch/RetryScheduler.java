package com.cq.proxy.service.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RetryScheduler
{
    private static final Logger logger = LoggerFactory.getLogger(RetryScheduler.class);
    private final JdbcTemplate jdbcTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    public RetryScheduler(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${batch.retry.scheduler-cron:0 */30 * * * ?}")
    @Transactional
    public void scanAndRedispatch()
    {
        String sql = "SELECT id, task_id, source_agent_id, source_agent_name, source_path, target_path, " +
                "file_name, file_size_bytes, target_agent_id, target_agent_name, " +
                "retry_count, proxy_retry_count FROM batch_transfer_subtask " +
                "WHERE status = 'FAILED' AND next_retry_after <= NOW() " +
                "AND task_id IN (SELECT id FROM batch_transfer_task WHERE status = 'RUNNING' AND retry_enabled = 1) " +
                "LIMIT 100";
        List<Map<String, Object>> failedSubtasks = jdbcTemplate.queryForList(sql);
        if (failedSubtasks.isEmpty())
        {
            return;
        }
        logger.info("Found {} failed subtasks eligible for Level 2 retry", failedSubtasks.size());

        for (Map<String, Object> subtask : failedSubtasks)
        {
            Long subtaskId = ((Number) subtask.get("id")).longValue();
            Long taskId = ((Number) subtask.get("task_id")).longValue();
            String targetAgentId = (String) subtask.get("target_agent_id");
            try
            {
                String sourceAgentSql = "SELECT source_agent_id, target_agents, target_dirs FROM batch_transfer_task WHERE id = ?";
                Map<String, Object> taskInfo = jdbcTemplate.queryForMap(sourceAgentSql, taskId);
                String sourceAgentId = (String) taskInfo.get("source_agent_id");
                if (sourceAgentId == null)
                {
                    logger.warn("Task {} not found, skipping subtask {}", taskId, subtaskId);
                    continue;
                }
                String agentUrlSql = "SELECT agent_ip, agent_port FROM agent_registry WHERE id = ? AND node_status = 1";
                Map<String, Object> agentInfo = jdbcTemplate.queryForMap(agentUrlSql, sourceAgentId);
                String sourceAgentApiUrl = "http://" + agentInfo.get("agent_ip") + ":" + agentInfo.get("agent_port");

                String targetDirs = (String) taskInfo.get("target_dirs");
                String targetAgentsJson = (String) taskInfo.get("target_agents");
                Map<String, String> agentTargetDirs = buildAgentDirMap(targetAgentsJson, targetDirs);
                String agentTargetDir = agentTargetDirs.getOrDefault(targetAgentId, "/tmp");

                Map<String, Object> retryItem = new java.util.LinkedHashMap<>();
                retryItem.put("subtaskId", subtaskId);
                retryItem.put("taskId", taskId);
                retryItem.put("filePath", subtask.get("source_path"));
                retryItem.put("fileName", subtask.get("file_name"));
                retryItem.put("fileSizeBytes", subtask.get("file_size_bytes"));
                retryItem.put("targetAgentId", targetAgentId);
                retryItem.put("targetDir", agentTargetDir);
                retryItem.put("sourceAgentId", subtask.get("source_agent_id"));
                retryItem.put("sourceAgentName", subtask.get("source_agent_name"));
                retryItem.put("targetAgentName", subtask.get("target_agent_name"));
                retryItem.put("sourcePath", subtask.get("source_path"));
                retryItem.put("targetPath", subtask.get("target_path"));
                retryItem.put("priority", 5);
                Map<String, Object> dispatchRequest = new java.util.LinkedHashMap<>();
                dispatchRequest.put("dispatchId", "retry-" + subtaskId);
                dispatchRequest.put("taskId", taskId);
                dispatchRequest.put("agentTargetDirs", agentTargetDirs);
                dispatchRequest.put("subtasks", List.of(retryItem));

                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dispatchRequest);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sourceAgentApiUrl + "/api/internal/batch/dispatch"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(30))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 400)
                {
                    int newProxyRetryCount = ((Number) subtask.get("proxy_retry_count")).intValue() + 1;
                    jdbcTemplate.update(
                            "UPDATE batch_transfer_subtask SET status = 'QUEUED', proxy_retry_count = ?, " +
                                    "next_retry_after = NULL WHERE id = ?",
                            newProxyRetryCount, subtaskId);
                    logger.info("Level 2 retry dispatched: subtaskId={}, proxyRetryCount={}", subtaskId, newProxyRetryCount);
                }
                else
                {
                    logger.warn("Level 2 retry dispatch failed: subtaskId={}, status={}", subtaskId, response.statusCode());
                }
            }
            catch (Exception e)
            {
                logger.error("Failed to redispatch subtask {}: {}", subtaskId, e.getMessage());
            }
        }
    }

    private Map<String, String> buildAgentDirMap(String targetAgentsJson, String targetDirs)
    {
        Map<String, String> map = new LinkedHashMap<>();
        if (targetAgentsJson == null || targetDirs == null) return map;
        List<String> targetAgents;
        try
        {
            targetAgents = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    targetAgentsJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        }
        catch (Exception e)
        {
            logger.warn("Failed to parse targetAgents JSON: {}", targetAgentsJson);
            return map;
        }
        String[] dirs = targetDirs.split(";");
        for (int i = 0; i < targetAgents.size() && i < dirs.length; i++)
        {
            map.put(targetAgents.get(i), dirs[i].trim());
        }
        return map;
    }
}
