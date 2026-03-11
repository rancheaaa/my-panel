package com.cq.panel.admin.server.web.domain.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "用户状态修改对象")
public class SysUserStatusDTO {
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "帐号状态（0正常 1停用）", example = "0")
    private String status;
}
