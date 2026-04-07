import request from '../../utils/request';

// 查询定时任务列表
export function listJob(query) {
  return request({
    url: '/monitor/job/list',
    method: 'get',
    params: query
  });
}

// 查询定时任务详细
export function getJob(jobId) {
  return request({
    url: '/monitor/job/' + jobId,
    method: 'get'
  });
}

// 新增定时任务
export function addJob(data) {
  return request({
    url: '/monitor/job',
    method: 'post',
    data: data
  });
}

// 修改定时任务
export function updateJob(data) {
  return request({
    url: '/monitor/job',
    method: 'put',
    data: data
  });
}

// 删除定时任务
export function delJob(jobId) {
  return request({
    url: '/monitor/job/' + jobId,
    method: 'delete'
  });
}

// 任务状态修改
export function changeJobStatus(jobId, status) {
  const data = {
    jobId,
    status
  };
  return request({
    url: '/monitor/job/changeStatus',
    method: 'put',
    data: data
  });
}

// 执行一次任务
export function runJob(jobId) {
  const data = {
    jobId
  };
  return request({
    url: '/monitor/job/run',
    method: 'put',
    data: data
  });
}

// 导出定时任务
export function exportJob(query) {
  return request({
    url: '/monitor/job/export',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}

// 查询任务组名列表
export function getJobGroups(jobGroup) {
  return request({
    url: '/monitor/job/jobGroups',
    method: 'get',
    params: { jobGroup }
  });
}

// 扫描内置方法列表
export function scanMethods() {
  return request({
    url: '/monitor/job/methods',
    method: 'get'
  });
}

// 验证内置方法
export function validateMethod(data) {
  return request({
    url: '/monitor/job/validateMethod',
    method: 'post',
    data: data
  });
}