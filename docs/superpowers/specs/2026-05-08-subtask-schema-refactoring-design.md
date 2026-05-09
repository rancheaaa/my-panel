# batch_transfer_subtask Schema Refactoring

## Background

当前 `batch_transfer_subtask` 表的 `file_path` 字段存储的是相对路径(相对于sourceDir)。前端展示发送节点、接收节点等信息需要admin端通过JOIN查询`agent_registry`和`batch_transfer_task`来补全。本次重构将这些信息直接存储在subtask表中，由proxy在创建时补全。

## Schema Changes

### Remove
- `file_path` varchar(1000) -- 被source_path和target_path替代

### Add
- `source_agent_id` varchar(50) NOT NULL -- 源Agent ID(从task表反规范化)
- `source_agent_name` varchar(100) DEFAULT NULL -- 源节点名称(从agent_registry查询)
- `target_agent_name` varchar(100) DEFAULT NULL -- 目标节点名称(从agent_registry查询)
- `source_path` varchar(1000) NOT NULL -- 源文件完整路径(sourceDir + relativePath)
- `target_path` varchar(1000) NOT NULL -- 目标文件完整路径(targetDir + relativePath)

### Keep
- `file_name` -- 保留，纯文件名
- `target_agent_id` -- 保留

### Index Changes
- Remove: `idx_task_file_target` (`task_id`, `file_path`(255), `target_agent_id`)
- Add: `idx_task_source_target` (`task_id`, `source_path`(255), `target_agent_id`)

## Data Flow

### Proxy创建Subtask时的数据来源

```
batch_transfer_task表:
  source_agent_id  --> subtask.source_agent_id
  source_dir       --> 拼接source_path用

agent_registry表:
  source agent id  --> node_name --> subtask.source_agent_name
  target agent id  --> node_name --> subtask.target_agent_name

扫描结果:
  relativePath     --> 拼接source_path和target_path用

agentDirMap:
  targetAgentId    --> targetDir --> 拼接target_path用
```

### source_path拼接逻辑
```
source_path = sourceDir + "/" + relativePath
例: /data/files + a/b.txt = /data/files/a/b.txt
```

### target_path拼接逻辑
```
preserveDirStructure=1: target_path = targetDir + "/" + relativePath
preserveDirStructure=0: target_path = targetDir + "/" + fileName
```

## Impact Analysis

### Proxy模块 (高影响 - 原始SQL)

| 文件 | 修改 |
|------|------|
| `BatchTaskScheduler.persistSubtasks()` | INSERT语句添加新字段，从task和agent_registry查询补全数据 |
| `BatchTaskScheduler.generateSubtasks()` | subtask map添加新字段 |
| `BatchTaskScheduler.startTask()` | 查询source_agent_name和target_agent_name |
| `BatchInternalController.persistSubtasks()` | INSERT语句添加新字段，lookup key从file_path改为source_path |
| `RetryScheduler.java` | SELECT语句更新，dispatch时使用新字段 |
| `ProgressAggregator.java` | composite key构建使用source_path替代filePath |

### Admin模块 (中低影响)

| 文件 | 修改 |
|------|------|
| `schema.sql` | 修改表定义 |
| `BatchTransferSubtask.java` | 移除filePath，添加5个新字段 |
| `BatchTransferSubtaskMapper.xml` | 更新resultMap、SELECT、INSERT语句 |
| `BatchTransferController.java` | 简化listSubtasks中的enrichment(不再需要resolveNodeName) |

### Frontend (低影响)

| 文件 | 修改 |
|------|------|
| `SubtaskTable.jsx` | 发送节点/目录列改用subtask自身的sourceAgentName/sourcePath |
| `FileProgressBar.jsx` | tooltip改用sourcePath/targetPath |
| `subtaskDetail/index.jsx` | 简化enrichedInfo传递 |

## Admin listSubtasks简化

当前admin的`listSubtasks`做了以下enrichment:
1. `sourceNodeName` -- 通过`resolveNodeName(task.getSourceAgentId())`查询
2. `targetAgentInfoMap` -- 遍历targetAgents查询nodeName

重构后:
- `sourceNodeName`直接从subtask的`sourceAgentName`获取
- `targetAgentInfoMap`中不再需要查询nodeName，因为subtask已有`targetAgentName`
- 保留`sourceDir`和`targetAgentInfoMap`中的dir信息(这些来自task配置)
