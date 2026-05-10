import { renderHook, act } from '@testing-library/react';

jest.mock('../../../../api/batch', () => ({
  getTaskList: jest.fn(),
  createTask: jest.fn(),
  updateTask: jest.fn(),
  deleteTasks: jest.fn(),
  startTask: jest.fn(),
  pauseTask: jest.fn(),
  resumeTask: jest.fn(),
  stopTask: jest.fn()
}));

import { useBatchTasks } from '../useBatchTasks';
import * as batchApi from '../../../../api/batch';

describe('useBatchTasks', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('should load tasks on mount', async () => {
    const mockTasks = [
      { id: 1, taskName: 'Task A', status: 'READY' },
      { id: 2, taskName: 'Task B', status: 'RUNNING' }
    ];
    batchApi.getTaskList.mockResolvedValue({ code: 200, data: mockTasks, total: 2 });

    let hookResult;
    await act(async () => {
      const { result } = renderHook(() => useBatchTasks());
      hookResult = result;
      await new Promise(resolve => setTimeout(resolve, 0));
    });

    expect(hookResult.current.tasks).toEqual(mockTasks);
    expect(batchApi.getTaskList).toHaveBeenCalled();
  });

  test('createTask should call API and return result', async () => {
    batchApi.getTaskList.mockResolvedValue({ code: 200, data: [], total: 0 });
    batchApi.createTask.mockResolvedValue({ code: 200, data: 100 });

    let hookResult;
    await act(async () => {
      const { result } = renderHook(() => useBatchTasks());
      hookResult = result;
      await new Promise(resolve => setTimeout(resolve, 0));
    });

    const createResult = await act(async () => {
      return await hookResult.current.createTask({ taskName: 'New Task' });
    });

    expect(batchApi.createTask).toHaveBeenCalledWith({ taskName: 'New Task' });
    expect(createResult).toBe(100);
  });

  test('deleteTask should call deleteTasks with ids array', async () => {
    batchApi.getTaskList.mockResolvedValue({ code: 200, data: [], total: 0 });
    batchApi.deleteTasks.mockResolvedValue({ code: 200 });

    let hookResult;
    await act(async () => {
      const { result } = renderHook(() => useBatchTasks());
      hookResult = result;
      await new Promise(resolve => setTimeout(resolve, 0));
    });

    await act(async () => {
      await hookResult.current.deleteTask([1, 2, 3]);
    });

    expect(batchApi.deleteTasks).toHaveBeenCalledWith([1, 2, 3]);
  });

  test('startTask should call startTask API', async () => {
    batchApi.getTaskList.mockResolvedValue({ code: 200, data: [], total: 0 });
    batchApi.startTask.mockResolvedValue({ code: 200 });

    let hookResult;
    await act(async () => {
      const { result } = renderHook(() => useBatchTasks());
      hookResult = result;
      await new Promise(resolve => setTimeout(resolve, 0));
    });

    await act(async () => {
      await hookResult.current.startTask(1);
    });

    expect(batchApi.startTask).toHaveBeenCalledWith(1);
  });

  test('pauseTask should call pauseTask API', async () => {
    batchApi.getTaskList.mockResolvedValue({ code: 200, data: [], total: 0 });
    batchApi.pauseTask.mockResolvedValue({ code: 200 });

    let hookResult;
    await act(async () => {
      const { result } = renderHook(() => useBatchTasks());
      hookResult = result;
      await new Promise(resolve => setTimeout(resolve, 0));
    });

    await act(async () => {
      await hookResult.current.pauseTask(1);
    });

    expect(batchApi.pauseTask).toHaveBeenCalledWith(1);
  });

  test('resumeTask should call resumeTask API', async () => {
    batchApi.getTaskList.mockResolvedValue({ code: 200, data: [], total: 0 });
    batchApi.resumeTask.mockResolvedValue({ code: 200 });

    let hookResult;
    await act(async () => {
      const { result } = renderHook(() => useBatchTasks());
      hookResult = result;
      await new Promise(resolve => setTimeout(resolve, 0));
    });

    await act(async () => {
      await hookResult.current.resumeTask(1);
    });

    expect(batchApi.resumeTask).toHaveBeenCalledWith(1);
  });

  test('stopTask should call stopTask API', async () => {
    batchApi.getTaskList.mockResolvedValue({ code: 200, data: [], total: 0 });
    batchApi.stopTask.mockResolvedValue({ code: 200 });

    let hookResult;
    await act(async () => {
      const { result } = renderHook(() => useBatchTasks());
      hookResult = result;
      await new Promise(resolve => setTimeout(resolve, 0));
    });

    await act(async () => {
      await hookResult.current.stopTask(1);
    });

    expect(batchApi.stopTask).toHaveBeenCalledWith(1);
  });

  test('should expose all required methods and state', () => {
    batchApi.getTaskList.mockResolvedValue({ code: 200, data: [], total: 0 });

    const { result } = renderHook(() => useBatchTasks());

    expect(result.current).toHaveProperty('tasks');
    expect(result.current).toHaveProperty('loading');
    expect(result.current).toHaveProperty('pagination');
    expect(result.current).toHaveProperty('fetchTasks');
    expect(result.current).toHaveProperty('createTask');
    expect(result.current).toHaveProperty('updateTask');
    expect(result.current).toHaveProperty('deleteTask');
    expect(result.current).toHaveProperty('startTask');
    expect(result.current).toHaveProperty('pauseTask');
    expect(result.current).toHaveProperty('resumeTask');
    expect(result.current).toHaveProperty('stopTask');
  });
});
