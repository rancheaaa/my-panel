# 批量传输功能简化设计

## 概述

简化现有批量传输文件功能，删除监控、统计、告警、队列管理等非核心功能，仅保留最核心的传输任务管理和子任务明细记录。

## 设计原则

- 核心传输流程不受影响：Admin创建任务 → Proxy协调 → Agent扫描+分发+上传 → 进度回报
- 子任务明细表（`batch_transfer_subtask`）完整保留
- Agent模块不做修改（独立的传输执行引擎）

## 保留范围

### SQL表（3张）

| 表 | 说明 |
|----|------|
| `batch_transfer_task` | 任务配置 |
| `batch_transfer_subtask` | 子任务明细 |
| `batch_transfer_operation_log` | 操作日志 |

### 前端（2页面 + 5组件）

| 类型 | 文件 |
|------|------|
| 页面 | `pages/batch/taskList/index.jsx` |
| 页面 | `pages/batch/subtaskDetail/index.jsx` |
| 组件 | `CreateTaskModal.jsx`, `UpdateTaskConfigModal.jsx`, `TaskConfigDetailModal.jsx` |
| 组件 | `SubtaskTable.jsx`, `FileProgressBar.jsx` |
| API | `api/batch/task.js`（删除statistics相关方法） |

### Admin后端

- Controller: `BatchTransferController`（保留全部端点）
- Service: `BatchTransferService`（删除statistics/dashboard方法）, `ProxyApiClient`
- Repository: Task, Subtask, OperationLog 全栈
- DTO/VO/Converter: 任务和子任务相关

### Proxy后端

- `BatchAdminController`（删除agent-state端点）
- `BatchInternalController`（保留progress + subtasks/persist）
- `BatchTaskScheduler`, `ProgressAggregator`, `RetryScheduler`, `RoutingScheduler`
- 所有路由策略类

## 删除范围

### SQL表（4张）

| 表 |
|----|
| `batch_transfer_statistics` |
| `batch_transfer_agent_state` |
| `agent_queue_snapshot` |
| `batch_alert_event` |

### 前端

| 类型 | 文件 |
|------|------|
| 页面 | `pages/batch/queueMonitor/index.jsx` |
| 组件 | `QueueStatusCard.jsx`, `QueueTrendChart.jsx`, `MitigationSuggestion.jsx` |
| 组件 | `AlertList.jsx`, `TaskSummaryCard.jsx`, `TargetProgressPanel.jsx` |
| 组件 | `TransferModeSelector.jsx`, `RoutingStrategySelector.jsx`, `RegionConfigEditor.jsx`, `CronExpressionInput.jsx` |
| API | `api/batch/monitor.js` 整个删除 |

### Admin后端

| 层 | 文件 |
|----|------|
| Controller | `BatchMonitorController.java` |
| Service接口 | `IBatchAlertEventService`, `IBatchTransferStatisticsService`, `IBatchTransferAgentStateService` |
| Service实现 | `BatchAlertEventServiceImpl`, `BatchTransferStatisticsServiceImpl`, `BatchTransferAgentStateServiceImpl` |
| Mapper接口 | `BatchAlertEventMapper`, `BatchTransferStatisticsMapper`, `BatchTransferAgentStateMapper` |
| Mapper XML | `BatchAlertEventMapper.xml`, `BatchTransferStatisticsMapper.xml`, `BatchTransferAgentStateMapper.xml` |
| Domain | `BatchAlertEvent`, `BatchTransferStatistics`, `BatchTransferAgentState` |
| VO | `BatchDashboardVO`, `BatchAlertEventVO`, `AgentQueueStatusVO` |

### Proxy后端

| 文件 | 说明 |
|------|------|
| `QueueMonitor.java` | 队列监控 |
| `BandwidthManager.java` | 带宽管理 |

### data.sql菜单清理

删除：3010 队列监控, 3011 传输仪表盘, 3012 传输统计, 3013 Agent状态

## 实施顺序

1. 删除前端页面和组件
2. 删除Admin后端代码（Controller → Service → Mapper → Domain → DTO/VO）
3. 删除Proxy后端代码
4. 清理schema.sql和data.sql
5. 编译验证