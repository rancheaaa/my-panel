package com.cq.panel.admin.server.service.batch.impl;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchSyncEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.mapper.BatchSyncEventMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.service.batch.IBatchTransferTaskService;
import com.cq.panel.admin.server.service.batch.dto.BatchTransferTaskDTO;
import com.cq.panel.admin.server.service.batch.util.AgentDirectoryChecker;
import com.cq.panel.admin.server.service.batch.util.BatchConfigSerializer;
import com.cq.panel.admin.server.service.batch.util.CronExpressionValidator;
import com.cq.panel.admin.server.service.batch.util.WildcardConflictDetector;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskListWithStatusVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskNodeStatusVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final BatchTransferSubtaskMapper subtaskMapper;
    private final BatchSyncEventMapper eventMapper;
    private final AgentRegistryMapper agentRegistryMapper;
    private final AgentDirectoryChecker directoryChecker;
    private final WildcardConflictDetector conflictDetector;
    private final CronExpressionValidator cronValidator;
    private final BatchConfigSerializer configSerializer;
    private final ObjectMapper objectMapper;

    public BatchTransferTaskServiceImpl(
            BatchTransferTaskMapper taskMapper,
            BatchTransferSubtaskMapper subtaskMapper,
            BatchSyncEventMapper eventMapper,
            AgentRegistryMapper agentRegistryMapper,
            AgentDirectoryChecker directoryChecker,
            WildcardConflictDetector conflictDetector,
            CronExpressionValidator cronValidator,
            BatchConfigSerializer configSerializer,
            ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.subtaskMapper = subtaskMapper;
        this.eventMapper = eventMapper;
        this.agentRegistryMapper = agentRegistryMapper;
        this.directoryChecker = directoryChecker;
        this.conflictDetector = conflictDetector;
        this.cronValidator = cronValidator;
        this.configSerializer = configSerializer;
        this.objectMapper = objectMapper;
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
    public void updateTask(Long taskId, BatchTransferTaskDTO dto, String userId) {
        BatchTransferTask existingTask = getExistingTask(taskId);

        checkWildcardConflictForUpdate(dto, existingTask);

        BatchTransferTask updatedTask = convertToEntity(dto);
        updatedTask.setId(taskId);
        updatedTask.setStatus(existingTask.getStatus());
        updatedTask.setCreateBy(existingTask.getCreateBy());
        updatedTask.setCreateTime(existingTask.getCreateTime());
        updatedTask.setUpdateBy(userId);
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

        taskMapper.deleteById(taskId);

        createSyncEvent("TASK_DELETED", taskId, task.getSourceAgentId(), task);

        log.info("停止批量传输任务成功(逻辑删除): taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTasks(List<Long> taskIds) {
        for (Long taskId : taskIds) {
            BatchTransferTask task = getExistingTask(taskId);
            
            taskMapper.deleteById(taskId);
            
            // 使用createSyncEvent确保事务一致性（事件插入失败会抛出异常触发回滚）
            createSyncEvent("TASK_DELETED", taskId, task.getSourceAgentId(), task);
        }
        
        log.info("批量删除传输任务成功: count={}, ids={}", taskIds.size(), taskIds);
    }

    @Override
    public BatchTransferTask getTaskById(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    @Override
    public List<BatchTransferTask> getTaskList(BatchTransferTask query, Integer pageNum, Integer pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return taskMapper.selectPageList(query, offset, pageSize);
    }

    @Override
    public List<TaskListWithStatusVO> getTaskListWithNodeStatus(BatchTransferTask query, Integer pageNum, Integer pageSize) {
        List<BatchTransferTask> tasks = getTaskList(query, pageNum, pageSize);
        List<TaskListWithStatusVO> result = new ArrayList<>();

        for (BatchTransferTask task : tasks) {
            TaskListWithStatusVO vo = new TaskListWithStatusVO();
            vo.setTask(task);

            // 查询源节点状态
            TaskNodeStatusVO sourceStatus = buildNodeStatus(task.getSourceAgentId(), task.getSourceDir());
            vo.setSourceNodeStatus(sourceStatus);

            // 查询目标节点状态
            List<TaskNodeStatusVO> targetStatusList = buildTargetNodeStatusList(task);
            vo.setTargetNodeStatusList(targetStatusList);

            result.add(vo);
        }

        return result;
    }

    /**
     * 构建单个节点状态
     */
    private TaskNodeStatusVO buildNodeStatus(String agentId, String dirPath) {
        TaskNodeStatusVO status = new TaskNodeStatusVO();
        status.setAgentId(agentId);
        status.setDirPath(dirPath);

        AgentRegistry agent = agentRegistryMapper.selectAgentRegistryById(agentId);
        if (agent != null) {
            status.setAgentName(agent.getNodeName());
            status.setNodeStatus(agent.getNodeStatus());

            if (agent.getNodeStatus() != null && agent.getNodeStatus() == 1) {
                Boolean dirExists = directoryChecker.checkDirectoryExists(agentId, dirPath);
                status.setDirExists(dirExists != null ? dirExists : Boolean.TRUE);
            } else {
                status.setDirExists(Boolean.FALSE);
            }
        } else {
            status.setAgentName(null);
            status.setNodeStatus(0);
            status.setDirExists(Boolean.FALSE);
        }

        return status;
    }

    /**
     * 构建目标节点状态列表
     */
    private List<TaskNodeStatusVO> buildTargetNodeStatusList(BatchTransferTask task) {
        List<TaskNodeStatusVO> statusList = new ArrayList<>();

        try {
            List<String> targetAgentIds = parseJsonArray(task.getTargetAgentIds());
            List<String> targetAgentNames = parseJsonArray(task.getTargetAgentNames());
            List<String> targetDirs = parseTargetDirs(task.getTargetDirs());

            for (int i = 0; i < targetAgentIds.size(); i++) {
                String agentId = targetAgentIds.get(i);
                String dirPath = i < targetDirs.size() ? targetDirs.get(i) : "";

                TaskNodeStatusVO status = new TaskNodeStatusVO();
                status.setAgentId(agentId);
                status.setDirPath(dirPath);

                AgentRegistry agent = agentRegistryMapper.selectAgentRegistryById(agentId);
                if (agent != null) {
                    status.setAgentName(agent.getNodeName());
                    status.setNodeStatus(agent.getNodeStatus());

                    if (agent.getNodeStatus() != null && agent.getNodeStatus() == 1) {
                        Boolean dirExists = directoryChecker.checkDirectoryExists(agentId, dirPath);
                        status.setDirExists(dirExists != null ? dirExists : Boolean.TRUE);
                    } else {
                        status.setDirExists(Boolean.FALSE);
                    }
                } else {
                    status.setAgentName(targetAgentNames.size() > i ? targetAgentNames.get(i) : null);
                    status.setNodeStatus(0);
                    status.setDirExists(Boolean.FALSE);
                }

                statusList.add(status);
            }
        } catch (Exception e) {
            log.warn("解析目标节点信息失败: taskId={}", task.getId(), e);
        }

        return statusList;
    }

    /**
     * 解析分号分隔的目标目录
     */
    private List<String> parseTargetDirs(String targetDirs) {
        if (targetDirs == null || targetDirs.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(targetDirs.split(";"));
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

    @Override
    public Map<String, Object> getTaskStatistics(Long taskId) {
        BatchTransferTask task = getExistingTask(taskId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("taskId", taskId);
        stats.put("taskName", task.getTaskName());
        stats.put("status", task.getStatus());

        List<Map<String, Object>> statusCounts = subtaskMapper.countByTaskIdGroupByStatus(taskId);
        long totalSubtasks = 0;
        for (Map<String, Object> row : statusCounts) {
            String status = (String) row.get("status");
            long count = ((Number) row.get("count")).longValue();
            stats.put(status.toLowerCase() + "Count", count);
            totalSubtasks += count;
        }
        stats.put("totalSubtasks", totalSubtasks);

        return stats;
    }

    private void validateCreateTask(BatchTransferTaskDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("任务信息不能为空");
        }
        List<String> missingFields = new ArrayList<>();
        if (dto.getTaskName() == null || dto.getTaskName().trim().isEmpty()) {
            missingFields.add("任务名称");
        }
        if (dto.getSourceAgentId() == null || dto.getSourceAgentId().trim().isEmpty()) {
            missingFields.add("源节点");
        }
        if (dto.getSourceDir() == null || dto.getSourceDir().trim().isEmpty()) {
            missingFields.add("源目录");
        }
        if (dto.getTargetAgentIds() == null || dto.getTargetAgentIds().isEmpty()) {
            missingFields.add("目标节点");
        }
        if (dto.getTargetDirs() == null || dto.getTargetDirs().trim().isEmpty()) {
            missingFields.add("目标目录");
        }
        if (dto.getScanCronExpression() == null || dto.getScanCronExpression().trim().isEmpty()) {
            missingFields.add("执行频率(Cron)");
        }
        if (!missingFields.isEmpty()) {
            throw new IllegalArgumentException("以下必填字段未填写: " + String.join("、", missingFields));
        }

        if (!cronValidator.isValid(dto.getScanCronExpression())) {
            throw new IllegalArgumentException("无效的Cron表达式: " + cronValidator.getErrorMessage(dto.getScanCronExpression()));
        }
    }

    private void checkWildcardConflict(BatchTransferTaskDTO dto) {
        if (dto.getIncludePatterns() != null && !dto.getIncludePatterns().isEmpty()) {
            BatchTransferTask query = new BatchTransferTask();
            query.setSourceAgentId(dto.getSourceAgentId());
            List<BatchTransferTask> existingTasks = taskMapper.selectList(query);

            for (BatchTransferTask existing : existingTasks) {
                List<String> existingPatterns = parseJsonArray(existing.getIncludePatterns());
                if (existingPatterns.isEmpty()) {
                    continue;
                }

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

    private void checkWildcardConflictForUpdate(BatchTransferTaskDTO dto, BatchTransferTask existingTask) {
        if (dto.getIncludePatterns() != null && !dto.getIncludePatterns().isEmpty()) {
            BatchTransferTask query = new BatchTransferTask();
            query.setSourceAgentId(dto.getSourceAgentId());
            List<BatchTransferTask> otherTasks = taskMapper.selectList(query)
                .stream()
                .filter(t -> !t.getId().equals(existingTask.getId()))
                .collect(Collectors.toList());

            for (BatchTransferTask other : otherTasks) {
                List<String> otherPatterns = parseJsonArray(other.getIncludePatterns());
                if (otherPatterns.isEmpty()) {
                    continue;
                }

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
            event.setStatus("PENDING");
            event.setRetryCount(0);
            // 设置过期时间：24小时后
            event.setExpireAt(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
            event.setPayload(configSerializer.serializeForAgent(task));
            eventMapper.insertEvent(event);
        } catch (Exception e) {
            log.error("创建同步事件失败: eventType={}, taskId={}", eventType, taskId, e);
            // 抛出异常以触发事务回滚，确保配置更新和事件插入的原子性
            // spec.md要求：如果事件插入失败，整个事务必须回滚
            throw new RuntimeException("创建同步事件失败: eventType=" + eventType + ", taskId=" + taskId, e);
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
