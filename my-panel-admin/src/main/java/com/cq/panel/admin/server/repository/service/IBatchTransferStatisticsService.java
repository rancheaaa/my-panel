package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.BatchTransferStatistics;
import java.util.List;

public interface IBatchTransferStatisticsService
{
    BatchTransferStatistics selectByTaskId(Long taskId);

    List<BatchTransferStatistics> selectList(BatchTransferStatistics query);

    int insert(BatchTransferStatistics entity);

    int update(BatchTransferStatistics entity);

    int updateByTaskId(BatchTransferStatistics entity);

    int deleteByTaskId(Long taskId);

    int deleteByTaskIds(Long[] taskIds);
}
