# 批量传输功能简化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除批量传输功能中的监控、统计、告警、队列管理等非核心代码和表，仅保留核心传输任务管理和子任务明细记录。

**Architecture:** 按依赖关系从外到内删除：前端 → Admin Controller → Admin Service引用清理 → Admin Repository/Domain/DTO/VO → Proxy → SQL。每步删除后编译验证。

**Tech Stack:** React 19, Spring Boot 4, MyBatis, H2

---

## 文件变更总览

### 删除文件

**前端 (my-panel-ui):**
- `src/pages/batch/queueMonitor/index.jsx`
- `src/components/batch/QueueStatusCard.jsx`
- `src/components/batch/QueueTrendChart.jsx`
- `src/components/batch/MitigationSuggestion.jsx`
- `src/components/batch/AlertList.jsx`
- `src/components/batch/TaskSummaryCard.jsx`
- `src/components/batch/TargetProgressPanel.jsx`
- `src/components/batch/TransferModeSelector.jsx`
- `src/components/batch/RoutingStrategySelector.jsx`
- `src/components/batch/RegionConfigEditor.jsx`
- `src/components/batch/CronExpressionInput.jsx`
- `src/api/batch/monitor.js`

**Admin (my-panel-admin):**
- `web/controller/batch/BatchMonitorController.java`
- `repository/service/IBatchAlertEventService.java`
- `repository/service/IBatchTransferStatisticsService.java`
- `repository/service/IBatchTransferAgentStateService.java`
- `repository/service/impl/BatchAlertEventServiceImpl.java`
- `repository/service/impl/BatchTransferStatisticsServiceImpl.java`
- `repository/service/impl/BatchTransferAgentStateServiceImpl.java`
- `repository/mapper/BatchAlertEventMapper.java`
- `repository/mapper/BatchTransferStatisticsMapper.java`
- `repository/mapper/BatchTransferAgentStateMapper.java`
- `resources/mapper/batch/BatchAlertEventMapper.xml`
- `resources/mapper/batch/BatchTransferStatisticsMapper.xml`
- `resources/mapper/batch/BatchTransferAgentStateMapper.xml`
- `repository/domain/BatchAlertEvent.java`
- `repository/domain/BatchTransferStatistics.java`
- `repository/domain/BatchTransferAgentState.java`
- `web/domain/vo/batch/BatchDashboardVO.java`
- `web/domain/vo/batch/BatchAlertEventVO.java`
- `web/domain/vo/batch/AgentQueueStatusVO.java`

**Proxy (my-panel-proxy):**
- `service/batch/QueueMonitor.java`
- `service/batch/BandwidthManager.java`
- `config/BatchTransferProperties.java`

### 修改文件

- `src/api/batch/task.js` — 删除 `getTaskStatistics`, `refreshTaskStatistics`
- `BatchTransferService.java` — 删除 statistics/dashboard 相关方法和依赖
- `BatchTransferConverter.java` — 删除 alert 相关映射方法
- `BatchAdminController.java` — 删除 agent-state 端点
- `BatchInternalController.java` — 删除 queue/snapshot 和 post-process-result 端点，移除 QueueMonitor 依赖
- `ProgressAggregator.java` — 删除 `updateTaskStatistics()` 和 `receivePostProcessResult()`
- `schema.sql` — 删除 4 张表定义
- `data.sql` — 删除 4 个菜单项

---

## Task 1: 删除前端页面和组件

**Files:**
- Delete: `my-panel-ui/src/pages/batch/queueMonitor/index.jsx`
- Delete: `my-panel-ui/src/components/batch/QueueStatusCard.jsx`
- Delete: `my-panel-ui/src/components/batch/QueueTrendChart.jsx`
- Delete: `my-panel-ui/src/components/batch/MitigationSuggestion.jsx`
- Delete: `my-panel-ui/src/components/batch/AlertList.jsx`
- Delete: `my-panel-ui/src/components/batch/TaskSummaryCard.jsx`
- Delete: `my-panel-ui/src/components/batch/TargetProgressPanel.jsx`
- Delete: `my-panel-ui/src/components/batch/TransferModeSelector.jsx`
- Delete: `my-panel-ui/src/components/batch/RoutingStrategySelector.jsx`
- Delete: `my-panel-ui/src/components/batch/RegionConfigEditor.jsx`
- Delete: `my-panel-ui/src/components/batch/CronExpressionInput.jsx`
- Delete: `my-panel-ui/src/api/batch/monitor.js`
- Modify: `my-panel-ui/src/api/batch/task.js`

