package com.cq.panel.admin.server.web.controller.rc;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.RcProject;
import com.cq.panel.admin.server.repository.service.IRcProjectService;
import com.cq.panel.admin.server.web.domain.dto.rc.RcProjectDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcProjectQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.rc.RcProjectVO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.rc.RcProjectConverter;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 应用管理 Controller
 * 
 * @author cq
 */
@Tag(name = "应用管理", description = "应用管理相关接口")
@RestController
@RequestMapping("/rc/project")
public class RcProjectController extends BaseController
{
    private final IRcProjectService rcProjectService;

    private final RcProjectConverter rcProjectConverter;

    public RcProjectController(IRcProjectService rcProjectService, RcProjectConverter rcProjectConverter) {
        this.rcProjectService = rcProjectService;
        this.rcProjectConverter = rcProjectConverter;
    }

    /**
     * 查询应用管理列表
     */
    @Operation(summary = "查询应用管理列表", description = "根据条件分页获取应用管理列表")
    @RequirePermission("rc:appManage:list")
    @GetMapping("/list")
    public Result<PageVO<RcProjectVO>> list(@Parameter(description = "查询参数") RcProjectQueryDTO query)
    {
        startPage();
        RcProject rcProject = rcProjectConverter.toEntity(query);
        List<RcProject> list = rcProjectService.selectRcProjectList(rcProject);
        List<RcProjectVO> voList = rcProjectConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }

    /**
     * 导出应用管理列表
     */
    @Operation(summary = "导出应用管理列表", description = "导出符合条件的应用管理数据")
    @Log(title = "应用管理", businessType = BusinessType.EXPORT)
    @RequirePermission("rc:appManage:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") RcProjectQueryDTO query)
    {
        RcProject rcProject = rcProjectConverter.toEntity(query);
        List<RcProject> list = rcProjectService.selectRcProjectList(rcProject);
        ExcelUtil<RcProject> util = new ExcelUtil<>(RcProject.class);
        util.exportExcel(response, list, "应用管理数据");
    }

    /**
     * 获取应用管理详细信息
     */
    @Operation(summary = "获取应用管理详细信息", description = "根据项目ID获取应用管理详细信息")
    @RequirePermission("rc:appManage:query")
    @GetMapping(value = "/{id}")
    public Result<RcProjectVO> getInfo(@Parameter(description = "项目ID", required = true) @PathVariable("id") Long id)
    {
        return Result.success(rcProjectConverter.toVO(rcProjectService.selectRcProjectById(id)));
    }

    /**
     * 新增应用管理
     */
    @Operation(summary = "新增应用管理", description = "新增应用管理信息")
    @RequirePermission("rc:appManage:add")
    @Log(title = "应用管理", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody RcProjectDTO dto)
    {
        RcProject rcProject = rcProjectConverter.toEntity(dto);
        if (!rcProjectService.checkProjectNameUnique(rcProject))
        {
            return Result.error("新增应用'" + rcProject.getProjectName() + "'失败，应用名称已存在");
        }
        rcProject.setCreateBy(getUsername());
        rcProjectService.insertRcProject(rcProject);
        return Result.success();
    }

    /**
     * 修改应用管理
     */
    @Operation(summary = "修改应用管理", description = "修改应用管理信息")
    @RequirePermission("rc:appManage:edit")
    @Log(title = "应用管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody RcProjectDTO dto)
    {
        RcProject rcProject = rcProjectConverter.toEntity(dto);
        if (!rcProjectService.checkProjectNameUnique(rcProject))
        {
            return Result.error("修改应用'" + rcProject.getProjectName() + "'失败，应用名称已存在");
        }
        rcProject.setUpdateBy(getUsername());
        rcProjectService.updateRcProject(rcProject);
        return Result.success();
    }

    /**
     * 删除应用管理
     */
    @Operation(summary = "删除应用管理", description = "批量删除应用管理")
    @RequirePermission("rc:appManage:remove")
    @Log(title = "应用管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public Result<Void> remove(@Parameter(description = "项目ID数组", required = true) @PathVariable Long[] ids)
    {
        rcProjectService.deleteRcProjectByIds(ids);
        return Result.success();
    }
}
