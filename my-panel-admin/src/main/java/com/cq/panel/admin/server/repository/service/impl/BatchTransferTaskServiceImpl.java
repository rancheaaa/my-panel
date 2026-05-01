package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper;
import com.cq.panel.admin.server.repository.service.IBatchTransferTaskService;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;

@Service
public class BatchTransferTaskServiceImpl implements IBatchTransferTaskService
{
    private final BatchTransferTaskMapper batchTransferTaskMapper;

    public BatchTransferTaskServiceImpl(BatchTransferTaskMapper batchTransferTaskMapper)
    {
        this.batchTransferTaskMapper = batchTransferTaskMapper;
    }

    @Override
    public BatchTransferTask selectById(Long id)
    {
        return batchTransferTaskMapper.selectBatchTransferTaskById(id);
    }

    @Override
    public List<BatchTransferTask> selectList(BatchTransferTask query)
    {
        return batchTransferTaskMapper.selectBatchTransferTaskList(query);
    }

    @Override
    public int insert(BatchTransferTask entity)
    {
        entity.setCreateTime(new Date());
        return batchTransferTaskMapper.insertBatchTransferTask(entity);
    }

    @Override
    public int update(BatchTransferTask entity)
    {
        entity.setUpdateTime(new Date());
        return batchTransferTaskMapper.updateBatchTransferTask(entity);
    }

    @Override
    public int deleteByIds(Long[] ids)
    {
        return batchTransferTaskMapper.deleteBatchTransferTaskByIds(ids);
    }

    @Override
    public int updateStatus(Long id, String status)
    {
        return batchTransferTaskMapper.updateTaskStatus(id, status);
    }
}
