package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.repository.service.IMonitorDashboardService;
import com.cq.panel.admin.server.web.domain.dto.monitor.MetricTrendQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.authlite.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 服务监控大屏
 *
 * @author cq
 */
@Tag(name = "服务监控大屏", description = "服务监控大屏数据接口")
@RestController
@RequestMapping("/monitor/dashboard")
public class DashboardController {
    private final IMonitorDashboardService monitorDashboardService;

    public DashboardController(IMonitorDashboardService monitorDashboardService) {
        this.monitorDashboardService = monitorDashboardService;
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取服务监控大屏数据(兼容接口)", description = "获取服务监控大屏的实时监控数据，兼容历史接口")
    @GetMapping("/data")
    public Result<Map<String, Object>> getDashboardData() throws Exception {
        return Result.success(monitorDashboardService.collectSnapshotAndPersist());
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取监控大屏总览", description = "返回最近采样时间的完整指标总览和告警概览，支持按服务实例过滤")
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String serviceIpPort) {
        return Result.success(monitorDashboardService.getDashboardOverview(serviceId, serviceIpPort));
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取监控指标趋势", description = "支持按分类、指标名、时间范围和粒度查询趋势")
    @PostMapping("/trend")
    public Result<Map<String, Object>> getTrend(@RequestBody MetricTrendQueryDTO queryDTO) {
        return Result.success(monitorDashboardService.getTrend(queryDTO));
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取服务实例列表", description = "获取所有已注册的服务实例信息")
    @GetMapping("/service-instances")
    public Result<List<Map<String, String>>> listServiceInstances() {
        return Result.success(monitorDashboardService.listServiceInstances());
    }
}
