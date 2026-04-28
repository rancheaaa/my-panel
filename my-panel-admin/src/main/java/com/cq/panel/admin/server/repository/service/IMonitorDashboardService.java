package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.web.domain.dto.monitor.MetricTrendQueryDTO;
import java.util.List;
import java.util.Map;

public interface IMonitorDashboardService {
    Map<String, Object> collectSnapshotAndPersist();

    Map<String, Object> getDashboardOverview(String serviceId, String serviceIpPort);

    Map<String, Object> getTrend(MetricTrendQueryDTO queryDTO);

    List<Map<String, String>> listServiceInstances();

    int cleanupHistory();
}
