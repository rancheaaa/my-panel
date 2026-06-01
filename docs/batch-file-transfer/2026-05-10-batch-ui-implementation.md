# 批量文件传输前端UI - 实现计划

> **For agentic workers:** 使用 subagent-driven-development 模式执行此计划  
> **Goal:** 构建批量文件传输管理的React前端界面(MVP)，支持任务CRUD、状态管理、实时统计监控  
> **Architecture:** React 19 + Vite 7 + Ant Design 6 + Custom Hooks模式  
> **Tech Stack**: React Hooks, Ant Design, Axios, Jest, React Testing Library

---

## 文件结构总览

```
my-panel-ui/src/
├── pages/batch/
│   ├── index.jsx                    # [修改] 主页面入口
│   ├── components/
│   │   ├── TaskListTab.jsx         # [新建] 任务列表Tab
│   │   ├── CreateEditTab.jsx       # [新建] 创建/编辑Tab
│   │   ├── StatisticsTab.jsx       # [新建] 统计面板Tab
│   │   ├── TaskForm.jsx            # [新建] 任务表单组件
│   │   └── TaskStatusBadge.jsx    # [新建] 状态徽标组件
│   └── hooks/
│       ├── useBatchTasks.js        # [新建] 任务CRUD Hook
│       ├── useTaskStatistics.js    # [新建] 统计数据Hook
│       └── usePolling.js           # [新建] 轮询Hook
├── api/
│   └── batch/
│       └── index.js               # [修改] API接口封装
└── utils/
    └── request.js                 # [已有] HTTP请求工具
```

**总计**: 新建10个文件 + 修改2个现有文件

---

## Task 1: 常量定义 + 基础设施

**Files:**
- Create: `src/pages/batch/constants.js`

### Step 1: 编写常量定义测试

```javascript
// src/pages/batch/__tests__/constants.test.js
describe('Batch Constants', () => {
  test('should export task status enum', () => {
    const { TASK_STATUS } = require('../constants');
    expect(TASK_STATUS.READY).toBe('READY');
    expect(TASK_STATUS.RUNNING).toBe('RUNNING');
    expect(TASK_STATUS.PAUSED).toBe('PAUSED');
    expect(TASK_STATUS.STOPPED).toBe('STOPPED');
    expect(TASK_STATUS.COMPLETED).toBe('COMPLETED');
  });

  test('should export status color mapping', () => {
    const { STATUS_COLOR } = require('../constants');
    expect(STATUS_COLOR.RUNNING).toBe('success');
    expect(STATUS_COLOR.STOPPED).toBe('error');
    expect(STATUS_COLOR.PAUSED).toBe('warning');
  });
});
```

### Step 2: 运行测试验证失败

```bash
cd my-panel-ui && npx jest src/pages/batch/__tests__/constants.test.js --no-cache
# Expected: FAIL - Cannot find module '../constants'
```

### Step 3: 实现常量定义

```javascript
// src/pages/batch/constants.js
export const TASK_STATUS = {
  READY: 'READY',
  RUNNING: 'RUNNING',
  PAUSED: 'PAUSED',
  STOPPED: 'STOPPED',
  COMPLETED: 'COMPLETED',
  ERROR: 'ERROR'
};

export const STATUS_COLOR = {
  [TASK_STATUS.READY]: 'default',
  [TASK_STATUS.RUNNING]: 'success',
  [TASK_STATUS.PAUSED]: 'warning',
  [TASK_STATUS.STOPPED]: 'error',
  [TASK_STATUS.COMPLETED]: 'processing',
  [TASK_STATUS.ERROR]: 'error'
};

export const DEFAULT_PAGINATION = {
  current: 1,
  pageSize: 10,
  total: 0
};

export const POLLING_INTERVAL = 3000; // 3 seconds
```

### Step 4: 运行测试验证通过

```bash
cd my-panel-ui && npx jest src/pages/batch/__tests__/constants.test.js --no-cache
# Expected: PASS (2 tests)
```

### Step 5: Git提交

```bash
git add src/pages/batch/constants.js src/pages/batch/__tests__/constants.test.js
git commit -m "feat(batch): add constants for status enum and colors"
```

