package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertEvent;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface MonitorAlertEventMapper {
    int insert(MonitorAlertEvent event);

    List<MonitorAlertEvent> selectByTimeRange(@Param("beginTime") Date beginTime,
                                              @Param("endTime") Date endTime,
                                              @Param("limit") Integer limit);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
