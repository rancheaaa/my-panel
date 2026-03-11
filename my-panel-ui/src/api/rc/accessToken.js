import request from '../../utils/request';

// 查询AccessToken列表
export function listAccessToken(query) {
  return request({
    url: '/rc/accessToken/list',
    method: 'get',
    params: query
  });
}

// 查询AccessToken详细
export function getAccessToken(id) {
  return request({
    url: '/rc/accessToken/' + id,
    method: 'get'
  });
}

// 新增AccessToken
export function addAccessToken(data) {
  return request({
    url: '/rc/accessToken',
    method: 'post',
    data: data
  });
}

// 修改AccessToken
export function updateAccessToken(data) {
  return request({
    url: '/rc/accessToken',
    method: 'put',
    data: data
  });
}

// 删除AccessToken
export function delAccessToken(ids) {
  return request({
    url: '/rc/accessToken/' + ids,
    method: 'delete'
  });
}

// 导出AccessToken
export function exportAccessToken(query) {
  return request({
    url: '/rc/accessToken/export',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}

// 状态修改
export function changeAccessTokenStatus(id, status) {
  const data = {
    id,
    status
  };
  return request({
    url: '/rc/accessToken/changeStatus',
    method: 'put',
    data: data
  });
}
