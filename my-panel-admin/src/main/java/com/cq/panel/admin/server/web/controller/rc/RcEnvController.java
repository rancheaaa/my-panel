package com.cq.panel.admin.server.web.controller.rc;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.RcEnv;
import com.cq.panel.admin.server.repository.service.IRcEnvService;
import com.cq.panel.admin.server.web.domain.dto.rc.RcEnvDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcEnvQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.rc.RcEnvVO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.rc.RcEnvConverter;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 环境管理 Controller
 * 
 * @author cq
 */
@Tag(name = "环境管理", description = "环境管理相关接口")
@RestController
@RequestMapping("/rc/env")
public class RcEnvController extends BaseController
{
    @Autowired
    private IRcEnvService rcEnvService;

    @Autowired
    private RcEnvConverter rcEnvConverter;

    /**
     * 查询环境管理列表
     */
    @Operation(summary = "查询环境管理列表", description = "根据条件分页获取环境管理列表")
    @PreAuthorize("@ss.hasPermi('rc:envManage:list')")
    @GetMapping("/list")
    public Result<PageVO<RcEnvVO>> list(@Parameter(description = "查询参数") RcEnvQueryDTO query)
    {
        startPage();
        RcEnv rcEnv = rcEnvConverter.toEntity(query);
        List<RcEnv> list = rcEnvService.selectRcEnvList(rcEnv);
        List<RcEnvVO> voList = rcEnvConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo(list).getTotal()));
    }

    /**
     * 导出环境管理列表
     */
    @Operation(summary = "导出环境管理列表", description = "导出符合条件的环境管理数据")
    @Log(title = "环境管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('rc:envManage:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") RcEnvQueryDTO query)
    {
        RcEnv rcEnv = rcEnvConverter.toEntity(query);
        List<RcEnv> list = rcEnvService.selectRcEnvList(rcEnv);
        ExcelUtil<RcEnv> util = new ExcelUtil<RcEnv>(RcEnv.class);
        util.exportExcel(response, list, "环境管理数据");
    }

    /**
     * 获取环境管理详细信息
     */
    @Operation(summary = "获取环境管理详细信息", description = "根据环境ID获取环境管理详细信息")
    @PreAuthorize("@ss.hasPermi('rc:envManage:query')")
    @GetMapping(value = "/{id}")
    public Result<RcEnvVO> getInfo(@Parameter(description = "环境ID", required = true) @PathVariable("id") Long id)
    {
        return Result.success(rcEnvConverter.toVO(rcEnvService.selectRcEnvById(id)));
    }

    /**
     * 新增环境管理
     */
    @Operation(summary = "新增环境管理", description = "新增环境管理信息")
    @PreAuthorize("@ss.hasPermi('rc:envManage:add')")
    @Log(title = "环境管理", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody RcEnvDTO dto)
    {
        RcEnv rcEnv = rcEnvConverter.toEntity(dto);
        if (!rcEnvService.checkEnvNameUnique(rcEnv))
        {
            return Result.error("新增环境'" + rcEnv.getEnvName() + "'失败，环境名称已存在");
        }
        rcEnv.setCreateBy(getUsername());
        rcEnvService.insertRcEnv(rcEnv);
        return Result.success();
    }

    /**
     * 修改环境管理
     */
    @Operation(summary = "修改环境管理", description = "修改环境管理信息")
    @PreAuthorize("@ss.hasPermi('rc:envManage:edit')")
    @Log(title = "环境管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody RcEnvDTO dto)
    {
        RcEnv rcEnv = rcEnvConverter.toEntity(dto);
        if (!rcEnvService.checkEnvNameUnique(rcEnv))
        {
            return Result.error("修改环境'" + rcEnv.getEnvName() + "'失败，环境名称已存在");
        }
        rcEnv.setUpdateBy(getUsername());
        rcEnvService.updateRcEnv(rcEnv);
        return Result.success();
    }

    /**
     * 删除环境管理
     */
    @Operation(summary = "删除环境管理", description = "批量删除环境管理")
    @PreAuthorize("@ss.hasPermi('rc:envManage:remove')")
    @Log(title = "环境管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public Result<Void> remove(@Parameter(description = "环境ID数组", required = true) @PathVariable Long[] ids)
    {
        rcEnvService.deleteRcEnvByIds(ids);
        return Result.success();
    }
}
