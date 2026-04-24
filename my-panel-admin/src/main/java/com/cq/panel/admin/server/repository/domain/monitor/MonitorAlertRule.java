package com.cq.panel.admin.server.repository.domain.monitor;

import lombok.Data;

import java.util.Date;

@Data
public class MonitorAlertRule {
    private Long id;
    private String ruleName;
    private String metricCategory;
    private String metricName;
    private String metricScope;
    private String operator;
    private Double thresholdValue;
    private Integer durationSeconds;
    private String severity;
    private String enabled;
    private String description;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
}
