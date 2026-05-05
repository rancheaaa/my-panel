package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.common.enums.OperationType;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchTransferOperationLog;
import com.cq.panel.admin.server.repository.domain.BatchTransferStatistics;
import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.service.IAgentRegistryService;
import com.cq.panel.admin.server.repository.service.IBatchTransferOperationLogService;
import com.cq.panel.admin.server.repository.service.IBatchTransferStatisticsService;
import com.cq.panel.admin.server.repository.service.IBatchTransferSubtaskService;
import com.cq.panel.admin.server.repository.service.IBatchTransferTaskService;
import com.cq.panel.admin.server.web.converter.batch.BatchTransferConverter;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskConfigUpdateDTO;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskCreateDTO;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTaskDetailVO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTaskDetailVO.SubtaskSummary;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTaskVO;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BatchTransferService {
    private static final Logger logger = LoggerFactory.getLogger(BatchTransferService.class);

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_STOPPED = "STOPPED";

    private final IBatchTransferTaskService batchTransferTaskService;
    private final IBatchTransferSubtaskService batchTransferSubtaskService;
    private final IBatchTransferOperationLogService batchTransferOperationLogService;
    private final IBatchTransferStatisticsService statisticsService;
    private final IAgentRegistryService agentRegistryService;
    private final ProxyApiClient proxyApiClient;
    private final BatchTransferConverter batchTransferConverter;

    public BatchTransferService(IBatchTransferTaskService batchTransferTaskService,
            IBatchTransferSubtaskService batchTransferSubtaskService,
            IBatchTransferOperationLogService batchTransferOperationLogService,
            IBatchTransferStatisticsService statisticsService,
            IAgentRegistryService agentRegistryService,
            ProxyApiClient proxyApiClient,
            BatchTransferConverter batchTransferConverter) {
        this.batchTransferTaskService = batchTransferTaskService;
        this.batchTransferSubtaskService = batchTransferSubtaskService;
        this.batchTransferOperationLogService = batchTransferOperationLogService;
        this.statisticsService = statisticsService;
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
        entity.setScanCronExpression(dto.getScanCronExpression());
        entity.setStatus(STATUS_DRAFT);
        entity.setDeleted(0);
        entity.setTotalFiles(0);
        entity.setTotalSizeBytes(0L);
        batchTransferTaskService.insert(entity);
        logOperation(entity.getId(), OperationType.CREATE, null, null, operatorId, operatorName);
        return entity.getId();
    }

    public void startTask(Long taskId, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!STATUS_DRAFT.equals(task.getStatus()) && !STATUS_PAUSED.equals(task.getStatus())) {
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
        proxyRequest.put("scanCronExpression", task.getScanCronExpression());

        String oldStatus = task.getStatus();
        task.setStatus(STATUS_RUNNING);
        if (task.getStartedAt() == null) {
            task.setStartedAt(new Date());
        }
        batchTransferTaskService.update(task);

        try {
            Map<String, Object> result = proxyApiClient.startTask(taskId, proxyRequest);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            if (!success) {
                task.setStatus(oldStatus);
                batchTransferTaskService.update(task);
                throw new RuntimeException("Proxy启动任务失败: " + result.get("message"));
            }
            updateTaskFromProxyResult(taskId, result);
            refreshStatistics(taskId);
            logOperation(taskId, OperationType.START, oldStatus, STATUS_RUNNING, operatorId, operatorName);
        } catch (Exception e) {
            logger.error("Failed to start task {} via proxy: {}", taskId, e.getMessage(), e);
            task.setStatus(oldStatus);
            batchTransferTaskService.update(task);
            throw new RuntimeException("启动任务失败: " + e.getMessage(), e);
        }
    }

    public void pauseTask(Long taskId, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!STATUS_RUNNING.equals(task.getStatus())) {
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
        batchTransferTaskService.updateStatus(taskId, STATUS_PAUSED);
        refreshStatistics(taskId);
        logOperation(taskId, OperationType.PAUSE, oldStatus, STATUS_PAUSED, operatorId, operatorName);
    }

    public void resumeTask(Long taskId, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!STATUS_PAUSED.equals(task.getStatus())) {
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
        batchTransferTaskService.updateStatus(taskId, STATUS_RUNNING);
        refreshStatistics(taskId);
        logOperation(taskId, OperationType.RESUME, STATUS_PAUSED, STATUS_RUNNING, operatorId, operatorName);
    }

    public void cancelTask(Long taskId, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (STATUS_STOPPED.equals(task.getStatus())) {
            throw new IllegalStateException("当前状态已停止，状态: " + task.getStatus());
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
        batchTransferTaskService.updateStatus(taskId, STATUS_STOPPED);
        refreshStatistics(taskId);
        logOperation(taskId, OperationType.CANCEL, oldStatus, STATUS_STOPPED, operatorId, operatorName);
    }

    @Transactional
    public void updateConfig(Long taskId, BatchTaskConfigUpdateDTO dto, String operatorId, String operatorName) {
        BatchTransferTask task = batchTransferTaskService.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!STATUS_DRAFT.equals(task.getStatus()) && !STATUS_PAUSED.equals(task.getStatus())) {
            throw new IllegalStateException("只有草稿或暂停状态的任务可以修改配置");
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
        refreshStatistics(taskId);
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

    public SubtaskSummary getSubtaskSummary(Long taskId) {
        List<BatchTransferSubtask> subtasks = batchTransferSubtaskService.selectByTaskId(taskId);
        return computeSubtaskSummary(taskId, subtasks);
    }

    public BatchTransferStatistics getStatistics(Long taskId) {
        BatchTransferStatistics stats = statisticsService.selectByTaskId(taskId);
        if (stats == null) {
            stats = refreshStatistics(taskId);
        }
        return stats;
    }

    public void deleteTasks(Long[] ids) {
        for (Long id : ids) {
            BatchTransferTask task = batchTransferTaskService.selectById(id);
            if (task != null && !STATUS_STOPPED.equals(task.getStatus()) && !STATUS_DRAFT.equals(task.getStatus())) {
                throw new IllegalStateException("任务[" + id + "]状态为[" + task.getStatus() + "]，只能删除已停止或草稿状态的任务");
            }
        }
        batchTransferTaskService.deleteByIds(ids);
        statisticsService.deleteByTaskIds(ids);
    }

    public BatchTransferStatistics refreshStatistics(Long taskId) {
        List<BatchTransferSubtask> subtasks = batchTransferSubtaskService.selectByTaskId(taskId);
        SubtaskSummary summary = computeSubtaskSummary(taskId, subtasks);

        BatchTransferTask task = batchTransferTaskService.selectById(taskId);

        BatchTransferStatistics stats = new BatchTransferStatistics();
        stats.setTaskId(taskId);
        stats.setSnapshotTime(new Date());

        stats.setTotalSubtasks(summary.getTotalSubtasks());
        stats.setCompletedCount(summary.getCompletedCount());
        stats.setFailedCount(summary.getFailedCount());
        stats.setRunningCount(summary.getRunningCount());
        stats.setQueuedCount(summary.getQueuedCount());
        stats.setRetryingCount(summary.getRetryingCount());
        stats.setCancelledCount(summary.getCancelledCount());

        stats.setTotalSizeBytes(summary.getTotalSizeBytes());
        stats.setTransferredBytes(summary.getTransferredSizeBytes());
        stats.setTransferredFiles(summary.getCompletedCount());
        stats.setFailedFiles(summary.getFailedCount());
        stats.setRemainingBytes(summary.getTotalSizeBytes() - summary.getTransferredSizeBytes());
        stats.setProgressPercent(summary.getProgressPercent());

        stats.setStartedAt(task != null ? task.getStartedAt() : null);
        stats.setLastActivityAt(new Date());

        long totalRetry = subtasks.stream()
                .filter(s -> s.getRetryCount() != null)
                .mapToInt(s -> s.getRetryCount())
                .sum();
        int proxyRetrySum = subtasks.stream()
                .filter(s -> s.getProxyRetryCount() != null)
                .mapToInt(s -> s.getProxyRetryCount())
                .sum();
        stats.setTotalRetryCount((int) totalRetry + proxyRetrySum);

        int failedWithRetries = (int) subtasks.stream()
                .filter(s -> "FAILED".equals(s.getStatus()) && s.getRetryCount() != null && s.getRetryCount() > 0)
                .count();
        stats.setMaxSingleFileRetries(failedWithRetries > 0
                ? subtasks.stream().filter(s -> "FAILED".equals(s.getStatus()) && s.getRetryCount() != null)
                        .mapToInt(s -> s.getRetryCount()).max().orElse(0)
                : 0);
        if (failedWithRetries > 0) {
            double avg = subtasks.stream()
                    .filter(s -> "FAILED".equals(s.getStatus()) && s.getRetryCount() != null)
                    .mapToInt(s -> s.getRetryCount())
                    .average().orElse(0);
            stats.setAvgRetryCount(BigDecimal.valueOf(avg).setScale(2, BigDecimal.ROUND_HALF_UP));
        }

        BatchTransferStatistics existing = statisticsService.selectByTaskId(taskId);
        if (existing != null) {
            stats.setId(existing.getId());
            stats.setDataVersion(existing.getDataVersion() != null ? existing.getDataVersion() + 1 : 1);
            statisticsService.updateByTaskId(stats);
        } else {
            statisticsService.insert(stats);
        }
        return stats;
    }

    private SubtaskSummary computeSubtaskSummary(Long taskId, List<BatchTransferSubtask> subtasks) {
        SubtaskSummary summary = new SubtaskSummary();
        summary.setTotalSubtasks(subtasks.size());

        int completedCount = 0;
        int failedCount = 0;
        int runningCount = 0;
        int queuedCount = 0;
        int retryingCount = 0;
        int cancelledCount = 0;
        long totalSizeBytes = 0L;
        long transferredSizeBytes = 0L;

        for (BatchTransferSubtask subtask : subtasks) {
            String status = subtask.getStatus();
            switch (status) {
                case "COMPLETED":
                    completedCount++;
                    transferredSizeBytes += subtask.getTransferredBytes() != null ? subtask.getTransferredBytes() : 0L;
                    break;
                case "FAILED":
                    failedCount++;
                    break;
                case "SENDING":
                    runningCount++;
                    break;
                case "QUEUED":
                    queuedCount++;
                    break;
                case "RETRYING":
                    retryingCount++;
                    break;
                case "CANCELLED":
                    cancelledCount++;
                    break;
                default:
                    break;
            }
            totalSizeBytes += subtask.getFileSizeBytes() != null ? subtask.getFileSizeBytes() : 0L;
        }

        summary.setCompletedCount(completedCount);
        summary.setFailedCount(failedCount);
        summary.setRunningCount(runningCount);
        summary.setQueuedCount(queuedCount);
        summary.setRetryingCount(retryingCount);
        summary.setCancelledCount(cancelledCount);
        summary.setTotalSizeBytes(totalSizeBytes);
        summary.setTransferredSizeBytes(transferredSizeBytes);

        if (summary.getTotalSubtasks() > 0) {
            double progress = (completedCount * 100.0) / summary.getTotalSubtasks();
            summary.setProgressPercent(BigDecimal.valueOf(progress).setScale(2, BigDecimal.ROUND_HALF_UP));
        } else {
            summary.setProgressPercent(BigDecimal.ZERO);
        }

        return summary;
    }

    private void validateCreateTask(BatchTaskCreateDTO dto) {
        String sourceDir = dto.getSourceDir();
        if (sourceDir.contains("..")) {
            throw new IllegalArgumentException("源目录路径不允许包含..");
        }
        if (!sourceDir.startsWith("/") && (sourceDir.length() < 2 || sourceDir.charAt(1) != ':')) {
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
            if (!trimmedDir.startsWith("/") && (trimmedDir.length() < 2 || trimmedDir.charAt(1) != ':')) {
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
            update.setStatus(STATUS_RUNNING);
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
}
