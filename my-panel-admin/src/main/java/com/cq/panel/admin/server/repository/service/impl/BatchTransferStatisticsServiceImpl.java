package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.BatchTransferStatistics;
import com.cq.panel.admin.server.repository.mapper.BatchTransferStatisticsMapper;
import com.cq.panel.admin.server.repository.service.IBatchTransferStatisticsService;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;

@Service
public class BatchTransferStatisticsServiceImpl implements IBatchTransferStatisticsService
{
    private final BatchTransferStatisticsMapper batchTransferStatisticsMapper;

    public BatchTransferStatisticsServiceImpl(BatchTransferStatisticsMapper batchTransferStatisticsMapper)
    {
        this.batchTransferStatisticsMapper = batchTransferStatisticsMapper;
    }

    @Override
    public BatchTransferStatistics selectByTaskId(Long taskId)
    {
        return batchTransferStatisticsMapper.selectByTaskId(taskId);
    }

    @Override
    public List<BatchTransferStatistics> selectList(BatchTransferStatistics query)
    {
        return batchTransferStatisticsMapper.selectList(query);
    }

    @Override
    public int insert(BatchTransferStatistics entity)
    {
        entity.setCreateTime(new Date());
        entity.setDataVersion(1);
        return batchTransferStatisticsMapper.insert(entity);
    }

    @Override
    public int update(BatchTransferStatistics entity)
    {
        entity.setUpdateTime(new Date());
        return batchTransferStatisticsMapper.update(entity);
    }

    @Override
    public int updateByTaskId(BatchTransferStatistics entity)
    {
        entity.setUpdateTime(new Date());
        return batchTransferStatisticsMapper.updateByTaskId(entity);
    }

    @Override
    public int deleteByTaskId(Long taskId)
    {
        return batchTransferStatisticsMapper.deleteByTaskId(taskId);
    }

    @Override
    public int deleteByTaskIds(Long[] taskIds)
    {
        return batchTransferStatisticsMapper.deleteByTaskIds(taskIds);
    }
}
