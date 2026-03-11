package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "参数配置查询对象")
public record SysConfigQueryDTO(
    @Schema(description = "参数名称", example = "主题")
    String configName,

    @Schema(description = "系统内置（Y是 N否）", example = "Y")
    String configType,

    @Schema(description = "参数键名", example = "sys.index.sideTheme")
    String configKey,

    @Schema(description = "开始时间", example = "2024-01-01")
    String beginTime,

    @Schema(description = "结束时间", example = "2024-12-31")
    String endTime
) {}
