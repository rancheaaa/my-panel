package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.monitor.MonitorMetricSample;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface MonitorMetricMapper {
    int batchInsert(@Param("samples") List<MonitorMetricSample> samples);

    List<MonitorMetricSample> selectByTimeRange(@Param("category") String category,
                                                @Param("metricNames") List<String> metricNames,
                                                @Param("beginTime") Date beginTime,
                                                @Param("endTime") Date endTime,
                                                @Param("limit") Integer limit);

    List<MonitorMetricSample> selectLatestByCategory(@Param("category") String category,
                                                     @Param("sampleTime") Date sampleTime);

    int deleteBefore(@Param("cutoffTime") Date cutoffTime);
}
