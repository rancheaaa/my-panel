package com.cq.proxy.controller;

import com.cq.proxy.dto.ApiResponse;
import com.cq.proxy.service.AgentForwardService;
import com.cq.proxy.service.batch.AgentPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent请求转发Controller
 * 提供Admin调用的转发端点，实现Admin->Proxy->Agent的调用链路
 */
@RestController
public class AgentForwardController {

    private static final Logger log = LoggerFactory.getLogger(AgentForwardController.class);

    private final AgentForwardService agentForwardService;

    public AgentForwardController(AgentForwardService agentForwardService) {
        this.agentForwardService = agentForwardService;
    }

    /**
     * Ping检测端点，用于Admin验证与Proxy的连通性
     * GET /forward/ping
     */
    @GetMapping("/forward/ping")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ping() {
        Map<String, Object> data = Map.of(
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 转发命令执行请求
     * POST /forward/agent/execute?agentId=xxx
     * Body: Agent原始请求体 (command, timeout)
     */
    @PostMapping(value = "/execute", params = "agentId")
    public ResponseEntity<ApiResponse<String>> executeCommand(
            @RequestParam String agentId,
            @RequestBody String requestBody) {
        try {
            String result = agentForwardService.forwardExecute(agentId, requestBody);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (AgentPushService.AgentNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 转发连通性探测请求
     * GET /forward/agent/probe?agentId=xxx&host=xxx&port=xxx
     */
    @GetMapping("/probe")
    public ResponseEntity<ApiResponse<String>> probe(
            @RequestParam String agentId,
            @RequestParam String host,
            @RequestParam int port) {
        try {
            String result = agentForwardService.forwardProbe(agentId, host, port);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (AgentPushService.AgentNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 从Proxy发起TCP端口探测
     * GET /forward/agent/tcp-probe?host=xxx&port=xxx
     */
    @GetMapping("/tcp-probe")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tcpProbe(
            @RequestParam String host,
            @RequestParam int port) {
        Map<String, Object> result = agentForwardService.tcpProbe(host, port);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 转发目录详情检查请求
     * GET /forward/agent/file/dir-check?agentId=xxx&path=xxx
     */
    @GetMapping("/file/dir-check")
    public ResponseEntity<ApiResponse<String>> dirCheck(
            @RequestParam String agentId,
            @RequestParam String path) {
        try {
            String result = agentForwardService.forwardDirCheck(agentId, path);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (AgentPushService.AgentNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 转发目录存在性检查请求
     * GET /forward/agent/file/exists?agentId=xxx&path=xxx
     */
    @GetMapping("/file/exists")
    public ResponseEntity<ApiResponse<String>> fileExists(
            @RequestParam String agentId,
            @RequestParam String path) {
        try {
            String result = agentForwardService.forwardFileExists(agentId, path);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (AgentPushService.AgentNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 转发配置校验请求
     * GET /forward/agent/config/verify?agentId=xxx
     */
    @GetMapping("/config/verify")
    public ResponseEntity<ApiResponse<String>> configVerify(@RequestParam String agentId) {
        try {
            String result = agentForwardService.forwardConfigVerify(agentId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (AgentPushService.AgentNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 转发配置推送初始化请求
     * POST /forward/agent/config/push/init?agentId=xxx
     * Body: Agent原始请求体 (totalSize, totalChunks, chunkSize)
     */
    @PostMapping("/config/push/init")
    public ResponseEntity<ApiResponse<String>> configPushInit(
            @RequestParam String agentId,
            @RequestBody String requestBody) {
        try {
            String result = agentForwardService.forwardConfigPushInit(agentId, requestBody);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (AgentPushService.AgentNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 转发配置推送分块请求
     * POST /forward/agent/config/push/chunk?agentId=xxx
     * Body: Agent原始请求体 (sessionId, chunkIndex, data)
     */
    @PostMapping("/config/push/chunk")
    public ResponseEntity<ApiResponse<String>> configPushChunk(
            @RequestParam String agentId,
            @RequestBody String requestBody) {
        try {
            String result = agentForwardService.forwardConfigPushChunk(agentId, requestBody);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (AgentPushService.AgentNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 转发配置推送完成请求
     * POST /forward/agent/config/push/complete?agentId=xxx
     * Body: Agent原始请求体 (sessionId)
     */
    @PostMapping("/config/push/complete")
    public ResponseEntity<ApiResponse<String>> configPushComplete(
            @RequestParam String agentId,
            @RequestBody String requestBody) {
        try {
            String result = agentForwardService.forwardConfigPushComplete(agentId, requestBody);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (AgentPushService.AgentNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
