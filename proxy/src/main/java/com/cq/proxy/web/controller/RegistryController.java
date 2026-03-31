package com.cq.proxy.web.controller;

import com.cq.proxy.api.ApiResponse;
import com.cq.proxy.api.dto.ServiceInstance;
import com.cq.proxy.service.RegistryService;
import com.cq.proxy.api.dto.ServiceRegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "服务注册", description = "服务注册相关接口")
@RestController
@RequestMapping("/api/v1/registry")
public class RegistryController {

    private final RegistryService registryService;

    public RegistryController(RegistryService registryService) {
        this.registryService = registryService;
    }

    @Operation(summary = "服务注册", description = "注册服务实例")
    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody ServiceRegisterRequest request) {
        registryService.register(request);
        return ApiResponse.ok("REGISTERED", null);
    }

    @Operation(summary = "服务发现", description = "获取服务实例列表")
    @GetMapping("/discover")
    public ApiResponse<List<ServiceInstance>> discover(
            @Parameter(description = "服务名称", required = true) @RequestParam String serviceName,
            @Parameter(description = "环境", required = true) @RequestParam String environment) {
        return ApiResponse.ok(registryService.discover(serviceName, environment));
    }
}