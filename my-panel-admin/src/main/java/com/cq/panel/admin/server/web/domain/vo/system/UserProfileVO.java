package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户个人信息视图对象")
public class UserProfileVO {
    @Schema(description = "用户信息")
    private SysUserVO user;

    @Schema(description = "用户所属角色组")
    private String roleGroup;

    @Schema(description = "用户所属岗位组")
    private String postGroup;

    public void setUser(SysUserVO user) {
        this.user = user;
    }

    public void setRoleGroup(String roleGroup) {
        this.roleGroup = roleGroup;
    }

    public void setPostGroup(String postGroup) {
        this.postGroup = postGroup;
    }
}

