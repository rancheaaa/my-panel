package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.common.enums.OperationType;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchTransferOperationLog;
import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.service.IAgentRegistryService;
import com.cq.panel.admin.server.repository.service.IBatchTransferOperationLogService;
import com.cq.panel.admin.server.repository.service.IBatchTransferSubtaskService;
import com.cq.panel.admin.server.repository.service.IBatchTransferTaskService;
import com.cq.panel.admin.server.web.converter.batch.BatchTransferConverter;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskConfigUpdateDTO;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskCreateDTO;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTaskDetailVO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTaskVO;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BatchTransferService {
    private static final Logger logger = LoggerFactory.getLogger(BatchTransferService.class);

    private final IBatchTransferTaskService batchTransferTaskService;
    private final IBatchTransferSubtaskService batchTransferSubtaskService;
    private final IBatchTransferOperationLogService batchTransferOperationLogService;
    private final IAgentRegistryService agentRegistryService;
    private final ProxyApiClient proxyApiClient;
    private final BatchTransferConverter batchTransferConverter;

    public BatchTransferService(IBatchTransferTaskService batchTransferTaskService,
            IBatchTransferSubtaskService batchTransferSubtaskService,
            IBatchTransferOperationLogService batchTransferOperationLogService,
            IAgentRegistryService agentRegistryService,
            ProxyApiClient proxyApiClient,
            BatchTransferConverter batchTransferConverter) {
        this.batchTransferTaskService = batchTransferTaskService;
        this.batchTransferSubtaskService = batchTransferSubtaskService;
        this.batchTransferOperationLogService = batchTransferOperationLogService;
        this.agentRegistryService = agentRegistryService;
        this.proxyApiClient = proxyApiClient;
        this.batchTransferConverter = batchTransferConverter;
    }

    @Transactional
    public Long createTask(BatchTaskCreateDTO dto, String operatorId, String operatorName) {
        validateCreateTask(dto);
        BatchTransferTask entity = batchTransferConverter.toEntity(dto);
        if (dto.getIncludePatterns() != null) {
            entity.setIncludePatterns(toJsonString(dto.getIncludePatterns()));
        }
        if (dto.getExcludePatterns() != null) {
            entity.setExcludePatterns(toJsonString(dto.getExcludePatterns()));
        }
        if (dto.getTargetAgents() != null) {
            entity.setTargetAgents(toJsonString(dto.getTargetAgents()));
        }
        entity.setRetryEnabled(dto.getRetryEnabled() != null && dto.getRetryEnabled() ? 1 : 0);
        entity.setPreserveDirStructure(dto.getPreserveDirStructure() != null && dto.getPreserveDirStructure() ? 1 : 0);
        entity.setStatus("PENDING");
        entity.setDeleted(0);
        entity.setTotalFiles(0);
        entity.setTotalSizeBytes(0L);
        entity.setTransferredFiles(0);
        entity.setTransferredSizeBytes(0L);
        entity.setFailedFiles(0);
        entity.setPostProcessFiles(0);
        entity.setPostProcessFailed(0);
        batchTransferTaskService.insert(entity);
        logOperation(entity.getId(), OperationType.CREATE, null, null, operatorId, operatorName);
        return entity.getId();
    }

    public void startTask(Long taskId, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!"PENDING".equals(task.getStatus()) && !"PAUSED".equals(task.getStatus())) {
            throw new IllegalStateException("当前状态不允许启动，状态: " + task.getStatus());
        }
        validateAgentOnline(task.getSourceAgentId());

        String sourceAgentApiUrl = buildAgentApiUrl(task.getSourceAgentId());
        Map<String, Object> scanRequest = buildScanRequest(task);
        Map<String, Object> proxyRequest = new LinkedHashMap<>();
        proxyRequest.put("sourceAgentId", task.getSourceAgentId());
        proxyRequest.put("sourceAgentApiUrl", sourceAgentApiUrl);
        proxyRequest.put("targetAgents", parseJsonToList(task.getTargetAgents()));
        proxyRequest.put("transferMode", task.getTransferMode());
        proxyRequest.put("routingStrategy", task.getRoutingStrategy());
        proxyRequest.put("routingConfig", task.getRoutingConfig());
        proxyRequest.put("scanRequest", scanRequest);
        proxyRequest.put("maxBandwidthBytesPerSec",
                task.getMaxBandwidthKbS() != null ? task.getMaxBandwidthKbS() * 1024L : null);
        proxyRequest.put("targetDirs", task.getTargetDirs());
        proxyRequest.put("preserveDirStructure", task.getPreserveDirStructure());

        String oldStatus = task.getStatus();
        task.setStatus("SCANNING");
        task.setStartedAt(new Date());
        batchTransferTaskService.update(task);

        try {
            Map<String, Object> result = proxyApiClient.startTask(taskId, proxyRequest);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            if (!success) {
                batchTransferTaskService.updateStatus(taskId, "FAILED");
                throw new RuntimeException("Proxy启动任务失败: " + result.get("message"));
            }
            updateTaskFromProxyResult(taskId, result);
            logOperation(taskId, OperationType.START, oldStatus, "SCANNING", operatorId, operatorName);
        } catch (Exception e) {
            logger.error("Failed to start task {} via proxy: {}", taskId, e.getMessage(), e);
            batchTransferTaskService.updateStatus(taskId, "FAILED");
            throw new RuntimeException("启动任务失败: " + e.getMessage(), e);
        }
    }

    public void pauseTask(Long taskId, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!"TRANSFERRING".equals(task.getStatus()) && !"SCANNING".equals(task.getStatus())) {
            throw new IllegalStateException("当前状态不允许暂停，状态: " + task.getStatus());
        }
        String oldStatus = task.getStatus();
        String sourceAgentId = task.getSourceAgentId();
        String sourceAgentApiUrl = buildAgentApiUrl(sourceAgentId);
        try {
            proxyApiClient.pauseTask(taskId, sourceAgentId, sourceAgentApiUrl);
        } catch (Exception e) {
            logger.warn("Failed to notify proxy for pausing task {}, will update local status anyway: {}", taskId,
                    e.getMessage());
        }
        batchTransferTaskService.updateStatus(taskId, "PAUSED");
        logOperation(taskId, OperationType.PAUSE, oldStatus, "PAUSED", operatorId, operatorName);
    }

    public void resumeTask(Long taskId, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!"PAUSED".equals(task.getStatus())) {
            throw new IllegalStateException("当前状态不允许恢复，状态: " + task.getStatus());
        }
        String sourceAgentId = task.getSourceAgentId();
        String sourceAgentApiUrl = buildAgentApiUrl(sourceAgentId);
        try {
            proxyApiClient.resumeTask(taskId, sourceAgentId, sourceAgentApiUrl);
        } catch (Exception e) {
            logger.warn("Failed to notify proxy for resuming task {}, will update local status anyway: {}", taskId,
                    e.getMessage());
        }
        batchTransferTaskService.updateStatus(taskId, "TRANSFERRING");
        logOperation(taskId, OperationType.RESUME, "PAUSED", "TRANSFERRING", operatorId, operatorName);
    }

    public void cancelTask(Long taskId, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if ("COMPLETED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
            throw new IllegalStateException("当前状态不允许取消，状态: " + task.getStatus());
        }
        String oldStatus = task.getStatus();
        String sourceAgentId = task.getSourceAgentId();
        String sourceAgentApiUrl = buildAgentApiUrl(sourceAgentId);
        try {
            proxyApiClient.cancelTask(taskId, sourceAgentId, sourceAgentApiUrl);
        } catch (Exception e) {
            logger.warn("Failed to notify proxy for cancelling task {}, will update local status anyway: {}", taskId,
                    e.getMessage());
        }
        batchTransferTaskService.updateStatus(taskId, "CANCELLED");
        logOperation(taskId, OperationType.CANCEL, oldStatus, "CANCELLED", operatorId, operatorName);
    }

    @Transactional
    public void updateConfig(Long taskId, BatchTaskConfigUpdateDTO dto, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        String oldConfig = buildConfigSnapshot(task);
        if (dto.getScanFrequencySec() != null)
            task.setScanFrequencySec(dto.getScanFrequencySec());
        if (dto.getMaxScanFiles() != null)
            task.setMaxScanFiles(dto.getMaxScanFiles());
        if (dto.getMaxBandwidthKbS() != null)
            task.setMaxBandwidthKbS(dto.getMaxBandwidthKbS());
        if (dto.getRetryEnabled() != null)
            task.setRetryEnabled(dto.getRetryEnabled() ? 1 : 0);
        if (dto.getRetryIntervalMin() != null)
            task.setRetryIntervalMin(dto.getRetryIntervalMin());
        batchTransferTaskService.update(task);
        String newConfig = buildConfigSnapshot(task);
        logOperation(taskId, OperationType.CONFIG_UPDATE, oldConfig, newConfig, operatorId, operatorName);
    }

    public void retrySubtask(Long taskId, Long subtaskId, String operatorId, String operatorName) {
        BatchTransferSubtask subtask = batchTransferSubtaskService.selectById(subtaskId);
        if (subtask == null || !subtask.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("子任务不存在");
        }
        if (!"FAILED".equals(subtask.getStatus())) {
            throw new IllegalStateException("仅失败状态的子任务可重试");
        }
        batchTransferSubtaskService.updateStatus(subtaskId, "QUEUED");
        logOperation(taskId, OperationType.MANUAL_RETRY, null, "subtaskId=" + subtaskId, operatorId, operatorName);
    }

    public List<BatchTransferTask> listTasks(BatchTaskQueryDTO query) {
        BatchTransferTask entity = new BatchTransferTask();
        if (query.getStatus() != null)
            entity.setStatus(query.getStatus());
        if (query.getSourceAgentId() != null)
            entity.setSourceAgentId(query.getSourceAgentId());
        if (query.getKeyword() != null)
            entity.setTaskName(query.getKeyword());
        return batchTransferTaskService.selectList(entity);
    }

    public BatchTransferTask getTaskDetail(Long taskId) {
        return batchTransferTaskService.selectById(taskId);
    }

    public void deleteTasks(Long[] ids) {
        batchTransferTaskService.deleteByIds(ids);
    }

    private void validateCreateTask(BatchTaskCreateDTO dto) {
        String sourceDir = dto.getSourceDir();
        if (sourceDir.contains("..")) {
            throw new IllegalArgumentException("源目录路径不允许包含..");
        }
        if (!sourceDir.startsWith("/") && sourceDir.charAt(1) != ':') {
            throw new IllegalArgumentException("源目录必须是绝对路径");
        }
        if (sourceDir.endsWith("/") || sourceDir.endsWith("\\")) {
            throw new IllegalArgumentException("源目录路径不能以分隔符结尾");
        }
        String targetDirs = dto.getTargetDirs();
        if (targetDirs.contains("..")) {
            throw new IllegalArgumentException("目标目录路径不允许包含..");
        }
        String[] dirArr = targetDirs.split(";");
        if (dto.getTargetAgents() != null && dirArr.length != dto.getTargetAgents().size()) {
            throw new IllegalArgumentException(
                    "目标目录数量(" + dirArr.length + ")必须与目标Agent数量(" + dto.getTargetAgents().size() + ")一致");
        }
        for (String dir : dirArr) {
            String trimmedDir = dir.trim();
            if (!trimmedDir.startsWith("/") && trimmedDir.charAt(1) != ':') {
                throw new IllegalArgumentException("目标目录必须是绝对路径: " + trimmedDir);
            }
            if (trimmedDir.endsWith("/") || trimmedDir.endsWith("\\")) {
                throw new IllegalArgumentException("目标目录路径不能以分隔符结尾: " + trimmedDir);
            }
        }
        if (dto.getTargetAgents() != null && dto.getTargetAgents().contains(dto.getSourceAgentId())) {
            throw new IllegalArgumentException("目标Agent不能包含源Agent");
        }
        if (dto.getMaxBandwidthKbS() != null && dto.getMaxBandwidthKbS() <= 0) {
            throw new IllegalArgumentException("带宽限制必须大于0");
        }
        if ("ONE_TO_ONE".equals(dto.getTransferMode()) && "BROADCAST".equals(dto.getRoutingStrategy())) {
            dto.setRoutingStrategy("SINGLE");
        }
        if ("REGION_BASED".equals(dto.getRoutingStrategy())) {
            if (dto.getRoutingConfig() == null || dto.getRoutingConfig().isEmpty()) {
                throw new IllegalArgumentException("区域路由策略必须配置routingConfig");
            }
            try {
                new com.google.gson.Gson().fromJson(dto.getRoutingConfig(), Object.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("routingConfig必须是合法的JSON格式");
            }
        }
        if ("BACKUP".equals(dto.getPostTransferAction())
                && (dto.getBackupDir() == null || dto.getBackupDir().isEmpty())) {
            throw new IllegalArgumentException("备份操作必须指定备份目录");
        }
    }

    private void validateAgentOnline(String agentId) {
        AgentRegistry agent = agentRegistryService.selectAgentRegistryById(agentId);
        if (agent == null || agent.getNodeStatus() != 1) {
            throw new IllegalStateException("源Agent [" + agentId + "] 未注册或离线");
        }
    }

    private String buildAgentApiUrl(String agentId) {
        AgentRegistry agent = agentRegistryService.selectAgentRegistryById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent不存在: " + agentId);
        }
        return "http://" + agent.getAgentIp() + ":" + agent.getAgentPort();
    }

    private Map<String, Object> buildScanRequest(BatchTransferTask task) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("requestId", "scan-" + task.getId() + "-" + System.currentTimeMillis());
        request.put("taskId", task.getId());
        request.put("baseDir", task.getSourceDir());
        request.put("includePatterns", parseJsonToList(task.getIncludePatterns()));
        request.put("excludePatterns", parseJsonToList(task.getExcludePatterns()));
        request.put("maxFiles", task.getMaxScanFiles());
        request.put("computeMd5", false);
        return request;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonToList(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return new com.google.gson.Gson().fromJson(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private void updateTaskFromProxyResult(Long taskId, Map<String, Object> result) {
        Object dataObj = result.get("data");
        if (!(dataObj instanceof Map)) {
            return;
        }
        Map<String, Object> data = (Map<String, Object>) dataObj;
        Object scanResultObj = data.get("scanResult");
        if (scanResultObj instanceof Map) {
            Map<String, Object> scanResult = (Map<String, Object>) scanResultObj;
            BatchTransferTask update = new BatchTransferTask();
            update.setId(taskId);
            Object totalFiles = scanResult.get("totalFiles");
            Object totalSizeBytes = scanResult.get("totalSizeBytes");
            if (totalFiles instanceof Number)
                update.setTotalFiles(((Number) totalFiles).intValue());
            if (totalSizeBytes instanceof Number)
                update.setTotalSizeBytes(((Number) totalSizeBytes).longValue());
            update.setStatus("TRANSFERRING");
            batchTransferTaskService.update(update);
        }
    }

    private void logOperation(Long taskId, OperationType type, String oldVal, String newVal, String operatorId,
            String operatorName) {
        BatchTransferOperationLog log = new BatchTransferOperationLog();
        log.setTaskId(taskId);
        log.setOperationType(type.getCode());
        log.setOldConfig(oldVal);
        log.setNewConfig(newVal);
        log.setOperationTime(new Date());
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        batchTransferOperationLogService.insert(log);
    }

    private String buildConfigSnapshot(BatchTransferTask task) {
        return String.format(
                "{\"scanFrequencySec\":%d,\"maxScanFiles\":%d,\"maxBandwidthKbS\":%s,\"retryEnabled\":%d,\"retryIntervalMin\":%d}",
                task.getScanFrequencySec(), task.getMaxScanFiles(),
                task.getMaxBandwidthKbS() != null ? task.getMaxBandwidthKbS() : "null",
                task.getRetryEnabled(), task.getRetryIntervalMin());
    }

    private String toJsonString(Object obj) {
        try {
            return new com.google.gson.Gson().toJson(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
