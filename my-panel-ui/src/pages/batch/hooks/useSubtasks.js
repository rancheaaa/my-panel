import { useState, useCallback, useEffect } from 'react';
import request from '../../../utils/request';

const subtaskApi = {
  getList(params) {
    return request({
      url: '/batch/subtask/list',
      method: 'get',
      params: {
        pageNum: params?.page || 1,
        pageSize: params?.size || 10,
        taskId: params?.taskId || undefined,
        status: params?.status || undefined,
        sourceFilePath: params?.sourceFilePath || undefined,
        targetAgentId: params?.targetAgentId || undefined
      }
    });
  }
};

export function useSubtasks() {
  const [subtasks, setSubtasks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const fetchSubtasks = useCallback(async (params = {}) => {
    setLoading(true);
    try {
      const res = await subtaskApi.getList(params);
      if (res.code === 200) {
        setSubtasks(res.data?.data || []);
        setPagination(prev => ({ 
          ...prev, 
          total: res.data?.total || 0,
          current: res.data?.pageNum || 1 
        }));
      }
    } catch (error) {
      console.error('加载传输明细失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSubtasks();
  }, [fetchSubtasks]);

  return {
    subtasks,
    loading,
    pagination,
    fetchSubtasks
  };
}
