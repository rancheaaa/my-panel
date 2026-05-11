import request from '../../utils/request';

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
      method: 'put'
    });
  },

  /**
   * 暂停任务
   */
  pauseTask(taskId) {
    return request({
      url: `/batch/task/${taskId}/pause`,
      method: 'put'
    });
  },

  /**
   * 恢复任务
   */
  resumeTask(taskId) {
    return request({
      url: `/batch/task/${taskId}/resume`,
      method: 'put'
    });
  },

  /**
   * 停止任务（逻辑删除）
   */
  stopTask(taskId) {
    return request({
      url: `/batch/task/${taskId}/stop`,
      method: 'delete'
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
