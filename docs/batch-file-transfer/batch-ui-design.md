# 批量文件传输 - 前端UI设计文档

> **日期**: 2026-05-10  
> **状态**: 已批准  
> **模式**: MVP (核心5模块)

---

## 1. 设计决策总结

| 决策项 | 选择 |
|--------|------|
| 功能范围 | MVP (任务列表/CRUD/状态管理/监控/统计) |
| 页面布局 | 单页Tab切换 (3个Tab) |
| 实时更新 | 轮询Polling (3秒间隔) |
| 代码架构 | React Hooks + 自定义Hook |

---

## 2. 整体页面结构

### 2.1 组件树

```
BatchTransferPage (主容器)
├── Ant Design Tabs
│   ├── Tab1: "任务列表" (TaskListTab)
│   │   ├── SearchBar (搜索/筛选)
│   │   ├── TaskTable (表格+操作按钮)
│   │   └── Pagination
│   ├── Tab2: "创建/编辑" (CreateEditTab)
│   │   └── TaskForm (表单)
│   └── Tab3: "统计面板" (StatisticsTab)
│       └── SummaryCards (4个统计卡片)
```

### 2.2 文件结构

```
src/pages/batch/
├── index.jsx                 # 主页面
├── components/
│   ├── TaskListTab.jsx
│   ├── CreateEditTab.jsx
│   ├── StatisticsTab.jsx
│   ├── TaskForm.jsx
│   └── TaskStatusBadge.jsx
├── hooks/
│   ├── useBatchTasks.js      # [核心] 任务CRUD Hook
│   ├── useTaskStatistics.js  # 统计数据Hook
│   └── usePolling.js         # 轮询Hook(3s)
└── constants.js              # 状态枚举/常量
```

---

## 3. 核心Hooks设计

### 3.1 `useBatchTasks` - 任务管理Hook

```javascript
// 使用示例
const { tasks, loading, createTask, updateTask, deleteTask, startTask } = useBatchTasks();

// 功能
- tasks: Task[]           // 任务列表
- loading: boolean        // 加载状态
- createTask(dto): void  // 创建任务
- updateTask(id, dto): void // 更新任务
- deleteTask(ids): void   // 删除任务(批量)
- startTask(id): void    // 启动任务
- pauseTask(id): void    // 暂停任务
// ... 其他状态操作

// 内部实现
- 调用 src/api/batch/index.js 的API方法
- 使用 useState/useEffect管理状态
- 错误处理: message.error()提示
```

### 3.2 `useTaskStatistics` - 统计数据Hook

```javascript
const { statistics, refreshStats } = useTaskStatistics(3000);

// 参数: pollIntervalMs (轮询间隔，默认3000ms)
// 返回值
- statistics: {
    total: number,
    running: number,
    paused: number,
    ready: number,
    stopped: number,
    error: number
  }
- refreshStats(): void  // 手动刷新

// 内部实现
- useEffect定时调用 GET /batch/task/statistics
- 组件卸载时清除定时器
```

### 3.3 `usePolling` - 通用轮询Hook

```javascript
const { data, start, stop } = usePolling(fetchFn, 3000);

// 通用轮询逻辑，可复用
- 自动启动/组件卸载时停止
- 支持手动stop/start
```

---

## 4. API集成层

### 4.1 API方法映射 (基于后端Controller)

| 前端方法 | HTTP Method | 后端Endpoint | 说明 |
|---------|------------|--------------|------|
| `getTaskList(params)` | GET | `/batch/task/list` | 分页查询 |
| `getTaskById(id)` | GET | `/batch/task/{id}` | 详情 |
| `createTask(dto)` | POST | `/batch/task` | 创建 |
| `updateTask(id, dto)` | PUT | `/batch/task/{id}` | 更新 |
| `deleteTasks(ids)` | DELETE | `/batch/task/{ids}` | 批量删除 |
| `startTask(id)` | POST | `/batch/task/{id}/start` | 启动 |
| `pauseTask(id)` | POST | `/batch/task/{id}/pause` | 暂停 |
| `resumeTask(id)` | POST | `/batch/task/{id}/resume` | 恢复 |
| `stopTask(id)` | POST | `/batch/task/{id}/stop` | 停止 |
| `getStatistics()` | GET | `/batch/task/statistics` | 统计 |

### 4.2 DTO数据结构 (与后端BatchTransferTaskDTO对齐)

