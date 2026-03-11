package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "角色添加/修改对象")
public class SysRoleDTO {
    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "管理员")
    @NotBlank(message = "角色名称不能为空")
    @Size(min = 0, max = 30, message = "角色名称长度不能超过30个字符")
    private String roleName;

    @Schema(description = "权限字符", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    @NotBlank(message = "权限字符不能为空")
    @Size(min = 0, max = 100, message = "权限字符长度不能超过100个字符")
    private String roleKey;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "显示顺序不能为空")
    private Integer roleSort;

    @Schema(description = "数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）", example = "1")
    private String dataScope;

    @Schema(description = "菜单树选择项是否关联显示", example = "true")
    private boolean menuCheckStrictly;

    @Schema(description = "部门树选择项是否关联显示", example = "true")
    private boolean deptCheckStrictly;

    @Schema(description = "角色状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "备注", example = "管理员角色")
    private String remark;

    @Schema(description = "菜单组")
    private Long[] menuIds;

    @Schema(description = "部门组")
    private Long[] deptIds;
}
