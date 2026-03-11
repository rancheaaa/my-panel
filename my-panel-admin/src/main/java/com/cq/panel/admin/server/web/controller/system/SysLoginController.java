package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.repository.domain.SysMenu;
import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.web.domain.dto.system.LoginDTO;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import com.cq.panel.admin.server.repository.service.ISysMenuService;
import com.cq.panel.admin.server.web.service.SysLoginService;
import com.cq.panel.admin.server.web.service.SysPermissionService;
import com.cq.panel.admin.server.web.domain.vo.system.LoginVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.system.UserInfoVO;
import com.cq.panel.admin.server.web.domain.vo.system.RouterVo;
import com.cq.panel.admin.server.web.converter.system.SysUserConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Set;

/**
 * 登录验证
 * 
 * @author cq
 */
@Tag(name = "登录验证")
@RestController
public class SysLoginController
{
    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private SysUserConverter userConverter;

    /**
     * 登录方法
     * 
     * @param loginDTO 登录信息
     * @return 结果
     */
    @Operation(summary = "登录方法")
    @PostMapping("/login")
    public Result<LoginVO> login(@Validated @RequestBody LoginDTO loginDTO)
    {
        // 生成令牌
        String token = loginService.login(loginDTO.getUsername(), loginDTO.getPassword(), loginDTO.getCode(),
                loginDTO.getUuid());
        return Result.success(new LoginVO(token));
    }

    /**
     * 获取用户信息
     * 
     * @return 用户信息
     */
    @Operation(summary = "获取用户信息")
    @GetMapping("getInfo")
    public Result<UserInfoVO> getInfo()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(user);
        UserInfoVO vo = new UserInfoVO();
        vo.setUser(userConverter.toVO(user));
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        return Result.success(vo);
    }

    /**
     * 获取路由信息
     * 
     * @return 路由信息
     */
    @Operation(summary = "获取路由信息")
    @GetMapping("getRouters")
    public Result<List<RouterVo>> getRouters()
    {
        Long userId = SecurityUtils.getUserId();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return Result.success(menuService.buildMenus(menus));
    }
}






