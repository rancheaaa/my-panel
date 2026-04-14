package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色和部门关联 sys_role_dept
 * 
 * @author cq
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class SysRoleDept
{
    /** 角色ID */
    private Long roleId;
    
    /** 部门ID */
    private Long deptId;
}