- [ ] **Step 1: 删除 queueMonitor 页面**

```bash
rm my-panel-ui/src/pages/batch/queueMonitor/index.jsx
```

- [ ] **Step 2: 删除不需要的组件**

```bash
rm my-panel-ui/src/components/batch/QueueStatusCard.jsx
rm my-panel-ui/src/components/batch/QueueTrendChart.jsx
rm my-panel-ui/src/components/batch/MitigationSuggestion.jsx
rm my-panel-ui/src/components/batch/AlertList.jsx
rm my-panel-ui/src/components/batch/TaskSummaryCard.jsx
rm my-panel-ui/src/components/batch/TargetProgressPanel.jsx
rm my-panel-ui/src/components/batch/TransferModeSelector.jsx
rm my-panel-ui/src/components/batch/RoutingStrategySelector.jsx
rm my-panel-ui/src/components/batch/RegionConfigEditor.jsx
rm my-panel-ui/src/components/batch/CronExpressionInput.jsx
```

- [ ] **Step 3: 删除 monitor API 文件**

```bash
rm my-panel-ui/src/api/batch/monitor.js
```

- [ ] **Step 4: 清理 task.js 中的 statistics 相关方法**

从 `src/api/batch/task.js` 中删除 `getTaskStatistics` 和 `refreshTaskStatistics` 函数。

- [ ] **Step 5: 验证前端无编译错误**

```bash
cd my-panel-ui && npx vite build 2>&1 | tail -5
```

Expected: 构建成功，无错误。

---

## Task 2: 删除 Admin BatchMonitorController

**Files:**
- Delete: `my-panel-admin/src/main/java/com/cq/panel/admin/server/web/controller/batch/BatchMonitorController.java`

- [ ] **Step 1: 删除文件**

```bash
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/web/controller/batch/BatchMonitorController.java
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl my-panel-admin -q
```

Expected: 编译成功（该Controller没有被其他代码引用）。

---

## Task 3: 清理 BatchTransferService 中的 statistics 依赖

**Files:**
- Modify: `my-panel-admin/src/main/java/com/cq/panel/admin/server/service/batch/BatchTransferService.java`

- [ ] **Step 1: 读取文件并删除 statistics 相关代码**

需要删除的内容：
1. import `BatchTransferStatistics` 和 `IBatchTransferStatisticsService`
2. 字段 `private final IBatchTransferStatisticsService statisticsService`
3. 构造函数参数 `IBatchTransferStatisticsService statisticsService` 及赋值
4. 方法 `getStatistics(Long taskId)` (约第278行)
5. 方法 `refreshStatistics(Long taskId)` (约第297行)
6. `deleteTasks` 方法中的 `statisticsService.deleteByTaskIds(ids)` 调用 (约第294行)
7. 所有 `refreshStatistics(taskId)` 调用（约第128、156、177、254行）— 直接删除这些行
8. `updateTaskStatistics` 相关逻辑（如果存在）

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl my-panel-admin -q
```

Expected: 编译成功。

---

## Task 4: 清理 BatchTransferConverter 中的 alert 映射

**Files:**
- Modify: `my-panel-admin/src/main/java/com/cq/panel/admin/server/web/converter/batch/BatchTransferConverter.java`

- [ ] **Step 1: 删除 alert 相关 import 和方法**

删除：
1. `import com.cq.panel.admin.server.repository.domain.BatchAlertEvent`
2. `import com.cq.panel.admin.server.web.domain.vo.batch.BatchAlertEventVO`
3. 方法 `BatchAlertEventVO toAlertVO(BatchAlertEvent entity)`
4. 方法 `List<BatchAlertEventVO> toAlertVOList(List<BatchAlertEvent> list)`

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl my-panel-admin -q
```

