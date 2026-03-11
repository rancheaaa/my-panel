package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AccessToken管理 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "AccessToken管理DTO")
public class RcAccessTokenDTO {
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "Token值")
    @NotBlank(message = "Token值不能为空")
    @Size(min = 0, max = 100, message = "Token值长度不能超过100个字符")
    private String tokenValue;

    @Schema(description = "Token描述")
    @Size(min = 0, max = 200, message = "Token描述长度不能超过200个字符")
    private String tokenDesc;

    @Schema(description = "状态（0启用 1禁用）")
    private String status;
}
