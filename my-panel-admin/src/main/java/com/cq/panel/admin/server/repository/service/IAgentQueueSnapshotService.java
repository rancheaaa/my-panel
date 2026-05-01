package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.AgentQueueSnapshot;
import java.util.Date;
import java.util.List;

public interface IAgentQueueSnapshotService
{
    int insert(AgentQueueSnapshot entity);

    AgentQueueSnapshot selectLatestByAgentId(String agentId);

    List<AgentQueueSnapshot> selectByAgentIdAndTimeRange(String agentId, Date from, Date to);

    int deleteBefore(Date time);
}