---

## Task 5: 删除 Admin Repository 层（Service接口+实现+Mapper接口+XML）

**Files:**
- Delete: `repository/service/IBatchAlertEventService.java`
- Delete: `repository/service/IBatchTransferStatisticsService.java`
- Delete: `repository/service/IBatchTransferAgentStateService.java`
- Delete: `repository/service/impl/BatchAlertEventServiceImpl.java`
- Delete: `repository/service/impl/BatchTransferStatisticsServiceImpl.java`
- Delete: `repository/service/impl/BatchTransferAgentStateServiceImpl.java`
- Delete: `repository/mapper/BatchAlertEventMapper.java`
- Delete: `repository/mapper/BatchTransferStatisticsMapper.java`
- Delete: `repository/mapper/BatchTransferAgentStateMapper.java`
- Delete: `resources/mapper/batch/BatchAlertEventMapper.xml`
- Delete: `resources/mapper/batch/BatchTransferStatisticsMapper.xml`
- Delete: `resources/mapper/batch/BatchTransferAgentStateMapper.xml`

- [ ] **Step 1: 删除 Service 接口**

```bash
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/IBatchAlertEventService.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/IBatchTransferStatisticsService.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/IBatchTransferAgentStateService.java
```

- [ ] **Step 2: 删除 Service 实现**

```bash
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/impl/BatchAlertEventServiceImpl.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/impl/BatchTransferStatisticsServiceImpl.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/service/impl/BatchTransferAgentStateServiceImpl.java
```

- [ ] **Step 3: 删除 Mapper 接口**

```bash
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/mapper/BatchAlertEventMapper.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/mapper/BatchTransferStatisticsMapper.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/mapper/BatchTransferAgentStateMapper.java
```

- [ ] **Step 4: 删除 Mapper XML**

```bash
rm my-panel-admin/src/main/resources/mapper/batch/BatchAlertEventMapper.xml
rm my-panel-admin/src/main/resources/mapper/batch/BatchTransferStatisticsMapper.xml
rm my-panel-admin/src/main/resources/mapper/batch/BatchTransferAgentStateMapper.xml
```

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -pl my-panel-admin -q
```

---

## Task 6: 删除 Admin Domain/VO/DTO

**Files:**
- Delete: `repository/domain/BatchAlertEvent.java`
- Delete: `repository/domain/BatchTransferStatistics.java`
- Delete: `repository/domain/BatchTransferAgentState.java`
- Delete: `web/domain/vo/batch/BatchDashboardVO.java`
- Delete: `web/domain/vo/batch/BatchAlertEventVO.java`
- Delete: `web/domain/vo/batch/AgentQueueStatusVO.java`

- [ ] **Step 1: 删除 Domain 类**

```bash
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchAlertEvent.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferStatistics.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferAgentState.java
```

- [ ] **Step 2: 删除 VO 类**

```bash
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/web/domain/vo/batch/BatchDashboardVO.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/web/domain/vo/batch/BatchAlertEventVO.java
rm my-panel-admin/src/main/java/com/cq/panel/admin/server/web/domain/vo/batch/AgentQueueStatusVO.java
```

- [ ] **Step 3: 编译验证**

```bash
mvn clean compile -pl my-panel-admin -q
```

Expected: Admin 模块编译成功。

---

## Task 7: 删除 Proxy 后端代码

**Files:**
- Delete: `my-panel-proxy/src/main/java/com/cq/proxy/service/batch/QueueMonitor.java`
- Delete: `my-panel-proxy/src/main/java/com/cq/proxy/service/batch/BandwidthManager.java`
- Delete: `my-panel-proxy/src/main/java/com/cq/proxy/config/BatchTransferProperties.java`
- Modify: `my-panel-proxy/src/main/java/com/cq/proxy/web/controller/batch/BatchAdminController.java`
- Modify: `my-panel-proxy/src/main/java/com/cq/proxy/web/controller/batch/BatchInternalController.java`
- Modify: `my-panel-proxy/src/main/java/com/cq/proxy/service/batch/ProgressAggregator.java`

- [ ] **Step 1: 删除 QueueMonitor、BandwidthManager、BatchTransferProperties**

```bash
rm my-panel-proxy/src/main/java/com/cq/proxy/service/batch/QueueMonitor.java
rm my-panel-proxy/src/main/java/com/cq/proxy/service/batch/BandwidthManager.java
rm my-panel-proxy/src/main/java/com/cq/proxy/config/BatchTransferProperties.java
```

- [ ] **Step 2: 清理 BatchAdminController — 删除 agent-state 端点**

从 `BatchAdminController.java` 中删除 `POST /agent-state` 端点方法及其相关 import。

- [ ] **Step 3: 清理 BatchInternalController — 删除 queue/snapshot 和 post-process-result 端点**

从 `BatchInternalController.java` 中：
1. 删除 `import com.cq.proxy.service.batch.QueueMonitor`
2. 删除字段 `private final QueueMonitor queueMonitor`
3. 从构造函数删除 `QueueMonitor queueMonitor` 参数
4. 删除 `receiveQueueSnapshot` 方法（第54-59行）
5. 删除 `receivePostProcessResult` 方法（第62-68行）

- [ ] **Step 4: 清理 ProgressAggregator — 删除 statistics 和 post-process 相关代码**

从 `ProgressAggregator.java` 中：
1. 删除 `updateTaskStatistics()` 方法
2. 删除 `receivePostProcessResult()` 方法
3. 删除 `flushAggregatedUpdates()` 中调用 `updateTaskStatistics` 的代码
4. 删除所有对 `batch_transfer_statistics` 表的 SQL 操作

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -pl my-panel-proxy -q
```

