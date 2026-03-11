package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.web.domain.vo.monitor.ServerVO;

/**
 * 服务器监控服务接口
 * 
 * @author cq
 */
public interface IServerService {
    /**
     * 获取服务器信息
     * 
     * @return 服务器信息
     */
    ServerVO getServerInfo() throws Exception;
}

