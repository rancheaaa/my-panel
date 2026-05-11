// 完全mock request模块，避免解析真实文件中的import.meta
jest.mock('../../../utils/request', () => {
  return jest.fn();
});

import { batchApi } from '../index';
import request from '../../../utils/request';

describe('Batch API', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('getTaskList should call GET /batch/task/list with correct params', async () => {
    const mockResponse = { code: 200, data: [], total: 0 };
    request.mockResolvedValue(mockResponse);

    const result = await batchApi.getTaskList({ page: 1, size: 10 });
    
    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/list',
      method: 'get',
      params: expect.objectContaining({
        pageNum: 1,
        pageSize: 10
      })
    });
    expect(result).toEqual(mockResponse);
  });

  test('getTaskList should support status and keyword filters', async () => {
    const mockResponse = { code: 200, data: [], total: 0 };
    request.mockResolvedValue(mockResponse);

    await batchApi.getTaskList({ page: 2, size: 20, status: 'RUNNING', keyword: 'test' });
    
    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/list',
      method: 'get',
      params: expect.objectContaining({
        pageNum: 2,
        pageSize: 20,
        status: 'RUNNING',
        taskName: 'test'
      })
    });
  });

  test('getTaskById should call GET /batch/task/:id', async () => {
    const mockResponse = { code: 200, data: { id: 1 } };
    request.mockResolvedValue(mockResponse);

    const result = await batchApi.getTaskById(1);
    
    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/1',
      method: 'get'
    });
    expect(result).toEqual(mockResponse);
  });

  test('createTask should call POST /batch/task with data', async () => {
    const mockResponse = { code: 200, data: 100 };
    request.mockResolvedValue(mockResponse);

    const taskData = { taskName: 'Test Task' };
    const result = await batchApi.createTask(taskData);
    
    expect(request).toHaveBeenCalledWith({
      url: '/batch/task',
      method: 'post',
      data: taskData
    });
    expect(result).toEqual(mockResponse);
  });

  test('updateTask should call PUT /batch/task/:id with data', async () => {
    const mockResponse = { code: 200, msg: '更新成功' };
    request.mockResolvedValue(mockResponse);

    const updateData = { taskName: 'Updated Task' };
    const result = await batchApi.updateTask(1, updateData);
    
    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/1',
      method: 'put',
      data: updateData
    });
    expect(result).toEqual(mockResponse);
  });

  test('deleteTasks should call DELETE /batch/task/:ids with array', async () => {
    const mockResponse = { code: 200, msg: '删除成功' };
    request.mockResolvedValue(mockResponse);

    const result = await batchApi.deleteTasks([1, 2, 3]);
    
    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/1,2,3',
      method: 'delete'
    });
    expect(result).toEqual(mockResponse);
  });

  test('startTask should call PUT /batch/task/:id/start', async () => {
    const mockResponse = { code: 200, msg: '启动成功' };
    request.mockResolvedValue(mockResponse);

    const result = await batchApi.startTask(1);

    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/1/start',
      method: 'put'
    });
    expect(result).toEqual(mockResponse);
  });

  test('pauseTask should call PUT /batch/task/:id/pause', async () => {
    const mockResponse = { code: 200, msg: '暂停成功' };
    request.mockResolvedValue(mockResponse);

    const result = await batchApi.pauseTask(1);

    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/1/pause',
      method: 'put'
    });
    expect(result).toEqual(mockResponse);
  });

  test('resumeTask should call PUT /batch/task/:id/resume', async () => {
    const mockResponse = { code: 200, msg: '恢复成功' };
    request.mockResolvedValue(mockResponse);

    const result = await batchApi.resumeTask(1);

    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/1/resume',
      method: 'put'
    });
    expect(result).toEqual(mockResponse);
  });

  test('stopTask should call DELETE /batch/task/:id/stop', async () => {
    const mockResponse = { code: 200, msg: '停止成功' };
    request.mockResolvedValue(mockResponse);

    const result = await batchApi.stopTask(1);

    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/1/stop',
      method: 'delete'
    });
    expect(result).toEqual(mockResponse);
  });

  test('getStatistics should call GET /batch/task/statistics', async () => {
    const mockResponse = { 
      code: 200, 
      data: { total: 10, running: 3, paused: 2, stopped: 1 }
    };
    request.mockResolvedValue(mockResponse);

    const result = await batchApi.getStatistics();
    
    expect(request).toHaveBeenCalledWith({
      url: '/batch/task/statistics',
      method: 'get'
    });
    expect(result).toEqual(mockResponse);
  });
});
