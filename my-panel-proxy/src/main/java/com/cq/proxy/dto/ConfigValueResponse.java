package com.cq.proxy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ConfigValueResponse", description = "配置获取响应")
public record ConfigValueResponse(
    @Schema(description = "配置键") String configKey,
    @Schema(description = "配置值") String configValue,
    @Schema(description = "配置描述") String configDesc
) {
}

