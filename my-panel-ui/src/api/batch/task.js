import request from '../../utils/request';

export function createBatchTask(data) {
  return request({ url: '/batch/tasks', method: 'post', data });
}

export function startBatchTask(taskId) {
  return request({ url: `/batch/tasks/${taskId}/start`, method: 'put' });
}

export function pauseBatchTask(taskId) {
  return request({ url: `/batch/tasks/${taskId}/pause`, method: 'put' });
}

export function resumeBatchTask(taskId) {
  return request({ url: `/batch/tasks/${taskId}/resume`, method: 'put' });
}

export function updateBatchTaskConfig(taskId, data) {
  return request({ url: `/batch/tasks/${taskId}/config`, method: 'put', data });
}

export function listBatchTasks(params) {
  return request({ url: '/batch/tasks', method: 'get', params });
}

export function getBatchTaskDetail(taskId) {
  return request({ url: `/batch/tasks/${taskId}`, method: 'get' });
}

export function retrySubtask(taskId, subtaskId) {
  return request({ url: `/batch/tasks/${taskId}/subtasks/${subtaskId}/retry`, method: 'post' });
}

export function deleteBatchTasks(ids) {
  return request({ url: `/batch/tasks/${ids}`, method: 'delete' });
}

export function listSubtasks(taskId, params) {
  return request({ url: `/batch/tasks/${taskId}/subtasks`, method: 'get', params });
}
