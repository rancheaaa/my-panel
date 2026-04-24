package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "告警规则保存参数")
public class MonitorAlertRuleSaveDTO {
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
}
