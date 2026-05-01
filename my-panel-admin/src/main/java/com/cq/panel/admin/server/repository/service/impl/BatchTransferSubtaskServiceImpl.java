package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.repository.service.IBatchTransferSubtaskService;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class BatchTransferSubtaskServiceImpl implements IBatchTransferSubtaskService
{
    private final BatchTransferSubtaskMapper batchTransferSubtaskMapper;

    public BatchTransferSubtaskServiceImpl(BatchTransferSubtaskMapper batchTransferSubtaskMapper)
    {
        this.batchTransferSubtaskMapper = batchTransferSubtaskMapper;
    }

    @Override
    public BatchTransferSubtask selectById(Long id)
    {
        return batchTransferSubtaskMapper.selectBatchTransferSubtaskById(id);
    }

    @Override
    public List<BatchTransferSubtask> selectByTaskId(Long taskId)
    {
        return batchTransferSubtaskMapper.selectSubtasksByTaskId(taskId);
    }

    @Override
    public List<BatchTransferSubtask> selectByTaskIdAndStatus(Long taskId, String status)
    {
        return batchTransferSubtaskMapper.selectSubtasksByTaskIdAndStatus(taskId, status);
    }

    @Override
    public List<BatchTransferSubtask> selectList(BatchTransferSubtask query)
    {
        return batchTransferSubtaskMapper.selectBatchTransferSubtaskList(query);
    }

    @Override
    public int insert(BatchTransferSubtask entity)
    {
        entity.setCreateTime(new Date());
        return batchTransferSubtaskMapper.insertBatchTransferSubtask(entity);
    }

    @Override
    public int update(BatchTransferSubtask entity)
    {
        entity.setUpdateTime(new Date());
        return batchTransferSubtaskMapper.updateBatchTransferSubtask(entity);
    }

    @Override
    public int updateStatus(Long id, String status)
    {
        return batchTransferSubtaskMapper.updateSubtaskStatus(id, status);
    }

    @Override
    public int batchInsert(List<BatchTransferSubtask> list)
    {
        if (list == null || list.isEmpty())
        {
            return 0;
        }
        Date now = new Date();
        for (BatchTransferSubtask item : list)
        {
            item.setCreateTime(now);
        }
        return batchTransferSubtaskMapper.batchInsertSubtasks(list);
    }

    @Override
    public List<BatchTransferSubtask> selectFailedForRetry(Date beforeTime)
    {
        return batchTransferSubtaskMapper.selectFailedSubtasksForRetry(beforeTime);
    }

    @Override
    public Map<String, Object> selectSubtaskSummaryByTaskId(Long taskId)
    {
        return batchTransferSubtaskMapper.selectSubtaskSummaryByTaskId(taskId);
    }
}
