package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Set;

/**
 * 角色表 sys_role
 * 
 * @author cq
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class SysRole extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色ID */
    @Excel(name = "角色序号", cellType = Excel.ColumnType.NUMERIC)
    private Long roleId;

    /** 角色名称 */
    @Excel(name = "角色名称")
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 30, message = "角色名称长度不能超过30个字符")
    private String roleName;

    /** 角色权限 */
    @Excel(name = "角色权限")
    @NotBlank(message = "权限字符不能为空")
    @Size(max = 100, message = "权限字符长度不能超过100个字符")
    private String roleKey;

    /** 角色排序 */
    @Excel(name = "角色排序")
    @NotNull(message = "显示顺序不能为空")
    private Integer roleSort;

    /** 数据范围（1：所有数据权限；2：自定义数据权限；3：本部门数据权限；4：本部门及以下数据权限；5：仅本人数据权限） */
    @Excel(name = "数据范围", readConverterExp = "1=所有数据权限,2=自定义数据权限,3=本部门数据权限,4=本部门及以下数据权限,5=仅本人数据权限")
    private String dataScope;

    /** 菜单树选择项是否关联显示（ 0：父子不互相关联显示 1：父子互相关联显示） */
    private boolean menuCheckStrictly;

    /** 部门树选择项是否关联显示（0：父子不互相关联显示 1：父子互相关联显示 ） */
    private boolean deptCheckStrictly;

    /** 角色状态（0正常 1停用） */
    @Excel(name = "角色状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 用户是否存在此角色标识 默认不存在 */
    private boolean flag = false;

    /** 菜单组 */
    private Long[] menuIds;

    /** 部门组（数据权限） */
    private Long[] deptIds;

    /** 角色菜单权限 */
    private Set<String> permissions;

    /**
     * 是否为超级管理员角色
     * @return 是否为超级管理员角色
     */
    public boolean isAdmin() {
        return isAdmin(this.roleId);
    }

    /**
     * 判断角色ID是否为超级管理员
     * @param roleId 角色ID
     * @return 结果
     */
    public static boolean isAdmin(Long roleId) {
        return roleId != null && 1L == roleId;
    }

    /**
     * 带角色ID的构造器
     * @param roleId 角色ID
     */
    public SysRole(Long roleId) {
        this.roleId = roleId;
    }

    /**
     * 默认构造器
     */
    public SysRole() {
    }
}