---

## Task 2: API接口层封装

**Files:**
- Modify: `src/api/batch/index.js`
- Create: `src/api/batch/__tests__/index.test.js`

### Step 1: 编写API测试 (Mock axios)

```javascript
// src/api/batch/__tests__/index.test.js
import { batchApi } from '../index';
import axios from 'axios';

jest.mock('axios');

describe('Batch API', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('getTaskList should call GET /batch/task/list', async () => {
    const mockData = { data: { code: 200, data: [], total: 0 } };
    axios.get.mockResolvedValue(mockData);

    const result = await batchApi.getTaskList({ page: 1, size: 10 });
    
    expect(axios.get).toHaveBeenCalledWith(
      '/api/batch/task/list',
      expect.objectContaining({ params: { pageNum: 1, pageSize: 10 } })
    );
    expect(result).toEqual(mockData.data);
  });

  test('createTask should call POST /batch/task', async () => {
    const mockData = { data: { code: 200, data: 100 } };
    axios.post.mockResolvedValue(mockData);

    const result = await batchApi.createTask({ taskName: 'test' });
    
    expect(axios.post).toHaveBeenCalledWith('/api/batch/task', expect.any(Object));
    expect(result).toEqual(mockData.data);
  });

  // ... 其他8个API方法的类似测试 (update/delete/getById/start/pause/resume/stop/statistics)
});
```

### Step 2: 运行测试验证失败

```bash
npx jest src/api/batch/__tests__/index.test.js --no-cache
# Expected: FAIL - module not found or method errors
```

### Step 3: 实现API封装

```javascript
// src/api/batch/index.js
import request from '@/utils/request';

/**
 * 批量传输任务API
 */
export const batchApi = {
  /**
   * 获取任务列表
   */
  getTaskList(params) {
    return request({
      url: '/batch/task/list',
      method: 'get',
      params: { 
        pageNum: params?.page || 1, 
        pageSize: params?.size || 10,
        status: params?.status || undefined,
        taskName: params?.keyword || undefined
      }
    });
  },

  /**
   * 获取任务详情
   */
  getTaskById(taskId) {
    return request({
      url: `/batch/task/${taskId}`,
      method: 'get'
    });
  },

  /**
   * 创建任务
   */
  createTask(data) {
    return request({
      url: '/batch/task',
      method: 'post',
      data
    });
  },

  /**
   * 更新任务
   */
  updateTask(taskId, data) {
    return request({
      url: `/batch/task/${taskId}`,
      method: 'put',
      data
    });
  },

  /**
   * 删除任务(批量)
   */
  deleteTasks(ids) {
    return request({
      url: `/batch/task/${ids.join(',')}`,
      method: 'delete'
    });
  },

  /**
   * 启动任务
   */
  startTask(taskId) {
    return request({
      url: `/batch/task/${taskId}/start`,
      method: 'post'
    });
  },

  /**
   * 暂停任务
   */
  pauseTask(taskId) {
    return request({
      url: `/batch/task/${taskId}/pause`,
      method: 'post'
    });
  },

  /**
   * 恢复任务
   */
  resumeTask(taskId) {
    return request({
      url: `/batch/task/${taskId}/resume`,
      method: 'post'
    });
  },

  /**
   * 停止任务
   */
  stopTask(taskId) {
    return request({
      url: `/batch/task/${taskId}/stop`,
      method: 'post'
    });
  },

  /**
   * 获取统计数据
   */
  getStatistics() {
    return request({
      url: '/batch/task/statistics',
      method: 'get'
    });
  }
};
```

### Step 4: 运行全部API测试

```bash
npx jest src/api/batch/__tests__/index.test.js --no-cache --coverage
# Expected: PASS (10 tests, 100% coverage)
```

### Step 5: Git提交

```bash
git add src/api/batch/index.js src/api/batch/__tests__/index.test.js
git commit -m "feat(batch): implement batch API layer with 10 endpoints"
```

---

## Task 3: usePolling Hook (通用轮询)

**Files:**
- Create: `src/pages/batch/hooks/usePolling.js`
- Create: `src/pages/batch/hooks/__tests__/usePolling.test.js`

