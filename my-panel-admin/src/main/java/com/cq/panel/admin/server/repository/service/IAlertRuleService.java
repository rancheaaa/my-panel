package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertEvent;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertRule;
import com.cq.panel.admin.server.web.domain.dto.monitor.MonitorAlertRuleSaveDTO;

import java.util.List;
import java.util.Map;

public interface IAlertRuleService {
    List<MonitorAlertRule> listAlertRules();

    void saveAlertRule(MonitorAlertRuleSaveDTO dto, String operator);

    void deleteAlertRule(Long id);

    List<MonitorAlertEvent> listAlertEvents(String range, Integer limit);

    void updateAlertEventStatus(Long id, String status);

    void evaluateAlertRules();

    Map<String, Object> buildAlertSummary();
}
