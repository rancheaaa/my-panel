package com.cq.panel.admin.server.web.domain.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.cq.panel.admin.server.repository.domain.SysUser;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 登录用户身份权限
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class LoginUser implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 用户唯一标识
     */
    private String token;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 登录IP地址
     */
    private String ipaddr;

    /**
     * 登录地点
     */
    private String loginLocation;

    /**
     * 浏览器类型
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 权限列表
     */
    private Set<String> permissions;

    /**
     * 用户信息
     */
    private SysUser user;

    @JSONField(serialize = false)
    public String getPassword()
    {
        return user.getPassword();
    }

    public String getUsername()
    {
        return user.getUserName();
    }

    /**
     * 默认构造器
     */
    public LoginUser() {
    }

    /**
     * 带参数的构造器
     * @param userId 用户ID
     * @param deptId 部门ID
     * @param user 用户信息
     * @param permissions 权限列表
     */
    public LoginUser(Long userId, Long deptId, SysUser user, Set<String> permissions) {
        this.userId = userId;
        this.deptId = deptId;
        this.user = user;
        this.permissions = permissions;
    }
}