# 批量任务导入功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现批量任务导入功能，支持Excel模板下载、上传校验、事务性批导入、批回退、批启用、批暂停。

**Architecture:** 临时表(batch_transfer_task_import)存储上传数据，两阶段提交(上传校验→事务导入)。复用现有ExcelUtil导入导出框架和WildcardConflictDetector冲突检测。前端新增独立页面TaskImportPage。

**Tech Stack:** Spring Boot 4 + MyBatis + H2/MySQL + ExcelUtil + React 19 + Ant Design 6 + Vite

---

## File Structure

### 后端新建文件

| 文件 | 职责 |
|------|------|
| `repository/domain/BatchTransferTaskImport.java` | 临时表Entity，继承BatchTransferTask字段+批次字段 |
| `repository/mapper/BatchTransferTaskImportMapper.java` | 临时表Mapper接口 |
| `resources/mapper/batch/BatchTransferTaskImportMapper.xml` | 临时表SQL映射 |
| `repository/service/IBatchTaskImportService.java` | 导入服务接口 |
| `repository/service/impl/BatchTaskImportServiceImpl.java` | 导入服务实现 |
| `web/controller/batch/BatchTaskImportController.java` | 导入Controller |
| `web/domain/vo/batch/TaskImportPreviewVO.java` | 上传预览响应VO |
| `web/domain/vo/batch/TaskImportRowVO.java` | 单行预览数据VO |
| `web/domain/vo/batch/TaskImportBatchVO.java` | 批次列表VO |

### 后端修改文件

| 文件 | 修改内容 |
|------|---------|
| `resources/sql/schema.sql` | 新增batch_transfer_task_import建表语句 |
| `resources/sql/data.sql` | 新增菜单数据 |
| `repository/domain/BatchTransferTask.java` | 给JSON字段添加@Excel注解(type=IMPORT) |

### 前端新建文件

| 文件 | 职责 |
|------|------|
| `pages/batch/TaskImportPage.jsx` | 任务导入页面主组件 |
| `pages/batch/hooks/useTaskImport.js` | 导入页面数据Hook |
| `api/batch/index.js` | 新增导入相关API方法 |

### 前端修改文件

| 文件 | 修改内容 |
|------|---------|
| `pages/batch/index.jsx` | 新增TaskImportPage路由 |
| `pages/batch/index.scss` | 新增导入页面样式 |

---

## Task 1: 临时表建表 + Entity + Mapper

**Files:**
- Modify: `my-panel-admin/src/main/resources/sql/schema.sql`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferTaskImport.java`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/mapper/BatchTransferTaskImportMapper.java`
- Create: `my-panel-admin/src/main/resources/mapper/batch/BatchTransferTaskImportMapper.xml`

- [ ] **Step 1: 在schema.sql末尾添加建表语句**

在schema.sql末尾追加：

