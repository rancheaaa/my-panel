package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 应用管理 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "应用管理DTO")
public class RcProjectDTO {
    @Schema(description = "应用ID")
    private Long id;

    @Schema(description = "应用名称")
    @NotBlank(message = "应用名称不能为空")
    @Size(min = 0, max = 100, message = "应用名称长度不能超过100个字符")
    private String projectName;

    @Schema(description = "应用描述")
    @Size(min = 0, max = 200, message = "应用描述长度不能超过200个字符")
    private String projectDesc;
}
