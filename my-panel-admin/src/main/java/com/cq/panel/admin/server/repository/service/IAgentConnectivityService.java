package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO;

public interface IAgentConnectivityService {

    AgentConnectivityVO checkConnectivity(String sourceNodeName, String targetNodeName);
}
