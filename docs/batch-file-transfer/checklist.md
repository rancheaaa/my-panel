# 批量文件传输功能 - 质量检查清单 (Checklist)

> **使用说明:** 每完成一个功能模块的开发和测试后，必须逐项检查本清单。所有项目必须全部通过（✅）才能进入下一阶段开发。

**质量标准:**
- 🎯 单元测试覆盖率：**100%**
- ✅ 单元测试通过率：**100%**
- 🔍 代码规范检查：**0 warning**
- 📊 性能指标达标
- 🔒 安全性验证通过

---

## Phase 0: 基础设施与数据模型

### 0.1 batch_transfer_task 表结构

#### DDL验证
- [ ] **表创建成功**: `CREATE TABLE` 语句无语法错误
- [ ] **字段完整性**: 所有spec中定义的字段都已包含（共35个字段）
- [ ] **字段类型正确**: 每个字段的类型、长度、约束符合设计
- [ ] **默认值设置**: `status=READY`, `retry_enabled=1`, `deleted=0` 等
- [ ] **NOT NULL约束**: 必填字段都有NOT NULL约束
- [ ] **索引创建**: 4个索引已创建（idx_status, idx_source_agent, idx_create_by, idx_create_time）
- [ ] **注释完整**: 每个字段都有COMMENT说明
- [ ] **字符集正确**: utf8mb4

#### 废弃字段检查
- [ ] ❌ **scan_frequency_sec 字段不存在**: 已从DDL中移除
- [ ] ✅ **scan_cron_expression 字段存在**: 注释不包含"由scan_frequency_sec转化"

#### Agent Name字段检查
- [ ] ✅ **source_agent_name 字段存在**: varchar(100), NOT NULL
- [ ] ✅ **target_agent_names 字段存在**: text, NOT NULL (JSON数组格式)
- [ ] ✅ **字段注释准确**: 格式为 user@ip:port

#### 单元测试覆盖率（目标100%）
- [ ] `testCreateTable_success`: 验证表创建成功
- [ ] `testFieldTypes_correct`: 验证所有字段类型匹配
- [ ] `testIndexes_created`: 验证所有索引存在
- [ ] `testDefaultValues_set`: 验证默认值正确
- [ ] `testNotNullConstraints`: 验证非空约束生效
- [ ] **JaCoCo覆盖率报告**: ≥ 95%（Mapper层）

---

### 0.2 batch_transfer_subtask 表结构

#### DDL验证
- [ ] **表创建成功**: 无语法错误
- [ ] **外键关联**: task_id 正确引用 batch_transfer_task.id
- [ ] **状态枚举约束**: 只允许 QUEUED/SENDING/COMPLETED/FAILED/RETRYING
- [ ] **进度字段默认值**: transferred_chunks=0, total_chunks=0, transferred_bytes=0
- [ ] **复合索引**: idx_task_source_target, idx_task_id, idx_target_status, idx_status_retry, idx_transfer_id

#### 单元测试覆盖率（目标100%）
- [ ] `testCreateTable_success`
- [ ] `testForeignKeyRelation_valid`
- [ ] `testStatusEnum_constraint`
- [ ] `testProgressFields_defaultZero`
- [ ] `testCompositeIndex_performance`

---

### 0.3 batch_sync_event 表结构

#### DDL验证
- [ ] **表创建成功**: 无语法错误
- [ ] **事件类型枚举**: TASK_CREATED/TASK_UPDATED/TASK_DELETED/TASK_STATUS_CHANGED
- [ ] **事件状态枚举**: PENDING/PROCESSING/COMPLETED/FAILED
- [ ] **payload字段**: text类型，存储JSON格式
- [ ] **时间戳字段**: created_at, processed_at, expire_at
- [ ] **索引优化**: idx_status_created, idx_task_id, idx_source_agent, idx_expire_at

#### 单元测试覆盖率（目标100%）
- [ ] `testCreateTable_success`
- [ ] `testEventTypeEnum_constraint`
- [ ] `testEventStatusEnum_constraint`
- [ ] `testPayloadJsonFormat`
- [ ] `testExpireAtIndex_forCleanup`

---

### 0.4 MyBatis实体类与Mapper

#### BatchTransferTask 实体
- [ ] **字段映射完整**: 所有DB字段都有对应Java属性
- [ ] **驼峰命名**: Java属性使用camelCase（taskId, taskName, sourceAgentId等）
- [ ] **JSON注解**: @JsonProperty 或 Jackson配置正确
- [ ] **BaseEntity继承**: 继承createBy, createTime等公共字段

#### BatchTransferSubtask 实体
- [ ] **字段映射完整**: 包含进度相关字段
- [ ] **嵌套对象支持**: targetAgent信息正确映射

