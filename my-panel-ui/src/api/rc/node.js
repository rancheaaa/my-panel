import request from '../../utils/request';

// 查询注册中心节点列表
export function listNode(query) {
  return request({
    url: '/rc/node/list',
    method: 'get',
    params: query
  });
}

// 查询注册中心节点详细
export function getNode(id) {
  return request({
    url: '/rc/node/' + id,
    method: 'get'
  });
}

// 新增注册中心节点
export function addNode(data) {
  return request({
    url: '/rc/node',
    method: 'post',
    data: data
  });
}

// 修改注册中心节点
export function updateNode(data) {
  return request({
    url: '/rc/node',
    method: 'put',
    data: data
  });
}

// 删除注册中心节点
export function delNode(ids) {
  return request({
    url: '/rc/node/' + ids,
    method: 'delete'
  });
}

// 导出注册中心节点
export function exportNode(query) {
  return request({
    url: '/rc/node/export',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}
