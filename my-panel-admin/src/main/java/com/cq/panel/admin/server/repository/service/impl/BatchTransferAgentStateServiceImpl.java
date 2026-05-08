package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.BatchTransferAgentState;
import com.cq.panel.admin.server.repository.mapper.BatchTransferAgentStateMapper;
import com.cq.panel.admin.server.repository.service.IBatchTransferAgentStateService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BatchTransferAgentStateServiceImpl implements IBatchTransferAgentStateService
{
    private final BatchTransferAgentStateMapper mapper;

    public BatchTransferAgentStateServiceImpl(BatchTransferAgentStateMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public List<BatchTransferAgentState> selectList(BatchTransferAgentState query)
    {
        return mapper.selectList(query);
    }

    @Override
    public List<BatchTransferAgentState> selectByTaskId(Long taskId)
    {
        return mapper.selectByTaskId(taskId);
    }

    @Override
    public BatchTransferAgentState selectByTransferId(String transferId)
    {
        return mapper.selectByTransferId(transferId);
    }
}
