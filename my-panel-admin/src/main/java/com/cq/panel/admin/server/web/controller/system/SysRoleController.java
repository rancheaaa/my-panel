package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.repository.domain.SysDept;
import com.cq.panel.admin.server.repository.domain.SysRole;
import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.web.domain.model.LoginUser;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.SysUserRole;
import com.cq.panel.admin.server.repository.service.ISysDeptService;
import com.cq.panel.admin.server.repository.service.ISysRoleService;
import com.cq.panel.admin.server.repository.service.ISysUserService;
import com.cq.panel.admin.server.web.service.SysPermissionService;
import com.cq.panel.admin.server.web.service.TokenService;
import com.cq.panel.admin.server.web.domain.dto.system.SysRoleDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysRoleQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysRoleVO;
import com.cq.panel.admin.server.web.domain.vo.system.RoleDeptTreeSelectVO;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserRoleDTO;
import com.cq.panel.admin.server.web.domain.dto.system.RoleUsersDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysUserVO;
import com.cq.panel.admin.server.web.converter.system.SysRoleConverter;
import com.cq.panel.admin.server.web.converter.system.SysUserConverter;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
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
 * 角色信息
 * 
 * @author cq
 */
@Tag(name = "角色管理", description = "角色管理相关接口")
@RestController
@RequestMapping("/system/role")
public class SysRoleController extends BaseController
{
    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private SysRoleConverter roleConverter;

    @Autowired
    private SysUserConverter userConverter;

    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/list")
    @Operation(summary = "获取角色列表", description = "根据条件分页获取角色列表")
    public Result<PageVO<SysRoleVO>> list(@Parameter(description = "查询参数") SysRoleQueryDTO query)
    {
        startPage();
        SysRole role = roleConverter.toEntity(query);
        List<SysRole> list = roleService.selectRoleList(role);
        return Result.success(new PageVO<>(roleConverter.toVOList(list), new PageInfo(list).getTotal()));
    }

    @Log(title = "角色管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:role:export')")
    @PostMapping("/export")
    @Operation(summary = "导出角色数据", description = "导出符合条件的角色数据")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") SysRoleQueryDTO query)
    {
        SysRole role = roleConverter.toEntity(query);
        List<SysRole> list = roleService.selectRoleList(role);
        ExcelUtil<SysRole> util = new ExcelUtil<SysRole>(SysRole.class);
        util.exportExcel(response, list, "角色数据");
    }

