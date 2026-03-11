import request from '../../utils/request';

// 查询环境列表
export function listEnv(query) {
  return request({
    url: '/rc/env/list',
    method: 'get',
    params: query
  });
}

// 查询环境详细
export function getEnv(id) {
  return request({
    url: '/rc/env/' + id,
    method: 'get'
  });
}

// 新增环境
export function addEnv(data) {
  return request({
    url: '/rc/env',
    method: 'post',
    data: data
  });
}

// 修改环境
export function updateEnv(data) {
  return request({
    url: '/rc/env',
    method: 'put',
    data: data
  });
}

// 删除环境
export function delEnv(ids) {
  return request({
    url: '/rc/env/' + ids,
    method: 'delete'
  });
}

// 导出环境
export function exportEnv(query) {
  return request({
    url: '/rc/env/export',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}