```typescript
interface BatchTransferTaskDTO {
  taskName: string;
  sourceAgentId: string;
  sourceAgentName?: string;
  sourceDir: string;
  targetDirs: string;          // JSON数组序列化
  includePatterns: string[];   // ["*.log", "*.txt"]
  excludePatterns?: string[];
  targetAgentIds: string[];
  targetAgentNames?: string[];
  cronExpression?: string;
  maxRetries?: number;
}
```

---

## 5. 关键交互流程

### 5.1 创建任务流程

```
[用户点击"新建任务"]
    → Tab自动切换到"创建/编辑"
    → 填写TaskForm表单
    → 点击"提交"
    → useBatchTasks.createTask(dto) 调用API
    → 成功: message.success() + 切换到"任务列表"Tab + 刷新
    → 失败: message.error(错误信息) + 表单保留数据
```

### 5.2 启动任务流程

```
[用户点击表格行"启动"按钮]
    → Modal.confirm("确认启动该任务？")
    → 确认: useBatchTasks.startTask(id)
    → 成功: 
      - 该行状态badge变为"RUNNING" (绿色)
      - 自动刷新统计数据
    → 失败: message.error()
```

### 5.3 实时监控流程

```
[任务运行中...]
    → useTaskStatistics 每3秒轮询 /batch/task/statistics
    → StatisticsTab的SummaryCards数字更新
    → TaskListTable中的进度条/状态badge更新
    → (可选) 浏览器Title显示 "(3个运行中)"
```

---

## 6. UI组件规范

### 6.1 状态Badge颜色

| 状态 | 颜色 | Ant Design Tag Color |
|------|------|---------------------|
| READY | 默认灰 | default |
| RUNNING | 绿色 | success (带脉冲动画) |
| PAUSED | 橙色 | warning |
| STOPPED | 红色 | error |
| COMPLETED | 蓝色 | processing |

### 6.2 表格列定义

| 列名 | 字段 | 宽度 | 操作 |
|------|------|------|------|
| 任务ID | id | 80px | - |
| 任务名称 | taskName | 200px | - |
| 源Agent | sourceAgentName | 150px | - |
| 目标数 | targetAgentIds.length | 80px | - |
| 状态 | status (Badge) | 100px | - |
| Cron | cronExpression | 120px | - |
| 操作 | - | 200px | 启动/暂停/编辑/删除 |

### 6.3 统计卡片布局

```
┌─────────────┬─────────────┬─────────────┬─────────────┐
│   📊 总计    │   🟢 运行中   │   ⏸️ 暂停    │   🔴 失败    │
│     25       │      3       │      2       │      1       │
└─────────────┴─────────────┴─────────────┴─────────────┘
```

使用 Ant Design `Statistic` 组件或自定义Card。

---

## 7. 测试策略

### 7.1 单元测试 (Jest + React Testing Library)

- **Hooks测试**: `useBatchTasks.test.js`
  - Mock API调用 (jest.mock('../../api/batch'))
  - 验证状态更新、错误处理
  
- **组件测试**: `TaskListTab.test.js`
  - 渲染验证 (snapshot testing)
  - 交互测试 (click事件)

### 7.2 E2E测试 (可选，后续阶段)

- Playwright/Cypress 浏览器自动化
- 完整流程测试: 创建→启动→查看进度→停止

---

## 8. 实现优先级 (Phase顺序)

### P0 - 核心功能 (本次实现)
1. ✅ 常量定义 + API接口封装
2. ✅ `useBatchTasks` Hook + CRUD
3. ✅ `TaskListTab` 组件 (表格+分页+搜索)
4. ✅ `CreateEditTab` + `TaskForm` 组件
5. ✅ 主页面 `index.jsx` Tabs整合

### P1 - 监控增强 (后续迭代)
6. ⏳ `useTaskStatistics` Hook + 轮询
7. ⏳ `StatisticsTab` 组件 (统计卡片)
8. ⏳ `TaskStatusBadge` 组件 (状态徽标+动画)

### P2 - 体验优化 (未来)
9. 📋 WebSocket实时推送 (替代轮询)
10. 📋 子任务详情Modal
11. 📋 批量操作 (多选/批量启停)

---

## 9. 技术约束 & 风险点

| 风险 | 缓解措施 | 优先级 |
|------|---------|--------|
| 后端API未就绪 | 使用Mock数据进行前端开发 | 低 |
| 轮询性能开销 | 优化: 仅在"统计Tab激活时"轮询 | 中 |
| 大量任务渲染卡顿 | 虚拟滚动(Virtual Scroll) | 中 |
| 表单校验复杂度 | 使用Ant Design Form + 自定义规则 | 低 |

---

**设计者**: AI Assistant  
**审查状态**: 待用户最终确认  
**下一步**: 调用 writing-plans skill → TDD实现
