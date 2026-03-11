package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.web.domain.dto.monitor.SysLogininforQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysLogininforVO;
import com.cq.panel.admin.server.web.converter.monitor.SysLogininforConverter;
import com.github.pagehelper.PageInfo;
import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.web.service.SysPasswordService;
import com.cq.panel.admin.server.repository.domain.SysLogininfor;
import com.cq.panel.admin.server.repository.service.ISysLogininforService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 系统访问记录
 * 
 * @author cq
 */
@Tag(name = "登录日志", description = "系统登录日志管理接口")
@RestController
@RequestMapping("/monitor/logininfor")
public class SysLogininforController extends BaseController
{
    @Autowired
    private ISysLogininforService logininforService;

    @Autowired
    private SysPasswordService passwordService;

    @Autowired
    private SysLogininforConverter logininforConverter;

    @Operation(summary = "查询系统访问记录列表", description = "获取登录日志列表，支持分页和条件查询")
    @PreAuthorize("@ss.hasPermi('monitor:logininfor:list')")
    @GetMapping("/list")
    public Result<PageVO<SysLogininforVO>> list(@Parameter(description = "查询条件") SysLogininforQueryDTO query)
    {
        startPage();
        SysLogininfor sysLogininfor = logininforConverter.toEntity(query);
        List<SysLogininfor> list = logininforService.selectLogininforList(sysLogininfor);
        List<SysLogininforVO> voList = logininforConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo(list).getTotal()));
    }

    @Operation(summary = "导出系统访问记录列表", description = "导出符合条件的登录日志数据")
    @PreAuthorize("@ss.hasPermi('monitor:logininfor:export')")
    @Log(title = "登录日志", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询条件") SysLogininforQueryDTO query)
    {
        SysLogininfor sysLogininfor = logininforConverter.toEntity(query);
        List<SysLogininfor> list = logininforService.selectLogininforList(sysLogininfor);
        ExcelUtil<SysLogininfor> util = new ExcelUtil<SysLogininfor>(SysLogininfor.class);
        util.exportExcel(response, list, "登录日志");
    }

    @Operation(summary = "删除系统访问记录", description = "批量删除登录日志")
    @PreAuthorize("@ss.hasPermi('monitor:logininfor:remove')")
    @Log(title = "登录日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{infoIds}")
    public Result<Void> remove(@Parameter(description = "日志ID串", required = true) @PathVariable Long[] infoIds)
    {
        logininforService.deleteLogininforByIds(infoIds);
        return Result.success();
    }

    @Operation(summary = "清空系统访问记录", description = "清空所有登录日志")
    @PreAuthorize("@ss.hasPermi('monitor:logininfor:remove')")
    @Log(title = "登录日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public Result<Void> clean()
    {
        logininforService.cleanLogininfor();
        return Result.success();
    }

    @Operation(summary = "账户解锁", description = "解锁被锁定的用户账户")
    @PreAuthorize("@ss.hasPermi('monitor:logininfor:unlock')")
    @Log(title = "账户解锁", businessType = BusinessType.OTHER)
    @GetMapping("/unlock/{userName}")
    public Result<Void> unlock(@Parameter(description = "用户账号", required = true) @PathVariable("userName") String userName)
    {
        passwordService.clearLoginRecordCache(userName);
        return Result.success();
    }
}





