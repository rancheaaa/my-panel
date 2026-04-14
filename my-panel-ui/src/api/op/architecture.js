import request from '@/utils/request';

// 查询架构图列表
export function listDiagram(query) {
  return request({
    url: '/arch/diagram/list',
    method: 'get',
    params: query
  });
}

// 查询架构图详细
export function getDiagram(id) {
  return request({
    url: '/arch/diagram/' + id,
    method: 'get'
  });
}

// 新增架构图
export function addDiagram(data) {
  return request({
    url: '/arch/diagram',
    method: 'post',
    data: data
  });
}

// 修改架构图
export function updateDiagram(data) {
  return request({
    url: '/arch/diagram',
    method: 'put',
    data: data
  });
}

// 发布架构图
export function publishDiagram(id) {
  return request({
    url: '/arch/diagram/' + id + '/publish',
    method: 'post'
  });
}

// 删除架构图
export function delDiagram(ids) {
  return request({
    url: '/arch/diagram/' + ids,
    method: 'delete'
  });
}

// 加载架构图完整数据（包含所有节点和边缘）
export function loadDiagramData(id) {
  return request({
    url: '/arch/diagram/' + id + '/data',
    method: 'get'
  });
}

// ==================== 节点操作 ====================

// 新增节点
export function addNode(data) {
  return request({
    url: '/arch/node',
    method: 'post',
    data: data
  });
}

// 批量新增节点
export function batchAddNode(data) {
  return request({
    url: '/arch/node/batch',
    method: 'post',
    data: data
  });
}

// 修改节点
export function updateNode(data) {
  return request({
    url: '/arch/node',
    method: 'put',
    data: data
  });
}

// 拖拽节点位置
export function updateNodePosition(data) {
  return request({
    url: '/arch/node/position',
    method: 'put',
    data: data
  });
}

// 删除节点
export function delNode(ids) {
  return request({
    url: '/arch/node/' + ids,
    method: 'delete'
  });
}

// 批量删除节点
export function batchDelNode(data) {
  return request({
    url: '/arch/node/batch',
    method: 'delete',
    data: data
  });
}

// ==================== 边缘操作 ====================

// 新增边缘
export function addEdge(data) {
  return request({
    url: '/arch/edge',
    method: 'post',
    data: data
  });
}

// 批量新增边缘
export function batchAddEdge(data) {
  return request({
    url: '/arch/edge/batch',
    method: 'post',
    data: data
  });
}

// 修改边缘
export function updateEdge(data) {
  return request({
    url: '/arch/edge',
    method: 'put',
    data: data
  });
}

// 删除边缘
export function delEdge(ids) {
  return request({
    url: '/arch/edge/' + ids,
    method: 'delete'
  });
}

// 批量删除边缘
export function batchDelEdge(data) {
  return request({
    url: '/arch/edge/batch',
    method: 'delete',
    data: data
  });
}

// ==================== 批量操作 ====================

// 批量保存架构图数据
export function batchSaveDiagramData(id, data) {
  return request({
    url: '/arch/diagram/' + id + '/batch-save',
    method: 'post',
    data: data
  });
}

// 全量更新架构图
export function replaceDiagramData(id, data) {
  return request({
    url: '/arch/diagram/' + id + '/replace',
    method: 'put',
    data: data
  });
}