package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.BatchTransferOperationLog;
import java.util.List;

public interface IBatchTransferOperationLogService
{
    int insert(BatchTransferOperationLog entity);

    List<BatchTransferOperationLog> selectByTaskId(Long taskId);
}
