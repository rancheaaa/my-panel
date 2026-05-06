package com.cq.proxy.web.controller.batch;

import com.cq.proxy.service.batch.BatchTaskScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "批量传输管理接口", description = "Admin调用Proxy的管理接口")
@RestController
@RequestMapping("/api/v1/batch")
public class BatchAdminController
{
    private static final Logger logger = LoggerFactory.getLogger(BatchAdminController.class);
    private final BatchTaskScheduler batchTaskScheduler;

    public BatchAdminController(BatchTaskScheduler batchTaskScheduler)
    {
        this.batchTaskScheduler = batchTaskScheduler;
    }

    @Operation(summary = "启动批量传输任务")
    @PostMapping("/tasks/{taskId}/start")
    public Map<String, Object> startTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: start task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        @SuppressWarnings("unchecked")
        java.util.List<String> targetAgents = (java.util.List<String>) request.get("targetAgents");
        String transferMode = (String) request.getOrDefault("transferMode", "ONE_TO_MANY");
        String routingStrategy = (String) request.getOrDefault("routingStrategy", "BROADCAST");
        String routingConfig = (String) request.get("routingConfig");
        Object scanRequest = request.get("scanRequest");
        Number maxBandwidth = (Number) request.get("maxBandwidthBytesPerSec");
        Long maxBandwidthBytesPerSec = maxBandwidth != null ? maxBandwidth.longValue() : null;
        String targetDirs = (String) request.get("targetDirs");
        Number preserveDirStructureNum = (Number) request.get("preserveDirStructure");
        Integer preserveDirStructure = preserveDirStructureNum != null ? preserveDirStructureNum.intValue() : 1;
        String scanCronExpression = (String) request.get("scanCronExpression");

        BatchTaskScheduler.StartResult result = batchTaskScheduler.startTask(
                taskId, sourceAgentId, sourceAgentApiUrl, scanRequest, targetAgents,
                transferMode, routingStrategy, routingConfig, maxBandwidthBytesPerSec,
                targetDirs, preserveDirStructure, scanCronExpression);

        return Map.of(
                "success", result.success,
                "message", result.message,
                "data", result.scanResult != null ? result.scanResult : Map.of()
        );
    }

    @Operation(summary = "暂停批量传输任务")
    @PutMapping("/tasks/{taskId}/pause")
    public Map<String, Object> pauseTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: pause task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        batchTaskScheduler.pauseTask(taskId, sourceAgentId, sourceAgentApiUrl);
        return Map.of("success", true, "message", "Task paused");
    }

    @Operation(summary = "恢复批量传输任务")
    @PutMapping("/tasks/{taskId}/resume")
    public Map<String, Object> resumeTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: resume task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        batchTaskScheduler.resumeTask(taskId, sourceAgentId, sourceAgentApiUrl);
        return Map.of("success", true, "message", "Task resumed");
    }

    @Operation(summary = "取消批量传输任务")
    @PutMapping("/tasks/{taskId}/cancel")
    public Map<String, Object> cancelTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: cancel task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        batchTaskScheduler.cancelTask(taskId, sourceAgentId, sourceAgentApiUrl);
        return Map.of("success", true, "message", "Task cancelled");
    }

    @Operation(summary = "更新批量传输任务配置")
    @PutMapping("/tasks/{taskId}/config")
    public Map<String, Object> updateTaskConfig(@PathVariable Long taskId, @RequestBody Map<String, Object> request)
    {
        logger.info("Admin request: update config for task {}", taskId);
        String sourceAgentId = (String) request.get("sourceAgentId");
        String sourceAgentApiUrl = (String) request.get("sourceAgentApiUrl");
        @SuppressWarnings("unchecked")
        java.util.List<String> targetAgents = (java.util.List<String>) request.get("targetAgents");
        Object scanRequest = request.get("scanRequest");
        Number maxBandwidth = (Number) request.get("maxBandwidthBytesPerSec");
        Long maxBandwidthBytesPerSec = maxBandwidth != null ? maxBandwidth.longValue() : null;
        String targetDirs = (String) request.get("targetDirs");
        Number preserveDirStructureNum = (Number) request.get("preserveDirStructure");
        Integer preserveDirStructure = preserveDirStructureNum != null ? preserveDirStructureNum.intValue() : 1;
        String scanCronExpression = (String) request.get("scanCronExpression");

        batchTaskScheduler.updateTaskConfig(taskId, sourceAgentId, sourceAgentApiUrl, scanRequest, targetAgents,
                targetDirs, preserveDirStructure, maxBandwidthBytesPerSec, scanCronExpression);

        return Map.of("success", true, "message", "Task config updated");
    }
}