### Step 1: 编写轮询Hook测试

```javascript
// src/pages/batch/hooks/__tests__/usePolling.test.js
import { renderHook } from '@testing-library/react-hooks';
import { usePolling } from '../usePolling';

describe('usePolling', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.clearAllMocks();
  });

  test('should call fetchFn on mount and then on interval', () => {
    const mockFetch = jest.fn().mockResolvedValue({ running: 3, paused: 1 });
    
    const { data } = renderHook(() => usePolling(mockFetch, 3000));
    
    expect(mockFetch).toHaveBeenCalledTimes(1);
    
    jest.advanceTimersByTime(3000);
    expect(mockFetch).toHaveBeenCalledTimes(2);
    
    jest.advanceTimersByTime(6000);
    expect(mockFetch).toHaveBeenCalledTimes(3);
  });

  test('should stop polling when unmounted', () => {
    const mockFetch = jest.fn();
    
    const { unmount } = renderHook(() => usePolling(mockFetch, 1000));
    
    unmount(); // 组件卸载
    
    jest.advanceTimersByTime(5000);
    expect(mockFetch).toHaveBeenCalledTimes(1); // 不应再调用
  });

  test('should support manual stop/start', () => {
    const mockFetch = jest.fn();
    
    const { result } = renderHook(() => usePolling(mockFetch, 1000));
    
    result.current.stop();
    jest.advanceTimersByTime(5000);
    expect(mockFetch).toHaveBeenCalledTimes(1); // 已停止
    
    result.current.start();
    jest.advanceTimersByTime(1500);
    expect(mockFetch).toHaveBeenCalledTimes(2); // 已恢复
  });
});
```

### Step 2: 运行测试失败

```bash
npx jest src/pages/batch/hooks/__tests__/usePolling.test.js --no-cache
# Expected: FAIL
```

### Step 3: 实现轮询Hook

```javascript
// src/pages/batch/hooks/usePolling.js
import { useEffect, useRef, useCallback } from 'react';

/**
 * 通用轮询Hook
 * @param {Function} fetchFn - 数据获取函数
 * @param {number} intervalMs - 轮询间隔(毫秒)
 * @returns {{ data: any, stop: Function, start: Function }}
 */
export function usePolling(fetchFn, intervalMs = 3000) {
  const [data, setData] = useState(null);
  const timerRef = useRef(null);
  const isRunningRef = useRef(true);

  const fetchData = useCallback(async () => {
    if (!isRunningRef.current) return;
    
    try {
      const result = await fetchFn();
      setData(result);
    } catch (error) {
      console.error('[usePolling] Fetch error:', error);
    }
  }, [fetchFn]);

  useEffect(() => {
    fetchData(); // 立即执行一次
    
    timerRef.current = setInterval(fetchData, intervalMs);
    
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, [fetchFn, intervalMs]);

  const stop = useCallback(() => {
    isRunningRef.current = false;
  }, []);

  const start = useCallback(() => {
    isRunningRef.current = true;
  }, []);

  return { data, stop, start };
}
```

### Step 4: 测试通过

```bash
npx jest src/pages/batch/hooks/__tests__/usePolling.test.js --no-cache
# Expected: PASS (4 tests)
```

### Step 5: Git提交

```bash
git add src/pages/batch/hooks/usePolling.js src/pages/batch/hooks/__tests__/usePolling.test.js
git commit -m "feat(batch): add usePolling hook with auto-stop on unmount"
```

---

## Task 4: useBatchTasks Hook (核心业务逻辑)

**Files:**
- Create: `src/pages/batch/hooks/useBatchTasks.js`
- Create: `src/pages/batch/hooks/__tests__/useBatchTasks.test.js`

### Step 1: 编写任务管理Hook测试

