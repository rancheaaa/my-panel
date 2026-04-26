package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertEvent;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertRule;
import com.cq.panel.admin.server.web.domain.dto.monitor.MetricTrendQueryDTO;
import com.cq.panel.admin.server.web.domain.dto.monitor.MonitorAlertRuleSaveDTO;

import java.util.List;
import java.util.Map;

public interface IMonitorDashboardService {
    Map<String, Object> collectSnapshotAndPersist();

    Map<String, Object> getDashboardOverview();

    Map<String, Object> getTrend(MetricTrendQueryDTO queryDTO);

    List<Map<String, String>> listServiceInstances();

    List<MonitorAlertRule> listAlertRules();

    void saveAlertRule(MonitorAlertRuleSaveDTO dto, String operator);

    void deleteAlertRule(Long id);

    List<MonitorAlertEvent> listAlertEvents(String range, Integer limit);

    void updateAlertEventStatus(Long id, String status);

    int cleanupHistory();
}
