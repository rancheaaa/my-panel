package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.repository.domain.SysDept;
import com.cq.panel.admin.server.repository.domain.SysRole;
import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.service.ISysDeptService;
import com.cq.panel.admin.server.repository.service.ISysPostService;
import com.cq.panel.admin.server.repository.service.ISysRoleService;
import com.cq.panel.admin.server.repository.service.ISysUserService;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserQueryDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserResetPwdDTO;
import com.cq.panel.admin.server.web.domain.dto.system.SysUserStatusDTO;
import com.cq.panel.admin.server.web.domain.dto.system.UserAuthRoleDTO;
import com.cq.panel.admin.server.web.domain.vo.system.SysUserDetailVO;
import com.cq.panel.admin.server.web.domain.vo.system.SysUserVO;
import com.cq.panel.admin.server.web.domain.vo.system.UserAuthRoleVO;
import com.cq.panel.admin.server.web.converter.system.SysUserConverter;
import com.cq.panel.admin.server.web.converter.system.SysRoleConverter;
import com.cq.panel.admin.server.web.domain.model.TreeSelect;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.github.pagehelper.PageInfo;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.ArrayUtils;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户信息
 * 
 * @author cq
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
public class SysUserController extends BaseController
{
    private final ISysUserService userService;

    private final ISysRoleService roleService;

    private final ISysDeptService deptService;

    private final ISysPostService postService;

    private final SysUserConverter userConverter;

    private final SysRoleConverter roleConverter;

    public SysUserController(ISysUserService userService, ISysRoleService roleService, ISysDeptService deptService, ISysPostService postService, SysUserConverter userConverter, SysRoleConverter roleConverter) {
        this.userService = userService;
        this.roleService = roleService;
        this.deptService = deptService;
        this.postService = postService;
        this.userConverter = userConverter;
        this.roleConverter = roleConverter;
    }

    /**
     * 获取用户列表
     */
    @RequirePermission("system:user:list")
    @GetMapping("/list")
    @Operation(summary = "获取用户列表", description = "分页获取用户列表")
    public Result<PageVO<SysUserVO>> list(@Parameter(description = "查询参数") SysUserQueryDTO query)
    {
        startPage();
        SysUser user = userConverter.toEntity(query);
        List<SysUser> list = userService.selectUserList(user);
        return Result.success(new PageVO<>(userConverter.toVOList(list), new PageInfo<>(list).getTotal()));
    }

    @Log(title = "用户管理", businessType = BusinessType.EXPORT)
    @RequirePermission("system:user:export")
    @PostMapping("/export")
    @Operation(summary = "导出用户数据", description = "导出所有符合查询条件的用户数据")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") SysUserQueryDTO query)
    {
        SysUser user = userConverter.toEntity(query);
        List<SysUser> list = userService.selectUserList(user);
        ExcelUtil<SysUser> util = new ExcelUtil<>(SysUser.class);
        util.exportExcel(response, list, "用户数据");
    }

    @Log(title = "用户管理", businessType = BusinessType.IMPORT)
    @RequirePermission("system:user:import")
    @PostMapping("/importData")
    @Operation(summary = "导入用户数据", description = "导入用户数据，支持覆盖更新")
    public Result<String> importData(@Parameter(description = "上传文件") MultipartFile file, @Parameter(description = "是否覆盖更新") boolean updateSupport) throws Exception
    {
        ExcelUtil<SysUser> util = new ExcelUtil<>(SysUser.class);
        List<SysUser> userList = util.importExcel(file.getInputStream());
        String operName = getUsername();
        return Result.success(userService.importUser(userList, updateSupport, operName));
    }

