import request from '../../utils/request';

// 查询应用列表
export function listProject(query) {
  return request({
    url: '/rc/project/list',
    method: 'get',
    params: query
  });
}

// 查询应用详细
export function getProject(id) {
  return request({
    url: '/rc/project/' + id,
    method: 'get'
  });
}

// 新增应用
export function addProject(data) {
  return request({
    url: '/rc/project',
    method: 'post',
    data: data
  });
}

// 修改应用
export function updateProject(data) {
  return request({
    url: '/rc/project',
    method: 'put',
    data: data
  });
}

// 删除应用
export function delProject(ids) {
  return request({
    url: '/rc/project/' + ids,
    method: 'delete'
  });
}

// 导出应用
export function exportProject(query) {
  return request({
    url: '/rc/project/export',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}
