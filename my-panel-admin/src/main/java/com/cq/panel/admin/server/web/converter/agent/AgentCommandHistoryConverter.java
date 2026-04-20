package com.cq.panel.admin.server.web.converter.agent;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.cq.panel.admin.server.repository.domain.AgentCommandHistory;
import com.cq.panel.admin.server.web.domain.vo.agent.AgentCommandHistoryVO;

@Component
public class AgentCommandHistoryConverter
{
    public AgentCommandHistoryVO toVO(AgentCommandHistory entity)
    {
        if (entity == null) return null;
        AgentCommandHistoryVO vo = new AgentCommandHistoryVO();
        vo.setId(entity.getId());
        vo.setAgentId(entity.getAgentId());
        vo.setAgentName(entity.getAgentName());
        vo.setAgentIp(entity.getAgentIp());
        vo.setAgentPort(entity.getAgentPort());
        vo.setCommand(entity.getCommand());
        vo.setCommandTimeout(entity.getCommandTimeout());
        vo.setCommandStatus(entity.getCommandStatus());
        vo.setExitCode(entity.getExitCode());
        vo.setOutput(entity.getOutput());
        vo.setError(entity.getError());
        vo.setExecuteTime(entity.getExecuteTime());
        vo.setSubmitTime(entity.getSubmitTime());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setUserName(entity.getUserName());
        return vo;
    }

    public List<AgentCommandHistoryVO> toVOList(List<AgentCommandHistory> list)
    {
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }
}