```javascript
// src/pages/batch/hooks/__tests__/useBatchTasks.test.js
import { renderHook, act } from '@testing-library/react-hooks';
import { useBatchTasks } from '../useBatchTasks';
import * as batchApi from '../../../api/batch';

jest.mock('../../../api/batch');

describe('useBatchTasks', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('should load tasks on mount', async () => {
    const mockTasks = [
      { id: 1, taskName: 'Task A', status: 'READY' },
      { id: 2, taskName: 'Task B', status: 'RUNNING' }
    ];
    batchApi.getTaskList.mockResolvedValue({ data: { code: 200, data: mockTasks, total: 2 } });

    let hookResult;
    await act(async () => {
      hookResult = renderHook(() => useBatchTasks());
    });

    expect(hookResult.tasks.current).toEqual(mockTasks);
    expect(hookResult.loading.current).toBe(false);
    expect(batchApi.getTaskList).toHaveBeenCalledWith(expect.objectContaining({
      params: expect.objectContaining({ pageNum: 1, pageSize: 10 })
    }));
  });

  test('createTask should call API and refresh list', async () => {
    batchApi.createTask.mockResolvedValue({ data: { code: 200, data: 100 } });
    batchApi.getTaskList.mockResolvedValue({ data: { code: 200, data: [], total: 0 } });

    let hookResult;
    await act(async () => {
      hookResult = renderHook(() => useBatchTasks());
    });

    await act(async () => {
      await hookResult.createTask({ taskName: 'New Task' });
    });

    expect(batchApi.createTask).toHaveBeenCalled();
    expect(batchApi.getTaskList).toHaveBeenCalledTimes(2); // 初始 + 创建后刷新
  });

  test('deleteTask should call delete API with ids array', async () => {
    batchApi.deleteTasks.mockResolvedValue({ data: { code: 200 } });
    
    let hookResult;
    await act(async () => {
      hookResult = renderHook(() => useBatchTasks());
    });

    await act(async () => {
      await hookResult.deleteTasks([1, 2, 3]);
    });

    expect(batchApi.deleteTasks).toHaveBeenCalledWith([1, 2, 3]);
  });

  // ... startTask/pauseTask/stopTask 类似测试 (共12+个用例)
});
```

### Step 2: 运行测试

```bash
npx jest src/pages/batch/hooks/__tests__/useBatchTasks.test.js --no-cache --coverage
# Expected: FAIL
```

### Step 3: 实现useBatchTasks Hook

```javascript
// src/pages/batch/hooks/useBatchTasks.js
import { useState, useCallback } from 'react';
import * as batchApi from '../../api/batch';
import { message } from 'antd';

export function useBatchTasks() {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const fetchTasks = useCallback(async (params = {}) => {
    setLoading(true);
    try {
      const res = await batchApi.getTaskList(params);
      if (res.code === 200) {
        setTasks(res.data || []);
        setPagination(prev => ({ ...prev, total: res.total || 0 }));
      }
    } catch (error) {
      message.error('加载任务列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const createTask = useCallback(async (data) => {
    try {
      const res = await batchApi.createTask(data);
      if (res.code === 200) {
        message.success('任务创建成功');
        await fetchTasks(); // 刷新列表
        return res.data;
      } else {
        message.error(res.msg || '创建失败');
        return null;
      }
    } catch (error) {
      message.error('网络异常');
      return null;
    }
  }, []);

  const updateTask = useCallback(async (id, data) => {
    try {
      const res = await batchApi.updateTask(id, data);
      if (res.code === 200) {
        message.success('更新成功');
        await fetchTasks();
      } else {
        message.error(res.msg || '更新失败');
      }
    } catch (error) {
      message.error('网络异常');
    }
  }, []);

  const deleteTask = useCallback(async (ids) => {
    try {
      const res = await batchApi.deleteTasks(ids);
      if (res.code === 200) {
        message.success(`成功删除${ids.length}个任务`);
        await fetchTasks();
      } else {
        message.error(res.msg || '删除失败');
      }
    } catch (error) {
      message.error('网络异常');
    }
  }, []);

  const startTask = useCallback(async (id) => {
    try {
      const res = await batchApi.startTask(id);
      if (res.code === 200) {
        message.success('任务已启动');
        await fetchTasks();
      } else {
        message.error(res.msg || '启动失败');
      }
    } catch (error) {
      message.error('网络异常');
    }
  }, []);

  const pauseTask = useCallback(async (id) => {
    try {
      const res = await batchApi.pauseTask(id);
      if (res.code === 200) {
        message.success('任务已暂停');
        await fetchTasks();
      } else {
        message.error(res.msg || '暂停失败');
      }
    } catch (error) {
      message.error('网络异常');
    }
  }, []);

  const resumeTask = useCallback(async (id) => {
    try {
      const res = await batchApi.resumeTask(id);
      if (res.code === 200) {
        message.success('任务已恢复');
        await fetchTasks();
      } else {
        message.error(res.msg || '恢复失败');
      }
    } catch (error) {
      message.error('网络异常');
    }
  }, []);

  const stopTask = useCallback(async (id) => {
    try {
      const res = await batchApi.stopTask(id);
      if (res.code === 200) {
        message.success('任务已停止');
        await fetchTasks();
      } else {
        message.error(res.msg || '停止失败');
      }
    } catch (error) {
      message.error('网络异常');
    }
  }, []);

  // 初始加载
  useState(() => {
    fetchTasks();
  });

  return {
    tasks,
    loading,
    pagination,
    fetchTasks,
    createTask,
    updateTask,
    deleteTask,
    startTask,
    pauseTask,
    resumeTask,
    stopTask
  };
}
```

