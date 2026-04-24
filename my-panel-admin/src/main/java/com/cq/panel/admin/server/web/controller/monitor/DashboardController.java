package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.repository.service.IServerService;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.monitor.ServerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务监控大屏
 *
 * @author cq
 */
@Tag(name = "服务监控大屏", description = "服务监控大屏数据接口")
@RestController
@RequestMapping("/monitor/dashboard")
public class DashboardController
{
    private final IServerService serverService;

    public DashboardController(IServerService serverService) {
        this.serverService = serverService;
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取服务监控大屏数据", description = "获取服务监控大屏的实时监控数据")
    @GetMapping("/data")
    public Result<Map<String, Object>> getDashboardData() throws Exception
    {
        ServerVO serverInfo = serverService.getServerInfo();
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> basicStats = new HashMap<>();
        basicStats.put("cpuUsage", serverInfo.getCpu().getUsed());
        basicStats.put("cpuCore", serverInfo.getCpu().getCpuNum());
        basicStats.put("memUsage", serverInfo.getMem().getUsage());
        basicStats.put("memTotal", serverInfo.getMem().getTotal());
        basicStats.put("memUsed", serverInfo.getMem().getUsed());
        basicStats.put("jvmUsage", serverInfo.getJvm().getUsage());
        basicStats.put("jvmTotal", serverInfo.getJvm().getTotal());
        basicStats.put("jvmUsed", serverInfo.getJvm().getUsed());

        Map<String, Object> serviceStatus = new HashMap<>();
        serviceStatus.put("serverName", serverInfo.getSys().getComputerName());
        serviceStatus.put("serverIp", serverInfo.getSys().getComputerIp());
        serviceStatus.put("osName", serverInfo.getSys().getOsName());
        serviceStatus.put("osArch", serverInfo.getSys().getOsArch());
        serviceStatus.put("jvmName", serverInfo.getJvm().getName());
        serviceStatus.put("jvmVersion", serverInfo.getJvm().getVersion());

        Map<String, Object> healthStatus = new HashMap<>();
        double cpuUsage = serverInfo.getCpu().getUsed();
        double memUsage = serverInfo.getMem().getUsage();
        double jvmUsage = serverInfo.getJvm().getUsage();

        if (cpuUsage > 90 || memUsage > 90 || jvmUsage > 90) {
            healthStatus.put("status", "critical");
            healthStatus.put("level", 3);
        } else if (cpuUsage > 70 || memUsage > 80 || jvmUsage > 80) {
            healthStatus.put("status", "warning");
            healthStatus.put("level", 2);
        } else {
            healthStatus.put("status", "normal");
            healthStatus.put("level", 1);
        }

        result.put("basicStats", basicStats);
        result.put("serviceStatus", serviceStatus);
        result.put("healthStatus", healthStatus);
        result.put("diskInfo", serverInfo.getSysFiles());
        result.put("cpuHistory", serverInfo.getCpu());
        result.put("memHistory", serverInfo.getMem());

        return Result.success(result);
    }
}