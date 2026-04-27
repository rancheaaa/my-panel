import request from '../../utils/request';

// 获取服务信息
export function getServer() {
  return request({
    url: '/monitor/server',
    method: 'get'
  });
}

// 获取进程内存分布
export function getProcessMemoryDistribution() {
  return request({
    url: '/monitor/server/memory-distribution',
    method: 'get'
  });
}
