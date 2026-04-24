import request from '../../utils/request';

export function getDashboardData() {
  return request({
    url: '/monitor/dashboard/data',
    method: 'get'
  });
}