```sql
CREATE TABLE IF NOT EXISTS batch_transfer_task_import (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_no varchar(32) NOT NULL COMMENT '批次号',
    row_num int NOT NULL COMMENT 'Excel行号',
    task_name varchar(200) NOT NULL COMMENT '任务名称',
    task_description varchar(500) DEFAULT NULL COMMENT '任务描述',
    source_agent_id varchar(50) NOT NULL COMMENT '源Agent ID',
    source_agent_name varchar(100) NOT NULL COMMENT '源节点名称',
    source_dir varchar(500) NOT NULL COMMENT '源目录绝对路径',
    target_dirs varchar(2000) NOT NULL COMMENT '目标节点目录(分号分隔)',
    include_patterns text DEFAULT NULL COMMENT '包含通配符(分号分隔)',
    exclude_patterns text DEFAULT NULL COMMENT '排除通配符(分号分隔)',
    scan_cron_expression varchar(100) DEFAULT NULL COMMENT '定时扫描Cron表达式',
    max_scan_files int NOT NULL DEFAULT 10000 COMMENT '单次最大扫描文件数',
    target_agent_ids text NOT NULL COMMENT '目标Agent ID列表(分号分隔)',
    target_agent_names text NOT NULL COMMENT '目标节点名称列表(分号分隔)',
    retry_enabled tinyint NOT NULL DEFAULT 1 COMMENT '是否启用自动重试',
    retry_max_days int NOT NULL DEFAULT 7 COMMENT '重试保留天数',
    retry_interval_min int NOT NULL DEFAULT 30 COMMENT '首次重试间隔(分钟)',
    max_retry_count int NOT NULL DEFAULT 10 COMMENT '最大重试次数',
    retry_backoff_type varchar(20) NOT NULL DEFAULT 'EXPONENTIAL' COMMENT '重试退避策略',
    post_transfer_action varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '传输后操作',
    backup_dir varchar(500) DEFAULT NULL COMMENT '备份目录',
    backup_mode varchar(10) DEFAULT 'COPY' COMMENT '备份模式',
    preserve_dir_structure tinyint NOT NULL DEFAULT 1 COMMENT '是否保持目录结构',
    transfer_mode varchar(20) NOT NULL DEFAULT 'ONE_TO_MANY' COMMENT '传输模式',
    routing_strategy varchar(20) NOT NULL DEFAULT 'BROADCAST' COMMENT '路由策略',
    routing_config text DEFAULT NULL COMMENT '路由策略配置JSON',
    status varchar(20) NOT NULL DEFAULT 'READY' COMMENT '任务状态',
    scheduled_enabled tinyint NOT NULL DEFAULT 0 COMMENT '是否开启定时传输',
    scheduled_start_time time DEFAULT NULL COMMENT '定时传输开始时间',
    scheduled_end_time time DEFAULT NULL COMMENT '定时传输结束时间',
    task_priority int NOT NULL DEFAULT 5 COMMENT '任务优先级',
    started_at datetime DEFAULT NULL COMMENT '首次启动时间',
    validate_status varchar(10) NOT NULL DEFAULT 'PENDING' COMMENT '校验状态: PENDING/PASS/FAIL',
    validate_message varchar(500) DEFAULT NULL COMMENT '校验失败原因',
    import_status varchar(10) NOT NULL DEFAULT 'PENDING' COMMENT '导入状态: PENDING/IMPORTED/ROLLBACK',
    imported_task_id bigint DEFAULT NULL COMMENT '导入后对应的正式表任务ID',
    create_by varchar(64) DEFAULT '' COMMENT '创建人',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新人',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_batch_no (batch_no),
    KEY idx_import_status (import_status),
    KEY idx_imported_task_id (imported_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量任务导入临时表';
```

- [ ] **Step 2: 创建BatchTransferTaskImport Entity**

继承BatchTransferTask的所有字段（通过组合，非继承），加上批次相关字段。使用@Data注解，字段与建表语句一一对应。注意：临时表中target_agent_ids/target_agent_names/target_dirs/include_patterns/exclude_patterns存储分号分隔文本（非JSON），导入时再转换。

- [ ] **Step 3: 创建BatchTransferTaskImportMapper接口**

定义方法：
- `void batchInsert(@Param("list") List<BatchTransferTaskImport> list)`
- `List<BatchTransferTaskImport> selectByBatchNo(@Param("batchNo") String batchNo)`
- `List<BatchTransferTaskImport> selectByBatchNoAndValidateStatus(@Param("batchNo") String batchNo, @Param("validateStatus") String validateStatus)`
- `List<BatchTransferTaskImport> selectByBatchNoAndImportStatus(@Param("batchNo") String batchNo, @Param("importStatus") String importStatus)`
- `void updateImportStatus(@Param("id") Long id, @Param("importStatus") String importStatus, @Param("importedTaskId") Long importedTaskId)`
- `void updateValidateStatus(@Param("id") Long id, @Param("validateStatus") String validateStatus, @Param("validateMessage") String validateMessage)`
- `List<Map<String, Object>> selectBatchList()` — 按batch_no分组查询批次列表
- `void deleteByBatchNo(@Param("batchNo") String batchNo)`

- [ ] **Step 4: 创建BatchTransferTaskImportMapper.xml**

