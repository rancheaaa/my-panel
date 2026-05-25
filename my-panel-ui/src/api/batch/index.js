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
  },

  /**
   * 获取任务列表（带节点在线状态和目录存在状态）
   */
  getTaskListWithStatus(params) {
    return request({
      url: '/batch/task/list-with-status',
      method: 'get',
      params: {
        id: params?.id || undefined,
        pageNum: params?.page || 1,
        pageSize: params?.size || 10,
        status: params?.status || undefined,
        sourceAgentId: params?.sourceAgentId || undefined,
        sourceAgentName: params?.sourceAgentName || undefined,
        taskName: params?.taskName || undefined,
        taskDescription: params?.taskDescription || undefined,
        sourceDir: params?.sourceDir || undefined,
        targetAgentId: params?.targetAgentId || undefined,
        targetAgentName: params?.targetAgentName || undefined,
        targetDir: params?.targetDir || undefined,
        transferMode: params?.transferMode || undefined,
        routingStrategy: params?.routingStrategy || undefined,
        postTransferAction: params?.postTransferAction || undefined,
        retryEnabled: params?.retryEnabled !== undefined ? params.retryEnabled : undefined,
        preserveDirStructure: params?.preserveDirStructure !== undefined ? params.preserveDirStructure : undefined
      }
    });
  },

  /**
   * 检查目录是否存在
   */
  checkDirectory(agentId, dirPath) {
    return request({
      url: '/batch/task/check-dir',
      method: 'get',
      params: { agentId, dirPath }
    });
  },

  /**
   * 获取传输明细文件汇总统计
   */
  getSubtaskSummary() {
    return request({
      url: '/batch/statistics/subtask-summary',
      method: 'get'
    });
  },

  /**
   * 获取传输任务汇总统计
   */
  getTaskSummary() {
    return request({
      url: '/batch/statistics/task-summary',
      method: 'get'
    });
  },

  /**
   * 获取全部汇总统计（任务+文件明细）
   */
  getFullSummary() {
    return request({
      url: '/batch/statistics/summary',
      method: 'get'
    });
  }
};
