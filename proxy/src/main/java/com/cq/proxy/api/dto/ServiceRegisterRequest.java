package com.cq.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ServiceRegisterRequest", description = "服务注册请求")
public record ServiceRegisterRequest(
    @Schema(description = "服务名(对应应用/项目名称)", example = "order-service") String serviceName,
    @Schema(description = "环境名称", example = "dev") String environment,
    @Schema(description = "实例IP", example = "127.0.0.1") String host,
    @Schema(description = "实例端口", example = "8080") int port
) {
}

