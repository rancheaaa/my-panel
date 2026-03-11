package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "用户密码修改对象")
public class UserPwdUpdateDTO {
    @Schema(description = "旧密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "12345678")
    @NotBlank(message = "新密码不能为空")
    @Length(min = 5, max = 20, message = "新密码长度必须在5到20个字符之间")
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
