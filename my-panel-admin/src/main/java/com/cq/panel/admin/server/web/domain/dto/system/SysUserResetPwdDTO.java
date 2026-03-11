package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "用户重置密码对象")
public class SysUserResetPwdDTO {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    
    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @Size(min = 5, max = 20, message = "密码长度必须在5到20个字符之间")
    private String password;
}
