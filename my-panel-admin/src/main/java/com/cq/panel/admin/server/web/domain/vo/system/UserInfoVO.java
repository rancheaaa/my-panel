package com.cq.panel.admin.server.web.domain.vo.system;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Data
@Schema(description = "用户信息视图对象")
public class UserInfoVO {
    @Schema(description = "用户信息")
    private SysUserVO user;
    
    @Schema(description = "角色集合")
    private Set<String> roles;
    
    @Schema(description = "权限集合")
    private Set<String> permissions;

    public void setUser(SysUserVO user) {
        this.user = user;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }
}

