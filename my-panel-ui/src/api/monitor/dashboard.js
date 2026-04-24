import request from '../../utils/request';

export function getDashboardData() {
  return request({
    url: '/monitor/dashboard/data',
    method: 'get'
  });
}

export function getDashboardOverview() {
  return request({
    url: '/monitor/dashboard/overview',
    method: 'get'
  });
}

export function getDashboardTrend(data) {
  return request({
    url: '/monitor/dashboard/trend',
    method: 'post',
    data
  });
}

export function getAlertRules() {
  return request({
    url: '/monitor/dashboard/alert/rules',
    method: 'get'
  });
}

export function saveAlertRule(data) {
  return request({
    url: '/monitor/dashboard/alert/rule',
    method: 'post',
    data
  });
}

export function deleteAlertRule(id) {
  return request({
    url: `/monitor/dashboard/alert/rule/${id}`,
    method: 'delete'
  });
}

export function getAlertEvents(params) {
  return request({
    url: '/monitor/dashboard/alert/events',
    method: 'get',
    params
  });
}

export function updateAlertEventStatus(id, status) {
  return request({
    url: `/monitor/dashboard/alert/event/${id}/status`,
    method: 'put',
    params: { status }
  });
}
