package com.cq.panel.admin.server.service.proxy;

import com.cq.panel.common.loadbalancer.Server;

/**
 * 带故障转移的负载均衡请求封装
 *
 * @param <T> 响应类型
 */
@FunctionalInterface
public interface LoadBalancedRequest<T> {

    /**
     * 使用选中的Server执行请求
     *
     * @param server 选中的Proxy服务器
     * @return 请求结果
     */
    T execute(Server server);
}
