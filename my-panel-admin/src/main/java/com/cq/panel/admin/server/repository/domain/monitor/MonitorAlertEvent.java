package com.cq.panel.admin.server.repository.domain.monitor;

import lombok.Data;

import java.util.Date;

@Data
public class MonitorAlertEvent {
    private Long id;
    private Long ruleId;
    private String ruleName;
    private String metricCategory;
    private String metricName;
    private String metricScope;
    private String severity;
    private Double observedValue;
    private Double thresholdValue;
    private Date triggerTime;
    private String status;
    private String detail;
}
