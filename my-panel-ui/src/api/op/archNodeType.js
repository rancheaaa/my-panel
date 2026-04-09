import request from '@/utils/request';

// 查询节点类型列表
export function listNodeType(query) {
  return request({
    url: '/arch/nodeType/list',
    method: 'get',
    params: query
  });
}

// 查询所有节点类型
export function listAllNodeType() {
  return request({
    url: '/arch/nodeType/all',
    method: 'get'
  });
}

// 获取节点类型详细信息
export function getNodeType(id) {
  return request({
    url: '/arch/nodeType/' + id,
    method: 'get'
  });
}

// 新增节点类型
export function addNodeType(data) {
  return request({
    url: '/arch/nodeType',
    method: 'post',
    data: data
  });
}

// 修改节点类型
export function updateNodeType(data) {
  return request({
    url: '/arch/nodeType',
    method: 'put',
    data: data
  });
}

// 删除节点类型
export function delNodeType(ids) {
  return request({
    url: '/arch/nodeType/' + ids,
    method: 'delete'
  });
}
