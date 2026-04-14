package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户和岗位关联 sys_user_post
 * 
 * @author cq
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SysUserPost
{
    /** 用户ID */
    private Long userId;
    
    /** 岗位ID */
    private Long postId;
}
