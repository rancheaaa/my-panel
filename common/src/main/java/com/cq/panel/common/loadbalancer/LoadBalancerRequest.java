package com.cq.panel.common.loadbalancer;

/**
 * 负载均衡请求接口
 * 模仿Ribbon的LoadBalancerRequest接口
 */
@FunctionalInterface
public interface LoadBalancerRequest<T> {

    /**
     * 在指定服务器上执行请求
     * 
     * @param server 服务器
     * @return 响应实体
     * @throws Exception 执行异常
     */
    HttpResponse<T> apply(Server server) throws Exception;
}