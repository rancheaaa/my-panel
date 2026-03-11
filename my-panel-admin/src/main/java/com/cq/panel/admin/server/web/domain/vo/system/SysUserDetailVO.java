package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import com.cq.panel.admin.server.repository.domain.SysPost;
import lombok.Data;

import java.util.List;

/**
 * 用户详情信息 VO
 *
 * @author cq-panel
 */
@Data
@Schema(description = "用户详情对象")
public class SysUserDetailVO {
    @Schema(description = "用户信息")
    private SysUserVO user;

    @Schema(description = "角色列表")
    private List<SysRoleVO> roles;

    @Schema(description = "岗位列表")
    private List<SysPost> posts;

    @Schema(description = "岗位ID列表")
    private List<Long> postIds;

    @Schema(description = "角色ID列表")
    private List<Long> roleIds;

    public void setUser(SysUserVO user) {
        this.user = user;
    }

    public void setRoles(List<SysRoleVO> roles) {
        this.roles = roles;
    }

    public void setPosts(List<SysPost> posts) {
        this.posts = posts;
    }

    public void setPostIds(List<Long> postIds) {
        this.postIds = postIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
