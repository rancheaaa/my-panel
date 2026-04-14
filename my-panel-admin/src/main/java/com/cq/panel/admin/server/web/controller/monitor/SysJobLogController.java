package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobLogQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysJobLogVO;
import com.cq.panel.admin.server.web.converter.monitor.SysJobLogConverter;
import com.github.pagehelper.PageInfo;
import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.SysJobLog;
import com.cq.panel.admin.server.repository.service.ISysJobLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 调度日志操作处理
 * 
 * @author cq
 */
@Tag(name = "调度日志", description = "定时任务调度日志管理接口")
@RestController
@RequestMapping("/monitor/jobLog")
public class SysJobLogController extends BaseController
{
    private final ISysJobLogService jobLogService;

    private final SysJobLogConverter jobLogConverter;

    public SysJobLogController(ISysJobLogService jobLogService, SysJobLogConverter jobLogConverter) {
        this.jobLogService = jobLogService;
        this.jobLogConverter = jobLogConverter;
    }

    /**
     * 查询定时任务调度日志列表
     */
    @Operation(summary = "查询定时任务调度日志列表", description = "获取调度日志列表，支持分页和条件查询")
    @RequirePermission("monitor:job:list")
    @GetMapping("/list")
    public Result<PageVO<SysJobLogVO>> list(@Parameter(description = "查询条件") SysJobLogQueryDTO query)
    {
        startPage();
        SysJobLog sysJobLog = jobLogConverter.toEntity(query);
        List<SysJobLog> list = jobLogService.selectJobLogList(sysJobLog);
        List<SysJobLogVO> voList = jobLogConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }

    /**
     * 导出定时任务调度日志列表
     */
    @Operation(summary = "导出定时任务调度日志列表", description = "导出符合条件的调度日志数据")
    @RequirePermission("monitor:job:export")
    @Log(title = "任务调度日志", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询条件") SysJobLogQueryDTO query)
    {
        SysJobLog sysJobLog = jobLogConverter.toEntity(query);
        List<SysJobLog> list = jobLogService.selectJobLogList(sysJobLog);
        ExcelUtil<SysJobLog> util = new ExcelUtil<>(SysJobLog.class);
        util.exportExcel(response, list, "调度日志");
    }

    /**
     * 根据调度编号获取详细信息
     */
    @Operation(summary = "根据调度编号获取详细信息", description = "根据日志ID获取详细信息")
    @RequirePermission("monitor:job:query")
    @GetMapping(value = "/{jobLogId}")
    public Result<SysJobLogVO> getInfo(@Parameter(description = "日志ID", required = true) @PathVariable Long jobLogId)
    {
        return Result.success(jobLogConverter.toVO(jobLogService.selectJobLogById(jobLogId)));
    }

    /**
     * 删除定时任务调度日志
     */
    @Operation(summary = "删除定时任务调度日志", description = "批量删除调度日志")
    @RequirePermission("monitor:job:remove")
    @Log(title = "定时任务调度日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{jobLogIds}")
    public Result<Void> remove(@Parameter(description = "日志ID串", required = true) @PathVariable Long[] jobLogIds)
    {
        jobLogService.deleteJobLogByIds(jobLogIds);
        return Result.success();
    }

    /**
     * 清空定时任务调度日志
     */
    @Operation(summary = "清空定时任务调度日志", description = "清空所有调度日志")
    @RequirePermission("monitor:job:remove")
    @Log(title = "调度日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public Result<Void> clean()
    {
        jobLogService.cleanJobLog();
        return Result.success();
    }
}





