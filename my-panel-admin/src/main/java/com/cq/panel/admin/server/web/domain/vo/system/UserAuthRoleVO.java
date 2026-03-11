package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "用户授权角色视图对象")
public class UserAuthRoleVO {
    @Schema(description = "用户信息")
    private SysUserVO user;

    @Schema(description = "角色列表")
    private List<SysRoleVO> roles;

    public void setUser(SysUserVO user) {
        this.user = user;
    }

    public void setRoles(List<SysRoleVO> roles) {
        this.roles = roles;
    }
}