#### BatchSyncEvent 实体
- [ ] **枚举类型**: EventType, EventStatus 使用Java Enum
- [ ] **payload序列化**: 支持JSON字符串存取

#### Mapper接口测试（每个方法都要有测试）
**BatchTransferTaskMapper:**
- [ ] `testInsert_success`: 插入返回自增ID
- [ ] `testInsert_withAllFields`: 所有字段正确持久化
- [ ] `testSelectById_found`: 根据ID查询成功
- [ ] `testSelectById_notFound`: 不存在的ID返回null
- [ ] `testUpdate_success`: 更新字段正确保存
- [ ] `testDeleteLogical_success`: deleted标记为1
- [ ] `testSelectListByStatus`: 按状态筛选
- [ ] `testSelectListBySourceAgentId`: 按源Agent筛选
- [ ] `testSelectListByCreateTime_pagination`: 分页正确
- [ ] **覆盖率**: 100%

**BatchTransferSubtaskMapper:**
- [ ] `testInsert_success`
- [ ] `testBatchInsert_performance`: 批量插入性能达标
- [ ] `testSelectByTaskIdAndStatus`: 复合条件查询
- [ ] `testUpdateProgress_partial`: 部分更新进度
- [ ] `testUpdateStatus_withErrorInfo`: 状态+错误信息更新
- [ ] **覆盖率**: 100%

**BatchSyncEventMapper:**
- [ ] `testInsertEvent_success`
- [ ] `testSelectPendingEvents_forUpdateSkipLocked`: 并发安全查询
- [ ] `testUpdateStatusToProcessing`: 状态变更
- [ ] `testUpdateStatusToCompleted`: 完成标记
- [ ] `testSelectExpiredEvents_forCleanup`: 过期事件查询
- [ ] **覆盖率**: 100%

#### XML Mapper文件
- [ ] **命名空间正确**: 对应Mapper接口全路径
- [ ] **resultMap定义**: 字段映射无误
- [ ] **SQL语句**: 无语法错误，参数使用#{param}
- [ ] **动态SQL**: <if>, <where> 等标签使用正确

---

## Phase 1: 核心工具类与算法

### 1.1 通配符冲突检测算法 - WildcardConflictDetector

#### 功能完整性
- [ ] **detectConflict()方法**: 主入口方法实现
- [ ] **expandWildcardPattern()方法**: 通配符展开逻辑
- [ ] **hasOverlap()方法**: 集合重叠判断
- [ ] **getExistingTasks()方法**: 数据库查询现有任务

