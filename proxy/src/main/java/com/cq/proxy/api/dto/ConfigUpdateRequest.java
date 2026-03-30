package com.cq.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ConfigUpdateRequest", description = "配置更新请求")
public record ConfigUpdateRequest(
    @Schema(description = "环境名称", example = "dev") String environment,
    @Schema(description = "服务名(对应应用/项目名称)", example = "order-service") String serviceName,
    @Schema(description = "配置值") String configValue,
    @Schema(description = "配置描述") String configDesc
) {
}

