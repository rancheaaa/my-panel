import request from '@/utils/request';

// 查询标签列表
export function listTag(query) {
  return request({
    url: '/arch/tag/list',
    method: 'get',
    params: query
  });
}

// 查询所有标签
export function getAllTags() {
  return request({
    url: '/arch/tag/all',
    method: 'get'
  });
}

// 查询标签详细
export function getTag(id) {
  return request({
    url: '/arch/tag/' + id,
    method: 'get'
  });
}

// 新增标签
export function addTag(data) {
  return request({
    url: '/arch/tag',
    method: 'post',
    data: data
  });
}

// 修改标签
export function updateTag(data) {
  return request({
    url: '/arch/tag',
    method: 'put',
    data: data
  });
}

// 删除标签
export function delTag(ids) {
  return request({
    url: '/arch/tag/' + ids,
    method: 'delete'
  });
}

