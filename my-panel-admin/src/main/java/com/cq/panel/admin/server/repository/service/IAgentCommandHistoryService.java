package com.cq.panel.admin.server.repository.service;

import java.util.List;
import com.cq.panel.admin.server.repository.domain.AgentCommandHistory;

public interface IAgentCommandHistoryService
{
    AgentCommandHistory selectById(Long id);

    List<AgentCommandHistory> selectList(AgentCommandHistory query);

    int insert(AgentCommandHistory record);

    int deleteByIds(Long[] ids);

    int deleteByAgentId(String agentId);
}
