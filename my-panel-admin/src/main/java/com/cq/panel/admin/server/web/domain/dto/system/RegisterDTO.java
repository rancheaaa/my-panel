package com.cq.panel.admin.server.web.domain.dto.system;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户注册对象
 */
@Data
@Schema(description = "用户注册对象")
public class RegisterDTO {
    /**
     * 用户名
     */
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "ry")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在2到20个字符之间")
    private String username;

    /**
     * 用户密码
     */
    @Schema(description = "用户密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 5, max = 20, message = "密码长度必须在5到20个字符之间")
    private String password;

    /**
     * 验证码
     */
    @Schema(description = "验证码", example = "8888")
    private String code;

    /**
     * 唯一标识
     */
    @Schema(description = "唯一标识", example = "5822a106-44c7-432e-8367-73d274577888")
    private String uuid;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}

