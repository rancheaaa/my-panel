package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchAlertEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BatchAlertEventMapper
{
    int insertBatchAlertEvent(BatchAlertEvent entity);

    List<BatchAlertEvent> selectUnresolved();

    List<BatchAlertEvent> selectRecent(int limit);

    int resolve(@Param("id") Long id, @Param("resolvedBy") String resolvedBy, @Param("resolutionNote") String resolutionNote);
}
