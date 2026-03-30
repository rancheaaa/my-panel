package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.web.domain.dto.monitor.SysOperLogQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysOperLogVO;
import com.cq.panel.admin.server.web.converter.monitor.SysOperLogConverter;
import com.github.pagehelper.PageInfo;
import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.SysOperLog;
import com.cq.panel.admin.server.repository.service.ISysOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 操作日志记录
 * 
 * @author cq
 */
@Tag(name = "操作日志", description = "系统操作日志管理接口")
@RestController
@RequestMapping("/monitor/operlog")
public class SysOperLogController extends BaseController
{
    private final ISysOperLogService operLogService;

    private final SysOperLogConverter operLogConverter;

    public SysOperLogController(ISysOperLogService operLogService, SysOperLogConverter operLogConverter) {
        this.operLogService = operLogService;
        this.operLogConverter = operLogConverter;
    }

    @Operation(summary = "查询操作日志记录列表", description = "获取操作日志列表，支持分页和条件查询")
    @RequirePermission("monitor:operlog:list")
    @GetMapping("/list")
    public Result<PageVO<SysOperLogVO>> list(@Parameter(description = "查询条件") SysOperLogQueryDTO query)
    {
        startPage();
        SysOperLog sysOperLog = operLogConverter.toEntity(query);
        List<SysOperLog> list = operLogService.selectOperLogList(sysOperLog);
        List<SysOperLogVO> voList = operLogConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }

    @Operation(summary = "导出操作日志记录列表", description = "导出符合条件的操作日志数据")
    @Log(title = "操作日志", businessType = BusinessType.EXPORT)
    @RequirePermission("monitor:operlog:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询条件") SysOperLogQueryDTO query)
    {
        SysOperLog sysOperLog = operLogConverter.toEntity(query);
        List<SysOperLog> list = operLogService.selectOperLogList(sysOperLog);
        ExcelUtil<SysOperLog> util = new ExcelUtil<>(SysOperLog.class);
        util.exportExcel(response, list, "操作日志");
    }

    @Operation(summary = "删除操作日志记录", description = "批量删除操作日志")
    @Log(title = "操作日志", businessType = BusinessType.DELETE)
    @RequirePermission("monitor:operlog:remove")
    @DeleteMapping("/{operIds}")
    public Result<Void> remove(@Parameter(description = "日志ID串", required = true) @PathVariable Long[] operIds)
    {
        operLogService.deleteOperLogByIds(operIds);
        return Result.success();
    }

    @Operation(summary = "清空操作日志记录", description = "清空所有操作日志")
    @Log(title = "操作日志", businessType = BusinessType.CLEAN)
    @RequirePermission("monitor:operlog:remove")
    @DeleteMapping("/clean")
    public Result<Void> clean()
    {
        operLogService.cleanOperLog();
        return Result.success();
    }
}