---

## Task 8: 清理 SQL 文件

**Files:**
- Modify: `my-panel-admin/src/main/resources/sql/schema.sql`
- Modify: `my-panel-admin/src/main/resources/sql/data.sql`

- [ ] **Step 1: 从 schema.sql 删除 4 张表定义**

删除以下表的 CREATE TABLE 语句及其索引：
1. `batch_transfer_statistics`（约第743行开始）
2. `batch_transfer_agent_state`（约第854行开始）
3. `agent_queue_snapshot`（约第893行开始）
4. `batch_alert_event`（约第946行开始）

- [ ] **Step 2: 从 data.sql 删除 4 个菜单项**

删除以下 INSERT 语句（menu_id）：
1. `3010` — 队列监控
2. `3011` — 传输仪表盘
3. `3012` — 传输统计
4. `3013` — Agent状态

- [ ] **Step 3: 全量编译验证**

```bash
mvn clean compile -pl my-panel-admin,my-panel-proxy -q
```

Expected: 两个模块都编译成功。

---

## Task 9: 最终验证

- [ ] **Step 1: 全量编译（不含 distribution）**

```bash
mvn clean install -DskipTests -pl !distribution -q
```

Expected: 所有模块安装成功。

- [ ] **Step 2: 前端构建验证**

```bash
cd my-panel-ui && npx vite build 2>&1 | tail -5
```

Expected: 构建成功。

- [ ] **Step 3: 确认保留的文件存在**

```bash
# 前端保留页面
ls my-panel-ui/src/pages/batch/taskList/index.jsx
ls my-panel-ui/src/pages/batch/subtaskDetail/index.jsx
# 保留的组件
ls my-panel-ui/src/components/batch/CreateTaskModal.jsx
ls my-panel-ui/src/components/batch/SubtaskTable.jsx
# Admin保留的核心服务
ls my-panel-admin/src/main/java/com/cq/panel/admin/server/service/batch/BatchTransferService.java
ls my-panel-admin/src/main/java/com/cq/panel/admin/server/web/controller/batch/BatchTransferController.java
```

Expected: 所有文件存在。