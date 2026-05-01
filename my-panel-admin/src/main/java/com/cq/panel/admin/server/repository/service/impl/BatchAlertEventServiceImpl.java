package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.BatchAlertEvent;
import com.cq.panel.admin.server.repository.mapper.BatchAlertEventMapper;
import com.cq.panel.admin.server.repository.service.IBatchAlertEventService;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;

@Service
public class BatchAlertEventServiceImpl implements IBatchAlertEventService
{
    private final BatchAlertEventMapper batchAlertEventMapper;

    public BatchAlertEventServiceImpl(BatchAlertEventMapper batchAlertEventMapper)
    {
        this.batchAlertEventMapper = batchAlertEventMapper;
    }

    @Override
    public int insert(BatchAlertEvent entity)
    {
        entity.setCreateTime(new Date());
        return batchAlertEventMapper.insertBatchAlertEvent(entity);
    }

    @Override
    public List<BatchAlertEvent> selectUnresolved()
    {
        return batchAlertEventMapper.selectUnresolved();
    }

    @Override
    public List<BatchAlertEvent> selectRecent(int limit)
    {
        return batchAlertEventMapper.selectRecent(limit);
    }

    @Override
    public int resolve(Long id, String resolvedBy, String resolutionNote)
    {
        return batchAlertEventMapper.resolve(id, resolvedBy, resolutionNote);
    }
}
