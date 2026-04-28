package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertEvent;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertRule;
import com.cq.panel.admin.server.repository.service.IMonitorDashboardService;
import com.cq.panel.admin.server.web.domain.dto.monitor.MonitorAlertRuleSaveDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.authlite.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报警规则管理
 *
 * @author cq
 */
@Tag(name = "报警规则管理", description = "报警规则和告警事件管理接口")
@RestController
@RequestMapping("/monitor/alert")
public class AlertRuleController
{
    private final IMonitorDashboardService monitorDashboardService;

    public AlertRuleController(IMonitorDashboardService monitorDashboardService) {
        this.monitorDashboardService = monitorDashboardService;
    }

    @RequirePermission("op:alertRule:list")
    @Operation(summary = "获取告警规则列表")
    @GetMapping("/rules")
    public Result<List<MonitorAlertRule>> listAlertRules()
    {
        return Result.success(monitorDashboardService.listAlertRules());
    }

    @RequirePermission("op:alertRule:add")
    @Operation(summary = "新增或更新告警规则")
    @PostMapping("/rule")
    public Result<Void> saveAlertRule(@RequestBody MonitorAlertRuleSaveDTO dto)
    {
        monitorDashboardService.saveAlertRule(dto, "system");
        return Result.success();
    }

    @RequirePermission("op:alertRule:remove")
    @Operation(summary = "删除告警规则")
    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteAlertRule(@PathVariable("id") Long id)
    {
        monitorDashboardService.deleteAlertRule(id);
        return Result.success();
    }

    @RequirePermission("op:alertEvent:query")
    @Operation(summary = "获取告警事件")
    @GetMapping("/events")
    public Result<List<MonitorAlertEvent>> listAlertEvents(@RequestParam(value = "range", required = false, defaultValue = "1h") String range,
                                                           @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit)
    {
        return Result.success(monitorDashboardService.listAlertEvents(range, limit));
    }

    @RequirePermission("op:alertEvent:query")
    @Operation(summary = "更新告警事件状态")
    @PutMapping("/event/{id}/status")
    public Result<Void> updateAlertEventStatus(@PathVariable("id") Long id,
                                               @RequestParam("status") String status)
    {
        monitorDashboardService.updateAlertEventStatus(id, status);
        return Result.success();
    }
}
