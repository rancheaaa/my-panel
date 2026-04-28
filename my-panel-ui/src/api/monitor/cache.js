import request from '../../utils/request';

// 获取缓存监控信息
export function getCache() {
  return request({
    url: '/monitor/cache',
    method: 'get'
  });
}

// 查询缓存名称列表
export function listCacheName() {
  return request({
    url: '/monitor/cache/getNames',
    method: 'get'
  });
}

// 查询缓存键名列表
export function listCacheKey(cacheKey) {
  return request({
    url: '/monitor/cache/getKeys',
    method: 'get',
    params: { cacheKey }
  });
}

// 查询缓存内容
export function getCacheValue(cacheKey) {
  return request({
    url: '/monitor/cache/getValue',
    method: 'get',
    params: { cacheKey }
  });
}

// 清理指定名称缓存
export function clearCacheName(cacheName) {
  return request({
    url: '/monitor/cache/clearCacheName/' + encodeURIComponent(cacheName),
    method: 'delete'
  });
}

// 清理指定键名缓存
export function clearCacheKey(cacheKey) {
  return request({
    url: '/monitor/cache/clearCacheKey/' + encodeURIComponent(cacheKey),
    method: 'delete'
  });
}

// 清理全部缓存
export function clearCacheAll() {
  return request({
    url: '/monitor/cache/clearCacheAll',
    method: 'delete'
  });
}
