package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.monitor.MonitorMetricSample;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface MonitorMetricMapper {
    int batchInsert(@Param("samples") List<MonitorMetricSample> samples);

    List<MonitorMetricSample> selectByTimeRange(@Param("category") String category,
            @Param("metricNames") List<String> metricNames,
            @Param("beginTime") Date beginTime,
            @Param("endTime") Date endTime,
            @Param("serviceId") String serviceId,
            @Param("serviceIpPort") String serviceIpPort,
            @Param("limit") Integer limit);

    List<MonitorMetricSample> selectLatestByCategory(@Param("category") String category,
            @Param("serviceId") String serviceId,
            @Param("serviceIpPort") String serviceIpPort);

    List<Map<String, String>> selectDistinctServiceInstances();

    int deleteBefore(@Param("cutoffTime") Date cutoffTime);

    List<MonitorMetricSample> selectLatestAll(@Param("serviceId") String serviceId,
            @Param("serviceIpPort") String serviceIpPort);
}