实现上述SQL。batchInsert使用foreach批量插入。selectBatchList使用GROUP BY batch_no查询批次汇总信息。

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -pl my-panel-admin -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add my-panel-admin/src/main/resources/sql/schema.sql my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferTaskImport.java my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/mapper/BatchTransferTaskImportMapper.java my-panel-admin/src/main/resources/mapper/batch/BatchTransferTaskImportMapper.xml
git commit -m "feat(batch): add batch_transfer_task_import table, entity and mapper"
```

---

## Task 2: VO类 + BatchTransferTask的@Excel导入注解

**Files:**
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/web/domain/vo/batch/TaskImportPreviewVO.java`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/web/domain/vo/batch/TaskImportRowVO.java`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/web/domain/vo/batch/TaskImportBatchVO.java`
- Modify: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferTask.java`

- [ ] **Step 1: 创建TaskImportPreviewVO**

字段：batchNo(String), total(int), passCount(int), failCount(int), rows(List<TaskImportRowVO>)

- [ ] **Step 2: 创建TaskImportRowVO**

字段：rowNum(int), taskName(String), sourceAgentId(String), sourceAgentName(String), sourceDir(String), targetAgentIds(String), targetAgentNames(String), targetDirs(String), validateStatus(String), validateMessage(String)

- [ ] **Step 3: 创建TaskImportBatchVO**

字段：batchNo(String), createTime(String), total(int), passCount(int), failCount(int), importStatus(String) — importStatus取该批次中import_status的聚合值(PENDING/IMPORTED/ROLLBACK/PARTIAL)

- [ ] **Step 4: 给BatchTransferTask添加导入用@Excel注解**

给以下字段添加 `type = Excel.Type.IMPORT` 的@Excel注解（如果还没有）：
- targetDirs: `@Excel(name = "目标目录", type = Excel.Type.IMPORT)`
- targetAgentIds: `@Excel(name = "目标节点ID", type = Excel.Type.IMPORT)`
- targetAgentNames: 已有注解，确认type包含IMPORT
- includePatterns: `@Excel(name = "包含模式", type = Excel.Type.IMPORT)`
- excludePatterns: `@Excel(name = "排除模式", type = Excel.Type.IMPORT)`
- routingConfig: `@Excel(name = "路由配置", type = Excel.Type.IMPORT)`

同时给枚举字段添加combo下拉选项：
- transferMode: `combo = "ONE_TO_ONE,ONE_TO_MANY"`
- routingStrategy: `combo = "BROADCAST,ROUND_ROBIN,RANDOM,REGION_BASED"`
- retryBackoffType: `combo = "LINEAR,EXPONENTIAL"`
- postTransferAction: `combo = "NONE,DELETE,BACKUP"`
- status: `combo = "READY,RUNNING,PAUSED"`

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -pl my-panel-admin -q`

- [ ] **Step 6: Commit**

```bash
git add my-panel-admin/src/main/java/com/cq/panel/admin/server/web/domain/vo/batch/TaskImportPreviewVO.java my-panel-admin/src/main/java/com/cq/panel/admin/server/web/domain/vo/batch/TaskImportRowVO.java my-panel-admin/src/main/java/com/cq/panel/admin/server/web/domain/vo/batch/TaskImportBatchVO.java my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferTask.java
git commit -m "feat(batch): add import VOs and Excel import annotations on BatchTransferTask"
```

---

## Task 3: Service层 — 导入服务接口与实现

**Files:**
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/IBatchTaskImportService.java`
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/impl/BatchTaskImportServiceImpl.java`

- [ ] **Step 1: 创建IBatchTaskImportService接口**

方法签名：
- `TaskImportPreviewVO uploadAndValidate(MultipartFile file, String userId)` — 解析Excel+校验+写入临时表
- `TaskImportPreviewVO preview(String batchNo)` — 预览临时表数据
- `int commitImport(String batchNo)` — 事务性批导入
- `int rollbackImport(String batchNo)` — 批回退
- `int batchStart(String batchNo)` — 批启用
- `int batchPause(String batchNo)` — 批暂停
- `List<TaskImportBatchVO> listBatches()` — 批次列表
- `void deleteBatch(String batchNo)` — 删除批次

- [ ] **Step 2: 实现BatchTaskImportServiceImpl**

核心逻辑：

**uploadAndValidate:**
1. ExcelUtil.importExcel(file.getInputStream()) 解析
2. 生成batchNo = UUID.randomUUID().toString().replace("-","")
3. 逐行校验：必填字段、枚举值、Cron格式、分号分隔字段格式
4. 冲突检测：复用WildcardConflictDetector，与正式表+批次内行间检测
5. 分号分隔字段原样存入临时表（不转JSON，commit时再转）
6. 批量插入临时表
7. 构造TaskImportPreviewVO返回

**commitImport:**
1. @Transactional
2. 查询临时表 validate_status=PASS 且 import_status=PENDING
3. 二次冲突校验（防止并发变化）
4. 逐行：分号→JSON转换 → INSERT batch_transfer_task → INSERT batch_sync_event(TASK_CREATED) → UPDATE import_status=IMPORTED
5. 任何一行失败则事务回滚

