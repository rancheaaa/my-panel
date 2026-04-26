package com.cq.panel.admin.server.repository.domain.monitor;

import lombok.Data;

import java.util.Date;

@Data
public class MonitorMetricSample {
    private Long id;
    private String metricCategory;
    private String metricName;
    private String metricScope;
    private Double metricValue;
    private String metricUnit;
    private String tagJson;
    private String serviceId;
    private String serviceIpPort;
    private Date sampleTime;
    private Date createTime;
}
