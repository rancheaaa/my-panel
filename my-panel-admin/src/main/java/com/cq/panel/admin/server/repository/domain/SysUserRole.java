package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户和角色关联 sys_user_role
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SysUserRole
{
    /** 用户ID */
    private Long userId;
    
    /** 角色ID */
    private Long roleId;
}
