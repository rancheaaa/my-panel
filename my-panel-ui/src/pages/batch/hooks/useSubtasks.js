import { useState, useCallback, useEffect, useRef } from 'react';
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
        targetFilePath: params?.targetFilePath || undefined,
        targetAgentId: params?.targetAgentId || undefined,
        sourceAgentId: params?.sourceAgentId || undefined,
        sourceAgentName: params?.sourceAgentName || undefined,
        targetAgentName: params?.targetAgentName || undefined,
        fileName: params?.fileName || undefined,
        scanBatchId: params?.scanBatchId || undefined,
        fileBatchId: params?.fileBatchId || undefined
      }
    });
  }
};

export function useSubtasks() {
  const [subtasks, setSubtasks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const paginationRef = useRef(pagination);
  paginationRef.current = pagination;

  const fetchSubtasks = useCallback(async (params = {}) => {
    setLoading(true);
    try {
      const page = params?.page || paginationRef.current.current;
      const size = params?.size || paginationRef.current.pageSize;
      const res = await subtaskApi.getList({ page, size, ...params });
      if (res.code === 200) {
        setSubtasks(res.data?.data || []);
        setPagination({
          current: res.data?.pageNum || page,
          pageSize: size,
          total: res.data?.total || 0
        });
      }
    } catch (error) {
      console.error('加载传输明细失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSubtasks();
  }, []);

  return {
    subtasks,
    loading,
    pagination,
    fetchSubtasks
  };
}
