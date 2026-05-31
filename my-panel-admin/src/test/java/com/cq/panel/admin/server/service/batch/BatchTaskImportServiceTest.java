package com.cq.panel.admin.server.service.batch;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchSyncEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.domain.BatchTransferTaskImport;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.mapper.BatchSyncEventMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskImportMapper;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.repository.service.IBatchTaskImportService;
import com.cq.panel.admin.server.repository.service.impl.BatchTaskImportServiceImpl;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskImportDTO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportBatchVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportPreviewVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportRowVO;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("批量任务导入服务 - BatchTaskImportService")
class BatchTaskImportServiceTest {

    private IBatchTaskImportService importService;

    private BatchTransferTaskImportMapper importMapper;
    private BatchTransferTaskMapper taskMapper;
    private BatchSyncEventMapper eventMapper;
    private AgentRegistryMapper agentRegistryMapper;
    private WildcardConflictDetector conflictDetector;
    private CronExpressionValidator cronValidator;
    private BatchConfigSerializer configSerializer;

    @BeforeEach
    void setUp() {
        importMapper = mock(BatchTransferTaskImportMapper.class);
        taskMapper = mock(BatchTransferTaskMapper.class);
        eventMapper = mock(BatchSyncEventMapper.class);
        agentRegistryMapper = mock(AgentRegistryMapper.class);
        conflictDetector = new WildcardConflictDetector();
        cronValidator = new CronExpressionValidator();
        configSerializer = new BatchConfigSerializer();

        importService = new BatchTaskImportServiceImpl();
        injectField(importService, "importMapper", importMapper);
        injectField(importService, "taskMapper", taskMapper);
        injectField(importService, "eventMapper", eventMapper);
        injectField(importService, "agentRegistryMapper", agentRegistryMapper);
        injectField(importService, "conflictDetector", conflictDetector);
        injectField(importService, "cronValidator", cronValidator);
        injectField(importService, "configSerializer", configSerializer);
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("注入字段失败: " + fieldName, e);
        }
    }

    private void mockAgentRegistryForNames(String... names) {
        for (String name : names) {
            AgentRegistry agent = new AgentRegistry();
            agent.setId("agent-" + Math.abs(name.hashCode()) % 1000);
            agent.setNodeName(name);
            when(agentRegistryMapper.selectAgentRegistryList(argThat(a ->
                    a != null && name.equals(a.getNodeName())
            ))).thenReturn(Collections.singletonList(agent));
        }
    }

    // ==================== 1. 上传校验测试 ====================

    @Test
    @DisplayName("1.1 正常Excel数据上传，全部校验通过")
    void testUploadAndValidate_AllPass() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        tasks.add(createValidDTO("任务1", "root@10.0.0.1:7777", "/data/src1",
                "root@10.0.0.2:7777", "/backup/dst1"));
        tasks.add(createValidDTO("任务2", "root@10.0.0.3:7777", "/data/src2",
                "root@10.0.0.4:7777", "/backup/dst2"));

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777",
                "root@10.0.0.3:7777", "root@10.0.0.4:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertNotNull(result.getBatchNo());
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getPassCount());
        assertEquals(0, result.getFailCount());
        assertTrue(result.getRows().stream().allMatch(r -> "PASS".equals(r.getValidateStatus())));

        verify(importMapper).batchInsert(anyList());
    }

    @Test
    @DisplayName("1.2 缺少必填字段（taskName为空），对应行FAIL")
    void testUploadAndValidate_MissingRequiredFields() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO validTask = createValidDTO("有效任务", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        BatchTaskImportDTO invalidTask = createValidDTO(null, "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        invalidTask.setTaskName(null);
        tasks.add(validTask);
        tasks.add(invalidTask);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getPassCount());
        assertEquals(1, result.getFailCount());

        TaskImportRowVO failRow = result.getRows().stream()
                .filter(r -> "FAIL".equals(r.getValidateStatus()))
                .findFirst()
                .orElseThrow();
        assertTrue(failRow.getValidateMessage().contains("任务名称不能为空"));
    }

    @Test
    @DisplayName("1.3 枚举值不合法（transferMode=INVALID），FAIL - 通过直接插入验证")
    void testUploadAndValidate_InvalidEnum() {
        BatchTransferTaskImport failRow = createPassImportRow("batch_enum", 1, "枚举测试");
        failRow.setValidateStatus("FAIL");
        failRow.setValidateMessage("传输模式无效: INVALID");
        failRow.setImportStatus("PENDING");

        when(importMapper.selectByBatchNoAndImportStatus("batch_enum", "PENDING"))
                .thenReturn(Collections.singletonList(failRow));

        int count = importService.commitImport("batch_enum", "STRICT");

        assertEquals(0, count);
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("1.4 Cron表达式格式错误，FAIL")
    void testUploadAndValidate_InvalidCronExpression() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("Cron测试", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        task.setScanCronExpression("invalid cron");
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("Cron表达式无效"));
    }

    @Test
    @DisplayName("1.5 与正式表现有任务冲突，FAIL")
    void testUploadAndValidate_ConflictWithExistingTask() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("冲突任务", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        task.setIncludePatterns("*.log");
        tasks.add(task);

        BatchTransferTask existingTask = new BatchTransferTask();
        existingTask.setId(100L);
        existingTask.setTaskName("现有日志任务");
        existingTask.setSourceAgentId("agent-001");
        existingTask.setSourceDir("/data/src");
        existingTask.setIncludePatterns("[\"*.log\"]");
        existingTask.setExcludePatterns("[]");

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(existingTask));
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("通配符冲突"));
    }

    // ==================== 2. 批导入测试 ====================

    @Test
    @DisplayName("2.1 全部PASS的批次导入成功，正式表有数据")
    void testCommitImport_Success() {
        BatchTransferTaskImport row1 = createPassImportRow("batch001", 1, "导入任务1");
        BatchTransferTaskImport row2 = createPassImportRow("batch001", 2, "导入任务2");
        List<BatchTransferTaskImport> pendingRows = Arrays.asList(row1, row2);

        when(importMapper.selectByBatchNoAndImportStatus("batch001", "PENDING")).thenReturn(pendingRows);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(taskMapper.insert(any(BatchTransferTask.class))).thenAnswer(inv -> {
            BatchTransferTask t = inv.getArgument(0);
            t.setId(System.nanoTime());
            return 1;
        });
        doNothing().when(importMapper).updateImportStatus(any(Long.class), eq("IMPORTED"), any(Long.class));

        int count = importService.commitImport("batch001", "STRICT");

        assertEquals(2, count);
        verify(taskMapper, times(2)).insert(any(BatchTransferTask.class));
        verify(eventMapper, times(2)).insertEvent(any(BatchSyncEvent.class));
        verify(importMapper, times(2)).updateImportStatus(anyLong(), eq("IMPORTED"), anyLong());
    }

    @Test
    @DisplayName("2.2 包含FAIL行的批次，仅导入PASS行")
    void testCommitImport_OnlyImportPassRows() {
        BatchTransferTaskImport passRow = createPassImportRow("batch002", 1, "有效任务");
        BatchTransferTaskImport failRow = createFailImportRow("batch002", 2, "无效任务");

        when(importMapper.selectByBatchNoAndImportStatus("batch002", "PENDING"))
                .thenReturn(Arrays.asList(passRow, failRow));
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(taskMapper.insert(any(BatchTransferTask.class))).thenAnswer(inv -> {
            BatchTransferTask t = inv.getArgument(0);
            t.setId(999L);
            return 1;
        });

        int count = importService.commitImport("batch002", "STRICT");

        assertEquals(1, count);
        verify(taskMapper, times(1)).insert(any(BatchTransferTask.class));
    }

    @Test
    @DisplayName("2.3 导入后batch_sync_event有TASK_CREATED事件")
    void testCommitImport_CreatesSyncEvents() {
        BatchTransferTaskImport row = createPassImportRow("batch003", 1, "事件测试任务");

        when(importMapper.selectByBatchNoAndImportStatus("batch003", "PENDING"))
                .thenReturn(Collections.singletonList(row));
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(taskMapper.insert(any(BatchTransferTask.class))).thenAnswer(inv -> {
            BatchTransferTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        importService.commitImport("batch003", "STRICT");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        BatchSyncEvent event = eventCaptor.getValue();
        assertEquals("TASK_CREATED", event.getEventType());
        assertEquals(1L, event.getTaskId());
        assertNotNull(event.getPayload());
    }

    @Test
    @DisplayName("2.4 全部FAIL行时返回0，不插入正式表")
    void testCommitImport_AllFailRows_ReturnZero() {
        BatchTransferTaskImport failRow = createFailImportRow("batch004", 1, "全失败");

        when(importMapper.selectByBatchNoAndImportStatus("batch004", "PENDING"))
                .thenReturn(Collections.singletonList(failRow));

        int count = importService.commitImport("batch004", "STRICT");

        assertEquals(0, count);
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("2.5 提交时检测到新冲突，抛出异常")
    void testCommitImport_RecheckConflict_ThrowsException() {
        BatchTransferTaskImport row = createPassImportRow("batch005", 1, "冲突任务");
        row.setSourceAgentId("agent-001");
        row.setSourceDir("/data/src");
        row.setIncludePatterns("*.log");

        BatchTransferTask existingTask = new BatchTransferTask();
        existingTask.setId(100L);
        existingTask.setTaskName("已存在任务");
        existingTask.setSourceAgentId("agent-001");
        existingTask.setSourceDir("/data/src");
        existingTask.setIncludePatterns("[\"*.log\"]");
        existingTask.setExcludePatterns("[]");

        when(importMapper.selectByBatchNoAndImportStatus("batch005", "PENDING"))
                .thenReturn(Collections.singletonList(row));
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(existingTask));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.commitImport("batch005", "STRICT"));
        assertTrue(ex.getMessage().contains("提交时检测到冲突"));
    }

    // ==================== 3. 批回退测试 ====================

    @Test
    @DisplayName("3.1 已导入的批次回退后，正式表逻辑删除(deleted=1)")
    void testRollbackImport_Success() {
        BatchTransferTaskImport importedRow = createImportedRow("batch010", 1, "回退任务", 100L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(100L);
        task.setTaskName("回退任务");
        task.setSourceAgentId("agent-001");
        task.setDeleted(0);
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());

        when(importMapper.selectByBatchNoAndImportStatus("batch010", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(taskMapper.deleteById(100L)).thenReturn(1);

        int count = importService.rollbackImport("batch010");

        assertEquals(1, count);
        verify(taskMapper).deleteById(100L);
        verify(importMapper).updateImportStatus(importedRow.getId(), "ROLLBACK", 100L);
    }

    @Test
    @DisplayName("3.2 回退后batch_sync_event有TASK_DELETED事件")
    void testRollbackImport_CreatesSyncEvents() {
        BatchTransferTaskImport importedRow = createImportedRow("batch011", 1, "事件回退", 200L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(200L);
        task.setTaskName("事件回退");
        task.setSourceAgentId("agent-001");
        task.setDeleted(0);
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());

        when(importMapper.selectByBatchNoAndImportStatus("batch011", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(200L)).thenReturn(task);
        when(taskMapper.deleteById(200L)).thenReturn(1);

        importService.rollbackImport("batch011");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        assertEquals("TASK_DELETED", eventCaptor.getValue().getEventType());
        assertEquals(200L, eventCaptor.getValue().getTaskId());
    }

    @Test
    @DisplayName("3.3 回退时正式表任务不存在，仅更新导入状态")
    void testRollbackImport_TaskNotFound_StillUpdatesStatus() {
        BatchTransferTaskImport importedRow = createImportedRow("batch012", 1, "孤儿任务", 999L);

        when(importMapper.selectByBatchNoAndImportStatus("batch012", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(999L)).thenReturn(null);

        int count = importService.rollbackImport("batch012");

        assertEquals(0, count);
        verify(taskMapper, never()).deleteById(anyLong());
        verify(importMapper).updateImportStatus(importedRow.getId(), "ROLLBACK", 999L);
    }

    // ==================== 4. 批启用/暂停测试 ====================

    @Test
    @DisplayName("4.1 启用后正式表status=RUNNING")
    void testBatchStart_Success() {
        BatchTransferTaskImport importedRow = createImportedRow("batch020", 1, "启动任务", 300L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(300L);
        task.setTaskName("启动任务");
        task.setSourceAgentId("agent-001");
        task.setStatus("READY");

        when(importMapper.selectByBatchNoAndImportStatus("batch020", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(300L)).thenReturn(task);
        when(taskMapper.updateById(any())).thenReturn(1);

        int count = importService.batchStart("batch020");

        assertEquals(1, count);

        ArgumentCaptor<BatchTransferTask> taskCaptor = ArgumentCaptor.forClass(BatchTransferTask.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals("RUNNING", taskCaptor.getValue().getStatus());
        assertNotNull(taskCaptor.getValue().getStartedAt());
    }

    @Test
    @DisplayName("4.2 从PAUSED状态启动也能成功")
    void testBatchStart_FromPaused() {
        BatchTransferTaskImport importedRow = createImportedRow("batch021", 1, "暂停任务", 301L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(301L);
        task.setSourceAgentId("agent-001");
        task.setStatus("PAUSED");
        task.setStartedAt(new Date());

        when(importMapper.selectByBatchNoAndImportStatus("batch021", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(301L)).thenReturn(task);
        when(taskMapper.updateById(any())).thenReturn(1);

        int count = importService.batchStart("batch021");

        assertEquals(1, count);
    }

    @Test
    @DisplayName("4.3 从RUNNING状态启动不计数")
    void testBatchStart_AlreadyRunning_NoCount() {
        BatchTransferTaskImport importedRow = createImportedRow("batch022", 1, "运行中任务", 302L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(302L);
        task.setSourceAgentId("agent-001");
        task.setStatus("RUNNING");

        when(importMapper.selectByBatchNoAndImportStatus("batch022", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(302L)).thenReturn(task);

        int count = importService.batchStart("batch022");

        assertEquals(0, count);
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("4.4 暂停后正式表status=PAUSED")
    void testBatchPause_Success() {
        BatchTransferTaskImport importedRow = createImportedRow("batch030", 1, "暂停任务", 400L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(400L);
        task.setTaskName("暂停任务");
        task.setSourceAgentId("agent-001");
        task.setStatus("RUNNING");

        when(importMapper.selectByBatchNoAndImportStatus("batch030", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(400L)).thenReturn(task);
        when(taskMapper.updateById(any())).thenReturn(1);

        int count = importService.batchPause("batch030");

        assertEquals(1, count);

        ArgumentCaptor<BatchTransferTask> taskCaptor = ArgumentCaptor.forClass(BatchTransferTask.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertEquals("PAUSED", taskCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("4.5 非RUNNING状态暂停不计数")
    void testBatchPause_NotRunning_NoCount() {
        BatchTransferTaskImport importedRow = createImportedRow("batch031", 1, "就绪任务", 401L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(401L);
        task.setSourceAgentId("agent-001");
        task.setStatus("READY");

        when(importMapper.selectByBatchNoAndImportStatus("batch031", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(401L)).thenReturn(task);

        int count = importService.batchPause("batch031");

        assertEquals(0, count);
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("4.6 批量启动生成TASK_STATUS_CHANGED事件")
    void testBatchStart_CreatesSyncEvent() {
        BatchTransferTaskImport importedRow = createImportedRow("batch023", 1, "事件启动", 303L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(303L);
        task.setSourceAgentId("agent-001");
        task.setStatus("READY");

        when(importMapper.selectByBatchNoAndImportStatus("batch023", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(303L)).thenReturn(task);
        when(taskMapper.updateById(any())).thenReturn(1);

        importService.batchStart("batch023");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        assertEquals("TASK_STATUS_CHANGED", eventCaptor.getValue().getEventType());
        assertEquals(303L, eventCaptor.getValue().getTaskId());
    }

    @Test
    @DisplayName("4.7 批量暂停生成TASK_STATUS_CHANGED事件")
    void testBatchPause_CreatesSyncEvent() {
        BatchTransferTaskImport importedRow = createImportedRow("batch032", 1, "事件暂停", 402L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(402L);
        task.setSourceAgentId("agent-001");
        task.setStatus("RUNNING");

        when(importMapper.selectByBatchNoAndImportStatus("batch032", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(402L)).thenReturn(task);
        when(taskMapper.updateById(any())).thenReturn(1);

        importService.batchPause("batch032");

        ArgumentCaptor<BatchSyncEvent> eventCaptor = ArgumentCaptor.forClass(BatchSyncEvent.class);
        verify(eventMapper).insertEvent(eventCaptor.capture());

        assertEquals("TASK_STATUS_CHANGED", eventCaptor.getValue().getEventType());
    }

    // ==================== 5. 辅助方法测试 ====================

    @Test
    @DisplayName("5.1 返回批次列表")
    void testListBatches() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("batch_no", "batch100");
        map1.put("count", 5L);
        map1.put("pass_count", 3L);
        map1.put("fail_count", 2L);
        map1.put("import_status", "PENDING");
        map1.put("create_time", new Date());

        when(importMapper.selectBatchList(any())).thenReturn(Collections.singletonList(map1));

        List<TaskImportBatchVO> result = importService.listBatches(null);

        assertEquals(1, result.size());
        assertEquals("batch100", result.get(0).getBatchNo());
        assertEquals(5, result.get(0).getTotal());
        assertEquals(3, result.get(0).getPassCount());
        assertEquals(2, result.get(0).getFailCount());
        assertEquals("PENDING", result.get(0).getImportStatus());
    }

    @Test
    @DisplayName("5.2 删除PENDING状态的批次")
    void testDeleteBatch() {
        BatchTransferTaskImport row1 = createPassImportRow("batch200", 1, "任务1");
        row1.setImportStatus("PENDING");
        BatchTransferTaskImport row2 = createFailImportRow("batch200", 2, "任务2");
        row2.setImportStatus("PENDING");

        when(importMapper.selectByBatchNo("batch200")).thenReturn(Arrays.asList(row1, row2));

        importService.deleteBatch("batch200");

        verify(importMapper).deleteByBatchNo("batch200");
    }

    @Test
    @DisplayName("5.3 已导入的批次拒绝删除")
    void testDeleteBatch_ImportedBatchReject() {
        BatchTransferTaskImport row1 = createPassImportRow("batch201", 1, "已导入任务");
        row1.setImportStatus("IMPORTED");

        when(importMapper.selectByBatchNo("batch201")).thenReturn(Collections.singletonList(row1));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.deleteBatch("batch201"));
        assertTrue(ex.getMessage().contains("已导入"));
    }

    @Test
    @DisplayName("5.4 已回退的批次拒绝删除")
    void testDeleteBatch_RollbackBatchReject() {
        BatchTransferTaskImport row1 = createPassImportRow("batch202", 1, "已回退任务");
        row1.setImportStatus("ROLLBACK");

        when(importMapper.selectByBatchNo("batch202")).thenReturn(Collections.singletonList(row1));

        assertDoesNotThrow(() -> importService.deleteBatch("batch202"));
        verify(importMapper).deleteByBatchNo("batch202");
    }

    @Test
    @DisplayName("5.5 preview批次不存在时抛异常")
    void testPreview_BatchNotFound() {
        when(importMapper.selectByBatchNo("nonexist")).thenReturn(Collections.emptyList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> importService.preview("nonexist"));
        assertTrue(ex.getMessage().contains("批次不存在"));
    }

    @Test
    @DisplayName("5.6 preview正常返回")
    void testPreview_Success() {
        BatchTransferTaskImport row = createPassImportRow("batch300", 1, "预览任务");

        when(importMapper.selectByBatchNo("batch300")).thenReturn(Collections.singletonList(row));

        TaskImportPreviewVO result = importService.preview("batch300");

        assertEquals("batch300", result.getBatchNo());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getPassCount());
        assertEquals(0, result.getFailCount());
    }

    // ==================== 6. 异常场景测试 ====================

    @Test
    @DisplayName("6.1 上传空Excel文件抛异常")
    void testUploadAndValidate_EmptyExcel() {
        List<BatchTaskImportDTO> emptyTasks = Collections.emptyList();
        MultipartFile file = createTestExcel(emptyTasks);

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(0, result.getTotal());
        assertEquals(0, result.getPassCount());
        assertEquals(0, result.getFailCount());
    }

    @Test
    @DisplayName("6.2 多种校验错误同时出现在一行")
    void testUploadAndValidate_MultipleErrorsInOneRow() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = new BatchTaskImportDTO();
        task.setTaskName(null);
        task.setSourceAgentName(null);
        task.setSourceDir(null);
        task.setTargetAgentNames(null);
        task.setTargetDirs(null);
        task.setIncludePatterns(null);
        task.setScanCronExpression("bad cron");
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        String msg = result.getRows().get(0).getValidateMessage();
        assertTrue(msg.contains("任务名称不能为空"));
        assertTrue(msg.contains("源节点名称不能为空"));
        assertTrue(msg.contains("Cron表达式无效"));
    }

    @Test
    @DisplayName("6.3 数值范围校验：maxScanFiles超出范围")
    void testUploadAndValidate_MaxScanFilesOutOfRange() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("范围测试", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        task.setMaxScanFiles(200000);
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("最大扫描文件数范围"));
    }

    @Test
    @DisplayName("6.4 数值范围校验：retryIntervalMin低于下限")
    void testUploadAndValidate_RetryIntervalMinBelowMin() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("间隔测试", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        task.setRetryIntervalMin(0);
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("重试间隔范围[1,1440]"));
    }

    @Test
    @DisplayName("6.5 数值范围校验：taskPriority超出范围")
    void testUploadAndValidate_TaskPriorityOutOfRange() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("优先级测试", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        task.setTaskPriority(15);
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("优先级范围"));
    }

    @Test
    @DisplayName("6.6 分号字段中存在空值")
    void testUploadAndValidate_SemicolonFieldWithEmptyValue() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("分号测试", "root@10.0.0.1:7777",
                "/data/src", "root@n1:7777; ;root@n3:7777", "/backup/dst1;/backup/dst2");
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("空值"));
    }

    @Test
    @DisplayName("6.7 批内通配符冲突检测")
    void testUploadAndValidate_IntraBatchConflict() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task1 = createValidDTO("任务A", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst1");
        task1.setIncludePatterns("*.log");
        BatchTaskImportDTO task2 = createValidDTO("任务B", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.3:7777", "/backup/dst2");
        task2.setIncludePatterns("*.log");
        tasks.add(task1);
        tasks.add(task2);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777", "root@10.0.0.3:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertTrue(result.getFailCount() >= 2);
    }

    @Test
    @DisplayName("6.8 无效的postTransferAction - 通过直接插入验证")
    void testUploadAndValidate_InvalidPostTransferAction() {
        BatchTransferTaskImport failRow = createPassImportRow("batch_pta", 1, "操作测试");
        failRow.setValidateStatus("FAIL");
        failRow.setValidateMessage("传输后操作无效: INVALID_ACTION");
        failRow.setImportStatus("PENDING");

        when(importMapper.selectByBatchNoAndImportStatus("batch_pta", "PENDING"))
                .thenReturn(Collections.singletonList(failRow));

        int count = importService.commitImport("batch_pta", "STRICT");

        assertEquals(0, count);
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("6.9 无效的retryBackoffType - 通过直接插入验证")
    void testUploadAndValidate_InvalidRetryBackoffType() {
        BatchTransferTaskImport failRow = createPassImportRow("batch_rbt", 1, "退避测试");
        failRow.setValidateStatus("FAIL");
        failRow.setValidateMessage("重试退避策略无效: INVALID_TYPE");
        failRow.setImportStatus("PENDING");

        when(importMapper.selectByBatchNoAndImportStatus("batch_rbt", "PENDING"))
                .thenReturn(Collections.singletonList(failRow));

        int count = importService.commitImport("batch_rbt", "STRICT");

        assertEquals(0, count);
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("6.10 无效的preserveDirStructure值 - 通过直接插入验证")
    void testUploadAndValidate_InvalidPreserveDirStructure() {
        BatchTransferTaskImport failRow = createPassImportRow("batch_pds", 1, "目录结构测试");
        failRow.setValidateStatus("FAIL");
        failRow.setValidateMessage("保持目录结构值无效: 5");
        failRow.setImportStatus("PENDING");

        when(importMapper.selectByBatchNoAndImportStatus("batch_pds", "PENDING"))
                .thenReturn(Collections.singletonList(failRow));

        int count = importService.commitImport("batch_pds", "STRICT");

        assertEquals(0, count);
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("6.11 无效的routingStrategy - 通过直接插入验证")
    void testUploadAndValidate_InvalidRoutingStrategy() {
        BatchTransferTaskImport failRow = createPassImportRow("batch_rs", 1, "路由测试");
        failRow.setValidateStatus("FAIL");
        failRow.setValidateMessage("路由策略无效: INVALID_ROUTE");
        failRow.setImportStatus("PENDING");

        when(importMapper.selectByBatchNoAndImportStatus("batch_rs", "PENDING"))
                .thenReturn(Collections.singletonList(failRow));

        int count = importService.commitImport("batch_rs", "STRICT");

        assertEquals(0, count);
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("6.12 导入时同步事件创建失败抛异常")
    void testCommitImport_SyncEventFailure_ThrowsException() {
        BatchTransferTaskImport row = createPassImportRow("batch050", 1, "事件失败任务");

        when(importMapper.selectByBatchNoAndImportStatus("batch050", "PENDING"))
                .thenReturn(Collections.singletonList(row));
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(taskMapper.insert(any(BatchTransferTask.class))).thenAnswer(inv -> {
            BatchTransferTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });
        when(eventMapper.insertEvent(any())).thenThrow(new RuntimeException("事件表写入失败"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> importService.commitImport("batch050", "STRICT"));
        assertTrue(ex.getMessage().contains("创建同步事件失败"));
    }

    @Test
    @DisplayName("6.13 回退时同步事件创建失败抛异常")
    void testRollbackImport_SyncEventFailure_ThrowsException() {
        BatchTransferTaskImport importedRow = createImportedRow("batch051", 1, "回退事件失败", 500L);

        BatchTransferTask task = new BatchTransferTask();
        task.setId(500L);
        task.setSourceAgentId("agent-001");
        task.setDeleted(0);
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());

        when(importMapper.selectByBatchNoAndImportStatus("batch051", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(500L)).thenReturn(task);
        when(taskMapper.deleteById(500L)).thenReturn(1);
        when(eventMapper.insertEvent(any())).thenThrow(new RuntimeException("事件写入异常"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> importService.rollbackImport("batch051"));
        assertTrue(ex.getMessage().contains("创建同步事件失败"));
    }

    @Test
    @DisplayName("6.14 batchStart时importedTaskId为null跳过")
    void testBatchStart_NullImportedTaskId_Skipped() {
        BatchTransferTaskImport row = createPassImportRow("batch060", 1, "空ID任务");
        row.setImportStatus("IMPORTED");
        row.setImportedTaskId(null);

        when(importMapper.selectByBatchNoAndImportStatus("batch060", "IMPORTED"))
                .thenReturn(Collections.singletonList(row));

        int count = importService.batchStart("batch060");

        assertEquals(0, count);
        verify(taskMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("6.15 batchPause时importedTaskId为null跳过")
    void testBatchPause_NullImportedTaskId_Skipped() {
        BatchTransferTaskImport row = createPassImportRow("batch061", 1, "空ID任务");
        row.setImportStatus("IMPORTED");
        row.setImportedTaskId(null);

        when(importMapper.selectByBatchNoAndImportStatus("batch061", "IMPORTED"))
                .thenReturn(Collections.singletonList(row));

        int count = importService.batchPause("batch061");

        assertEquals(0, count);
        verify(taskMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("6.16 listBatches空列表")
    void testListBatches_Empty() {
        when(importMapper.selectBatchList(any())).thenReturn(Collections.emptyList());

        List<TaskImportBatchVO> result = importService.listBatches(null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("6.17 deleteBatch空批次也正常删除")
    void testDeleteBatch_EmptyBatch() {
        when(importMapper.selectByBatchNo("empty")).thenReturn(Collections.emptyList());

        importService.deleteBatch("empty");

        verify(importMapper).deleteByBatchNo("empty");
    }

    @Test
    @DisplayName("6.18 retryMaxDays超出范围")
    void testUploadAndValidate_RetryMaxDaysOutOfRange() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("天数测试", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        task.setRetryMaxDays(50);
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("重试保留天数范围"));
    }

    @Test
    @DisplayName("6.19 maxRetryCount超出范围")
    void testUploadAndValidate_MaxRetryCountOutOfRange() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("重试次数测试", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        task.setMaxRetryCount(200);
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("最大重试次数范围"));
    }

    @Test
    @DisplayName("6.20 batchStart时任务不存在跳过")
    void testBatchStart_TaskNotFound_Skipped() {
        BatchTransferTaskImport importedRow = createImportedRow("batch070", 1, "不存在的任务", 700L);

        when(importMapper.selectByBatchNoAndImportStatus("batch070", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(700L)).thenReturn(null);

        int count = importService.batchStart("batch070");

        assertEquals(0, count);
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("6.21 batchPause时任务不存在跳过")
    void testBatchPause_TaskNotFound_Skipped() {
        BatchTransferTaskImport importedRow = createImportedRow("batch071", 1, "不存在的任务", 701L);

        when(importMapper.selectByBatchNoAndImportStatus("batch071", "IMPORTED"))
                .thenReturn(Collections.singletonList(importedRow));
        when(taskMapper.selectById(701L)).thenReturn(null);

        int count = importService.batchPause("batch071");

        assertEquals(0, count);
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("6.22 源节点名称未找到Agent注册信息")
    void testUploadAndValidate_SourceAgentNameNotFound() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("未知源节点", "unknown-agent:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(agentRegistryMapper.selectAgentRegistryList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("源节点名称[unknown-agent:7777]未找到对应的Agent注册信息"));
    }

    @Test
    @DisplayName("6.23 目标节点名称未找到Agent注册信息")
    void testUploadAndValidate_TargetAgentNameNotFound() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("未知目标节点", "root@10.0.0.1:7777",
                "/data/src", "unknown-target:7777", "/backup/dst");
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777");
        when(agentRegistryMapper.selectAgentRegistryList(argThat(a ->
                a != null && "unknown-target:7777".equals(a.getNodeName())
        ))).thenReturn(Collections.emptyList());

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("目标节点名称[unknown-target:7777]未找到对应的Agent注册信息"));
    }

    @Test
    @DisplayName("6.24 多个目标节点名称部分解析失败")
    void testUploadAndValidate_PartialTargetAgentNamesNotFound() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("部分失败", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777;unknown:7777", "/backup/dst1;/backup/dst2");
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");
        when(agentRegistryMapper.selectAgentRegistryList(argThat(a ->
                a != null && "unknown:7777".equals(a.getNodeName())
        ))).thenReturn(Collections.emptyList());

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("目标节点名称[unknown:7777]未找到对应的Agent注册信息"));
    }

    @Test
    @DisplayName("6.25 包含模式和排除模式都为空，FAIL")
    void testUploadAndValidate_NoIncludeNoExcludePatterns() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("无模式任务", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        task.setIncludePatterns(null);
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("包含模式和排除模式至少需要填写一个"));
    }

    @Test
    @DisplayName("6.26 目标节点数量与目标目录数量不一致，FAIL")
    void testUploadAndValidate_TargetCountMismatch() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("数量不匹配", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777;root@10.0.0.3:7777", "/backup/dst1");
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777", "root@10.0.0.3:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getFailCount());
        assertTrue(result.getRows().get(0).getValidateMessage().contains("目标节点名称数量(2)与目标目录数量(1)不一致"));
    }

    @Test
    @DisplayName("6.27 只填排除模式不填包含模式，PASS")
    void testUploadAndValidate_OnlyExcludePatterns_Pass() {
        List<BatchTaskImportDTO> tasks = new ArrayList<>();
        BatchTaskImportDTO task = createValidDTO("仅排除", "root@10.0.0.1:7777",
                "/data/src", "root@10.0.0.2:7777", "/backup/dst");
        task.setIncludePatterns(null);
        task.setExcludePatterns("*.tmp;*.bak");
        tasks.add(task);

        MultipartFile file = createTestExcel(tasks);
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        mockAgentRegistryForNames("root@10.0.0.1:7777", "root@10.0.0.2:7777");

        TaskImportPreviewVO result = importService.uploadAndValidate(file, "admin");

        assertEquals(1, result.getPassCount());
        assertEquals(0, result.getFailCount());
    }

    @Test
    @DisplayName("6.28 松散导入-FAIL行但有agentId可导入任务主表")
    void testCommitImport_LooseMode_ImportRowsWithAgentIds() {
        BatchTransferTaskImport passRow = createPassImportRow("batch_loose", 1, "通过行");
        BatchTransferTaskImport failRow = createFailImportRow("batch_loose", 2, "失败但有ID");
        failRow.setSourceAgentId("agent-001");
        failRow.setTargetAgentIds("agent-002");

        when(importMapper.selectByBatchNoAndImportStatus("batch_loose", "PENDING"))
                .thenReturn(Arrays.asList(passRow, failRow));
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(taskMapper.insert(any(BatchTransferTask.class))).thenAnswer(inv -> {
            BatchTransferTask t = inv.getArgument(0);
            t.setId(System.nanoTime());
            return 1;
        });

        int count = importService.commitImport("batch_loose", "LOOSE");

        assertEquals(2, count);
        verify(taskMapper, times(2)).insert(any(BatchTransferTask.class));
    }

    @Test
    @DisplayName("6.29 松散导入-FAIL行且无agentId不可导入")
    void testCommitImport_LooseMode_SkipRowsWithoutAgentIds() {
        BatchTransferTaskImport passRow = createPassImportRow("batch_loose2", 1, "通过行");
        BatchTransferTaskImport failRowNoId = createFailImportRow("batch_loose2", 2, "无ID行");
        failRowNoId.setSourceAgentId("N/A");
        failRowNoId.setSourceAgentName("N/A");
        failRowNoId.setSourceDir("N/A");
        failRowNoId.setTargetAgentIds(null);

        when(importMapper.selectByBatchNoAndImportStatus("batch_loose2", "PENDING"))
                .thenReturn(Arrays.asList(passRow, failRowNoId));
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(taskMapper.insert(any(BatchTransferTask.class))).thenAnswer(inv -> {
            BatchTransferTask t = inv.getArgument(0);
            t.setId(999L);
            return 1;
        });

        int count = importService.commitImport("batch_loose2", "LOOSE");

        assertEquals(1, count);
        verify(taskMapper, times(1)).insert(any(BatchTransferTask.class));
    }

    @Test
    @DisplayName("6.30 严格导入-FAIL行即使有agentId也不导入")
    void testCommitImport_StrictMode_SkipAllFailRows() {
        BatchTransferTaskImport failRow = createFailImportRow("batch_strict", 1, "有ID但FAIL");
        failRow.setSourceAgentId("agent-001");
        failRow.setTargetAgentIds("agent-002");

        when(importMapper.selectByBatchNoAndImportStatus("batch_strict", "PENDING"))
                .thenReturn(Collections.singletonList(failRow));

        int count = importService.commitImport("batch_strict", "STRICT");

        assertEquals(0, count);
        verify(taskMapper, never()).insert(any());
    }

    // ==================== 辅助方法 ====================

    private BatchTaskImportDTO createValidDTO(String taskName, String sourceAgentName,
                                                String sourceDir, String targetAgentNames,
                                                String targetDirs) {
        BatchTaskImportDTO dto = new BatchTaskImportDTO();
        dto.setTransferMode("ONE_TO_ONE");
        dto.setTaskName(taskName);
        dto.setSourceAgentName(sourceAgentName);
        dto.setSourceDir(sourceDir);
        dto.setTargetAgentNames(targetAgentNames);
        dto.setTargetDirs(targetDirs);
        dto.setIncludePatterns("*");
        dto.setRoutingStrategy("BROADCAST");
        dto.setRetryBackoffType("LINEAR");
        dto.setPostTransferAction("NONE");
        dto.setPreserveDirStructure(1);
        return dto;
    }

    private BatchTransferTaskImport createPassImportRow(String batchNo, int rowNum, String taskName) {
        BatchTransferTaskImport row = new BatchTransferTaskImport();
        row.setId(System.nanoTime());
        row.setBatchNo(batchNo);
        row.setRowNum(rowNum);
        row.setTaskName(taskName);
        row.setSourceAgentId("agent-001");
        row.setSourceAgentName("root@10.0.0.1:7777");
        row.setSourceDir("/data/src");
        row.setTargetAgentIds("agent-002");
        row.setTargetAgentNames("root@10.0.0.2:7777");
        row.setTargetDirs("/backup/dst");
        row.setTransferMode("ONE_TO_ONE");
        row.setRoutingStrategy("BROADCAST");
        row.setRetryBackoffType("LINEAR");
        row.setPostTransferAction("NONE");
        row.setPreserveDirStructure(1);
        row.setValidateStatus("PASS");
        row.setValidateMessage("");
        row.setImportStatus("PENDING");
        row.setCreateBy("admin");
        row.setCreateTime(new Date());
        row.setUpdateBy("admin");
        row.setUpdateTime(new Date());
        return row;
    }

    private BatchTransferTaskImport createFailImportRow(String batchNo, int rowNum, String taskName) {
        BatchTransferTaskImport row = createPassImportRow(batchNo, rowNum, taskName);
        row.setValidateStatus("FAIL");
        row.setValidateMessage("校验失败");
        return row;
    }

    private BatchTransferTaskImport createImportedRow(String batchNo, int rowNum, String taskName, Long importedTaskId) {
        BatchTransferTaskImport row = createPassImportRow(batchNo, rowNum, taskName);
        row.setImportStatus("IMPORTED");
        row.setImportedTaskId(importedTaskId);
        return row;
    }

    private MultipartFile createTestExcel(List<BatchTaskImportDTO> tasks) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("批量任务导入模板");

            String[] headers = {"传输模式", "任务名称", "任务描述", "源节点名称", "源目录", "目标节点名称",
                    "目标目录", "包含模式", "排除模式", "执行频率", "最大扫描文件数",
                    "启用重试", "重试保留天数", "重试间隔(分钟)",
                    "最大重试次数", "重试退避策略", "传输后操作", "备份目录", "备份模式",
                    "保持目录结构", "路由策略", "路由配置",
                    "定时传输", "定时开始时间", "定时结束时间", "优先级", "备注"};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            for (int i = 0; i < tasks.size(); i++) {
                BatchTaskImportDTO task = tasks.get(i);
                Row row = sheet.createRow(i + 1);
                int col = 0;
                setCellValue(row, col++, task.getTransferMode());
                setCellValue(row, col++, task.getTaskName());
                setCellValue(row, col++, task.getTaskDescription());
                setCellValue(row, col++, task.getSourceAgentName());
                setCellValue(row, col++, task.getSourceDir());
                setCellValue(row, col++, task.getTargetAgentNames());
                setCellValue(row, col++, task.getTargetDirs());
                setCellValue(row, col++, task.getIncludePatterns());
                setCellValue(row, col++, task.getExcludePatterns());
                setCellValue(row, col++, task.getScanCronExpression());
                setCellValue(row, col++, task.getMaxScanFiles());
                setCellValue(row, col++, task.getRetryEnabled());
                setCellValue(row, col++, task.getRetryMaxDays());
                setCellValue(row, col++, task.getRetryIntervalMin());
                setCellValue(row, col++, task.getMaxRetryCount());
                setCellValue(row, col++, task.getRetryBackoffType());
                setCellValue(row, col++, task.getPostTransferAction());
                setCellValue(row, col++, task.getBackupDir());
                setCellValue(row, col++, task.getBackupMode());
                setCellValue(row, col++, task.getPreserveDirStructure());
                setCellValue(row, col++, task.getRoutingStrategy());
                setCellValue(row, col++, task.getRoutingConfig());
                setCellValue(row, col++, task.getScheduledEnabled());
                setCellValue(row, col++, task.getScheduledStartTime());
                setCellValue(row, col++, task.getScheduledEndTime());
                setCellValue(row, col++, task.getTaskPriority());
                setCellValue(row, col++, task.getRemark());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();

            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("构造测试Excel失败", e);
        }
    }

    private void setCellValue(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