    /**
     * 根据角色编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping(value = "/{roleId}")
    @Operation(summary = "根据角色编号获取详细信息", description = "根据角色ID获取角色详细信息")
    public Result<SysRoleVO> getInfo(@Parameter(description = "角色ID", required = true) @PathVariable Long roleId)
    {
        roleService.checkRoleDataScope(roleId);
        return Result.success(roleConverter.toVO(roleService.selectRoleById(roleId)));
    }

    /**
     * 新增角色
     */
    @PreAuthorize("@ss.hasPermi('system:role:add')")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    @Operation(summary = "新增角色", description = "新增角色信息")
    public Result<Void> add(@Validated @RequestBody SysRoleDTO dto)
    {
        SysRole role = roleConverter.toEntity(dto);
        if (!roleService.checkRoleNameUnique(role))
        {
            return Result.error("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        }
        else if (!roleService.checkRoleKeyUnique(role))
        {
            return Result.error("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setCreateBy(getUsername());
        roleService.insertRole(role);
        return Result.success();
    }

    /**
     * 修改保存角色
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping
    @Operation(summary = "修改保存角色", description = "修改角色信息")
    public Result<Void> edit(@Validated @RequestBody SysRoleDTO dto)
    {
        SysRole role = roleConverter.toEntity(dto);
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        if (!roleService.checkRoleNameUnique(role))
        {
            return Result.error("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        }
        else if (!roleService.checkRoleKeyUnique(role))
        {
            return Result.error("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setUpdateBy(getUsername());
        
        if (roleService.updateRole(role) > 0)
        {
            // 更新缓存用户权限
            LoginUser loginUser = getLoginUser();
            if (StringUtils.isNotNull(loginUser.getUser()) && !loginUser.getUser().isAdmin())
            {
                loginUser.setUser(userService.selectUserByUserName(loginUser.getUser().getUserName()));
                loginUser.setPermissions(permissionService.getMenuPermission(loginUser.getUser()));
                tokenService.setLoginUser(loginUser);
            }
            return Result.success();
        }
        return Result.error("修改角色'" + role.getRoleName() + "'失败，请联系管理员");
    }

    /**
     * 修改保存数据权限
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/dataScope")
    @Operation(summary = "修改保存数据权限", description = "修改角色数据权限")
    public Result<Void> dataScope(@RequestBody SysRoleDTO dto)
    {
        SysRole role = roleConverter.toEntity(dto);
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        roleService.authDataScope(role);
        return Result.success();
    }

    /**
     * 状态修改
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    @Operation(summary = "状态修改", description = "修改角色状态")
    public Result<Void> changeStatus(@RequestBody SysRoleDTO dto)
    {
        SysRole role = roleConverter.toEntity(dto);
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        role.setUpdateBy(getUsername());
        roleService.updateRoleStatus(role);
        return Result.success();
    }

    /**
     * 删除角色
     */
    @PreAuthorize("@ss.hasPermi('system:role:remove')")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleIds}")
    @Operation(summary = "删除角色", description = "批量删除角色")
    public Result<Void> remove(@Parameter(description = "角色ID数组", required = true) @PathVariable Long[] roleIds)
    {
        roleService.deleteRoleByIds(roleIds);
        return Result.success();
    }

    /**
     * 获取角色选择框列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping("/optionselect")
    @Operation(summary = "获取角色选择框列表", description = "获取所有角色列表")
    public Result<List<SysRoleVO>> optionselect()
    {
        return Result.success(roleConverter.toVOList(roleService.selectRoleAll()));
    }

    /**
     * 查询已分配用户角色列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/authUser/allocatedList")
    @Operation(summary = "查询已分配用户角色列表", description = "分页查询已分配用户角色列表")
    public Result<PageVO<SysUserVO>> allocatedList(@Parameter(description = "查询参数") SysUserQueryDTO query)
    {
        startPage();
        SysUser user = userConverter.toEntity(query);
        List<SysUser> list = userService.selectAllocatedList(user);
        return Result.success(new PageVO<>(userConverter.toVOList(list), new PageInfo(list).getTotal()));
    }

    /**
     * 查询未分配用户角色列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/authUser/unallocatedList")
    @Operation(summary = "查询未分配用户角色列表", description = "分页查询未分配用户角色列表")
    public Result<PageVO<SysUserVO>> unallocatedList(@Parameter(description = "查询参数") SysUserQueryDTO query)
    {
        startPage();
        SysUser user = userConverter.toEntity(query);
        List<SysUser> list = userService.selectUnallocatedList(user);
        return Result.success(new PageVO<>(userConverter.toVOList(list), new PageInfo(list).getTotal()));
    }

    /**
     * 取消授权用户
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancel")
    @Operation(summary = "取消授权用户", description = "取消用户角色授权")
    public Result<Void> cancelAuthUser(@RequestBody SysUserRoleDTO dto)
    {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(dto.getUserId());
        userRole.setRoleId(dto.getRoleId());
        roleService.deleteAuthUser(userRole);
        return Result.success();
    }

    /**
     * 批量取消授权用户
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancelAll")
    @Operation(summary = "批量取消授权用户", description = "批量取消用户角色授权")
    public Result<Void> cancelAuthUserAll(@RequestBody RoleUsersDTO dto)
    {
        roleService.deleteAuthUsers(dto.getRoleId(), dto.getUserIds());
        return Result.success();
    }

    /**
     * 批量选择用户授权
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/selectAll")
    @Operation(summary = "批量选择用户授权", description = "批量给用户授权角色")
    public Result<Void> selectAuthUserAll(@RequestBody RoleUsersDTO dto)
    {
        roleService.checkRoleDataScope(dto.getRoleId());
        roleService.insertAuthUsers(dto.getRoleId(), dto.getUserIds());
        return Result.success();
    }

    /**
     * 获取对应角色部门树列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping(value = "/deptTree/{roleId}")
    @Operation(summary = "获取对应角色部门树列表", description = "根据角色ID获取部门树列表")
    public Result<RoleDeptTreeSelectVO> deptTree(@Parameter(description = "角色ID", required = true) @PathVariable("roleId") Long roleId)
    {
        RoleDeptTreeSelectVO vo = new RoleDeptTreeSelectVO();
        vo.setCheckedKeys(deptService.selectDeptListByRoleId(roleId));
        vo.setDepts(deptService.selectDeptTreeList(new SysDept()));
        return Result.success(vo);
    }
}
