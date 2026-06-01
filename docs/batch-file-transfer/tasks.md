# 批量文件传输功能 - 详细实施计划 (Tasks)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 构建基于Agent P2P传输的批量文件分发系统，支持Admin配置管理、Proxy事件驱动同步、Agent自治执行

**Architecture:** 三层解耦架构（Admin → 共享DB+事件队列 → Proxy → Agent），通过数据库事务保证可靠性，Agent本地持久化实现高可用

**Tech Stack:** Java 21, Spring Boot 4, MyBatis 4, React 19, Ant Design 6, Netty 4, Jackson, Quartz/Cron4j, JUnit 5, Mockito

**质量标准:**
- ✅ 单元测试覆盖率：100%
- ✅ 单元测试通过率：100%
- ✅ 每个阶段必须全部测试通过才能进入下一阶段
- ✅ TDD开发模式：先写测试，再实现代码

---

## Phase 0: 基础设施与数据模型

### Task 0.1: 创建 batch_transfer_task 表结构

**Files:**
- Modify: `my-panel-admin/src/main/resources/sql/schema.sql`
- Test: `my-panel-admin/src/test/java/com/cq/panel/admin/server/repository/domain/BatchTransferTaskTest.java`

**描述:** 创建批量传输任务主表，包含所有必需字段和索引

