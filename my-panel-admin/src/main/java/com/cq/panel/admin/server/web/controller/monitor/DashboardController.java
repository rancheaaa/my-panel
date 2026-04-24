package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertEvent;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertRule;
import com.cq.panel.admin.server.repository.service.IMonitorDashboardService;
import com.cq.panel.admin.server.web.domain.dto.monitor.MetricTrendQueryDTO;
import com.cq.panel.admin.server.web.domain.dto.monitor.MonitorAlertRuleSaveDTO;
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
public class DashboardController
{
    private final IMonitorDashboardService monitorDashboardService;

    public DashboardController(IMonitorDashboardService monitorDashboardService) {
        this.monitorDashboardService = monitorDashboardService;
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取服务监控大屏数据(兼容接口)", description = "获取服务监控大屏的实时监控数据，兼容历史接口")
    @GetMapping("/data")
    public Result<Map<String, Object>> getDashboardData() throws Exception
    {
        return Result.success(monitorDashboardService.collectSnapshotAndPersist());
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取监控大屏总览", description = "返回最近采样时间的完整指标总览和告警概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview()
    {
        return Result.success(monitorDashboardService.getDashboardOverview());
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取监控指标趋势", description = "支持按分类、指标名、时间范围和粒度查询趋势")
    @PostMapping("/trend")
    public Result<Map<String, Object>> getTrend(@RequestBody MetricTrendQueryDTO queryDTO)
    {
        return Result.success(monitorDashboardService.getTrend(queryDTO));
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取告警规则列表")
    @GetMapping("/alert/rules")
    public Result<List<MonitorAlertRule>> listAlertRules()
    {
        return Result.success(monitorDashboardService.listAlertRules());
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "新增或更新告警规则")
    @PostMapping("/alert/rule")
    public Result<Void> saveAlertRule(@RequestBody MonitorAlertRuleSaveDTO dto)
    {
        monitorDashboardService.saveAlertRule(dto, "system");
        return Result.success();
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "删除告警规则")
    @DeleteMapping("/alert/rule/{id}")
    public Result<Void> deleteAlertRule(@PathVariable("id") Long id)
    {
        monitorDashboardService.deleteAlertRule(id);
        return Result.success();
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "获取告警事件")
    @GetMapping("/alert/events")
    public Result<List<MonitorAlertEvent>> listAlertEvents(@RequestParam(value = "range", required = false, defaultValue = "1h") String range,
                                                           @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit)
    {
        return Result.success(monitorDashboardService.listAlertEvents(range, limit));
    }

    @RequirePermission("monitor:server:list")
    @Operation(summary = "更新告警事件状态")
    @PutMapping("/alert/event/{id}/status")
    public Result<Void> updateAlertEventStatus(@PathVariable("id") Long id,
                                               @RequestParam("status") String status)
    {
        monitorDashboardService.updateAlertEventStatus(id, status);
        return Result.success();
    }
}