**rollbackImport:**
1. @Transactional
2. 查询 import_status=IMPORTED
3. 逐行：逻辑删除batch_transfer_task → INSERT batch_sync_event(TASK_DELETED) → UPDATE import_status=ROLLBACK

**batchStart/batchPause:**
1. 查询 import_status=IMPORTED
2. 逐行：UPDATE batch_transfer_task.status → INSERT batch_sync_event(TASK_STATUS_CHANGED)

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl my-panel-admin -q`

- [ ] **Step 4: Commit**

```bash
git add my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/IBatchTaskImportService.java my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/impl/BatchTaskImportServiceImpl.java
git commit -m "feat(batch): add BatchTaskImportService with upload, validate, commit, rollback"
```

---

## Task 4: Controller层 — 导入API接口

**Files:**
- Create: `my-panel-admin/src/main/java/com/cq/panel/admin/server/web/controller/batch/BatchTaskImportController.java`

- [ ] **Step 1: 创建BatchTaskImportController**

实现9个接口（参考设计文档4.1节）：
- POST /batch/task-import/template — 下载模板
- POST /batch/task-import/upload — 上传校验
- GET /batch/task-import/preview/{batchNo} — 预览
- POST /batch/task-import/commit/{batchNo} — 批导入
- POST /batch/task-import/rollback/{batchNo} — 批回退
- PUT /batch/task-import/batch-start/{batchNo} — 批启用
- PUT /batch/task-import/batch-pause/{batchNo} — 批暂停
- GET /batch/task-import/batches — 批次列表
- DELETE /batch/task-import/batch/{batchNo} — 删除批次

所有接口权限：`@RequirePermission("batch:task:import")`

模板下载参考SysUserController.importTemplate的实现模式。

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl my-panel-admin -q`

- [ ] **Step 3: Commit**

```bash
git add my-panel-admin/src/main/java/com/cq/panel/admin/server/web/controller/batch/BatchTaskImportController.java
git commit -m "feat(batch): add BatchTaskImportController with 9 API endpoints"
```

---

## Task 5: 单元测试

**Files:**
- Create: `my-panel-admin/src/test/java/com/cq/panel/admin/server/service/batch/BatchTaskImportServiceTest.java`

- [ ] **Step 1: 编写BatchTaskImportServiceTest**

测试场景（使用H2 + @MybatisTest）：

**上传校验测试：**
- 正常Excel上传 → 全部PASS
- 缺少必填字段 → 对应行FAIL
- 枚举值不合法 → FAIL
- Cron表达式格式错误 → FAIL
- 与正式表冲突 → FAIL
- 批次内行间冲突 → FAIL

**批导入测试：**
- 全部PASS的批次 → 导入成功，正式表有数据，sync_event有事件
- 包含FAIL行的批次 → 仅导入PASS行
- 导入期间正式表新增冲突任务 → 二次校验失败，事务回滚

**批回退测试：**
- 已导入的批次 → 回退后正式表逻辑删除，sync_event有TASK_DELETED事件

**批启用/暂停测试：**
- 已导入批次 → 启用后status=RUNNING，暂停后status=PAUSED

**边界测试：**
- 空Excel上传
- 超大Excel（>1000行）
- 重复batchNo查询

- [ ] **Step 2: 运行测试**

Run: `mvn test -pl my-panel-admin -Dtest=BatchTaskImportServiceTest`
Expected: 全部PASS

- [ ] **Step 3: Commit**

```bash
git add my-panel-admin/src/test/java/com/cq/panel/admin/server/service/batch/BatchTaskImportServiceTest.java
git commit -m "test(batch): add BatchTaskImportServiceTest with comprehensive scenarios"
```

---

## Task 6: 菜单数据 + data.sql

**Files:**
- Modify: `my-panel-admin/src/main/resources/sql/data.sql`

- [ ] **Step 1: 在data.sql中添加菜单数据**

检查是否已存在批量传输目录(menu_id=2300/2301/2302)，如果存在则跳过。新增：
- 2303: 任务导入菜单(C)
- 2304-2309: 操作权限按钮(F)

