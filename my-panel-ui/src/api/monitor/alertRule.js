import request from '../../utils/request';

export function getAlertRules() {
  return request({
    url: '/monitor/alert/rules',
    method: 'get'
  });
}

export function saveAlertRule(data) {
  return request({
    url: '/monitor/alert/rule',
    method: 'post',
    data
  });
}

export function deleteAlertRule(id) {
  return request({
    url: `/monitor/alert/rule/${id}`,
    method: 'delete'
  });
}

export function getAlertEvents(params) {
  return request({
    url: '/monitor/alert/events',
    method: 'get',
    params
  });
}

export function updateAlertEventStatus(id, status) {
  return request({
    url: `/monitor/alert/event/${id}/status`,
    method: 'put',
    params: { status }
  });
}
