import request from '@/utils/request';

// 查询模板列表
export function listTemplate(query) {
  return request({
    url: '/arch/template/list',
    method: 'get',
    params: query
  });
}

// 查询所有模板
export function listAllTemplate() {
  return request({
    url: '/arch/template/all',
    method: 'get'
  });
}

// 获取模板详细信息
export function getTemplate(id) {
  return request({
    url: '/arch/template/' + id,
    method: 'get'
  });
}

// 新增模板
export function addTemplate(data) {
  return request({
    url: '/arch/template',
    method: 'post',
    data: data
  });
}

// 修改模板
export function updateTemplate(data) {
  return request({
    url: '/arch/template',
    method: 'put',
    data: data
  });
}

// 删除模板
export function delTemplate(ids) {
  return request({
    url: '/arch/template/' + ids,
    method: 'delete'
  });
}

// 使用模板
export function useTemplate(templateId) {
  return request({
    url: '/arch/template/use/' + templateId,
    method: 'post'
  });
}

// 评分模板
export function rateTemplate(templateId, rating) {
  return request({
    url: '/arch/template/rate/' + templateId,
    method: 'post',
    params: { rating }
  });
}