### Step 4: 测试通过

```bash
npx jest src/pages/batch/hooks/__tests__/useBatchTasks.test.js --no-cache --coverage
# Expected: PASS (12+ tests)
```

### Step 5: Git提交

```bash
git add src/pages/batch/hooks/useBatchTasks.js src/pages/batch/hooks/__tests__/useBatchTasks.test.js
git commit -m "feat(batch): add useBatchTasks hook with full CRUD operations"
```

---

## Task 5: TaskStatusBadge 状态徽标组件

**Files:**
- Create: `src/pages/batch/components/TaskStatusBadge.jsx`
- Create: `src/pages/batch/components/__tests__/TaskStatusBadge.test.jsx`

### Step 1: 编写状态徽标测试

```jsx
// src/pages/batch/components/__tests__/TaskStatusBadge.test.jsx
import { render } from '@testing-library/react';
import TaskStatusBadge from '../TaskStatusBadge';
import { TASK_STATUS, STATUS_COLOR } from '../../constants';

describe('TaskStatusBadge', () => {
  test('renders READY status with default color', () => {
    const { container } = render(<TaskStatusBadge status={TASK_STATUS.READY} />);
    expect(container.textContent).toContain('READY');
    expect(container.querySelector('.ant-tag')).toHaveClass('ant-tag-default');
  });

  test('renders RUNNING status with success color and pulse effect', () => {
    const { container } = render(<TaskStatusBadge status={TASK_STATUS.RUNNING} />);
    expect(container.textContent).toContain('RUNNING');
    expect(container.querySelector('.ant-tag')).toHaveClass('ant-tag-success');
  });

  test('renders STOPPED status with error color', () => {
    const { container } = render(<TaskStatusBadge status={TASK_STATUS.STOPPED} />);
    expect(container.querySelector('.ant-tag')).toHaveClass('ant-tag-error');
  });

  test('renders unknown status as fallback', () => {
    const { container } = render(<TaskStatusBadge status="UNKNOWN" />);
    expect(container.textContent).toContain('UNKNOWN');
  });
});
```

### Step 2: 测试失败

```bash
npx jest src/pages/batch/components/__tests__/TaskStatusBadge.test.jsx --no-cache
# Expected: FAIL
```

### Step 3: 实现状态徽标组件

```jsx
// src/pages/batch/components/TaskStatusBadge.jsx
import React from 'react';
import { Tag } from 'antd';
import { TASK_STATUS, STATUS_COLOR } from '../constants';

const TaskStatusBadge = ({ status }) => {
  const color = STATUS_COLOR[status] || 'default';
  const displayText = status || 'UNKNOWN';

  return (
    <Tag color={color}>{displayText}</Tag>
  );
};

export default React.memo(TaskStatusBadge);
```

### Step 4: 测试通过

```bash
npx jest src/pages/batch/components/__tests__/TaskStatusBadge.test.jsx --no-cache
# Expected: PASS (4 tests)
```

### Step 5: Git提交

