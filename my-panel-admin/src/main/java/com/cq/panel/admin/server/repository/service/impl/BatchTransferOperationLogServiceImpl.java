package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.BatchTransferOperationLog;
import com.cq.panel.admin.server.repository.mapper.BatchTransferOperationLogMapper;
import com.cq.panel.admin.server.repository.service.IBatchTransferOperationLogService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BatchTransferOperationLogServiceImpl implements IBatchTransferOperationLogService
{
    private final BatchTransferOperationLogMapper batchTransferOperationLogMapper;

    public BatchTransferOperationLogServiceImpl(BatchTransferOperationLogMapper batchTransferOperationLogMapper)
    {
        this.batchTransferOperationLogMapper = batchTransferOperationLogMapper;
    }

    @Override
    public int insert(BatchTransferOperationLog entity)
    {
        entity.setCreateTime(new java.util.Date());
        return batchTransferOperationLogMapper.insertBatchTransferOperationLog(entity);
    }

    @Override
    public List<BatchTransferOperationLog> selectByTaskId(Long taskId)
    {
        return batchTransferOperationLogMapper.selectByTaskId(taskId);
    }

    @Override
    public List<BatchTransferOperationLog> selectList(BatchTransferOperationLog query)
    {
        return batchTransferOperationLogMapper.selectList(query);
    }
}
