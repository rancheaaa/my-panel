package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色和菜单关联 sys_role_menu
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SysRoleMenu
{
    /** 角色ID */
    private Long roleId;
    
    /** 菜单ID */
    private Long menuId;
}
