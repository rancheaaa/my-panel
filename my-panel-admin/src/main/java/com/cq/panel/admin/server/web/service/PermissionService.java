package com.cq.panel.admin.server.web.service;

import java.util.Set;
import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.repository.domain.SysRole;
import com.cq.panel.admin.server.web.domain.model.LoginUser;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.context.PermissionContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;


/**
 * cq首创 自定义权限实现，ss取自SpringSecurity首字母
 * 
 * @author cq
 */
@Service("ss")
public class PermissionService
{
    /**
     * 验证用户是否具备某权限
     * 
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    public boolean hasPermission(String permission)
    {
        if (MyStringUtils.isEmpty(permission))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (CollectionUtils.isEmpty(loginUser.getPermissions()))
        {
            return false;
        }
        PermissionContextHolder.setContext(permission);
        return hasPermissions(loginUser.getPermissions(), permission);
    }

    /**
     * 验证用户是否不具备某权限，与 hasPermission逻辑相反
     *
     * @param permission 权限字符串
     * @return 用户是否不具备某权限
     */
    public boolean lacksPermission(String permission)
    {
        return !hasPermission(permission);
    }

    /**
     * 验证用户是否具有以下任意一个权限
     *
     * @param permissions 以 PERMISSION_DELIMITER 为分隔符的权限列表
     * @return 用户是否具有以下任意一个权限
     */
    public boolean hasAnyPermission(String permissions)
    {
        if (MyStringUtils.isEmpty(permissions))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (MyStringUtils.isNull(loginUser) || CollectionUtils.isEmpty(loginUser.getPermissions()))
        {
            return false;
        }
        PermissionContextHolder.setContext(permissions);
        Set<String> authorities = loginUser.getPermissions();
        for (String permission : permissions.split(Constants.PERMISSION_DELIMITER))
        {
            if (hasPermissions(authorities, permission))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断用户是否拥有某个角色
     * 
     * @param role 角色字符串
     * @return 用户是否具备某角色
     */
    public boolean hasRole(String role)
    {
        if (MyStringUtils.isEmpty(role))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (MyStringUtils.isNull(loginUser) || CollectionUtils.isEmpty(loginUser.getUser().getRoles()))
        {
            return false;
        }
        for (SysRole sysRole : loginUser.getUser().getRoles())
        {
            String roleKey = sysRole.getRoleKey();
            if (Constants.SUPER_ADMIN.equals(roleKey) || roleKey.equals(MyStringUtils.trim(role)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证用户是否不具备某角色，与 isRole逻辑相反。
     *
     * @param role 角色名称
     * @return 用户是否不具备某角色
     */
    public boolean lacksRole(String role)
    {
        return !hasRole(role);
    }

    /**
     * 验证用户是否具有以下任意一个角色
     *
     * @param roles 以 ROLE_NAMES_DELIMETER 为分隔符的角色列表
     * @return 用户是否具有以下任意一个角色
     */
    public boolean hasAnyRoles(String roles)
    {
        if (MyStringUtils.isEmpty(roles))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (MyStringUtils.isNull(loginUser) || CollectionUtils.isEmpty(loginUser.getUser().getRoles()))
        {
            return false;
        }
        for (String role : roles.split(Constants.ROLE_DELIMITER))
        {
            if (hasRole(role))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否包含权限
     * 
     * @param permissions 权限列表
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    private boolean hasPermissions(Set<String> permissions, String permission)
    {
        return permissions.contains(Constants.ALL_PERMISSION) || permissions.contains(MyStringUtils.trim(permission));
    }
}
