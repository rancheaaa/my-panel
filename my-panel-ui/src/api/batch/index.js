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
   * 导出任务数据为Excel
   */
  exportTasks(params, ids) {
    return request({
      url: '/batch/task/export',
      method: 'post',
      params: {
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
        preserveDirStructure: params?.preserveDirStructure !== undefined ? params.preserveDirStructure : undefined,
        ids: ids || undefined
      },
      responseType: 'blob'
    });
  },

  /**
   * 导出子任务数据为Excel
   */
  exportSubtasks(params, ids) {
    const queryParams = {
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
    };
    if (ids) {
      queryParams.ids = ids;
    }
    return request({
      url: '/batch/subtask/export',
      method: 'post',
      params: queryParams,
      responseType: 'blob'
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

  checkDirectories(taskId) {
    return request({
      url: '/batch/task/dir-check',
      method: 'get',
      params: { taskId }
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
  },

  downloadTemplate() {
    return request({
      url: '/batch/task-import/template',
      method: 'post',
      responseType: 'blob'
    });
  },

  uploadTaskImport(formData) {
    return request({
      url: '/batch/task-import/upload',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    });
  },

  previewImport(batchNo) {
    return request({
      url: `/batch/task-import/preview/${batchNo}`,
      method: 'get'
    });
  },

  commitImport(batchNo, mode) {
    return request({
      url: `/batch/task-import/commit/${batchNo}`,
      method: 'post',
      params: { mode: mode || 'STRICT' }
    });
  },

  rollbackImport(batchNo) {
    return request({
      url: `/batch/task-import/rollback/${batchNo}`,
      method: 'post'
    });
  },

  batchStartImport(batchNo) {
    return request({
      url: `/batch/task-import/batch-start/${batchNo}`,
      method: 'put'
    });
  },

  batchPauseImport(batchNo) {
    return request({
      url: `/batch/task-import/batch-pause/${batchNo}`,
      method: 'put'
    });
  },

  listImportBatches(keyword) {
    return request({
      url: '/batch/task-import/batches',
      method: 'get',
      params: keyword ? { keyword } : {}
    });
  },

  deleteImportBatch(batchNo) {
    return request({
      url: `/batch/task-import/batch/${batchNo}`,
      method: 'delete'
    });
  },

  checkConnectivity(sourceNodeName, targetNodeName) {
    return request({
      url: '/batch/connectivity/check',
      method: 'get',
      params: { sourceNodeName, targetNodeName }
    });
  },

  checkConnectivityOp(sourceNodeName, targetNodeName) {
    return request({
      url: '/batch/connectivity/check-op',
      method: 'get',
      params: { sourceNodeName, targetNodeName }
    });
  },

  /**
   * 导出Agent配置文件(zip)
   */
  exportAgentConfig(sourceAgentId) {
    return request({
      url: '/batch/config-export/download',
      method: 'post',
      data: { sourceAgentId },
      responseType: 'blob'
    });
  },

  /**
   * 校验Agent配置
   */
  verifyAgentConfig(sourceAgentId) {
    return request({
      url: '/batch/config-verify/verify',
      method: 'post',
      data: { sourceAgentId }
    });
  },

  /**
   * 强制推送配置到Agent
   */
  pushAgentConfig(sourceAgentId) {
    return request({
      url: '/batch/config-verify/push',
      method: 'post',
      data: { sourceAgentId }
    });
  }
};
