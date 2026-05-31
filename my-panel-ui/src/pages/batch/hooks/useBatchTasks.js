import { useState, useCallback, useEffect, useRef } from 'react';
import { batchApi } from '../../../api/batch';

export function useBatchTasks() {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const paginationRef = useRef(pagination);
  paginationRef.current = pagination;

  const fetchTasks = useCallback(async (params = {}) => {
    setLoading(true);
    try {
      const page = params.page || paginationRef.current.current;
      const size = params.size || paginationRef.current.pageSize;
      const apiParams = {
        id: params.id || undefined,
        page,
        size,
        status: params.status || undefined,
        sourceAgentId: params.sourceAgentId || undefined,
        sourceAgentName: params.sourceAgentName || undefined,
        taskName: params.taskName || undefined,
        taskDescription: params.taskDescription || undefined,
        sourceDir: params.sourceDir || undefined,
        targetAgentId: params.targetAgentId || undefined,
        targetAgentName: params.targetAgentName || undefined,
        targetDir: params.targetDir || undefined,
        transferMode: params.transferMode || undefined,
        routingStrategy: params.routingStrategy || undefined,
        postTransferAction: params.postTransferAction || undefined,
        retryEnabled: params.retryEnabled !== undefined ? params.retryEnabled : undefined,
        preserveDirStructure: params.preserveDirStructure !== undefined ? params.preserveDirStructure : undefined
      };
      const res = await batchApi.getTaskListWithStatus(apiParams);
      if (res.code === 200) {
        setTasks(res.data?.data || []);
        setPagination(prev => ({
          ...prev,
          current: res.data?.pageNum || page,
          pageSize: res.data?.pageSize || size,
          total: res.data?.total || 0
        }));
      }
    } catch (error) {
      console.error('加载任务列表失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  const createTask = useCallback(async (data) => {
    try {
      const res = await batchApi.createTask(data);
      if (res.code === 200) {
        await fetchTasks();
        return res.data;
      }
      throw new Error(res.msg || '创建任务失败');
    } catch (error) {
      console.error('创建任务失败:', error);
      throw error;
    }
  }, [fetchTasks]);

  const updateTask = useCallback(async (id, data) => {
    try {
      const res = await batchApi.updateTask(id, data);
      if (res.code === 200) {
        await fetchTasks();
      }
      return res;
    } catch (error) {
      console.error('更新任务失败:', error);
      return null;
    }
  }, [fetchTasks]);

  const deleteTask = useCallback(async (ids) => {
    try {
      const res = await batchApi.deleteTasks(ids);
      if (res.code === 200) {
        await fetchTasks();
      }
      return res;
    } catch (error) {
      console.error('删除任务失败:', error);
      return null;
    }
  }, [fetchTasks]);

  const startTask = useCallback(async (id) => {
    try {
      const res = await batchApi.startTask(id);
      if (res.code === 200) {
        await fetchTasks();
      }
      return res;
    } catch (error) {
      console.error('启动任务失败:', error);
      return null;
    }
  }, [fetchTasks]);

  const pauseTask = useCallback(async (id) => {
    try {
      const res = await batchApi.pauseTask(id);
      if (res.code === 200) {
        await fetchTasks();
      }
      return res;
    } catch (error) {
      console.error('暂停任务失败:', error);
      return null;
    }
  }, [fetchTasks]);

  const resumeTask = useCallback(async (id) => {
    try {
      const res = await batchApi.resumeTask(id);
      if (res.code === 200) {
        await fetchTasks();
      }
      return res;
    } catch (error) {
      console.error('恢复任务失败:', error);
      return null;
    }
  }, [fetchTasks]);

  const stopTask = useCallback(async (id) => {
    try {
      const res = await batchApi.stopTask(id);
      if (res.code === 200) {
        await fetchTasks();
      }
      return res;
    } catch (error) {
      console.error('停止任务失败:', error);
      return null;
    }
  }, [fetchTasks]);

  useEffect(() => {
    fetchTasks();
  }, [fetchTasks]);

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
