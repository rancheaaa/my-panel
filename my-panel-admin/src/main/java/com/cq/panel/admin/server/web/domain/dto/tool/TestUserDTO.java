package com.cq.panel.admin.server.web.domain.dto.tool;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 测试用户输入对象
 * 
 * @author cq
 */
@Data
@Schema(description = "测试用户输入对象")
public class TestUserDTO {
    @Schema(description = "用户ID", example = "1")
    private Integer userId;

    @Schema(description = "用户名称", example = "admin")
    private String username;

    @Schema(description = "用户密码", example = "admin123")
    private String password;

    @Schema(description = "用户手机", example = "15888888888")
    private String mobile;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }
}

