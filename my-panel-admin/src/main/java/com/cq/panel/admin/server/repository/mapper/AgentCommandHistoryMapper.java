package com.cq.panel.admin.server.repository.mapper;

import java.util.List;
import java.util.Date;
import com.cq.panel.admin.server.repository.domain.AgentCommandHistory;
import org.apache.ibatis.annotations.Param;

public interface AgentCommandHistoryMapper
{
    AgentCommandHistory selectById(Long id);

    List<AgentCommandHistory> selectList(AgentCommandHistory query);

    int insert(AgentCommandHistory record);

    int deleteByIds(Long[] ids);

    int deleteByAgentId(String agentId);
}
