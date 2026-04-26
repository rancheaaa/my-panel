package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Schema(description = "监控趋势查询参数")
public class MetricTrendQueryDTO {
    @Schema(description = "指标分类", example = "cpu")
    private String category;

    @Schema(description = "指标列表", example = "[\"cpu_usage_pct\",\"cpu_system_pct\"]")
    private List<String> metricNames;

    @Schema(description = "时间粒度，支持1m/5m/15m/1h/nh/1d/nd/7d", example = "1m")
    private String granularity = "1m";

    @Schema(description = "起始时间")
    private Instant beginTime;

    @Schema(description = "结束时间")
    private Instant endTime;

    @Schema(description = "服务实例ID")
    private String serviceId;

    @Schema(description = "限制返回点数", example = "2000")
    private Integer limit;
}
