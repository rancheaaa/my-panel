package com.cq.proxy.web.controller;

import com.cq.proxy.api.ApiResponse;
import com.cq.proxy.api.dto.AgentRegisterRequest;
import com.cq.proxy.api.dto.AgentRegisterResponse;
import com.cq.proxy.service.RegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Agent注册", description = "Agent注册相关接口")
@RestController
@RequestMapping("/agent/registry")
public class AgentRegistryController {

    private final RegistryService registryService;

    public AgentRegistryController(RegistryService registryService) {
        this.registryService = registryService;
    }

    @Operation(summary = "Agent注册", description = "Agent客户端注册接口")
    @PostMapping("/register")
    public ApiResponse<AgentRegisterResponse> register(@RequestBody AgentRegisterRequest request) {
        AgentRegisterResponse response = registryService.registerAgent(request);
        return ApiResponse.ok(response);
    }

    @Operation(summary = "Agent心跳", description = "Agent客户端心跳接口")
    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat(
            @RequestParam String agentIp,
            @RequestParam Integer agentPort) {
        boolean success = registryService.heartbeatAgent(agentIp, agentPort);
        if (success) {
            return ApiResponse.ok("HEARTBEAT_OK", null);
        } else {
            return ApiResponse.fail(404, "Agent not found");
        }
    }
}