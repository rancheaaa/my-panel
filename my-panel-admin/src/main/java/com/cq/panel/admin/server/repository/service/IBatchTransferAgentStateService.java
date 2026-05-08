package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.BatchTransferAgentState;
import java.util.List;

public interface IBatchTransferAgentStateService
{
    List<BatchTransferAgentState> selectList(BatchTransferAgentState query);

    List<BatchTransferAgentState> selectByTaskId(Long taskId);

    BatchTransferAgentState selectByTransferId(String transferId);
}