```bash
git add src/pages/batch/components/TaskStatusBadge.jsx src/pages/batch/components/__tests__/TaskStatusBadge.test.jsx
git commit -m "feat(batch): add TaskStatusBadge component"
```

---

## Task 6: TaskForm 任务表单组件

**Files:**
- Create: `src/pages/batch/components/TaskForm.jsx`
- Create: `src/pages/batch/components/__tests__/TaskForm.test.jsx`

### Step 1-5: TDD实现表单组件 (包含字段校验、提交回调等8个测试用例)

**关键功能点**:
- 表单字段: taskName, sourceAgentId, sourceDir, targetDirs, includePatterns, excludePatterns, targetAgentIds
- 校验规则: 必填项、格式验证
- 提交回调: onSubmit prop

*(详细代码略，遵循上述TDD模式)*

---

## Task 7: StatisticsTab 统计面板组件

**Files:**
- Create: `src/pages/batch/components/StatisticsTab.jsx`
- Create: `src/pages/batch/components/__tests__/StatisticsTab.test.jsx`

### Step 1-5: TDD实现统计面板

**关键功能点**:
- 4个Statistic卡片 (总数/运行中/暂停/失败)
- 使用 `useTaskStatistics` Hook获取数据
- 展示数字 + 图标 *(可选)*

---

## Task 8: TaskListTab 任务列表组件

**Files:**
- Create: `src/pages/batch/components/TaskListTab.jsx`
- Create: `src/pages/batch/components/__tests__/TaskListTab.test.jsx`

### Step 1-5: TDD实现任务列表

**关键功能点**:
- Table展示 (列: ID/名称/源Agent/目标数/状态/Cron/操作)
- 操作按钮: 启动/暂停/编辑/删除
- 分页器集成
- 搜索/筛选栏
- 状态Badge使用

---

## Task 9: CreateEditTab 创建/编辑Tab

**Files:**
- Create: `src/pages/batch/components/CreateEditTab.jsx`
- Create: `src/pages/batch/components/__tests__/CreateEditTab.test.jsx`

### Step 1-5: TDD实现创建编辑Tab

**关键功能点**:
- TaskForm嵌入
- 提交/取消按钮
- 编辑模式数据回填

---

## Task 10: 主页面整合 (index.jsx)

**Files:**
- Modify: `src/pages/batch/index.jsx`
- Create: `src/pages/batch/__tests__/index.test.jsx`

### Step 1-5: TDD实现主页面

**关键功能点**:
- Ant Design Tabs容器 (3个Tab)
- 整合所有子组件
- 默认激活"任务列表"Tab
- 页面标题/布局

---

## 实施顺序总结

| Task | 内容 | 复杂度 | 依赖 |
|------|------|--------|------|
| **1** | 常量定义 | ⭐ 低 | 无 |
| **2** | API接口层 | ⭐⭐ 中 | Task 1 |
| **3** | usePolling Hook | ⭐⭐ 中 | 无 |
| **4** | useBatchTasks Hook | ⭐⭐⭐ 高 | Task 2 |
| **5** | TaskStatusBadge组件 | ⭐ 低 | Task 1 |
| **6** | TaskForm组件 | ⭐⭐ 中 | Task 1 |
| **7** | StatisticsTab组件 | ⭐⭐ 中 | Task 4 |
| **8** | TaskListTab组件 | ⭐⭐⭐ 高 | Task 4,5 |
| **9** | CreateEditTab组件 | ⭐⭐ 中 | Task 6 |
| **10** | 主页面整合 | ⭐ 低 | Task 5-9 |

**预计总测试数**: 50+ 个单元测试  
**预计Git Commits**: 10次 (每Task一次)

---

## 风险与缓解措施

| 风险 | 缓解方案 | 优先级 |
|------|---------|--------|
| 后端API未就绪 | Mock数据开发前端，后续切换真实API | 低 |
| Ant Design版本兼容 | 锁定项目实际使用的Ant Design版本 | 中 |
| 样式冲突 | 遵循现有组件的CSS-in-JS模式 | 低 |

---

**Plan created by**: AI Assistant  
**Date**: 2026-05-10  
**Status**: Ready for execution  
**Next step**: 使用 subagent-driven-development 开始TDD实现
