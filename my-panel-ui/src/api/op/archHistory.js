import request from '@/utils/request';

// 查询架构图版本历史
export function listHistoryByDiagramId(diagramId) {
  return request({
    url: '/api/arch/version/list/' + diagramId,
    method: 'get'
  });
}

// 获取版本详细信息
export function getVersionDetail(versionId) {
  return request({
    url: '/api/arch/version/detail/' + versionId,
    method: 'get'
  });
}

// 创建版本快照
export function createSnapshot(data) {
  return request({
    url: '/api/arch/version/create',
    method: 'post',
    params: data
  });
}

// 恢复到指定版本
export function restoreVersion(versionId) {
  return request({
    url: '/api/arch/version/restore/' + versionId,
    method: 'post'
  });
}

// 删除版本
export function deleteVersion(versionId) {
  return request({
    url: '/api/arch/version/delete/' + versionId,
    method: 'delete'
  });
}
