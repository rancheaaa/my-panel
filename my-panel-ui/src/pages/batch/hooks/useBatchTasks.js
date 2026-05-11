import { useState, useCallback, useEffect } from 'react';
import { batchApi } from '../../../api/batch';

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
      return null;
    } catch (error) {
      console.error('创建任务失败:', error);
      return null;
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