    /**
     * 获取导入模板
     */
    @Operation(summary = "获取导入模板", description = "下载用户导入模板")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<SysUser> util = new ExcelUtil<>(SysUser.class);
        util.importTemplateExcel(response, "用户数据");
    }

    /**
     * 根据用户编号获取详细信息
     */
    @Operation(summary = "根据用户编号获取详细信息", description = "获取用户详细信息，包括角色、岗位等")
    @RequirePermission("system:user:query")
    @GetMapping(value = { "/", "/{userId}" })
    public Result<SysUserDetailVO> getInfo(@Parameter(description = "用户ID") @PathVariable(value = "userId", required = false) Long userId)
    {
        userService.checkUserDataScope(userId);
        SysUserDetailVO vo = new SysUserDetailVO();
        List<SysRole> roles = roleService.selectRoleAll();
        vo.setRoles(roleConverter.toVOList(SysUser.isAdmin(userId) ? roles : roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList())));
        vo.setPosts(postService.selectPostAll()); // Pending SysPostVO
        if (StringUtils.isNotNull(userId))
        {
            SysUser sysUser = userService.selectUserById(userId);
            vo.setUser(userConverter.toVO(sysUser));
            vo.setPostIds(postService.selectPostListByUserId(userId));
            vo.setRoleIds(sysUser.getRoles().stream().map(SysRole::getRoleId).collect(Collectors.toList()));
        }
        return Result.success(vo);
    }

    /**
     * 新增用户
     */
    @Operation(summary = "新增用户", description = "创建新用户")
    @RequirePermission("system:user:add")
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysUserDTO dto)
    {
        SysUser user = userConverter.toEntity(dto);
        deptService.checkDeptDataScope(user.getDeptId());
        roleService.checkRoleDataScope(user.getRoleIds());
        if (!userService.checkUserNameUnique(user))
        {
            return Result.error("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
        {
            return Result.error("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
        {
            return Result.error("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setCreateBy(getUsername());
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        userService.insertUser(user);
        return Result.success();
    }

    /**
     * 修改用户
     */
    @Operation(summary = "修改用户", description = "修改用户信息")
    @RequirePermission("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SysUserDTO dto)
    {
        SysUser user = userConverter.toEntity(dto);
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        deptService.checkDeptDataScope(user.getDeptId());
        roleService.checkRoleDataScope(user.getRoleIds());
        if (!userService.checkUserNameUnique(user))
        {
            return Result.error("修改用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
        {
            return Result.error("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
        {
            return Result.error("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setUpdateBy(getUsername());
        userService.updateUser(user);
        return Result.success();
    }

    /**
     * 删除用户
     */
    @Operation(summary = "删除用户", description = "批量删除用户")
    @RequirePermission("system:user:remove")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userIds}")
    public Result<Void> remove(@Parameter(description = "用户ID数组") @PathVariable Long[] userIds)
    {
        if (ArrayUtils.contains(userIds, getUserId()))
        {
            return Result.error("当前用户不能删除");
        }
        userService.deleteUserByIds(userIds);
        return Result.success();
    }

    /**
     * 重置密码
     */
    @Operation(summary = "重置密码", description = "重置用户密码")
    @RequirePermission("system:user:resetPwd")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/resetPwd")
    public Result<Void> resetPwd(@RequestBody SysUserResetPwdDTO dto)
    {
        SysUser user = userConverter.toEntity(dto);
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        user.setUpdateBy(getUsername());
        userService.resetPwd(user);
        return Result.success();
    }

    /**
     * 状态修改
     */
    @Operation(summary = "状态修改", description = "修改用户状态（正常/停用）")
    @RequirePermission("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public Result<Void> changeStatus(@RequestBody SysUserStatusDTO dto)
    {
        SysUser user = userConverter.toEntity(dto);
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setUpdateBy(getUsername());
        userService.updateUserStatus(user);
        return Result.success();
    }

    /**
     * 根据用户编号获取授权角色
     */
    @Operation(summary = "根据用户编号获取授权角色", description = "获取用户已授权的角色列表")
    @RequirePermission("system:user:query")
    @GetMapping("/authRole/{userId}")
    public Result<UserAuthRoleVO> authRole(@Parameter(description = "用户ID") @PathVariable("userId") Long userId)
    {
        UserAuthRoleVO vo = new UserAuthRoleVO();
        SysUser user = userService.selectUserById(userId);
        List<SysRole> roles = roleService.selectRolesByUserId(userId);
        vo.setUser(userConverter.toVO(user));
        vo.setRoles(roleConverter.toVOList(SysUser.isAdmin(userId) ? roles : roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList())));
        return Result.success(vo);
    }

    /**
     * 用户授权角色
     */
    @Operation(summary = "用户授权角色", description = "为用户分配角色")
    @RequirePermission("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @PutMapping("/authRole")
    public Result<Void> insertAuthRole(@RequestBody UserAuthRoleDTO dto)
    {
        userService.checkUserDataScope(dto.getUserId());
        roleService.checkRoleDataScope(dto.getRoleIds());
        userService.insertUserAuth(dto.getUserId(), dto.getRoleIds());
        return Result.success();
    }

    /**
     * 获取部门树列表
     */
    @Operation(summary = "获取部门树列表", description = "获取部门树结构列表")
    @RequirePermission("system:user:list")
    @GetMapping("/deptTree")
    public Result<List<TreeSelect>> deptTree(@Parameter(description = "部门查询参数") SysDept dept)
    {
        return Result.success(deptService.selectDeptTreeList(dept));
    }
}
