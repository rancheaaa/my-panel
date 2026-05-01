package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.AgentQueueSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;

@Mapper
public interface AgentQueueSnapshotMapper
{
    int insertAgentQueueSnapshot(AgentQueueSnapshot entity);

    AgentQueueSnapshot selectLatestByAgentId(String agentId);

    List<AgentQueueSnapshot> selectByAgentIdAndTimeRange(@Param("agentId") String agentId, @Param("from") Date from, @Param("to") Date to);

    int deleteBefore(Date time);
}
