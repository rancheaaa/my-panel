package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.web.domain.dto.system.SysDeptDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysDeptQueryDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysDeptSortDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.system.SysDeptVO;
import com.cq.panel.admin.server.web.converter.system.SysDeptConverter;
import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.common.constant.UserConstants;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.repository.domain.SysDept;
import com.cq.panel.admin.server.repository.service.ISysDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.ArrayUtils;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 部门信息
 * 
 * @author cq
 */
@Tag(name = "部门管理", description = "部门管理相关接口")
@RestController
@RequestMapping("/system/dept")
public class SysDeptController extends BaseController
{
    private final ISysDeptService deptService;

    private final SysDeptConverter deptConverter;

    public SysDeptController(ISysDeptService deptService, SysDeptConverter deptConverter) {
        this.deptService = deptService;
        this.deptConverter = deptConverter;
    }

    /**
     * 获取部门列表
     */
    @Operation(summary = "获取部门列表", description = "根据条件获取部门列表")
    @RequirePermission("system:dept:list")
    @GetMapping("/list")
    public Result<List<SysDeptVO>> list(@Parameter(description = "查询参数") SysDeptQueryDTO query)
    {
        SysDept dept = deptConverter.toEntity(query);
        List<SysDept> list = deptService.selectDeptList(dept);
        return Result.success(deptConverter.toVOList(list));
    }

    /**
     * 查询部门列表（排除节点）
     */
    @Operation(summary = "查询部门列表（排除节点）", description = "查询部门列表，排除指定节点及其子节点")
    @RequirePermission("system:dept:list")
    @GetMapping("/list/exclude/{deptId}")
    public Result<List<SysDeptVO>> excludeChild(@Parameter(description = "排除的部门ID", required = true) @PathVariable(value = "deptId", required = false) Long deptId)
    {
        List<SysDept> list = deptService.selectDeptList(new SysDept());
        list.removeIf(d -> d.getDeptId().intValue() == deptId || ArrayUtils.contains(StringUtils.split(d.getAncestors(), ","), deptId + ""));
        return Result.success(deptConverter.toVOList(list));
    }

    /**
     * 根据部门编号获取详细信息
     */
    @Operation(summary = "根据部门编号获取详细信息", description = "根据部门ID获取部门详细信息")
    @RequirePermission("system:dept:query")
    @GetMapping(value = "/{deptId}")
    public Result<SysDeptVO> getInfo(@Parameter(description = "部门ID", required = true) @PathVariable Long deptId)
    {
        deptService.checkDeptDataScope(deptId);
        return Result.success(deptConverter.toVO(deptService.selectDeptById(deptId)));
    }

    /**
     * 新增部门
     */
    @Operation(summary = "新增部门", description = "新增部门信息")
    @RequirePermission("system:dept:add")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysDeptDTO dto)
    {
        SysDept dept = deptConverter.toEntity(dto);
        if (!deptService.checkDeptNameUnique(dept))
        {
            return Result.error("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        dept.setCreateBy(getUsername());
        deptService.insertDept(dept);
        return Result.success();
    }

    /**
     * 修改部门
     */
    @Operation(summary = "修改部门", description = "修改部门信息")
    @RequirePermission("system:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SysDeptDTO dto)
    {
        SysDept dept = deptConverter.toEntity(dto);
        Long deptId = dept.getDeptId();
        deptService.checkDeptDataScope(deptId);
        if (!deptService.checkDeptNameUnique(dept))
        {
            return Result.error("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        else if (dept.getParentId().equals(deptId))
        {
            return Result.error("修改部门'" + dept.getDeptName() + "'失败，上级部门不能是自己");
        }
        else if (StringUtils.equals(UserConstants.DEPT_DISABLE, dept.getStatus()) && deptService.selectNormalChildrenDeptById(deptId) > 0)
        {
            return Result.error("该部门包含未停用的子部门！");
        }
        dept.setUpdateBy(getUsername());
        deptService.updateDept(dept);
        return Result.success();
    }

    /**
     * 部门排序
     */
    @Operation(summary = "部门排序", description = "批量修改部门排序")
    @RequirePermission("system:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @PutMapping("/sort")
    public Result<Void> sort(@Validated @RequestBody List<SysDeptSortDTO> sortList)
    {
        for (SysDeptSortDTO sort : sortList)
        {
            SysDept dept = new SysDept();
            dept.setDeptId(sort.getDeptId());
            dept.setOrderNum(sort.getOrderNum());
            dept.setUpdateBy(getUsername());
            deptService.updateDept(dept);
        }
        return Result.success();
    }

    /**
     * 删除部门
     */
    @Operation(summary = "删除部门", description = "删除部门")
    @RequirePermission("system:dept:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    public Result<Void> remove(@Parameter(description = "部门ID", required = true) @PathVariable Long deptId)
    {
        if (deptService.hasChildByDeptId(deptId))
        {
            return Result.error("存在下级部门,不允许删除");
        }
        if (deptService.checkDeptExistUser(deptId))
        {
            return Result.error("部门存在用户,不允许删除");
        }
        deptService.checkDeptDataScope(deptId);
        deptService.deleteDeptById(deptId);
        return Result.success();
    }
}