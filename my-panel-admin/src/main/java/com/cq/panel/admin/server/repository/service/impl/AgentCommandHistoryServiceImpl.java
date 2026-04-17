package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.common.utils.DateUtils;
import com.cq.panel.admin.server.repository.mapper.AgentCommandHistoryMapper;
import com.cq.panel.admin.server.repository.domain.AgentCommandHistory;
import com.cq.panel.admin.server.repository.service.IAgentCommandHistoryService;

@Service
public class AgentCommandHistoryServiceImpl implements IAgentCommandHistoryService
{
    private final AgentCommandHistoryMapper agentCommandHistoryMapper;

    public AgentCommandHistoryServiceImpl(AgentCommandHistoryMapper agentCommandHistoryMapper) {
        this.agentCommandHistoryMapper = agentCommandHistoryMapper;
    }

    @Override
    public AgentCommandHistory selectById(Long id) {
        return agentCommandHistoryMapper.selectById(id);
    }

    @Override
    public List<AgentCommandHistory> selectList(AgentCommandHistory query) {
        return agentCommandHistoryMapper.selectList(query);
    }

    @Override
    public int insert(AgentCommandHistory record) {
        if (record.getCreateTime() == null) {
            record.setCreateTime(DateUtils.getNowDate());
        }
        return agentCommandHistoryMapper.insert(record);
    }

    @Override
    public int deleteByIds(Long[] ids) {
        return agentCommandHistoryMapper.deleteByIds(ids);
    }

    @Override
    public int deleteByAgentId(String agentId) {
        return agentCommandHistoryMapper.deleteByAgentId(agentId);
    }
}