**DDL:**
```sql
CREATE TABLE IF NOT EXISTS `batch_transfer_task` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_name` varchar(200) NOT NULL COMMENT '任务名称',
    `task_description` varchar(500) DEFAULT NULL COMMENT '任务描述',
    `source_agent_id` varchar(50) NOT NULL COMMENT '源Agent ID',
    `source_agent_name` varchar(100) NOT NULL COMMENT '源节点名称',
    `source_dir` varchar(500) NOT NULL COMMENT '源目录绝对路径',
    `target_dirs` varchar(2000) NOT NULL COMMENT '目标节点目录(分号分隔)',
    `include_patterns` text DEFAULT NULL COMMENT '包含通配符(JSON数组)',
    `exclude_patterns` text DEFAULT NULL COMMENT '排除通配符(JSON数组)',
    `scan_cron_expression` varchar(100) DEFAULT NULL COMMENT '定时扫描Cron表达式',
    `max_scan_files` int NOT NULL DEFAULT 10000 COMMENT '单次最大扫描文件数',
    `target_agent_ids` text NOT NULL COMMENT '目标Agent ID列表(JSON数组)',
    `target_agent_names` text NOT NULL COMMENT '目标节点名称列表(JSON数组)',
    `retry_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用自动重试',
    `retry_max_days` int NOT NULL DEFAULT 7 COMMENT '重试保留天数',
    `retry_interval_min` int NOT NULL DEFAULT 30 COMMENT '首次重试间隔(分钟)',
    `max_retry_count` int NOT NULL DEFAULT 10 COMMENT '单个子任务最大重试次数',
    `retry_backoff_type` varchar(20) NOT NULL DEFAULT 'EXPONENTIAL' COMMENT '重试退避策略',
    `post_transfer_action` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '传输后操作',
    `backup_dir` varchar(500) DEFAULT NULL COMMENT '备份目录绝对路径',
    `backup_mode` varchar(10) DEFAULT 'COPY' COMMENT '备份模式',
    `preserve_dir_structure` tinyint NOT NULL DEFAULT 1 COMMENT '是否保持原始目录结构',
    `transfer_mode` varchar(20) NOT NULL DEFAULT 'ONE_TO_MANY' COMMENT '传输模式',
    `routing_strategy` varchar(20) NOT NULL DEFAULT 'BROADCAST' COMMENT '路由策略',
    `routing_config` text DEFAULT NULL COMMENT '路由策略配置JSON',
    `status` varchar(20) NOT NULL DEFAULT 'READY' COMMENT '任务运行状态',
    `started_at` datetime DEFAULT NULL COMMENT '首次启动时间',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建人用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新人用户ID',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_source_agent` (`source_agent_id`),
    KEY `idx_create_by` (`create_by`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量传输任务表(模板配置)';
```

**单元测试案例（必须100%通过）:**
```java
@Test
void testCreateTable_success() {
    // 验证表创建成功
}

@Test
void testFieldTypes_correct() {
    // 验证所有字段类型正确
}

@Test
void testIndexes_created() {
    // 验证所有索引已创建
}

@Test
void testDefaultValues_set() {
    // 验证默认值正确设置
}

@Test
void testNotNullConstraints() {
    // 验证NOT NULL约束生效
}
```

---

### Task 0.2: 创建 batch_transfer_subtask 表结构

**Files:**
- Modify: `my-panel-admin/src/main/resources/sql/schema.sql`
- Test: `my-panel-admin/src/test/java/com/cq/panel/admin/server/repository/domain/BatchTransferSubtaskTest.java`

**描述:** 创建批量子任务表，记录每个文件的传输实例

**单元测试案例:**
```java
@Test
void testCreateTable_success() { }
@Test
void testForeignKeyRelation_valid() { }
@Test
void testStatusEnumConstraint() { }  // QUEUED/SENDING/COMPLETED/FAILED/RETRYING
@Test
void testProgressFields_defaultZero() { }
@Test
void testCompositeIndex_performance() { }
```

---

### Task 0.3: 创建 batch_sync_event 表结构

**Files:**
- Modify: `my-panel-admin/src/main/resources/sql/schema.sql`
- Test: `my-panel-admin/src/test/java/com/cq/panel/admin/server/repository/domain/BatchSyncEventTest.java`

**描述:** 创建事件队列表，用于Admin-Proxy异步通信

**单元测试案例:**
```java
@Test
void testCreateTable_success() { }
@Test
void testEventTypeEnum_constraint() { }  // TASK_CREATED/TASK_UPDATED/TASK_DELETED/TASK_STATUS_CHANGED
@Test
void testEventStatusEnum_constraint() { }  // PENDING/PROCESSING/COMPLETED/FAILED
@Test
void testPayloadJsonFormat() { }
@Test
void testExpireAtIndex_forCleanup() { }
```

---

### Task 0.4: MyBatis实体类与Mapper接口定义

**Files:**
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferTask.java`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferSubtask.java`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchSyncEvent.java`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/mapper/BatchTransferTaskMapper.java`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/mapper/BatchTransferSubtaskMapper.java`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/mapper/BatchSyncEventMapper.java`
- Create: `my-panel-admin/src/main/resources/mapper/batch/BatchTransferTaskMapper.xml`
- Create: `my-panel-admin/src/main/resources/mapper/batch/BatchTransferSubtaskMapper.xml`
- Create: `my-panel-admin/src/main/resources/mapper/batch/BatchSyncEventMapper.xml`
- Test: `my-panel-admin/src/test/java/com/cq/panel/admin/server/repository/mapper/*MapperTest.java`

**单元测试案例（每个Mapper都要有完整测试）:**

*BatchTransferTaskMapperTest:*
```java
@Test void testInsert_success() { }
@Test void testInsert_withAllFields() { }
@Test void testSelectById_found() { }
@Test void testSelectById_notFound() { }
@Test void testUpdate_success() { }
@Test void testDeleteLogical_success() { }
@Test void testSelectListByStatus() { }
@Test void testSelectListBySourceAgentId() { }
@Test void testSelectListByCreateTime_pagination() { }
```

*BatchTransferSubtaskMapperTest:*
```java
@Test void testInsert_success() { }
@Test void testBatchInsert_performance() { }
@Test void testSelectByTaskIdAndStatus() { }
@Test void testUpdateProgress_partial() { }
@Test void testUpdateStatus_withErrorInfo() { }
```

*BatchSyncEventMapperTest:*
```java
@Test void testInsertEvent_success() { }
@Test void testSelectPendingEvents_forUpdateSkipLocked() { }
@Test void testUpdateStatusToProcessing() { }
@Test void testUpdateStatusToCompleted() { }
@Test void testSelectExpiredEvents_forCleanup() { }
```

---

## Phase 1: 核心工具类与算法

### Task 1.1: 通配符冲突检测算法 - WildcardConflictDetector

**Files:**
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/service/batch/util/WildcardConflictDetector.java`
- Test: `my-panel-admin/src/test/java/com/cq/panel/admin/server/service/batch/util/WildcardConflictDetectorTest.java`

**方法清单:**
```java
public class WildcardConflictDetector {
    public ConflictResult detectConflict(String sourceDir, List<String> includePatterns,
                                        List<String> excludePatterns, Long excludeTaskId);
    
    private Set<String> expandWildcardPattern(String dir, String pattern);
    private boolean hasOverlap(Set<String> setA, Set<String> setB);
    private List<BatchTransferTask> getExistingTasks(String sourceAgentId);
}
```

**单元测试案例（覆盖率100%）:**
```java
// 基础匹配测试
@Test void testSamePattern_exactMatch_conflict() {
    // /var/log/*.log vs /var/log/*.log → 冲突
}

@Test void testSubDirectoryOverlap_conflict() {
    // 场景1: /var/log/*.log vs /var/log/app/*.log → 冲突
}

@Test void testPartialOverlapWithExclude_conflict() {
    // 场景2: /var/log/*.log vs /var/log/*.log 排除app* → 冲突（部分重叠）
}

@Test void testDifferentRootDirectory_noConflict() {
    // 场景3: /data/*.txt vs /backup/*.txt → 不冲突
}

@Test void testRecursiveWildcard_overlap() {
    // 场景4: /var/log/**/*.log vs /var/log/app/*.log → 冲突
}

@Test void testCompleteIsolation_noConflict() {
    // 场景5: /var/log/app*.log vs /var/log/*.log 排除app* → 不冲突
}

// 边界条件测试
@Test void testEmptyIncludePatterns_noConflict() { }
@Test void testNullExcludePatterns_handled() { }
@Test void testComplexWildcard_combination() {
    // *.log,*.txt vs debug*,temp* 组合测试
}
@Test void testCaseSensitive_matching() { }
@Test void testPathTraversal_prevented() {
    // ../../../etc/passwd 防护
}

// 性能测试
@Test void testPerformance_largePatternSet() {
    // 1000个现有任务的性能验证 < 100ms
}
```

---

### Task 1.2: JSON序列化工具 - BatchConfigSerializer

**Files:**
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/service/batch/util/BatchConfigSerializer.java`
- Create: `proxy/src/main/java/com/cq/proxy/util/BatchConfigSerializer.java`
- Create: `agent/src/main/java/com/cq/agent/util/BatchConfigSerializer.java`
- Test: 各模块对应的 *Test.java

**方法清单:**
```java
public class BatchConfigSerializer {
    public static String toJson(BatchTransferTask task);
    public static BatchTransferTask fromJson(String json);
    public static String toJson(BatchTransferSubtask subtask);
    public static BatchTransferSubtask fromSubtaskJson(String json);
    private static ObjectMapper getObjectMapper();
}
```

**单元测试案例:**
```java
@Test void testTaskToJson_completeFields() {
    // 验证所有字段正确序列化
}

@Test void testTaskFromJson_allFieldsRestored() {
    // 验证反序列化完整性
}

@Test void testJsonWithAgentName_fieldIncluded() {
    // 验证agentName字段包含在JSON中
}

@Test void testJsonWithoutFrequencySec_fieldAbsent() {
    // 验证废弃字段不在JSON中
}

@Test void testNestedTargetAgents_arrayFormat() {
    // 验证targetAgents数组格式
}

@Test void testInvalidJson_exceptionHandled() { }
@Test void testNullFields_jsonOmitted() { }
@Test void testSpecialCharacters_escaped() { }
@Test void testLargePayload_performance() { }
```

---

### Task 1.3: Cron表达式验证器 - CronExpressionValidator

**Files:**
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/service/batch/util/CronExpressionValidator.java`
- Test: `*Test.java`

**单元测试案例:**
```java
@Test void testValidCron6Fields() { }      // "0 */5 * * * ?"
@Test void testValidCron7Fields() { }       // "0 0 12 * * ? 2026"
@Test void testInvalidCron_exception() { }
@Test void testNullCron_allowed() { }        // 允许为空
@Test void testEmptyString_invalid() { }
@Test void testCronParsing_quartzCompatible() { }
```

---

## Phase 2: Admin模块核心功能

### Task 2.1: 任务创建服务 - createTask()

**Files:**
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/service/batch/BatchTransferTaskService.java`
- Method: `public Long createTask(BatchTransferTaskDTO dto, String userId)`
- Test: `BatchTransferTaskServiceTest.java`

**业务逻辑:**
1. 参数校验（必填字段、路径合法性）
2. 通配符冲突检测（调用Task 1.1）
3. 开启数据库事务
4. 插入 batch_transfer_task 记录（status=READY）
5. 插入 batch_sync_event 记录（event_type=TASK_CREATED）
6. 提交事务
7. 返回任务ID

**单元测试案例:**
```java
@Test void testCreateTask_success_returnsId() { }
@Test void testCreateTask_withConflict_throwsException() { }
@Test void testCreateTask_transactional_bothInserted() {
    // 验证任务和事件在同一事务中
}
@Test void testCreateTask_missingRequiredField_validationError() { }
@Test void testCreateTask_invalidPath_rejected() {
    // 路径穿越攻击防护
}
@Test void testCreateTask_eventPayloadContainsFullConfig() { }
@Test void testCreateTask_defaultStatusReady() { }
@Test void testCreateTask_agentNamesPersisted() { }
```

---

### Task 2.2: 任务修改服务 - updateTask()

**Files:**
- Modify: `BatchTransferTaskService.java`
- Method: `public void updateTask(Long taskId, BatchTransferTaskDTO dto)`
- Test: 同上

**单元测试案例:**
```java
@Test void testUpdateTask_success() { }
@Test void testUpdateTask_newConflict_detected() { }
@Test void testUpdateTask_eventTypeTASK_UPDATED() { }
@Test void testUpdateTask_partialUpdate_allowed() { }
@Test void testUpdateTask_statusNotChanged() { }
@Test void testUpdateTask_notFound_exception() { }
```

---

### Task 2.3: 任务状态管理服务

**Files:**
- Methods: `startTask()`, `pauseTask()`, `resumeTask()`, `stopTask()`, `deleteTasks()`
- Test: 完整的状态机测试

**单元测试案例:**
```java
// 状态转换测试
@Test void testStart_READY_to_RUNNING() { }
@Test void testPause_RUNNING_to_PAUSED() { }
@Test void testResume_PAUSED_to_RUNNING() { }
@Test void testStop RUNNING_or_PAUSED_to READY() { }

// 无效转换测试
@Test void testStart_ALREADY_RUNNING_exception() { }
@Test void testPause_READY_state_invalid() { }
@Test void testResume_RUNNING_state_invalid() { }

// 删除逻辑测试
@Test void testDelete_logicalDelete_only() { }
@Test void testDelete_batchIds() { }
@Test void testDelete_insertsTASK_DELETED_events() { }

// 事件生成测试
@Test void testStart_generatesTASK_STATUS_CHANGED_event() { }
@Test void testPause_eventPayload_includesNewStatus() { }
@Test void testStartedAt_setOnFirstStart() { }
```

---

### Task 2.4: 任务查询服务

**Files:**
- Methods: `getTaskById()`, `getTaskList()`, `getStatistics()`
- Test: 查询逻辑测试

**单元测试案例:**
```java
@Test void testGetById_found() { }
@Test void testGetById_deletedExcluded() { }
@Test void testListPagination_pageSize() { }
@Test void testListFilterByStatus() { }
@Test void testListFilterBySourceAgent() { }
@Test void testStatistics_calculationCorrect() { }
```

---

### Task 2.5: 子任务查询服务

**Files:**
- Methods: `getSubtaskById()`, `getSubtaskList()`
- Test: 子任务查询测试

**单元测试案例:**
```java
@Test void testGetSubtaskById_withProgress() { }
@Test void testListFilterByTaskId() { }
@Test void testListFilterByStatus() { }
@Test void testListFilterByTargetAgent() { }
@Test void testListOrderByCreateTime() { }
```

---

### Task 2.6: Admin REST Controller层

**Files:**
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/web/controller/batch/BatchTransferTaskController.java`
- Test: `BatchTransferTaskControllerTest.java` (MockMvc集成测试)

**API端点测试:**
```java
@Test void testPOST_batch_task_201Created() { }
@Test void testPOST_batch_task_conflict409() { }
@Test void testPUT_batch_task_id_200Ok() { }
@Test void testDELETE_batch_task_ids_200Ok() { }
@Test void testGET_batch_task_id_200Ok() { }
@Test void testGET_batch_task_list_200Ok() { }
@Test void testPUT_batch_task_id_start_200Ok() { }
@Test void testPUT_batch_task_id_pause_200Ok() { }
@Test void testPUT_batch_task_id_resume_200Ok() { }
@Test void testAuthorization_required() { }
@Test void testInputValidation_error400() { }
```

---

## Phase 3: Proxy模块 - 事件队列系统

### Task 3.1: 事件轮询器 - EventPoller

**Files:**
- Create: `proxy/src/main/java/com/cq/proxy/service/batch/EventPoller.java`
- Test: `EventPollerTest.java`

**方法清单:**
```java
public class EventPoller {
    public List<BatchSyncEvent> pollPendingEvents(int batchSize);
    private List<BatchSyncEvent> queryWithSkipLocked(int limit);
}
```

**单元测试案例:**
```java
@Test void testPollEmptyQueue_emptyList() { }
@Test void testPollSingleEvent_returned() { }
@Test void testPollMultipleEvents batchSizeLimited() { }
@Test void testPollWithFOR_UPDATE_SKIP_LOCKED_concurrentSafe() {
    // 验证并发轮询不会重复消费
}
@Test void testPollExcludeExpiredEvents() { }
@Test void testPollOrderByCreatedAt_asc() { }
@Test void testPollNoProcessingEvents() { }
```

---

### Task 3.2: 事件处理器 - EventHandler

**Files:**
- Create: `proxy/src/main/java/com/cq/proxy/service/batch/EventHandler.java`
- Test: `EventHandlerTest.java`

**方法清单:**
```java
public class EventHandler {
    public void handleEvent(BatchSyncEvent event);
    private void handleTaskCreated(BatchSyncEvent event);
    private void handleTaskUpdated(BatchSyncEvent event);
    private void handleTaskDeleted(BatchSyncEvent event);
    private void handleTaskStatusChanged(BatchSyncEvent event);
    private boolean pushConfigToAgent(String agentId, String payload);
    private void markEventCompleted(Long eventId);
    private void markEventFailed(Long eventId, String error);
    private void markEventRetry(Long eventId);
}
```

**单元测试案例:**
```java
// TASK_CREATED处理
@Test void testHandleTaskCreated_pushSuccess_markCompleted() { }
@Test void testHandleTaskCreated_pushFailed_markRetry() { }
@Test void testHandleTaskCreated_agentReturnsTrue_persisted() { }

// TASK_UPDATED处理
@Test void testHandleTaskUpdated_versionCheck() { }
@Test void testHandleTaskUpdated_sameVersion_skipped() { }

// TASK_DELETED处理
@Test void testHandleTaskDeleted_sendControlCommand() { }
@Test void testHandleTaskDeleted_agentConfirm_success() { }

// TASK_STATUS_CHANGED处理
@Test void testHandleStatusChange_start_running() { }
@Test void testHandleStatusChange_pause_paused() { }

// 错误处理
@Test void testHandle_networkTimeout_retry() { }
@Test void testHandle_agentError_maxRetry_failed() { }
@Test void testHandle_invalidEventType_exception() { }
```

---

### Task 3.3: 配置推送客户端 - AgentConfigPusher

**Files:**
- Create: `proxy/src/main/java/com/cq/proxy/service/batch/AgentConfigPusher.java`
- Test: `AgentConfigPusherTest.java`

**方法清单:**
```java
public class AgentConfigPusher {
    public PushResult pushConfig(String agentUrl, String payload);
    public PushResult sendControlCommand(String agentUrl, ControlCommand command);
}
```

**单元测试案例:**
```java
@Test void testPushConfig_success_response() {
    // 验证返回 configPersisted=true
}
@Test void testPushConfig_timeout_exception() { }
@Test void testPushConfig_connectionRefused_retry() { }
@Test void testSendControlDelete_agentConfirm() { }
@Test void testPushConfig_http5xx_serverError() { }
@Test void testPushConfig_http4xx_clientError_noRetry() { }
```

---

### Task 3.4: 重试调度器 - RetryScheduler

**Files:**
- Create: `proxy/src/main/java/com/cq/proxy/service/batch/RetryScheduler.java`
- Test: `RetrySchedulerTest.java`

**方法清单:**
```java
public class RetryScheduler {
    public void scheduleRetry(BatchSyncEvent event);
    private long calculateBackoffDelay(int retryCount);
    private boolean isMaxRetryReached(int retryCount);
}
```

**单元测试案例:**
```java
@Test void testExponentialBackoff_delays() {
    // 1s, 2s, 4s, 8s... 上限60s
}
@Test void testMaxRetryCount_limit() { }
@Test void testRetryScheduling_delayedExecution() { }
@Test void testRetryEventStatus_resetToPENDING() { }
@Test void testRetryCount_incremented() { }
```

---

### Task 3.5: 过期事件清理器 - EventCleanupJob

**Files:**
- Create: `proxy/src/main/java/com/cq/proxy/service/batch/EventCleanupJob.java`
- Test: `EventCleanupJobTest.java`

**单元测试案例:**
```java
@Test void testCleanup_expiredPendingEvents_markedFailed() { }
@Test void testCleanup_completedEvents_olderThan7days_deleted() { }
@Test void testCleanup_failedEvents_retained30days() { }
@Test void testCleanup_noActiveEventsAffected() { }
```

---

## Phase 4: Proxy模块 - 进度接收系统

### Task 4.1: 进度接收控制器 - ProgressReceiver

**Files:**
- Create: `proxy/src/main/java/com/cq/proxy/controller/batch/ProgressReceiverController.java`
- Test: `ProgressReceiverControllerTest.java`

**API端点:**
- POST `/api/batch/subtask/progress`
- POST `/api/batch/subtask/progress/batch`
- POST `/api/batch/subtask/complete`
- POST `/api/batch/subtask/failed`
- POST `/api/batch/subtask/retrying`

**单元测试案例:**
```java
@Test void testReceiveProgress_validData_updated() { }
@Test void testReceiveProgress_staleData_ignored() {
    // 乐观锁：只接受更新的timestamp
}
@Test void testReceiveBatchProgress_multipleUpdates() { }
@Test void testReceiveComplete_finalStateSet() { }
@Test void testReceiveFailed_errorInfoRecorded() { }
@Test void testReceiveRetrying_nextRetryAfterCalculated() { }
@Test void testRateLimit_exceeded429() { }
@Test void testInvalidSubtaskId_404() { }
```

---

### Task 4.2: 批量写入优化 - ProgressBatchWriter

**Files:**
- Create: `proxy/src/main/java/com/cq/proxy/service/batch/ProgressBatchWriter.java`
- Test: `ProgressBatchWriterTest.java`

**单元测试案例:**
```java
@Test void testBatchWrite_jdbcBatchUpdate() { }
@Test void testBatchWrite_optimisticLock_oldIgnored() { }
@Test void testBatchWrite_flushInterval_200ms() { }
@Test void testBatchWrite_minBatchSize_10() { }
@Test void testBatchWrite_maxBatchSize_50() { }
```

---

### Task 4.3: 限流保护 - ProgressRateLimiter

**Files:**
- Create: `proxy/src/main/java/com/cq/proxy/service/batch/ProgressRateLimiter.java`
- Test: `ProgressRateLimiterTest.java`

**单元测试案例:**
```java
@Test void testWithinLimit_allowed() { }
@Test void testExceedLimit_rejected() { }
@Test void testBurstSize_temporaryAllowed() { }
@Test void testPerAgent_isolation() { }
@Test void testPayloadSizeLimit_enforced() { }
```

---

## Phase 5: Agent模块 - 配置持久化系统

### Task 5.1: 配置文件管理器 - ConfigFileManager

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/config/ConfigFileManager.java`
- Test: `ConfigFileManagerTest.java`

**方法清单:**
```java
public class ConfigFileManager {
    public void saveTaskConfig(BatchTransferTaskConfig config);
    public BatchTransferTaskConfig loadTaskConfig(Long taskId);
    public void deleteTaskConfig(Long taskId);
    public List<TaskMetaInfo> loadAllTaskMeta();
    public void updateTasksMeta(TaskMetaInfo meta);
    private void atomicWrite(Path path, String content);
    private String readFileSafely(Path path);
}
```

**单元测试案例:**
```java
// 文件写入测试
@Test void testSaveTaskConfig_createsJsonFile() { }
@Test void testSaveTaskConfig_atomicRename() {
    // 验证tmp→正式文件的原子操作
}
@Test void testSaveTaskConfig_metaUpdated() { }

// 文件读取测试
@Test void testLoadTaskConfig_exists() { }
@Test void testLoadTaskConfig_notFound_null() { }
@Test void testLoadTaskConfig_corrupted_useCache() { }

// 元数据管理测试
@Test void testLoadAllTaskMeta_multipleTasks() { }
@Test void testLoadAllTaskMeta_emptyDir() { }

// 删除测试
@Test void testDeleteTaskConfig_fileRemoved() { }
@Test void testDeleteTaskConfig_metaUpdated() { }

// 并发安全测试
@Test void testConcurrentWrites_lockProtection() { }
@Test void testConcurrentReadDuringWrite_safe() { }
```

---

### Task 5.2: 版本管理器 - VersionManager

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/config/VersionManager.java`
- Test: `VersionManagerTest.java`

**单元测试案例:**
```java
@Test void testFirstReceive_newVersion_created() { }
@Test void testNewerVersion_updateApplied() { }
@Test void testSameVersion_idempotentReturn() { }
@Test void testOlderVersion_ignoredWithWarning() { }
@Test void testVersionComparison_timestampBased() { }
```

---

### Task 5.3: 配置热更新器 - ConfigHotUpdater

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/config/ConfigHotUpdater.java`
- Test: `ConfigHotUpdaterTest.java`

**单元测试案例:**
```java
@Test void testCronChanged_schedulerRebuilt() { }
@Test void testPatternsChanged_nextScanEffective() { }
@Test void testRetryConfigChanged_immediateApply() { }
@Test void testTargetAgentsChanged_subtasksRegenerated() { }
@Test void testRunningTask_hotUpdateWithoutInterrupt() { }
```

---

### Task 5.4: 启动加载器 - StartupLoader

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/config/StartupLoader.java`
- Test: `StartupLoaderTest.java`

**单元测试案例:**
```java
@Test void testFirstStart_emptyConfig_waitForProxy() { }
@Test void testExistingConfig_tasksLoaded() { }
@Test void testRunningTasks_schedulerRecreated() { }
@Test void testCorruptedMeta_recovery() { }
```

---

## Phase 6: Agent模块 - 调度与扫描系统

### Task 6.1: Cron调度器 - CronScheduler

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/scheduler/CronScheduler.java`
- Test: `CronSchedulerTest.java`

**方法清单:**
```java
public class CronScheduler {
    public void start();
    public void stop();
    public void pause();
    public void resume();
    public void updateCronExpression(String cronExpr);
    private ScheduledFuture<?> scheduleNextExecution();
}
```

**单元测试案例:**
```java
@Test void testSchedule_cronTriggered() { }
@Test void testStop_noMoreTriggers() { }
@Test void testPause_currentCompletes_noNew() { }
@Test void testResume_resumesScheduling() { }
@Test void testUpdateCron_dynamicChange() { }
@Test void testImmediateExecution_onStart() { }
```

---

### Task 6.2: 目录扫描器 - DirectoryScanner

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/scanner/DirectoryScanner.java`
- Test: `DirectoryScannerTest.java`

**方法清单:**
```java
public class DirectoryScanner {
    public List<FileInfo> scan(String sourceDir, List<String> includePatterns,
                               List<String> excludePatterns, int maxFiles);
    private boolean matchesPattern(String filePath, List<String> patterns);
    private boolean isExcluded(String filePath, List<String> excludePatterns);
}
```

**单元测试案例:**
```java
@Test void testScan_flatDirectory() { }
@Test void testScan_recursiveSubdirectories() { }
@Test void testIncludePattern_filtering() { }
@Test void testExcludePattern_filtering() { }
@Test void testMaxFilesLimit_respected() { }
@Test void testNonexistentDirectory_exception() { }
@Test void testPermissionDenied_handling() { }
@Test void testSymlinkHandling_option() { }
@Test void testHiddenFiles_includedOrExcluded() { }
@Test void testEmptyDirectory_emptyList() { }
@Test void testLargeDirectory_performance() {
    // 验证10000文件限制生效
}
```

---

### Task 6.3: 文件去重器 - FileDeduplicator

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/scanner/FileDeduplicator.java`
- Test: `FileDeduplicatorTest.java`

**单元测试案例:**
```java
@Test void testDedup_sameNameSizeModTime_skip() { }
@Test void testDedup_differentFile_include() { }
@Test void testDedup_modifiedFile_reinclude() { }
@Test void testDedup_sameDayRule_applied() { }
```

---

### Task 6.4: 子任务生成器 - SubtaskGenerator

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/scanner/SubtaskGenerator.java`
- Test: `SubtaskGeneratorTest.java`

**单元测试案例:**
```java
@Test void testBroadcast_oneToMany() { }
@Test void testRoundRobin_singleTarget() { }
@Test void testRandom_randomSelection() { }
@Test void testRegionBased_mapping() { }
@Test void testCartesianProduct_filesXtargets() { }
```

---

## Phase 7: Agent模块 - 传输执行系统

### Task 7.1: 全局并发控制 - BatchTransferManager

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/transfer/BatchTransferManager.java`
- Test: `BatchTransferManagerTest.java`

**单元测试案例:**
```java
@Test void testAcquirePermit_withinLimit() { }
@Test void testAcquirePermit_exceedLimit_blocks() { }
@Test void testReleasePermit_countDecremented() { }
@Test void testTryAcquire_timeout() { }
@Test void testFairness_noStarvation() { }
@Test void testAvailablePermits_monitoring() { }
```

---

### Task 7.2: 任务级并发控制 - TaskConcurrencyLimiter

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/transfer/TaskConcurrencyLimiter.java`
- Test: `TaskConcurrencyLimiterTest.java`

**单元测试案例:**
```java
@Test void testGlobalMaxRespected() { }
@Test void testTaskSpecificOverride() { }
@Test void testMultipleTasks_competition() { }
```

---

### Task 7.3: 上传监听器 - BatchUploadListener

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/transfer/BatchUploadListener.java`
- Test: `BatchUploadListenerTest.java`

**单元测试案例:**
```java
@Test void testOnStart_acquirePermit() { }
@Test void testOnChunkComplete_progressUpdated() { }
@Test void testOnComplete_releasePermit() { }
@Test void testOnFailure_releasePermitAndReport() { }
```

---

### Task 7.4: 重试管理器 - RetryManager

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java`
- Test: `RetryManagerTest.java`

**单元测试案例:**
```java
@Test void testLinearBackoff_intervals() { }
@Test void testExponentialBackoff_doubling() { }
@Test void testMaxRetries_limit() { }
@Test void testMaxDays_exceeded() { }
@Test void testBandwidthReduction_onRetry() { }
@Test void testRetryScheduling_delayed() { }
```

---

## Phase 8: Agent模块 - 进度上报系统

### Task 8.1: 进度缓冲区 - ProgressBuffer

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/report/ProgressBuffer.java`
- Test: `ProgressBufferTest.java`

**单元测试案例:**
```java
@Test void testAddEvent_buffered() { }
@Test void testFlush_interval() { }
@Test void testFlush_maxSize() { }
@Test void testConcurrentAccess_threadSafe() { }
```

---

### Task 8.2: 进度上报器 - ProgressReporter

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/report/ProgressReporter.java`
- Test: `ProgressReporterTest.java`

**单元测试案例:**
```java
@Test void testReportProgress_httpPost() { }
@Test void testReportFailure_retryLogic() { }
@Test void testReportBatch_efficient() { }
@Test void testNetworkDown_fallbackToFile() { }
@Test void testRecovery_loadFallback() { }
```

---

### Task 8.3: 本地持久化补报 - FallbackPersistenceService

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/report/FallbackPersistenceService.java`
- Test: `FallbackPersistenceServiceTest.java`

**单元测试案例:**
```java
@Test void testPersistToDisk_appendMode() { }
@Test void testLoadFromDisk_parseLines() { }
@Test void testClearAfterSuccessfulReport() { }
@Test void testCorruptedLine_skipped() { }
```

---

## Phase 9: Agent模块 - 配置接收API

### Task 9.1: 配置接收处理器 - ConfigReceiverHandler

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/api/ConfigReceiverHandler.java`
- Test: `ConfigReceiverHandlerTest.java`

**API端点:**
- POST `/api/batch/task/config`
- POST `/api/batch/task/control`

**单元测试案例:**
```java
@Test void testReceiveConfig_persistSuccess() { }
@Test void testReceiveConfig_versionCheck() { }
@Test void testReceiveConfig_idempotent() { }
@Test void testReceiveControl_deleteAction() { }
@Test void testResponse_formatCorrect() {
    // 返回 {success, data: {configPersisted, receivedAt, version}}
}
@Test void testAuth_tokenValidation() { }
```

---

## Phase 10: 集成测试与端到端验证

### Task 10.1: Admin-DB集成测试

**测试场景:**
- 完整的任务生命周期CRUD
- 事务原子性验证（配置+事件同时成功/失败）
- 并发创建任务的冲突检测

### Task 10.2: Proxy-DB集成测试

**测试场景:**
- 事件轮询与处理的完整流程
- FOR UPDATE SKIP LOCKED并发安全性
- 批量进度写入性能

### Task 10.3: Agent-本地存储集成测试

**测试场景:**
- 配置持久化与加载的完整性
- 调度器的启动/停止/暂停/恢复
- 文件扫描与匹配的正确性

### Task 10.4: 端到端流程测试

**测试场景:**
1. **完整创建流程**: Admin创建任务 → DB → Proxy轮询 → Agent接收 → 本地持久化
2. **启动流程**: Admin启动 → 状态变更事件 → Agent开始扫描
3. **传输流程**: 文件发现 → 子任务生成 → P2P传输 → 进度上报
4. **重试流程**: 传输失败 → 重试等待 → 自动重试 → 成功/最终失败
5. **修改流程**: Admin修改配置 → 热更新 → Agent无缝切换
6. **删除流程**: Admin删除 → Agent停止 → 清理资源

---

## 质量门禁检查点

### Gate 1: Phase 0-1 完成
- [ ] 所有SQL脚本可执行
- [ ] 所有Mapper单元测试通过（覆盖率≥95%）
- [ ] WildcardConflictDetector测试通过（覆盖率100%）
- [ ] 序列化工具测试通过（覆盖率100%）

### Gate 2: Phase 2 完成
- [ ] Admin Service层所有测试通过（覆盖率100%）
- [ ] Controller层MockMvc测试通过
- [ ] 通配符冲突检测集成验证通过
- [ ] 事务原子性验证通过

### Gate 3: Phase 3-4 完成
- [ ] Proxy事件队列全流程测试通过
- [ ] 进度接收与写入测试通过
- [ ] 并发轮询安全性验证通过
- [ ] 限流机制有效性验证通过

### Gate 4: Phase 5-9 完成
- [ ] Agent配置持久化完整性验证
- [ ] 调度器准确性验证
- [ ] 传输并发控制验证
- [ ] 进度上报可靠性验证
- [ ] 所有Agent模块单元测试覆盖率100%

### Gate 5: Phase 10 完成
- [ ] 所有集成测试通过
- [ ] 端到端流程验证通过
- [ ] 性能指标达标
- [ ] 安全性验证通过

---

## 执行顺序依赖关系

```
Phase 0 (基础设施)
   ↓ 必须完成
Phase 1 (工具类)
   ↓ 必须完成
Phase 2 (Admin模块) ← 可与Phase 3并行
Phase 3 (Proxy事件) ← 可与Phase 2并行
   ↓ 必须完成
Phase 4 (Proxy进度)
   ↓ 必须完成
Phase 5-9 (Agent模块) ← 可内部并行
   ↓ 必须完成
Phase 10 (集成测试)
```

**关键规则:**
- ❌ 下一阶段的代码编写必须等到当前阶段所有测试100%通过
- ✅ 允许同阶段内的任务并行开发
- ✅ 每个Task完成后立即提交代码
- ✅ 每个Phase完成后打Tag标记版本
