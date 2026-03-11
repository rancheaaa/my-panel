package com.cq.panel.admin.server.web.domain.vo.tool;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 测试用户视图对象
 * 
 * @author cq
 */
@Data
@Schema(description = "测试用户视图对象")
public class TestUserVO {
    @Schema(description = "用户ID", example = "1")
    private Integer userId;

    @Schema(description = "用户名称", example = "admin")
    private String username;

    @Schema(description = "用户密码", example = "admin123")
    private String password;

    @Schema(description = "用户手机", example = "15888888888")
    private String mobile;

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }
}

