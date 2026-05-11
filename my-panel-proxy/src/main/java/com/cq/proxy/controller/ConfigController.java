package com.cq.proxy.controller;

import com.cq.proxy.dto.ApiResponse;
import com.cq.proxy.dto.ConfigUpdateRequest;
import com.cq.proxy.dto.ConfigValueResponse;
import com.cq.proxy.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "配置管理", description = "配置管理相关接口")
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @Operation(summary = "获取配置", description = "根据配置键获取配置值")
    @GetMapping("/{configKey}")
    public ApiResponse<ConfigValueResponse> getConfig(
            @Parameter(description = "配置键", required = true) @PathVariable String configKey,
            @Parameter(description = "环境") @RequestParam(required = false) String environment,
            @Parameter(description = "服务名称") @RequestParam(required = false) String serviceName) {
        ConfigValueResponse response = configService.getConfig(configKey, environment, serviceName);
        return ApiResponse.ok(response);
    }

    @Operation(summary = "更新配置", description = "更新配置值")
    @PutMapping("/{configKey}")
    public ApiResponse<ConfigValueResponse> updateConfig(
            @Parameter(description = "配置键", required = true) @PathVariable String configKey,
            @RequestBody ConfigUpdateRequest request) {
        ConfigValueResponse response = configService.updateConfig(configKey, request);
        return ApiResponse.ok("UPDATED", response);
    }
}