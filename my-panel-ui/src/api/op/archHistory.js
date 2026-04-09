import request from '@/utils/request';

// 查询版本历史列表
export function listHistory(query) {
  return request({
    url: '/arch/history/list',
    method: 'get',
    params: query
  });
}

// 查询架构图版本历史
export function listHistoryByDiagramId(diagramId) {
  return request({
    url: '/arch/history/diagram/' + diagramId,
    method: 'get'
  });
}

// 获取当前版本
export function getCurrentVersion(diagramId) {
  return request({
    url: '/arch/history/current/' + diagramId,
    method: 'get'
  });
}

// 获取版本详细信息
export function getHistory(id) {
  return request({
    url: '/arch/history/' + id,
    method: 'get'
  });
}

// 创建版本快照
export function createSnapshot(data) {
  return request({
    url: '/arch/history/snapshot',
    method: 'post',
    params: data
  });
}

// 恢复到指定版本
export function restoreVersion(historyId) {
  return request({
    url: '/arch/history/restore/' + historyId,
    method: 'post'
  });
}

// 删除版本历史
export function delHistory(ids) {
  return request({
    url: '/arch/history/' + ids,
    method: 'delete'
  });
}
