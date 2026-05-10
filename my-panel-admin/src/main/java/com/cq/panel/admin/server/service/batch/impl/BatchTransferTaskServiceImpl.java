package com.cq.panel.admin.server.service.batch.impl;

import com.cq.panel.admin.server.repository.domain.BatchSyncEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.BatchSyncEventMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.service.batch.IBatchTransferTaskService;
import com.cq.panel.admin.server.service.batch.dto.BatchTransferTaskDTO;
import com.cq.panel.admin.server.service.batch.util.BatchConfigSerializer;
import com.cq.panel.admin.server.service.batch.util.CronExpressionValidator;
import com.cq.panel.admin.server.service.batch.util.WildcardConflictDetector;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BatchTransferTaskServiceImpl implements IBatchTransferTaskService {

    private static final Logger log = LoggerFactory.getLogger(BatchTransferTaskServiceImpl.class);

    private final BatchTransferTaskMapper taskMapper;
    private final BatchSyncEventMapper eventMapper;
    private final WildcardConflictDetector conflictDetector;
    private final CronExpressionValidator cronValidator;
    private final BatchConfigSerializer configSerializer;

    public BatchTransferTaskServiceImpl(
            BatchTransferTaskMapper taskMapper,
            BatchSyncEventMapper eventMapper,
            WildcardConflictDetector conflictDetector,
            CronExpressionValidator cronValidator,
            BatchConfigSerializer configSerializer) {
        this.taskMapper = taskMapper;
        this.eventMapper = eventMapper;
        this.conflictDetector = conflictDetector;
        this.cronValidator = cronValidator;
        this.configSerializer = configSerializer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(BatchTransferTaskDTO dto, String userId) {
        validateCreateTask(dto);

        checkWildcardConflict(dto);

        BatchTransferTask task = convertToEntity(dto);
        task.setStatus("READY");
        task.setCreateBy(userId);
        task.setCreateTime(new Date());
        task.setUpdateBy(userId);
        task.setUpdateTime(new Date());

        taskMapper.insert(task);

        createSyncEvent("TASK_CREATED", task.getId(), dto.getSourceAgentId(), task);

        log.info("创建批量传输任务成功: taskId={}, taskName={}", task.getId(), task.getTaskName());
        
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(Long taskId, BatchTransferTaskDTO dto) {
        BatchTransferTask existingTask = getExistingTask(taskId);

        checkWildcardConflictForUpdate(dto, existingTask);

        BatchTransferTask updatedTask = convertToEntity(dto);
        updatedTask.setId(taskId);
        updatedTask.setStatus(existingTask.getStatus());
        updatedTask.setCreateBy(existingTask.getCreateBy());
        updatedTask.setCreateTime(existingTask.getCreateTime());
        updatedTask.setUpdateBy(existingTask.getUpdateBy());
        updatedTask.setUpdateTime(new Date());

        taskMapper.updateById(updatedTask);

        createSyncEvent("TASK_UPDATED", taskId, existingTask.getSourceAgentId(), updatedTask);

        log.info("更新批量传输任务成功: taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startTask(Long taskId) {
        BatchTransferTask task = getExistingTask(taskId);

        if ("RUNNING".equals(task.getStatus())) {
            throw new IllegalStateException("任务已在运行中: " + taskId);
        }

        if (!"READY".equals(task.getStatus()) && !"PAUSED".equals(task.getStatus())) {
            throw new IllegalStateException("无法启动非就绪/暂停状态的任务，当前状态: " + task.getStatus());
        }

        task.setStatus("RUNNING");
        if (task.getStartedAt() == null) {
            task.setStartedAt(new Date());
        }
        task.setUpdateTime(new Date());
        taskMapper.updateById(task);

        createSyncEvent("TASK_STATUS_CHANGED", taskId, task.getSourceAgentId(), task);

        log.info("启动批量传输任务成功: taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseTask(Long taskId) {
        BatchTransferTask task = getExistingTask(taskId);

        if (!"RUNNING".equals(task.getStatus())) {
            throw new IllegalStateException("只能暂停运行中的任务，当前状态: " + task.getStatus());
        }

        task.setStatus("PAUSED");
        task.setUpdateTime(new Date());
        taskMapper.updateById(task);

        createSyncEvent("TASK_STATUS_CHANGED", taskId, task.getSourceAgentId(), task);

        log.info("暂停批量传输任务成功: taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeTask(Long taskId) {
        BatchTransferTask task = getExistingTask(taskId);

        if (!"PAUSED".equals(task.getStatus())) {
            throw new IllegalStateException("只能恢复暂停的任务，当前状态: " + task.getStatus());
        }

        task.setStatus("RUNNING");
        task.setUpdateTime(new Date());
        taskMapper.updateById(task);

        createSyncEvent("TASK_STATUS_CHANGED", taskId, task.getSourceAgentId(), task);

        log.info("恢复批量传输任务成功: taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stopTask(Long taskId) {
        BatchTransferTask task = getExistingTask(taskId);

        if (!"RUNNING".equals(task.getStatus()) && !"PAUSED".equals(task.getStatus())) {
            throw new IllegalStateException("只能停止运行中或暂停的任务，当前状态: " + task.getStatus());
        }

        task.setStatus("READY");
        task.setUpdateTime(new Date());
        taskMapper.updateById(task);

        createSyncEvent("TASK_STATUS_CHANGED", taskId, task.getSourceAgentId(), task);

        log.info("停止批量传输任务成功: taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTasks(List<Long> taskIds) {
        for (Long taskId : taskIds) {
            BatchTransferTask task = getExistingTask(taskId);
            
            taskMapper.deleteById(taskId);
            
            BatchSyncEvent deleteEvent = new BatchSyncEvent();
            deleteEvent.setEventType("TASK_DELETED");
            deleteEvent.setTaskId(taskId);
            deleteEvent.setSourceAgentId(task.getSourceAgentId());
            
            try {
                String payload = configSerializer.serialize(task);
                deleteEvent.setPayload(payload);
            } catch (Exception e) {
                deleteEvent.setPayload("{\"taskId\":" + taskId + "}");
            }
            
            eventMapper.insertEvent(deleteEvent);
        }
        
        log.info("批量删除传输任务成功: count={}, ids={}", taskIds.size(), taskIds);
    }

    @Override
    public BatchTransferTask getTaskById(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    @Override
    public List<BatchTransferTask> getTaskList(String status, String sourceAgentId, Integer pageNum, Integer pageSize) {
        BatchTransferTask query = new BatchTransferTask();
        
        if (status != null && !status.isEmpty()) {
            query.setStatus(status);
        }
        if (sourceAgentId != null && !sourceAgentId.isEmpty()) {
            query.setSourceAgentId(sourceAgentId);
        }
        
        return taskMapper.selectList(query);
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<BatchTransferTask> allTasks = taskMapper.selectList(new BatchTransferTask());
        
        long total = allTasks.size();
        long running = allTasks.stream().filter(t -> "RUNNING".equals(t.getStatus())).count();
        long paused = allTasks.stream().filter(t -> "PAUSED".equals(t.getStatus())).count();
        long ready = allTasks.stream().filter(t -> "READY".equals(t.getStatus())).count();
        
        stats.put("total", total);
        stats.put("running", running);
        stats.put("paused", paused);
        stats.put("ready", ready);
        stats.put("other", total - running - paused - ready);
        
        return stats;
    }

    private void validateCreateTask(BatchTransferTaskDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("任务信息不能为空");
        }
        if (dto.getTaskName() == null || dto.getTaskName().trim().isEmpty()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (dto.getSourceAgentId() == null || dto.getSourceAgentId().trim().isEmpty()) {
            throw new IllegalArgumentException("源Agent ID不能为空");
        }
        if (dto.getSourceDir() == null || dto.getSourceDir().trim().isEmpty()) {
            throw new IllegalArgumentException("源目录不能为空");
        }
        if (dto.getTargetDirs() == null || dto.getTargetDirs().trim().isEmpty()) {
            throw new IllegalArgumentException("目标目录不能为空");
        }
        if (dto.getTargetAgentIds() == null || dto.getTargetAgentIds().isEmpty()) {
            throw new IllegalArgumentException("目标Agent列表不能为空");
        }
        if (dto.getTargetAgentNames() == null || dto.getTargetAgentNames().isEmpty()) {
            throw new IllegalArgumentException("目标节点名称列表不能为空");
        }

        if (dto.getScanCronExpression() != null && !dto.getScanCronExpression().trim().isEmpty()) {
            if (!cronValidator.isValid(dto.getScanCronExpression())) {
                throw new IllegalArgumentException("无效的Cron表达式: " + cronValidator.getErrorMessage(dto.getScanCronExpression()));
            }
        }
    }

    private void checkWildcardConflict(BatchTransferTaskDTO dto) {
        if (dto.getIncludePatterns() != null && !dto.getIncludePatterns().isEmpty()) {
            List<BatchTransferTask> existingTasks = taskMapper.selectList(new BatchTransferTask());
            
            for (BatchTransferTask existing : existingTasks) {
                if (existing.getSourceDir() != null && existing.getSourceDir().equals(dto.getSourceDir())) {
                    List<String> existingPatterns = parseJsonArray(existing.getIncludePatterns());
                    
                    boolean hasConflict = conflictDetector.hasConflict(
                        dto.getSourceDir(),
                        dto.getIncludePatterns(),
                        dto.getExcludePatterns(),
                        existing.getSourceDir(),
                        existingPatterns,
                        parseJsonArray(existing.getExcludePatterns())
                    );
                    
                    if (hasConflict) {
                        throw new IllegalStateException(
                            "检测到通配符冲突! 新任务[" + dto.getTaskName() + "]与现有任务[" + 
                            existing.getTaskName() + "(ID=" + existing.getId() + ")的文件匹配范围重叠"
                        );
                    }
                }
            }
        }
    }

    private void checkWildcardConflictForUpdate(BatchTransferTaskDTO dto, BatchTransferTask existingTask) {
        if (dto.getIncludePatterns() != null && !dto.getIncludePatterns().isEmpty()) {
            List<BatchTransferTask> otherTasks = taskMapper.selectList(new BatchTransferTask())
                .stream()
                .filter(t -> !t.getId().equals(existingTask.getId()))
                .collect(Collectors.toList());
            
            for (BatchTransferTask other : otherTasks) {
                if (other.getSourceDir() != null && other.getSourceDir().equals(dto.getSourceDir())) {
                    List<String> otherPatterns = parseJsonArray(other.getIncludePatterns());
                    
                    boolean hasConflict = conflictDetector.hasConflict(
                        dto.getSourceDir(),
                        dto.getIncludePatterns(),
                        dto.getExcludePatterns(),
                        other.getSourceDir(),
                        otherPatterns,
                        parseJsonArray(other.getExcludePatterns())
                    );
                    
                    if (hasConflict) {
                        throw new IllegalStateException(
                            "更新后会产生通配符冲突! 任务[" + dto.getTaskName() + "]与任务[" + 
                            other.getTaskName() + "(ID=" + other.getId() + ")的文件匹配范围重叠"
                        );
                    }
                }
            }
        }
    }

    private BatchTransferTask getExistingTask(Long taskId) {
        BatchTransferTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return task;
    }

    private BatchTransferTask convertToEntity(BatchTransferTaskDTO dto) {
        BatchTransferTask task = new BatchTransferTask();
        task.setTaskName(dto.getTaskName());
        task.setTaskDescription(dto.getTaskDescription());
        task.setSourceAgentId(dto.getSourceAgentId());
        task.setSourceAgentName(dto.getSourceAgentName());
        task.setSourceDir(dto.getSourceDir());
        task.setTargetDirs(dto.getTargetDirs());
        task.setIncludePatterns(toJsonString(dto.getIncludePatterns()));
        task.setExcludePatterns(toJsonString(dto.getExcludePatterns()));
        task.setScanCronExpression(dto.getScanCronExpression());
        task.setMaxScanFiles(dto.getMaxScanFiles() != null ? dto.getMaxScanFiles() : 10000);
        task.setTargetAgentIds(toJsonString(dto.getTargetAgentIds()));
        task.setTargetAgentNames(toJsonString(dto.getTargetAgentNames()));
        task.setRetryEnabled(dto.getRetryEnabled() != null ? dto.getRetryEnabled() : 1);
        task.setRetryMaxDays(dto.getRetryMaxDays() != null ? dto.getRetryMaxDays() : 7);
        task.setRetryIntervalMin(dto.getRetryIntervalMin() != null ? dto.getRetryIntervalMin() : 30);
        task.setMaxRetryCount(dto.getMaxRetryCount() != null ? dto.getMaxRetryCount() : 10);
        task.setRetryBackoffType(dto.getRetryBackoffType() != null ? dto.getRetryBackoffType() : "EXPONENTIAL");
        task.setPostTransferAction(dto.getPostTransferAction() != null ? dto.getPostTransferAction() : "NONE");
        task.setBackupDir(dto.getBackupDir());
        task.setBackupMode(dto.getBackupMode() != null ? dto.getBackupMode() : "COPY");
        task.setPreserveDirStructure(dto.getPreserveDirStructure() != null ? dto.getPreserveDirStructure() : 1);
        task.setTransferMode(dto.getTransferMode() != null ? dto.getTransferMode() : "ONE_TO_MANY");
        task.setRoutingStrategy(dto.getRoutingStrategy() != null ? dto.getRoutingStrategy() : "BROADCAST");
        task.setRoutingConfig(dto.getRoutingConfig());
        task.setRemark(dto.getRemark());
        return task;
    }

    private void createSyncEvent(String eventType, Long taskId, String sourceAgentId, BatchTransferTask task) {
        try {
            BatchSyncEvent event = new BatchSyncEvent();
            event.setEventType(eventType);
            event.setTaskId(taskId);
            event.setSourceAgentId(sourceAgentId);
            event.setPayload(configSerializer.serialize(task));
            eventMapper.insertEvent(event);
        } catch (Exception e) {
            log.error("创建同步事件失败: eventType={}, taskId={}", eventType, taskId, e);
        }
    }

    private String toJsonString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return configSerializer.serialize(list);
        } catch (Exception e) {
            return "[" + String.join(",", list) + "]";
        }
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return configSerializer.deserialize(json, List.class);
        } catch (Exception e) {
            return Arrays.asList(json.split(","));
        }
    }
}
