import request from '@/utils/request';

// 查询标签关联列表
export function listTagRel(query) {
  return request({
    url: '/arch/tag/rel/list',
    method: 'get',
    params: query
  });
}

// 根据节点ID查询标签ID列表
export function getTagIdsByNodeId(nodeId) {
  return request({
    url: '/arch/tag/rel/node/' + nodeId,
    method: 'get'
  });
}

// 新增标签关联
export function addTagRel(data) {
  return request({
    url: '/arch/tag/rel',
    method: 'post',
    data: data
  });
}

// 批量新增标签关联
export function batchAddTagRel(data) {
  return request({
    url: '/arch/tag/rel/batch',
    method: 'post',
    data: data
  });
}

// 删除标签关联
export function delTagRel(ids) {
  return request({
    url: '/arch/tag/rel/' + ids,
    method: 'delete'
  });
}

// 根据节点ID删除标签关联
export function delTagRelByNodeId(nodeId) {
  return request({
    url: '/arch/tag/rel/node/' + nodeId,
    method: 'delete'
  });
}
