package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
@Schema(description = "用户重置密码对象")
public class SysUserResetPwdDTO {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotBlank(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotBlank(message = "新密码不能为空")
    private String password;
}
