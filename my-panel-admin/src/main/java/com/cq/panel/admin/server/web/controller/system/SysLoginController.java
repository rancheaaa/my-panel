package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.repository.domain.SysMenu;
import com.cq.panel.admin.server.repository.domain.SysUser;
import com.cq.panel.admin.server.web.domain.dto.system.LoginDTO;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import com.cq.panel.admin.server.repository.service.ISysMenuService;
import com.cq.panel.admin.server.web.service.SysLoginService;
import com.cq.panel.admin.server.web.service.SysPermissionService;
import com.cq.panel.admin.server.web.service.TokenService;
import com.cq.panel.admin.server.web.domain.vo.system.LoginVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.system.UserInfoVO;
import com.cq.panel.admin.server.web.domain.vo.system.RouterVo;
import com.cq.panel.admin.server.web.converter.system.SysUserConverter;
import com.cq.panel.admin.server.repository.service.ISysUserService;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.web.exception.ServiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
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
    private final SysLoginService loginService;

    private final ISysMenuService menuService;

    private final SysPermissionService permissionService;

    private final SysUserConverter userConverter;

    private final TokenService tokenService;

    private final ISysUserService userService;

    public SysLoginController(SysLoginService loginService, ISysMenuService menuService, SysPermissionService permissionService, SysUserConverter userConverter, TokenService tokenService, ISysUserService userService) {
        this.loginService = loginService;
        this.menuService = menuService;
        this.permissionService = permissionService;
        this.userConverter = userConverter;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @Operation(summary = "获取密码盐值")
    @GetMapping("/getSalt")
    public Result<String> getSalt(String username) {
        if (StringUtils.isEmpty(username)) {
            throw new ServiceException("用户名不能为空");
        }
        SysUser user = userService.selectUserByUserName(username);
        if (user == null || user.getSalt() == null) {
            return Result.success("用户不存在或密码盐值为空", null);
        }
        return Result.success("操作成功", user.getSalt());
    }

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

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request)
    {
        var loginUser = tokenService.getLoginUser(request);
        if (loginUser != null)
        {
            tokenService.delLoginUser(loginUser.getToken());
        }
        return Result.success("退出成功", null);
    }
}






