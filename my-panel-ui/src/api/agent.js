import request from '../utils/request';

// 查询Agent注册信息列表
export function listAgentRegistry(query) {
  return request({
    url: '/agent/registry/list',
    method: 'get',
    params: query
  });
}

// 查询Agent注册信息详细
export function getAgentRegistry(id) {
  return request({
    url: '/agent/registry/' + id,
    method: 'get'
  });
}

// 新增Agent注册信息
export function addAgentRegistry(data) {
  return request({
    url: '/agent/registry',
    method: 'post',
    data: data
  });
}

// 修改Agent注册信息
export function updateAgentRegistry(data) {
  return request({
    url: '/agent/registry',
    method: 'put',
    data: data
  });
}

// 删除Agent注册信息
export function delAgentRegistry(ids) {
  return request({
    url: '/agent/registry/' + ids,
    method: 'delete'
  });
}

// 导出Agent注册信息
export function exportAgentRegistry(query) {
  return request({
    url: '/agent/registry/export',
    method: 'post',
    responseType: 'blob'
  });
}

// 执行Agent命令
export function executeAgentCommand(agentId, command, timeout = 30) {
  return request({
    url: '/agent/registry/execute',
    method: 'post',
    data: {
      agentId: agentId,
      command: command,
      timeout: timeout
    },
    timeout: (timeout + 10) * 1000 // 设置请求超时时间，比命令超时多10秒
  });
}


// Agent注册
export function registerAgent(data) {
  return request({
    url: '/agent/registry/register',
    method: 'post',
    data: data
  });
}

// Agent心跳
export function heartbeat(agentIp, agentPort) {
  return request({
    url: '/agent/registry/heartbeat',
    method: 'post',
    params: { agentIp, agentPort }
  });
}

// 下线超时节点
export function offlineTimeoutNodes(timeoutSeconds) {
  return request({
    url: '/agent/registry/offline',
    method: 'post',
    params: { timeoutSeconds }
  });
}