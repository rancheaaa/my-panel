package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.repository.service.IAgentConnectivityService;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO;
import com.cq.panel.authlite.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Agent连通性检测", description = "检测两个Agent之间的网络连通性")
@RestController
@RequestMapping("/batch/connectivity")
public class AgentConnectivityController extends BaseController {

    @Autowired
    private IAgentConnectivityService connectivityService;

    @Operation(summary = "检测两个Agent之间的连通性")
    @GetMapping("/check")
    @RequirePermission("batch:connectivity:check")
    public Result<AgentConnectivityVO> checkConnectivity(
            @Parameter(description = "源节点名称，如 cq@172.19.200.130:7777")
            @RequestParam String sourceNodeName,
            @Parameter(description = "目标节点名称，如 dell@192.168.1.8:7777")
            @RequestParam String targetNodeName) {
        return Result.success(connectivityService.checkConnectivity(sourceNodeName, targetNodeName));
    }

    @Operation(summary = "Agent连通性检测（运维管理入口）")
    @GetMapping("/check-op")
    @RequirePermission("op:agentConnectivity:check")
    public Result<AgentConnectivityVO> checkConnectivityOp(
            @Parameter(description = "源节点名称，如 cq@172.19.200.130:7777")
            @RequestParam String sourceNodeName,
            @Parameter(description = "目标节点名称，如 dell@192.168.1.8:7777")
            @RequestParam String targetNodeName) {
        return Result.success(connectivityService.checkConnectivity(sourceNodeName, targetNodeName));
    }
}
