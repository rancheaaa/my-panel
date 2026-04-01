package com.cq.proxy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ServiceInstance", description = "服务实例")
public record ServiceInstance(
    @Schema(description = "服务名") String serviceName,
    @Schema(description = "环境") String environment,
    @Schema(description = "实例IP") String host,
    @Schema(description = "实例端口") int port,
    @Schema(description = "是否可用") boolean available,
    @Schema(description = "最后心跳时间") String lastHeartbeat
) {
}

