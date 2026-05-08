import request from '../../utils/request';

export function getAgentQueueStatus(agentId, params) {
  return request({ url: `/batch/monitor/agents/${agentId}/queue-status`, method: 'get', params });
}

export function getQueueStatus(agentId, params) {
  return getAgentQueueStatus(agentId, params);
}

export function getQueueTrend(agentId, params) {
  return request({ url: `/batch/monitor/agents/${agentId}/queue-trend`, method: 'get', params });
}

export function getBatchDashboard() {
  return request({ url: '/batch/monitor/dashboard', method: 'get' });
}

export function listBatchAlerts(params) {
  return request({ url: '/batch/monitor/alerts', method: 'get', params });
}

export function resolveBatchAlert(alertId, data) {
  return request({ url: `/batch/monitor/alerts/${alertId}/resolve`, method: 'put', data });
}

export function getOperationLogs(params) {
  return request({ url: '/batch/monitor/operation-logs', method: 'get', params });
}

export function getAgentStates(params) {
  return request({ url: '/batch/monitor/agent-states', method: 'get', params });
}

export function getStatisticsList(params) {
  return request({ url: '/batch/monitor/statistics', method: 'get', params });
}
