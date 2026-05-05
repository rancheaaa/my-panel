package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BatchTransferStatisticsMapper
{
    BatchTransferStatistics selectByTaskId(Long taskId);

    List<BatchTransferStatistics> selectList(BatchTransferStatistics query);

    int insert(BatchTransferStatistics entity);

    int update(BatchTransferStatistics entity);

    int updateByTaskId(BatchTransferStatistics entity);

    int deleteByTaskId(Long taskId);

    int deleteByTaskIds(Long[] taskIds);
}
