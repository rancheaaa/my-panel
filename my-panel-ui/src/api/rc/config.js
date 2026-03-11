import request from '../../utils/request';

// 查询配置列表
export function listConfig(query) {
  return request({
    url: '/rc/config/list',
    method: 'get',
    params: query
  });
}

// 查询配置详细
export function getConfig(id) {
  return request({
    url: '/rc/config/' + id,
    method: 'get'
  });
}

// 新增配置
export function addConfig(data) {
  return request({
    url: '/rc/config',
    method: 'post',
    data: data
  });
}

// 修改配置
export function updateConfig(data) {
  return request({
    url: '/rc/config',
    method: 'put',
    data: data
  });
}

// 删除配置
export function delConfig(ids) {
  return request({
    url: '/rc/config/' + ids,
    method: 'delete'
  });
}

// 批量导入配置
export function importConfig(data) {
  return request({
    url: '/rc/config/import',
    method: 'post',
    data: data
  });
}

// 预览配置内容
export function previewConfig(query) {
  return request({
    url: '/rc/config/preview',
    method: 'get',
    params: query
  });
}

// 导出配置
export function exportConfig(query) {
  return request({
    url: '/rc/config/export',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}
