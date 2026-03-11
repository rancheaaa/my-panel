package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "用户授权角色对象")
public class UserAuthRoleDTO {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "角色ID数组")
    private Long[] roleIds;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long[] getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Long[] roleIds) {
        this.roleIds = roleIds;
    }
}
