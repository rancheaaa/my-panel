package com.cq.panel.admin.server.web.domain.vo.system;

import lombok.Data;
import lombok.AllArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "登录返回信息")
public class LoginVO {
    @Schema(description = "访问令牌", example = "eyJhbGciOiJIUzUxMiJ9.eyJsb2dpbl91c2VyX2tleSI6IjU4MjJhMTA2LTQ0YzctNDMyZS04MzY3LTczZDI3NDU3Nzg4OCJ9.ExampleToken")
    private String token;

    public LoginVO(String token) {
        this.token = token;
    }
}

