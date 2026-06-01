package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchSyncEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.domain.BatchTransferTaskImport;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.mapper.BatchSyncEventMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskImportMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.repository.service.IBatchTaskImportService;
import com.cq.panel.admin.server.service.batch.BatchConfigSerializer;
import com.cq.panel.admin.server.service.batch.CronExpressionValidator;
import com.cq.panel.admin.server.service.batch.WildcardConflictDetector;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskImportDTO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportBatchVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportPreviewVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportRowVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BatchTaskImportServiceImpl implements IBatchTaskImportService {

    private static final Logger log = LoggerFactory.getLogger(BatchTaskImportServiceImpl.class);

    private static final Set<String> VALID_TRANSFER_MODES = Set.of("ONE_TO_ONE", "ONE_TO_MANY");
    private static final Set<String> VALID_ROUTING_STRATEGIES = Set.of("BROADCAST", "ROUND_ROBIN", "REGION_BASED", "RANDOM");
    private static final Set<String> VALID_RETRY_BACKOFF_TYPES = Set.of("LINEAR", "EXPONENTIAL");
    private static final Set<String> VALID_POST_TRANSFER_ACTIONS = Set.of("NONE", "DELETE", "BACKUP");
    private static final Set<Integer> VALID_PRESERVE_DIR_STRUCTURE = Set.of(0, 1);

    @Autowired
    private BatchTransferTaskImportMapper importMapper;

    @Autowired
    private BatchTransferTaskMapper taskMapper;

    @Autowired
    private BatchSyncEventMapper eventMapper;

    @Autowired
    private AgentRegistryMapper agentRegistryMapper;

    @Autowired
    private WildcardConflictDetector conflictDetector;

    @Autowired
    private CronExpressionValidator cronValidator;

    @Autowired
    private BatchConfigSerializer configSerializer;

    @Override
    public TaskImportPreviewVO uploadAndValidate(MultipartFile file, String userId) {
        List<BatchTaskImportDTO> excelRows = parseExcel(file);

        String batchNo = UUID.randomUUID().toString().replace("-", "");

        List<BatchTransferTaskImport> importRows = new ArrayList<>();
        for (int i = 0; i < excelRows.size(); i++) {
            BatchTaskImportDTO row = excelRows.get(i);
            BatchTransferTaskImport importRow = new BatchTransferTaskImport();
            copyRowFields(importRow, row);
            importRow.setBatchNo(batchNo);
            importRow.setRowNum(i + 1);
            importRow.setImportStatus("PENDING");
            importRow.setFileName(file.getOriginalFilename());
            importRow.setCreateBy(userId);
            importRow.setCreateTime(new Date());
            importRow.setUpdateBy(userId);
            importRow.setUpdateTime(new Date());

            List<String> resolveErrors = resolveAgentIds(importRow, row);
            List<String> errors = validateRow(row);
            errors.addAll(resolveErrors);

            if (errors.isEmpty()) {
                importRow.setValidateStatus("PASS");
                importRow.setValidateMessage("");
            } else {
                importRow.setValidateStatus("FAIL");
                importRow.setValidateMessage(String.join("; ", errors));
                fillNotNullPlaceholders(importRow);
            }

            importRows.add(importRow);
        }

        detectConflicts(importRows);

        importMapper.batchInsert(importRows);

        return buildPreviewVO(batchNo, importRows);
    }

    @Override
    public TaskImportPreviewVO preview(String batchNo) {
        List<BatchTransferTaskImport> rows = importMapper.selectByBatchNo(batchNo);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("批次不存在: " + batchNo);
        }
        return buildPreviewVO(batchNo, rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int commitImport(String batchNo, String mode) {
        List<BatchTransferTaskImport> rows = importMapper.selectByBatchNoAndImportStatus(batchNo, "PENDING");

        List<BatchTransferTaskImport> importableRows;
        if ("LOOSE".equalsIgnoreCase(mode)) {
            importableRows = rows.stream()
                    .filter(r -> canImportToTaskTable(r))
                    .collect(Collectors.toList());
        } else {
            importableRows = rows.stream()
                    .filter(r -> "PASS".equals(r.getValidateStatus()))
                    .collect(Collectors.toList());
        }

        if (importableRows.isEmpty()) {
            return 0;
        }

        recheckConflicts(importableRows);

        int successCount = 0;
        for (BatchTransferTaskImport importRow : importableRows) {
            BatchTransferTask task = buildTaskFromImport(importRow);
            taskMapper.insert(task);

            createSyncEvent("TASK_CREATED", task.getId(), task.getSourceAgentId(), task);

            importMapper.updateImportStatus(importRow.getId(), "IMPORTED", task.getId());
            successCount++;
        }

        log.info("批次导入完成: batchNo={}, mode={}, 成功{}条", batchNo, mode, successCount);
        return successCount;
    }

    private boolean canImportToTaskTable(BatchTransferTaskImport row) {
        if ("PASS".equals(row.getValidateStatus())) {
            return true;
        }
        if (isBlank(row.getTaskName()) || isPlaceholder(row.getTaskName())) {
            return false;
        }
        if (isBlank(row.getSourceAgentId()) || isPlaceholder(row.getSourceAgentId())) {
            return false;
        }
        if (isBlank(row.getSourceDir()) || isPlaceholder(row.getSourceDir())) {
            return false;
        }
        if (isBlank(row.getTargetAgentIds())) {
            return false;
        }
        return true;
    }

    private boolean isPlaceholder(String value) {
        return "N/A".equals(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rollbackImport(String batchNo) {
        List<BatchTransferTaskImport> rows = importMapper.selectByBatchNoAndImportStatus(batchNo, "IMPORTED");

        int rollbackCount = 0;
        for (BatchTransferTaskImport importRow : rows) {
            if (importRow.getImportedTaskId() != null) {
                BatchTransferTask task = taskMapper.selectById(importRow.getImportedTaskId());
                if (task != null) {
                    taskMapper.deleteById(task.getId());

                    createSyncEvent("TASK_DELETED", task.getId(), task.getSourceAgentId(), task);
                    rollbackCount++;
                } else {
                    log.warn("回退时主表任务不存在: importedTaskId={}", importRow.getImportedTaskId());
                }
            } else {
                log.warn("回退时importedTaskId为null, 跳过: importRowId={}", importRow.getId());
            }

            importMapper.updateImportStatus(importRow.getId(), "ROLLBACK", importRow.getImportedTaskId());
        }

        log.info("批次回退完成: batchNo={}, 回退{}条", batchNo, rollbackCount);
        return rollbackCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchStart(String batchNo) {
        List<BatchTransferTaskImport> rows = importMapper.selectByBatchNoAndImportStatus(batchNo, "IMPORTED");

        int count = 0;
        for (BatchTransferTaskImport importRow : rows) {
            if (importRow.getImportedTaskId() != null) {
                BatchTransferTask task = taskMapper.selectById(importRow.getImportedTaskId());
                if (task != null && ("READY".equals(task.getStatus()) || "PAUSED".equals(task.getStatus()))) {
                    task.setStatus("RUNNING");
                    if (task.getStartedAt() == null) {
                        task.setStartedAt(new Date());
                    }
                    task.setUpdateTime(new Date());
                    taskMapper.updateById(task);

                    createSyncEvent("TASK_STATUS_CHANGED", task.getId(), task.getSourceAgentId(), task);
                    count++;
                }
            }
        }

        log.info("批量启动完成: batchNo={}, 启动{}条", batchNo, count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchPause(String batchNo) {
        List<BatchTransferTaskImport> rows = importMapper.selectByBatchNoAndImportStatus(batchNo, "IMPORTED");

        int count = 0;
        for (BatchTransferTaskImport importRow : rows) {
            if (importRow.getImportedTaskId() != null) {
                BatchTransferTask task = taskMapper.selectById(importRow.getImportedTaskId());
                if (task != null && "RUNNING".equals(task.getStatus())) {
                    task.setStatus("PAUSED");
                    task.setUpdateTime(new Date());
                    taskMapper.updateById(task);

                    createSyncEvent("TASK_STATUS_CHANGED", task.getId(), task.getSourceAgentId(), task);
                    count++;
                }
            }
        }

        log.info("批量暂停完成: batchNo={}, 暂停{}条", batchNo, count);
        return count;
    }

    @Override
    public List<TaskImportBatchVO> listBatches(String searchKeyword) {
        List<Map<String, Object>> batchList = importMapper.selectBatchList(searchKeyword);
        List<TaskImportBatchVO> result = new ArrayList<>();

        for (Map<String, Object> map : batchList) {
            TaskImportBatchVO vo = new TaskImportBatchVO();
            vo.setBatchNo((String) map.get("batch_no"));
            vo.setFileName((String) map.get("file_name"));
            vo.setTotal(((Number) map.get("count")).intValue());
            vo.setPassCount(((Number) map.get("pass_count")).intValue());
            vo.setFailCount(((Number) map.get("fail_count")).intValue());
            vo.setImportStatus((String) map.get("import_status"));

            Object createTime = map.get("create_time");
            vo.setCreateTime(createTime != null ? createTime.toString() : null);

            result.add(vo);
        }

        return result;
    }

    @Override
    public void deleteBatch(String batchNo) {
        List<BatchTransferTaskImport> rows = importMapper.selectByBatchNo(batchNo);
        boolean hasImported = rows.stream().anyMatch(r -> "IMPORTED".equals(r.getImportStatus()));
        if (hasImported) {
            throw new IllegalStateException("批次中存在已导入的记录，请先回退再删除");
        }
        importMapper.deleteByBatchNo(batchNo);
        log.info("删除批次: batchNo={}", batchNo);
    }

    private List<BatchTaskImportDTO> parseExcel(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            ExcelUtil<BatchTaskImportDTO> util = new ExcelUtil<>(BatchTaskImportDTO.class);
            return util.importExcel(is, 0);
        } catch (Exception e) {
            log.error("解析Excel文件失败", e);
            throw new RuntimeException("解析Excel文件失败: " + e.getMessage(), e);
        }
    }

    private void copyRowFields(BatchTransferTaskImport target, BatchTaskImportDTO source) {
        target.setTaskName(source.getTaskName());
        target.setTaskDescription(source.getTaskDescription());
        target.setSourceAgentName(source.getSourceAgentName());
        target.setSourceDir(source.getSourceDir());
        target.setTargetAgentNames(source.getTargetAgentNames());
        target.setTargetDirs(source.getTargetDirs());
        target.setIncludePatterns(source.getIncludePatterns());
        target.setExcludePatterns(source.getExcludePatterns());
        target.setScanCronExpression(source.getScanCronExpression());
        target.setMaxScanFiles(source.getMaxScanFiles());
        target.setRetryEnabled(source.getRetryEnabled());
        target.setRetryMaxDays(source.getRetryMaxDays());
        target.setRetryIntervalMin(source.getRetryIntervalMin());
        target.setMaxRetryCount(source.getMaxRetryCount());
        target.setRetryBackoffType(source.getRetryBackoffType());
        target.setPostTransferAction(source.getPostTransferAction());
        target.setBackupDir(source.getBackupDir());
        target.setBackupMode(source.getBackupMode());
        target.setPreserveDirStructure(source.getPreserveDirStructure());
        target.setTransferMode(source.getTransferMode());
        target.setRoutingStrategy(source.getRoutingStrategy());
        target.setRoutingConfig(source.getRoutingConfig());
        target.setScheduledEnabled(source.getScheduledEnabled());
        target.setScheduledStartTime(source.getScheduledStartTime());
        target.setScheduledEndTime(source.getScheduledEndTime());
        target.setTaskPriority(source.getTaskPriority());
        target.setRemark(source.getRemark());
        target.setStatus("READY");
    }

    private List<String> validateRow(BatchTaskImportDTO row) {
        List<String> errors = new ArrayList<>();

        if (isBlank(row.getTaskName())) {
            errors.add("任务名称不能为空");
        }
        if (isBlank(row.getSourceAgentName())) {
            errors.add("源节点名称不能为空");
        }
        if (isBlank(row.getSourceDir())) {
            errors.add("源目录不能为空");
        }
        if (isBlank(row.getTargetAgentNames())) {
            errors.add("目标节点名称不能为空");
        }
        if (isBlank(row.getTargetDirs())) {
            errors.add("目标目录不能为空");
        }

        if (isBlank(row.getIncludePatterns()) && isBlank(row.getExcludePatterns())) {
            errors.add("包含模式和排除模式至少需要填写一个");
        }

        if (!isBlank(row.getTargetAgentNames()) && !isBlank(row.getTargetDirs())) {
            int nameCount = splitSemicolon(row.getTargetAgentNames()).size();
            int dirCount = splitSemicolon(row.getTargetDirs()).size();
            if (nameCount != dirCount) {
                errors.add("目标节点名称数量(" + nameCount + ")与目标目录数量(" + dirCount + ")不一致");
            }
        }

        if (!isBlank(row.getTransferMode()) && !VALID_TRANSFER_MODES.contains(row.getTransferMode())) {
            errors.add("传输模式无效: " + row.getTransferMode());
        }
        if (!isBlank(row.getRoutingStrategy()) && !VALID_ROUTING_STRATEGIES.contains(row.getRoutingStrategy())) {
            errors.add("路由策略无效: " + row.getRoutingStrategy());
        }
        if (!isBlank(row.getRetryBackoffType()) && !VALID_RETRY_BACKOFF_TYPES.contains(row.getRetryBackoffType())) {
            errors.add("重试退避策略无效: " + row.getRetryBackoffType());
        }
        if (!isBlank(row.getPostTransferAction()) && !VALID_POST_TRANSFER_ACTIONS.contains(row.getPostTransferAction())) {
            errors.add("传输后操作无效: " + row.getPostTransferAction());
        }
        if (row.getPreserveDirStructure() != null && !VALID_PRESERVE_DIR_STRUCTURE.contains(row.getPreserveDirStructure())) {
            errors.add("保持目录结构值无效: " + row.getPreserveDirStructure());
        }

        if (!isBlank(row.getScanCronExpression())) {
            if (!cronValidator.isValid(row.getScanCronExpression())) {
                errors.add("Cron表达式无效: " + cronValidator.getErrorMessage(row.getScanCronExpression()));
            }
        }

        if (row.getMaxScanFiles() != null && (row.getMaxScanFiles() < 1 || row.getMaxScanFiles() > 100000)) {
            errors.add("最大扫描文件数范围[1,100000]");
        }
        if (row.getRetryMaxDays() != null && (row.getRetryMaxDays() < 1 || row.getRetryMaxDays() > 30)) {
            errors.add("重试保留天数范围[1,30]");
        }
        if (row.getRetryIntervalMin() != null && (row.getRetryIntervalMin() < 1 || row.getRetryIntervalMin() > 1440)) {
            errors.add("重试间隔范围[1,1440]");
        }
        if (row.getMaxRetryCount() != null && (row.getMaxRetryCount() < 1 || row.getMaxRetryCount() > 100)) {
            errors.add("最大重试次数范围[1,100]");
        }
        if (row.getTaskPriority() != null && (row.getTaskPriority() < 1 || row.getTaskPriority() > 10)) {
            errors.add("优先级范围[1,10]");
        }

        validateSemicolonField(row.getTargetAgentNames(), "目标节点名称", errors);
        validateSemicolonField(row.getTargetDirs(), "目标目录", errors);
        if (!isBlank(row.getIncludePatterns())) {
            validateSemicolonField(row.getIncludePatterns(), "包含模式", errors);
        }
        if (!isBlank(row.getExcludePatterns())) {
            validateSemicolonField(row.getExcludePatterns(), "排除模式", errors);
        }

        return errors;
    }

    private void fillNotNullPlaceholders(BatchTransferTaskImport row) {
        if (isBlank(row.getSourceAgentId())) {
            row.setSourceAgentId("N/A");
        }
        if (isBlank(row.getSourceAgentName())) {
            row.setSourceAgentName("N/A");
        }
        if (isBlank(row.getSourceDir())) {
            row.setSourceDir("N/A");
        }
    }

    private List<String> resolveAgentIds(BatchTransferTaskImport importRow, BatchTaskImportDTO dto) {
        List<String> errors = new ArrayList<>();

        if (!isBlank(dto.getSourceAgentName())) {
            String sourceAgentId = resolveAgentIdByName(dto.getSourceAgentName());
            if (sourceAgentId != null) {
                importRow.setSourceAgentId(sourceAgentId);
            } else {
                errors.add("源节点名称[" + dto.getSourceAgentName() + "]未找到对应的Agent注册信息");
            }
        }

        if (!isBlank(dto.getTargetAgentNames())) {
            String[] names = dto.getTargetAgentNames().split(";");
            List<String> resolvedIds = new ArrayList<>();
            for (String name : names) {
                String trimmedName = name.trim();
                if (!trimmedName.isEmpty()) {
                    String agentId = resolveAgentIdByName(trimmedName);
                    if (agentId != null) {
                        resolvedIds.add(agentId);
                    } else {
                        errors.add("目标节点名称[" + trimmedName + "]未找到对应的Agent注册信息");
                    }
                }
            }
            if (!resolvedIds.isEmpty()) {
                importRow.setTargetAgentIds(String.join(";", resolvedIds));
            }
        }

        return errors;
    }

    private String resolveAgentIdByName(String nodeName) {
        AgentRegistry query = new AgentRegistry();
        query.setNodeName(nodeName);
        List<AgentRegistry> agents = agentRegistryMapper.selectAgentRegistryList(query);
        if (agents != null && !agents.isEmpty()) {
            return agents.get(0).getId();
        }
        return null;
    }

    private void validateSemicolonField(String value, String fieldName, List<String> errors) {
        if (isBlank(value)) {
            return;
        }
        String[] parts = value.split(";");
        for (String part : parts) {
            if (part.trim().isEmpty()) {
                errors.add(fieldName + "中存在空值");
                return;
            }
        }
    }

    private void detectConflicts(List<BatchTransferTaskImport> rows) {
        List<BatchTransferTaskImport> passRows = rows.stream()
                .filter(r -> "PASS".equals(r.getValidateStatus()))
                .collect(Collectors.toList());

        for (BatchTransferTaskImport importRow : passRows) {
            detectConflictWithExistingTasks(importRow);
        }

        detectIntraBatchConflicts(passRows);
    }

    private void detectConflictWithExistingTasks(BatchTransferTaskImport importRow) {
        List<String> includePatterns = splitSemicolon(importRow.getIncludePatterns());
        if (includePatterns.isEmpty()) {
            return;
        }

        List<String> excludePatterns = splitSemicolon(importRow.getExcludePatterns());

        BatchTransferTask query = new BatchTransferTask();
        query.setSourceAgentId(importRow.getSourceAgentId());
        List<BatchTransferTask> existingTasks = taskMapper.selectList(query);

        for (BatchTransferTask existing : existingTasks) {
            List<String> existingIncludePatterns = parseJsonArray(existing.getIncludePatterns());
            if (existingIncludePatterns.isEmpty()) {
                continue;
            }

            List<String> existingExcludePatterns = parseJsonArray(existing.getExcludePatterns());

            boolean hasConflict = conflictDetector.hasConflict(
                    importRow.getSourceDir(),
                    includePatterns,
                    excludePatterns,
                    existing.getSourceDir(),
                    existingIncludePatterns,
                    existingExcludePatterns
            );

            if (hasConflict) {
                importRow.setValidateStatus("FAIL");
                String msg = "与现有任务[" + existing.getTaskName() + "(ID=" + existing.getId() + ")]通配符冲突";
                importRow.setValidateMessage(
                        isBlank(importRow.getValidateMessage())
                                ? msg
                                : importRow.getValidateMessage() + "; " + msg
                );
                break;
            }
        }
    }

    private void detectIntraBatchConflicts(List<BatchTransferTaskImport> passRows) {
        Map<String, List<BatchTransferTaskImport>> grouped = passRows.stream()
                .filter(r -> "PASS".equals(r.getValidateStatus()))
                .collect(Collectors.groupingBy(
                        r -> r.getSourceAgentId() + "|" + r.getSourceDir()
                ));

        for (Map.Entry<String, List<BatchTransferTaskImport>> entry : grouped.entrySet()) {
            List<BatchTransferTaskImport> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }

            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    BatchTransferTaskImport row1 = group.get(i);
                    BatchTransferTaskImport row2 = group.get(j);

                    List<String> patterns1 = splitSemicolon(row1.getIncludePatterns());
                    List<String> patterns2 = splitSemicolon(row2.getIncludePatterns());

                    if (patterns1.isEmpty() || patterns2.isEmpty()) {
                        continue;
                    }

                    boolean hasConflict = conflictDetector.hasConflict(
                            row1.getSourceDir(),
                            patterns1,
                            splitSemicolon(row1.getExcludePatterns()),
                            row2.getSourceDir(),
                            patterns2,
                            splitSemicolon(row2.getExcludePatterns())
                    );

                    if (hasConflict) {
                        markConflict(row1, row2);
                    }
                }
            }
        }
    }

    private void markConflict(BatchTransferTaskImport row1, BatchTransferTaskImport row2) {
        String msg1 = "与第" + row2.getRowNum() + "行通配符冲突";
        String msg2 = "与第" + row1.getRowNum() + "行通配符冲突";

        markRowConflict(row1, msg1);
        markRowConflict(row2, msg2);
    }

    private void markRowConflict(BatchTransferTaskImport row, String msg) {
        row.setValidateStatus("FAIL");
        row.setValidateMessage(
                isBlank(row.getValidateMessage())
                        ? msg
                        : row.getValidateMessage() + "; " + msg
        );
    }

    private void recheckConflicts(List<BatchTransferTaskImport> passRows) {
        for (BatchTransferTaskImport importRow : passRows) {
            if (!"PASS".equals(importRow.getValidateStatus())) {
                continue;
            }

            List<String> includePatterns = splitSemicolon(importRow.getIncludePatterns());
            if (includePatterns.isEmpty()) {
                continue;
            }

            List<String> excludePatterns = splitSemicolon(importRow.getExcludePatterns());

            BatchTransferTask query = new BatchTransferTask();
            query.setSourceAgentId(importRow.getSourceAgentId());
            List<BatchTransferTask> existingTasks = taskMapper.selectList(query);

            for (BatchTransferTask existing : existingTasks) {
                List<String> existingIncludePatterns = parseJsonArray(existing.getIncludePatterns());
                if (existingIncludePatterns.isEmpty()) {
                    continue;
                }

                List<String> existingExcludePatterns = parseJsonArray(existing.getExcludePatterns());

                boolean hasConflict = conflictDetector.hasConflict(
                        importRow.getSourceDir(),
                        includePatterns,
                        excludePatterns,
                        existing.getSourceDir(),
                        existingIncludePatterns,
                        existingExcludePatterns
                );

                if (hasConflict) {
                    throw new IllegalStateException(
                            "提交时检测到冲突: 第" + importRow.getRowNum() + "行与现有任务[" +
                                    existing.getTaskName() + "(ID=" + existing.getId() + ")]通配符冲突"
                    );
                }
            }
        }
    }

    private BatchTransferTask buildTaskFromImport(BatchTransferTaskImport importRow) {
        BatchTransferTask task = new BatchTransferTask();
        task.setTaskName(importRow.getTaskName());
        task.setTaskDescription(importRow.getTaskDescription());
        task.setSourceAgentId(importRow.getSourceAgentId());
        task.setSourceAgentName(importRow.getSourceAgentName());
        task.setSourceDir(importRow.getSourceDir());
        task.setTargetDirs(importRow.getTargetDirs());
        task.setIncludePatterns(toJsonArray(importRow.getIncludePatterns()));
        task.setExcludePatterns(toJsonArray(importRow.getExcludePatterns()));
        task.setScanCronExpression(importRow.getScanCronExpression());
        task.setMaxScanFiles(importRow.getMaxScanFiles() != null ? importRow.getMaxScanFiles() : 10000);
        task.setTargetAgentIds(toJsonArray(importRow.getTargetAgentIds()));
        task.setTargetAgentNames(toJsonArray(importRow.getTargetAgentNames()));
        task.setRetryEnabled(importRow.getRetryEnabled() != null ? importRow.getRetryEnabled() : 1);
        task.setRetryMaxDays(importRow.getRetryMaxDays() != null ? importRow.getRetryMaxDays() : 7);
        task.setRetryIntervalMin(importRow.getRetryIntervalMin() != null ? importRow.getRetryIntervalMin() : 30);
        task.setMaxRetryCount(importRow.getMaxRetryCount() != null ? importRow.getMaxRetryCount() : 10);
        task.setRetryBackoffType(importRow.getRetryBackoffType() != null ? importRow.getRetryBackoffType() : "EXPONENTIAL");
        task.setPostTransferAction(importRow.getPostTransferAction() != null ? importRow.getPostTransferAction() : "NONE");
        task.setBackupDir(importRow.getBackupDir());
        task.setBackupMode(importRow.getBackupMode() != null ? importRow.getBackupMode() : "COPY");
        task.setPreserveDirStructure(importRow.getPreserveDirStructure() != null ? importRow.getPreserveDirStructure() : 1);
        task.setTransferMode(importRow.getTransferMode() != null ? importRow.getTransferMode() : "ONE_TO_MANY");
        task.setRoutingStrategy(importRow.getRoutingStrategy() != null ? importRow.getRoutingStrategy() : "BROADCAST");
        task.setRoutingConfig(importRow.getRoutingConfig());
        task.setStatus("READY");
        task.setDeleted(0);
        task.setScheduledEnabled(importRow.getScheduledEnabled() != null ? importRow.getScheduledEnabled() : 0);
        task.setScheduledStartTime(importRow.getScheduledStartTime());
        task.setScheduledEndTime(importRow.getScheduledEndTime());
        task.setTaskPriority(importRow.getTaskPriority() != null ? importRow.getTaskPriority() : 5);
        task.setCreateBy(importRow.getCreateBy());
        task.setCreateTime(new Date());
        task.setUpdateBy(importRow.getCreateBy());
        task.setUpdateTime(new Date());
        task.setRemark(importRow.getRemark());
        return task;
    }

    private String toJsonArray(String semicolonValue) {
        if (isBlank(semicolonValue)) {
            return null;
        }
        List<String> list = splitSemicolon(semicolonValue);
        try {
            return configSerializer.serialize(list);
        } catch (Exception e) {
            return semicolonValue;
        }
    }

    private List<String> splitSemicolon(String value) {
        if (isBlank(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> parseJsonArray(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return configSerializer.deserialize(json, List.class);
        } catch (Exception e) {
            return Arrays.asList(json.split(","));
        }
    }

    private void createSyncEvent(String eventType, Long taskId, String sourceAgentId, BatchTransferTask task) {
        try {
            BatchSyncEvent event = new BatchSyncEvent();
            event.setEventType(eventType);
            event.setTaskId(taskId);
            event.setSourceAgentId(sourceAgentId);
            event.setStatus("PENDING");
            event.setRetryCount(0);
            event.setExpireAt(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
            event.setPayload(configSerializer.serializeForAgent(task));
            eventMapper.insertEvent(event);
        } catch (Exception e) {
            log.error("创建同步事件失败: eventType={}, taskId={}", eventType, taskId, e);
            throw new RuntimeException("创建同步事件失败: eventType=" + eventType + ", taskId=" + taskId, e);
        }
    }

    private TaskImportPreviewVO buildPreviewVO(String batchNo, List<BatchTransferTaskImport> rows) {
        TaskImportPreviewVO vo = new TaskImportPreviewVO();
        vo.setBatchNo(batchNo);
        vo.setTotal(rows.size());

        int passCount = 0;
        int failCount = 0;
        List<TaskImportRowVO> rowVOs = new ArrayList<>();

        for (BatchTransferTaskImport row : rows) {
            if ("PASS".equals(row.getValidateStatus())) {
                passCount++;
            } else {
                failCount++;
            }

            TaskImportRowVO rowVO = new TaskImportRowVO();
            rowVO.setRowNum(row.getRowNum());
            rowVO.setTransferMode(row.getTransferMode());
            rowVO.setTaskName(row.getTaskName());
            rowVO.setTaskDescription(row.getTaskDescription());
            rowVO.setSourceAgentName(row.getSourceAgentName());
            rowVO.setSourceDir(row.getSourceDir());
            rowVO.setTargetAgentNames(row.getTargetAgentNames());
            rowVO.setTargetDirs(row.getTargetDirs());
            rowVO.setIncludePatterns(row.getIncludePatterns());
            rowVO.setExcludePatterns(row.getExcludePatterns());
            rowVO.setScanCronExpression(row.getScanCronExpression());
            rowVO.setMaxScanFiles(row.getMaxScanFiles());
            rowVO.setRetryEnabled(row.getRetryEnabled());
            rowVO.setRetryMaxDays(row.getRetryMaxDays());
            rowVO.setRetryIntervalMin(row.getRetryIntervalMin());
            rowVO.setMaxRetryCount(row.getMaxRetryCount());
            rowVO.setRetryBackoffType(row.getRetryBackoffType());
            rowVO.setPostTransferAction(row.getPostTransferAction());
            rowVO.setBackupDir(row.getBackupDir());
            rowVO.setBackupMode(row.getBackupMode());
            rowVO.setPreserveDirStructure(row.getPreserveDirStructure());
            rowVO.setRoutingStrategy(row.getRoutingStrategy());
            rowVO.setRoutingConfig(row.getRoutingConfig());
            rowVO.setScheduledEnabled(row.getScheduledEnabled());
            rowVO.setScheduledStartTime(row.getScheduledStartTime());
            rowVO.setScheduledEndTime(row.getScheduledEndTime());
            rowVO.setTaskPriority(row.getTaskPriority());
            rowVO.setRemark(row.getRemark());
            rowVO.setValidateStatus(row.getValidateStatus());
            rowVO.setValidateMessage(row.getValidateMessage());
            rowVO.setImportStatus(row.getImportStatus());
            rowVOs.add(rowVO);
        }

        vo.setPassCount(passCount);
        vo.setFailCount(failCount);
        vo.setRows(rowVOs);
        return vo;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
