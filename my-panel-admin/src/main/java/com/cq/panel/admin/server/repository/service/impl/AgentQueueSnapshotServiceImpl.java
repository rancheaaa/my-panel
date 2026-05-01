package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.AgentQueueSnapshot;
import com.cq.panel.admin.server.repository.mapper.AgentQueueSnapshotMapper;
import com.cq.panel.admin.server.repository.service.IAgentQueueSnapshotService;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;

@Service
public class AgentQueueSnapshotServiceImpl implements IAgentQueueSnapshotService
{
    private final AgentQueueSnapshotMapper agentQueueSnapshotMapper;

    public AgentQueueSnapshotServiceImpl(AgentQueueSnapshotMapper agentQueueSnapshotMapper)
    {
        this.agentQueueSnapshotMapper = agentQueueSnapshotMapper;
    }

    @Override
    public int insert(AgentQueueSnapshot entity)
    {
        entity.setCreateTime(new Date());
        return agentQueueSnapshotMapper.insertAgentQueueSnapshot(entity);
    }

    @Override
    public AgentQueueSnapshot selectLatestByAgentId(String agentId)
    {
        return agentQueueSnapshotMapper.selectLatestByAgentId(agentId);
    }

    @Override
    public List<AgentQueueSnapshot> selectByAgentIdAndTimeRange(String agentId, Date from, Date to)
    {
        return agentQueueSnapshotMapper.selectByAgentIdAndTimeRange(agentId, from, to);
    }

    @Override
    public int deleteBefore(Date time)
    {
        return agentQueueSnapshotMapper.deleteBefore(time);
    }
}