同时给admin角色(role_id=1)分配这些菜单权限。

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl my-panel-admin -q`

- [ ] **Step 3: Commit**

```bash
git add my-panel-admin/src/main/resources/sql/data.sql
git commit -m "feat(batch): add task import menu data to data.sql"
```

---

## Task 7: 前端API层

**Files:**
- Modify: `my-panel-ui/src/api/batch/index.js`

- [ ] **Step 1: 在batchApi中添加导入相关API方法**

```javascript
downloadTemplate: () => request({ url: '/batch/task-import/template', method: 'post', responseType: 'blob' }),
uploadTaskImport: (formData) => request({ url: '/batch/task-import/upload', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } }),
previewImport: (batchNo) => request({ url: `/batch/task-import/preview/${batchNo}`, method: 'get' }),
commitImport: (batchNo) => request({ url: `/batch/task-import/commit/${batchNo}`, method: 'post' }),
rollbackImport: (batchNo) => request({ url: `/batch/task-import/rollback/${batchNo}`, method: 'post' }),
batchStart: (batchNo) => request({ url: `/batch/task-import/batch-start/${batchNo}`, method: 'put' }),
batchPause: (batchNo) => request({ url: `/batch/task-import/batch-pause/${batchNo}`, method: 'put' }),
listBatches: () => request({ url: '/batch/task-import/batches', method: 'get' }),
deleteBatch: (batchNo) => request({ url: `/batch/task-import/batch/${batchNo}`, method: 'delete' })
```

- [ ] **Step 2: Commit**

```bash
git add my-panel-ui/src/api/batch/index.js
git commit -m "feat(batch): add task import API methods"
```

---

## Task 8: 前端Hook

**Files:**
- Create: `my-panel-ui/src/pages/batch/hooks/useTaskImport.js`

- [ ] **Step 1: 创建useTaskImport Hook**

管理状态：
- batches: 批次列表
- currentBatch: 当前选中批次
- previewData: 预览数据(TaskImportPreviewVO)
- uploading: 上传中状态
- operating: 操作中状态

方法：
- fetchBatches() — 加载批次列表
- handleUpload(file) — 上传Excel，成功后自动加载预览
- handleCommit() — 批导入
- handleRollback() — 批回退
- handleBatchStart() — 批启用
- handleBatchPause() — 批暂停
- handleDeleteBatch(batchNo) — 删除批次
- selectBatch(batchNo) — 选中批次并加载预览

- [ ] **Step 2: Commit**

```bash
git add my-panel-ui/src/pages/batch/hooks/useTaskImport.js
git commit -m "feat(batch): add useTaskImport hook"
```

---

## Task 9: 前端页面

**Files:**
- Create: `my-panel-ui/src/pages/batch/TaskImportPage.jsx`
- Modify: `my-panel-ui/src/pages/batch/index.jsx`
- Modify: `my-panel-ui/src/pages/batch/index.scss`

- [ ] **Step 1: 创建TaskImportPage.jsx**

页面布局：
- 上半区：Upload拖拽组件 + 操作按钮组（下载模板、批导入、批回退、批启用、批暂停、删除）
- 下半区：左侧批次列表（Table），右侧预览表格（校验通过绿色/失败红色标记）
- 所有操作按钮使用Popconfirm二次确认

- [ ] **Step 2: 在index.jsx中注册路由**

添加TaskImportPage的lazy import和路由配置。

- [ ] **Step 3: 在index.scss中添加导入页面样式**

添加 .task-import-page 相关样式，复用现有的 .subtask-toolbar / .subtask-table-container / .fixed-pagination-bar 样式。

- [ ] **Step 4: 运行lint**

Run: `cd my-panel-ui && npm run lint`
Expected: 0 errors

- [ ] **Step 5: Commit**

```bash
git add my-panel-ui/src/pages/batch/TaskImportPage.jsx my-panel-ui/src/pages/batch/index.jsx my-panel-ui/src/pages/batch/index.scss
git commit -m "feat(batch): add TaskImportPage with upload, preview, batch operations"
```

---

## Task 10: 集成测试 + 全量编译

**Files:**
- 无新增

- [ ] **Step 1: 后端全量编译**

Run: `mvn clean compile -pl my-panel-admin -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 运行后端全量测试**

Run: `mvn test -pl my-panel-admin`
Expected: 全部PASS

- [ ] **Step 3: 前端lint**

Run: `cd my-panel-ui && npm run lint`
Expected: 0 errors

- [ ] **Step 4: 启动后端验证接口可用**

启动admin服务，手动验证：
1. POST /batch/task-import/template → 下载模板
2. 填写模板后上传 → 返回校验结果
3. 批导入 → 正式表有数据
4. 批回退 → 正式表逻辑删除
5. 前端页面正常渲染

- [ ] **Step 5: Final Commit**

```bash
git add -A
git commit -m "feat(batch): complete batch task import feature - template, upload, validate, commit, rollback, batch operations"
```
