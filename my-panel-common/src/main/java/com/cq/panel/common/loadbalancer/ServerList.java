package com.cq.panel.common.loadbalancer;

import java.util.List;

/**
 * 服务器列表接口
 * 负责获取指定服务的服务器列表
 */
public interface ServerList {

    /**
     * 获取指定服务的服务器列表
     * 
     * @param serviceName 服务名
     * @return 服务器列表
     */
    List<Server> getServers(String serviceName);

    /**
     * 获取所有服务的服务器列表
     * 
     * @return 所有服务器列表
     */
    List<Server> getAllServers();


    /**
     * 获取指定服务的在线服务器列表
     *
     * @param serviceName 服务名
     * @return 在线服务器列表
     */
    List<Server> getUpServers(String serviceName);


    /**
     * 获取指定服务的下线服务器列表
     *
     * @param serviceName 服务名
     * @return 下线服务器列表
     */
    List<Server> getDownServers(String serviceName);

    /**
     * 刷新服务器列表
     */
    void refresh();

    /**
     * 获取所有服务名称
     * 
     * @return 服务名称列表
     */
    List<String> getAllServiceNames();
}