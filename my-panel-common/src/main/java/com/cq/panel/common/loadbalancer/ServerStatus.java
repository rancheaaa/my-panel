package com.cq.panel.common.loadbalancer;

/**
 * 服务器状态枚举
 */
public enum ServerStatus {
    /**
     * 服务器上线
     */
    UP,
    
    /**
     * 服务器下线
     */
    DOWN,
    
    /**
     * 服务器开始服务
     */
    STARTING,
    
    /**
     * 服务器停止服务
     */
    STOPPING,
    
    /**
     * 服务器未知状态
     */
    UNKNOWN
}