#### 场景覆盖（必须全部通过）
**基础场景（来自spec.md）:**
- [ ] ✅ `testSamePattern_exactMatch_conflict`: 相同模式→冲突
- [ ] ✅ `testSubDirectoryOverlap_conflict`: 场景1 - /var/log/*.log vs /var/log/app/*.log → 冲突
- [ ] ✅ `testPartialOverlapWithExclude_conflict`: 场景2 - /var/log/*.log vs 排除app* → 冲突（部分重叠）
- [ ] ✅ `testDifferentRootDirectory_noConflict`: 场景3 - 不同根目录 → 不冲突
- [ ] ✅ `testRecursiveWildcard_overlap`: 场景4 - **递归匹配 → 冲突
- [ ] ✅ `testCompleteIsolation_noConflict`: 场景5 - app*.log vs 排除app* → 不冲突（完全隔离）

**边界条件:**
- [ ] `testEmptyIncludePatterns_noConflict`: 空包含模式处理
- [ ] `testNullExcludePatterns_handled`: null排除模式处理
- [ ] `testComplexWildcard_combination`: 复杂通配符组合（*.log,*.txt vs debug*,temp*）
- [ ] `testCaseSensitive_matching`: 大小写敏感
- [ ] `testPathTraversal_prevented`: 路径穿越攻击防护（../../../etc/passwd）

**性能要求:**
- [ ] `testPerformance_largePatternSet`: 1000个现有任务检测时间 < 100ms

#### 代码质量
- [ ] **无硬编码路径**: 目录路径可配置
- [ ] **异常处理完善**: IO异常、数据库异常有明确处理
- [ ] **日志记录**: 关键步骤有DEBUG/INFO日志
- [ ] **线程安全**: 可安全并发调用
- [ ] **JaCoCo覆盖率**: **100%**（分支+行覆盖率）

---

### 1.2 JSON序列化工具 - BatchConfigSerializer

#### Admin端序列化器
**toJson()方法测试:**
- [ ] `testTaskToJson_completeFields`: 所有字段都包含在JSON中
- [ ] `testJsonWithAgentName_fieldIncluded`: sourceAgentName和targetAgents[].agentName存在
- [ ] `testJsonWithoutFrequencySec_fieldAbsent`: scan_frequency_sec/frequencySec不在JSON中
- [ ] `testNestedTargetAgents_arrayFormat`: targetAgents为数组格式
- [ ] `testNullFields_jsonOmitted`: null值字段省略或为null
- [ ] `testSpecialCharacters_escaped`: 特殊字符正确转义
- [ ] `testLargePayload_performance`: 大payload序列化性能

**fromJson()方法测试:**
- [ ] `testTaskFromJson_allFieldsRestored`: 反序列化后所有字段恢复
- [ ] `testInvalidJson_exceptionHandled`: 无效JSON抛出适当异常
- [ ] `testMissingOptionalFields_defaults`: 缺少可选字段使用默认值

#### Proxy端序列化器
- [ ] **格式一致性**: 与Admin端完全兼容
- [ ] **版本字段**: version = update_time 格式正确

#### Agent端序列化器
- [ ] **配置对象转换**: BatchTransferTask → BatchTransferTaskConfig
- [ ] **必要字段保留**: Agent执行所需字段完整
- [ ] **管理字段移除**: id, createBy等不需要的字段可选移除

#### 覆盖率要求
- [ ] **Admin端**: 100%
- [ ] **Proxy端**: 100%
- [ ] **Agent端**: 100%

---

### 1.3 Cron表达式验证器

#### 功能测试
- [ ] `testValidCron6Fields`: 6位Cron表达式验证通过
- [ ] `testValidCron7Fields`: 7位Cron表达式验证通过
- [ ] `testInvalidCron_exception`: 无效表达式抛出异常
- [ ] `testNullCron_allowed`: null值允许（表示未设置调度）
- [ ] `testEmptyString_invalid`: 空字符串无效
- [ ] `testCronParsing_quartzCompatible`: Quartz库兼容性

#### 覆盖率
- [ ] **行覆盖率**: 100%
- [ ] **分支覆盖率**: 100%

---

## Phase 2: Admin模块核心功能

### 2.1 任务创建服务 - createTask()

#### 业务逻辑验证
- [ ] **参数校验**: 必填字段缺失时抛出ValidationException
- [ ] **路径合法性**: 检测并拒绝路径穿越攻击
- [ ] **冲突检测**: 调用WildcardConflictDetector.detectConflict()
- [ ] **事务原子性**: 任务插入和事件插入在同一事务中
    - [ ] 成功场景: 两条记录同时提交
    - [ ] 失败场景: 任一失败则整体回滚
- [ ] **初始状态**: status=READY
- [ ] **事件生成**: event_type=TASK_COMPLETED, payload包含完整配置JSON
- [ ] **agentName字段**: source_agent_name和target_agent_names正确保存
- [ ] **返回值**: 返回新生成的任务ID

#### 单元测试案例
- [ ] `testCreateTask_success_returnsId`: 正常创建返回ID
- [ ] `testCreateTask_withConflict_throwsException`: 冲突时抛出BusinessException
- [ ] `testCreateTask_transactional_bothInserted`: 事务原子性验证
- [ ] `testCreateTask_missingRequiredField_validationError`: 参数校验
- [ ] `testCreateTask_invalidPath_rejected`: 路径安全
- [ ] `testCreateTask_eventPayloadContainsFullConfig`: 事件payload完整性
- [ ] `testCreateTask_defaultStatusReady`: 默认状态
- [ ] `testCreateTask_agentNamesPersisted`: agent名称持久化

#### 异常场景测试
- [ ] 数据库连接失败时的行为
- [ ] 并发创建相同任务的处理
- [ ] 超长字段值的处理

#### 覆盖率要求
- [ ] **BatchTransferTaskService.createTask()**: 100%
- [ ] **事务回滚路径**: 已覆盖

---

### 2.2-2.6 其他Admin服务

#### 任务修改服务 - updateTask()
- [ ] `testUpdateTask_success`: 更新成功
- [ ] `testUpdateTask_newConflict_detected`: 新冲突检测
- [ ] `testUpdateTask_eventTypeTASK_UPDATED`: 事件类型正确
- [ ] `testUpdateTask_partialUpdate_allowed`: 允许部分更新
- [ ] `testUpdateTask_statusNotChanged`: 状态不变更
- [ ] `testUpdateTask_notFound_exception`: 任务不存在异常
- [ ] **覆盖率**: 100%

#### 任务状态管理服务
**状态转换矩阵（必须全覆盖）:**
| 当前状态 | 操作 | 目标状态 | 测试用例 |
|---------|------|---------|---------|
| READY | start | RUNNING | ✅ testStart_READY_to_RUNNING |
| RUNNING | pause | PAUSED | ✅ testPause_RUNNING_to_PAUSED |
| PAUSED | resume | RUNNING | ✅ testResume_PAUSED_to_RUNNING |
| RUNNING/PAUSED | stop | READY | ✅ testStop_to_READY |

**非法转换测试:**
- [ ] `testStart_ALREADY_RUNNING_exception`: 已运行不能再次启动
- [ ] `testPause_READY_state_invalid`: READY状态不能暂停
- [ ] `testResume_RUNNING_state_invalid`: RUNNING状态不能恢复

**删除操作测试:**
- [ ] `testDelete_logicalDelete_only`: 仅逻辑删除
- [ ] `testDelete_batchIds`: 批量删除
- [ ] `testDelete_insertsTASK_DELETED_events`: 生成删除事件

**事件生成测试:**
- [ ] `testStart_generatesTASK_STATUS_CHANGED_event`
- [ ] `testPause_eventPayload_includesNewStatus`
- [ ] `testStartedAt_setOnFirstStart`: 首次启动设置时间
- [ ] **覆盖率**: 100%

#### Controller层测试（MockMvc）
- [ ] `testPOST_batch_task_201Created`: POST创建201
- [ ] `testPOST_batch_task_conflict409`: 冲突返回409
- [ ] `testPUT_batch_task_id_200Ok`: PUT更新200
- [ ] `testDELETE_batch_task_ids_200Ok`: DELETE删除200
- [ ] `testGET_batch_task_id_200Ok`: GET查询200
- [ ] `testGET_batch_task_list_200Ok`: GET列表200
- [ ] `testPUT_batch_task_id_start_200Ok`: 启动200
- [ ] `testPUT_batch_task_id_pause_200Ok`: 暂停200
- [ ] `testPUT_batch_task_id_resume_200Ok`: 恢复200
- [ ] `testAuthorization_required`: 权限校验
- [ ] `testInputValidation_error400`: 输入校验400
- [ ] **覆盖率**: 100%

---

## Phase 3: Proxy模块 - 事件队列系统

### 3.1 事件轮询器 - EventPoller

#### 功能验证
- [ ] **空队列**: 返回空列表
- [ ] **单事件**: 正确返回
- [ ] **多事件**: 受batchSize限制
- [ ] **FOR UPDATE SKIP LOCKED**: 并发轮询不重复消费
    - [ ] 测试两个Proxy实例同时轮询
    - [ ] 验证同一事件不会被重复获取
- [ ] **过期事件排除**: 不返回已过期的PENDING事件
- [ ] **排序**: 按created_at升序
- [ ] **状态过滤**: 只返回PENDING状态

#### 并发安全性
- [ ] **多线程测试**: 使用@RepeatedTest或并发框架验证
- [ ] **无数据竞争**: 无ConcurrentModificationException
- [ ] **无死锁**: 不会出现锁等待超时

#### 覆盖率
- [ ] **行覆盖率**: 100%
- [ ] **分支覆盖率**: 100%（包括异常路径）

---

### 3.2 事件处理器 - EventHandler

#### TASK_CREATED处理
- [ ] `testHandleTaskCreated_pushSuccess_markCompleted`: 推送成功→COMPLETED
- [ ] `testHandleTaskCreated_pushFailed_markRetry`: 推送失败→重试
- [ ] `testHandleTaskCreated_agentReturnsTrue_persisted`: Agent确认持久化

#### TASK_UPDATED处理
- [ ] `testHandleTaskUpdated_versionCheck`: 版本检查
- [ ] `testHandleTaskUpdated_sameVersion_skipped`: 相同版本跳过

#### TASK_DELETED处理
- [ ] `testHandleTaskDeleted_sendControlCommand`: 发送删除指令
- [ ] `testHandleTaskDeleted_agentConfirm_success`: Agent确认成功

#### TASK_STATUS_CHANGED处理
- [ ] `testHandleStatusChange_start_running`: 启动→运行
- [ ] `testHandleStatusChange_pause_paused`: 暂停→暂停

#### 错误处理
- [ ] `testHandle_networkTimeout_retry`: 网络超时重试
- [ ] `testHandle_agentError_maxRetry_failed`: 最大重试次数后失败
- [ ] `testHandle_invalidEventType_exception`: 无效事件类型异常

#### 覆盖率
- [ ] **每个private方法**: 都有间接测试覆盖
- [ ] **总覆盖率**: 100%

---

### 3.3-3.5 Proxy其他组件

#### AgentConfigPusher
- [ ] `testPushConfig_success_response`: 成功响应configPersisted=true
- [ ] `testPushConfig_timeout_exception`: 超时异常
- [ ] `testPushConfig_connectionRefused_retry`: 连接拒绝重试
- [ ] `testSendControlDelete_agentConfirm`: 删除确认
- [ ] `testPushConfig_http5xx_serverError`: 5xx错误
- [ ] `testPushConfig_http4xx_clientError_noRetry`: 4xx不重试
- [ ] **覆盖率**: 100%

#### RetryScheduler
- [ ] `testExponentialBackoff_delays`: 指数退避延迟（1s,2s,4s,8s...60s上限）
- [ ] `testMaxRetryCount_limit`: 最大重试限制
- [ ] `testRetryScheduling_delayedExecution`: 延迟执行
- [ ] `testRetryEventStatus_resetToPENDING`: 重置为PENDING
- [ ] `testRetryCount_incremented`: 重试计数增加
- [ ] **覆盖率**: 100%

#### EventCleanupJob
- [ ] `testCleanup_expiredPendingEvents_markedFailed`: 过期PENDING标记FAILED
- [ ] `testCleanup_completedEvents_olderThan7days_deleted`: 7天前COMPLETED删除
- [ ] `testCleanup_failedEvents_retained30days`: FAILED保留30天
- [ ] `testCleanup_noActiveEventsAffected`: 不影响活跃事件
- [ ] **覆盖率**: 100%

---

## Phase 4: Proxy模块 - 进度接收系统

### 4.1-4.3 进度接收组件

#### ProgressReceiverController
- [ ] `testReceiveProgress_validData_updated`: 有效数据更新
- [ ] `testReceiveProgress_staleData_ignored`: 过期数据忽略（乐观锁）
- [ ] `testReceiveBatchProgress_multipleUpdates`: 批量更新
- [ ] `testReceiveComplete_finalStateSet`: 完成最终状态
- [ ] `testReceiveFailed_errorInfoRecorded`: 错误信息记录
- [ ] `testReceiveRetrying_nextRetryAfterCalculated`: 下次重试时间计算
- [ ] `testRateLimit_exceeded429`: 限流429
- [ ] `testInvalidSubtaskId_404`: 无效ID返回404
- [ ] **覆盖率**: 100%

#### ProgressBatchWriter
- [ ] `testBatchWrite_jdbcBatchUpdate`: JDBC批量更新
- [ ] `testBatchWrite_optimisticLock_oldIgnored`: 乐观锁旧数据忽略
- [ ] `testBatchWrite_flushInterval_200ms`: 200ms刷新间隔
- [ ] `testBatchWrite_minBatchSize_10`: 最小批量10条
- [ ] `testBatchWrite_maxBatchSize_50`: 最大批量50条
- [ ] **覆盖率**: 100%

#### ProgressRateLimiter
- [ ] `testWithinLimit_allowed`: 限制内允许
- [ ] `testExceedLimit_rejected`: 超限拒绝
- [ ] `testBurstSize_temporaryAllowed`: 突发临时允许
- [ ] `testPerAgent_isolation`: 按Agent隔离
- [ ] `testPayloadSizeLimit_enforced`: payload大小限制
- [ ] **覆盖率**: 100%

---

## Phase 5: Agent模块 - 配置持久化系统

### 5.1 ConfigFileManager

#### 文件写入测试
- [ ] `testSaveTaskConfig_createsJsonFile`: 创建JSON文件
- [ ] `testSaveTaskConfig_atomicRename`: 原子重命名操作（tmp→正式）
- [ ] `testSaveTaskConfig_metaUpdated`: 元数据更新

#### 文件读取测试
- [ ] `testLoadTaskConfig_exists`: 存在的配置加载
- [ ] `testLoadTaskConfig_notFound_null`: 不存在返回null
- [ ] `testLoadTaskConfig_corrupted_useCache`: 损坏文件使用缓存

#### 元数据管理测试
- [ ] `testLoadAllTaskMeta_multipleTasks`: 多任务元数据
- [ ] `testLoadAllTaskMeta_emptyDir`: 空目录处理

#### 删除测试
- [ ] `testDeleteTaskConfig_fileRemoved`: 文件删除
- [ ] `testDeleteTaskConfig_metaUpdated`: 元数据更新

#### 并发安全测试
- [ ] `testConcurrentWrites_lockProtection`: 写入锁保护
- [ ] `testConcurrentReadDuringWrite_safe`: 读时写安全

#### 覆盖率
- [ ] **public方法**: 100%
- [ ] **private方法**: 通过public方法间接覆盖100%
- [ ] **异常路径**: 全部覆盖

---

### 5.2-5.4 配置管理其他组件

#### VersionManager
- [ ] `testFirstReceive_newVersion_created`: 首次接收新版本
- [ ] `testNewerVersion_updateApplied`: 新版本更新应用
- [ ] `testSameVersion_idempotentReturn`: 相同版本幂等
- [ ] `testOlderVersion_ignoredWithWarning`: 旧版本忽略警告
- [ ] `testVersionComparison_timestampBased`: 基于时间戳比较
- [ ] **覆盖率**: 100%

#### ConfigHotUpdater
- [ ] `testCronChanged_schedulerRebuilt`: Cron变更重建调度器
- [ ] `testPatternsChanged_nextScanEffective`: 模式变更下次扫描生效
- [ ] `testRetryConfigChanged_immediateApply`: 重试配置立即应用
- [ ] `testTargetAgentsChanged_subtasksRegenerated`: 目标变更重新生成子任务
- [ ] `testRunningTask_hotUpdateWithoutInterrupt`: 运行中热更新不中断
- [ ] **覆盖率**: 100%

#### StartupLoader
- [ ] `testFirstStart_emptyConfig_waitForProxy`: 首次启动空配置
- [ ] `testExistingConfig_tasksLoaded`: 已有配置加载
- [ ] `testRunningTasks_schedulerRecreated`: 运行任务重建调度器
- [ ] `testCorruptedMeta_recovery`: 损坏元数据恢复
- [ ] **覆盖率**: 100%

---

## Phase 6: Agent模块 - 调度与扫描系统

### 6.1-6.4 调度扫描组件

#### CronScheduler
- [ ] `testSchedule_cronTriggered`: Cron触发
- [ ] `testStop_noMoreTriggers`: 停止不再触发
- [ ] `testPause_currentCompletes_noNew`: 暂停当前完成无新任务
- [ ] `testResume_resumesScheduling`: 恢复调度
- [ ] `testUpdateCron_dynamicChange`: 动态更新Cron
- [ ] `testImmediateExecution_onStart`: 启动立即执行
- [ ] **覆盖率**: 100%

#### DirectoryScanner
- [ ] `testScan_flatDirectory`: 平铺目录扫描
- [ ] `testScan_recursiveSubdirectories`: 递归子目录
- [ ] `testIncludePattern_filtering`: 包含模式过滤
- [ ] `testExcludePattern_filtering`: 排除模式过滤
- [ ] `testMaxFilesLimit_respected`: 最大文件数限制
- [ ] `testNonexistentDirectory_exception`: 不存在目录异常
- [ ] `testPermissionDenied_handling`: 权限拒绝处理
- [ ] `testSymlinkHandling_option`: 符号链接处理选项
- [ ] `testHiddenFiles_includedOrExcluded`: 隐藏文件处理
- [ ] `testEmptyDirectory_emptyList`: 空目录空列表
- [ ] `testLargeDirectory_performance`: 大目录性能（10000文件）
- [ ] **覆盖率**: 100%

#### FileDeduplicator
- [ ] `testDedup_sameNameSizeModTime_skip`: 相同跳过
- [ ] `testDedup_differentFile_include`: 不同包含
- [ ] `testDedup_modifiedFile_reinclude`: 修改后重新包含
- [ ] `testDedup_sameDayRule_applied`: 同天规则应用
- [ ] **覆盖率**: 100%

#### SubtaskGenerator
- [ ] `testBroadcast_oneToMany`: 广播一对多
- [ ] `testRoundRobin_singleTarget`: 轮询单目标
- [ ] `testRandom_randomSelection`: 随机选择
- [ ] `testRegionBased_mapping`: 区域映射
- [ ] `testCartesianProduct_filesXtargets`: 笛卡尔积
- [ ] **覆盖率**: 100%

---

## Phase 7: Agent模块 - 传输执行系统

### 7.1-7.4 传输控制组件

#### BatchTransferManager
- [ ] `testAcquirePermit_withinLimit`: 限制内获取许可
- [ ] `testAcquirePermit_exceedLimit_blocks`: 超限阻塞
- [ ] `testReleasePermit_countDecremented`: 释放计数减少
- [ ] `testTryAcquire_timeout`: 尝试获取超时
- [ ] `testFairness_noStarvation`: 公平无饥饿
- [ ] `testAvailablePermits_monitoring`: 可用许可监控
- [ ] **覆盖率**: 100%

#### TaskConcurrencyLimiter
- [ ] `testGlobalMaxRespected`: 全局最大值遵守
- [ ] `testTaskSpecificOverride`: 任务特定覆盖
- [ ] `testMultipleTasks_competition`: 多任务竞争
- [ ] **覆盖率**: 100%

#### BatchUploadListener
- [ ] `testOnStart_acquirePermit`: 开始获取许可
- [ ] `testOnChunkComplete_progressUpdated`: 分块完成进度更新
- [ ] `testOnComplete_releasePermit`: 完成释放许可
- [ ] `testOnFailure_releasePermitAndReport`: 失败释放并上报
- [ ] **覆盖率**: 100%

#### RetryManager
- [ ] `testLinearBackoff_intervals`: 线性退避间隔
- [ ] `testExponentialBackoff_doubling`: 指数退避翻倍
- [ ] `testMaxRetries_limit`: 最大重试限制
- [ ] `testMaxDays_exceeded`: 最大天数超出
- [ ] `testBandwidthReduction_onRetry`: 重试带宽降低
- [ ] `testRetryScheduling_delayed`: 重试调度延迟
- [ ] **覆盖率**: 100%

---

## Phase 8: Agent模块 - 进度上报系统

### 8.1-8.3 上报组件

#### ProgressBuffer
- [ ] `testAddEvent_buffered`: 事件缓冲
- [ ] `testFlush_interval`: 间隔刷新
- [ ] `testFlush_maxSize`: 最大容量刷新
- [ ] `testConcurrentAccess_threadSafe`: 并发访问线程安全
- [ ] **覆盖率**: 100%

#### ProgressReporter
- [ ] `testReportProgress_httpPost`: HTTP POST上报
- [ ] `testReportFailure_retryLogic`: 失败重试逻辑
- [ ] `testReportBatch_efficient`: 批量高效上报
- [ ] `testNetworkDown_fallbackToFile`: 网络故障落盘
- [ ] `testRecovery_loadFallback`: 恢复加载落盘数据
- [ ] **覆盖率**: 100%

#### FallbackPersistenceService
- [ ] `testPersistToDisk_appendMode`: 追加模式写入
- [ ] `testLoadFromDisk_parseLines`: 解析行加载
- [ ] `testClearAfterSuccessfulReport`: 成功后清除
- [ ] `testCorruptedLine_skipped`: 损坏行跳过
- [ ] **覆盖率**: 100%

---

## Phase 9: Agent模块 - 配置接收API

### 9.1 ConfigReceiverHandler

#### API功能测试
- [ ] `testReceiveConfig_persistSuccess`: 配置持久化成功
- [ ] `testReceiveConfig_versionCheck`: 版本检查
- [ ] `testReceiveConfig_idempotent`: 幂等处理
- [ ] `testReceiveControl_deleteAction`: 删除控制操作
- [ ] `testResponse_formatCorrect`: 响应格式正确
- [ ] `testAuth_tokenValidation`: Token鉴权
- [ ] **覆盖率**: 100%

#### 响应格式验证
```json
{
  "success": true,
  "data": {
    "configPersisted": true,
    "receivedAt": "2026-05-09T10:30:00Z",
    "version": "20260509103000"
  }
}
```
- [ ] success字段: boolean
- [ ] data.configPersisted: boolean
- [ ] data.receivedAt: ISO 8601格式
- [ ] data.version: 时间戳格式

---

## Phase 10: 集成测试与端到端验证

### 10.1 Admin-DB集成测试

- [ ] **完整CRUD流程**: 创建→查询→修改→删除
- [ ] **事务原子性**: 配置+事件同成功/同失败
- [ ] **并发冲突检测**: 多用户同时创建任务的冲突处理
- [ ] **数据一致性**: 逻辑删除不影响查询结果
- [ ] **性能基准**: CRUD操作响应时间<100ms

### 10.2 Proxy-DB集成测试

- [ ] **事件生命周期**: PENDING→PROCESSING→COMPLETED
- [ ] **并发轮询**: 多Proxy实例安全消费
- [ ] **批量写入**: 进度批量更新性能
- [ ] **清理任务**: 过期事件自动清理

### 10.3 Agent-本地存储集成测试

- [ ] **配置持久化循环**: 保存→加载→验证一致性
- [ ] **启动恢复**: 重启后正确加载配置
- [ ] **损坏恢复**: 损坏文件的优雅降级
- [ ] **调度准确性**: Cron触发时间精确

### 10.4 端到端流程测试

**流程1: 完整创建流程**
```
Admin创建任务 → DB INSERT → 事件INSERT → Proxy轮询 → 
Agent接收 → 本地持久化 → 返回确认 → 事件COMPLETED
```
- [ ] 每一步都可验证
- [ ] 最终状态一致

**流程2: 启动流程**
```
Admin点击启动 → DB UPDATE status=RUNNING → 事件TASK_STATUS_CHANGED → 
Proxy推送 → Agent初始化调度器 → 首次扫描
```
- [ ] 调度器正确启动
- [ ] 首次立即执行

**流程3: 传输流程**
```
文件发现 → 子任务生成 → 获取上传许可 → P2P传输 → 
分块进度上报 → 传输完成 → 许可释放
```
- [ ] 进度实时可见
- [ ] 并发控制有效

**流程4: 重试流程**
```
传输失败 → 判断是否达上限 → 计算退避时间 → 
等待重试 → 降低带宽 → 重试或最终失败
```
- [ ] 退避策略正确
- [ ] 最大重试次数限制

**流程5: 修改流程**
```
Admin修改配置 → DB UPDATE → 事件TASK_UPDATED → 
Proxy推送 → Agent对比version → 热更新 → 调度器重建
```
- [ ] 版本比较正确
- [ ] 无缝切换

**流程6: 删除流程**
```
Admin删除 → 逻辑删除 → 事件TASK_DELETED → 
Proxy发送删除指令 → Agent停止调度器 → 清理资源
```
- [ ] 进行中的传输自然完成
- [ ] 资源正确释放

---

## 质量门禁汇总表

| 阶段 | 测试数量 | 覆盖率目标 | 通过率目标 | 状态 |
|------|---------|-----------|-----------|------|
| Phase 0: 基础设施 | ~50个 | ≥95% | 100% | ⬜ 待验证 |
| Phase 1: 工具类 | ~40个 | 100% | 100% | ⬜ 待验证 |
| Phase 2: Admin模块 | ~80个 | 100% | 100% | ⬜ 待验证 |
| Phase 3: Proxy事件 | ~60个 | 100% | 100% | ⬜ 待验证 |
| Phase 4: Proxy进度 | ~40个 | 100% | 100% | ⬜ 待验证 |
| Phase 5: Agent配置 | ~50个 | 100% | 100% | ⬜ 待验证 |
| Phase 6: Agent调度 | ~50个 | 100% | 100% | ⬜ 待验证 |
| Phase 7: Agent传输 | ~40个 | 100% | 100% | ⬜ 待验证 |
| Phase 8: Agent上报 | ~30个 | 100% | 100% | ⬜ 待验证 |
| Phase 9: Agent API | ~20个 | 100% | 100% | ⬜ 待验证 |
| Phase 10: 集成测试 | ~30个 | N/A | 100% | ⬜ 待验证 |
| **总计** | **~490个** | - | **100%** | - |

---

## 代码质量检查项

### 编码规范
- [ ] **无编译警告**: `mvn clean compile` 0 warnings
- [ ] **代码格式化**: 遵循项目Code Style
- [ ] **命名规范**: 类名、方法名、变量名符合Java惯例
- [ ] **注释规范**: 公共API必须有Javadoc
- [ ] **无魔法数字**: 常量提取到Constants类
- [ ] **日志规范**: 使用SLF4J，无System.out.println

### 安全性检查
- [ ] **SQL注入防护**: 全部使用参数化查询（#{param}）
- [ ] **XSS防护**: 用户输入转义
- [ ] **路径穿越防护**: 路径规范化验证
- [ ] **认证授权**: 所有API需要Token验证
- [ ] **敏感数据**: 密码、Token不在日志中出现

### 性能检查
- [ ] **数据库查询**: 无N+1问题，使用JOIN或批查询
- [ ] **内存泄漏**: 无未关闭的资源（Connection, Stream等）
- [ ] **线程安全**: 共享变量正确同步
- [ ] **缓存策略**: 合理使用缓存，避免过度查询

### 文档完整性
- [ ] **API文档**: Swagger/OpenAPI注解完整
- [ ] **README**: 模块使用说明清晰
- [ ] **变更日志**: 重要变更记录在CHANGELOG

---

## 验收标准（全部满足方可交付）

### 功能完整性 ✅
- [ ] spec.md中的所有需求点都有对应实现
- [ ] 所有API端点都可正常调用
- [ ] 所有业务流程可端到端跑通

### 质量指标 ✅
- [ ] 单元测试总数: ≥ 490个
- [ ] 单元测试通过率: **100%**
- [ ] 代码覆盖率: **≥ 95%**（核心模块100%）
- [ ] 集成测试通过率: **100%**
- [ ] 编译警告: **0**

### 性能指标 ✅
- [ ] API响应时间: P99 < 500ms
- [ ] 事件处理延迟: < 5秒
- [ ] 并发支持: ≥ 10个任务同时运行
- [ ] 内存占用: 无明显泄漏

### 安全性 ✅
- [ ] 无已知漏洞
- [ ] 权限控制完善
- [ ] 数据加密传输（HTTPS）

---

**最后更新:** 2026-05-09
**维护者:** AI Assistant
**版本:** v1.0
