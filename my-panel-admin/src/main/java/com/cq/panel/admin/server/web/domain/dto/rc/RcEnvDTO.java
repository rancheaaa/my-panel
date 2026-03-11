package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 环境管理 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "环境管理DTO")
public class RcEnvDTO {
    @Schema(description = "环境ID")
    private Long id;

    @Schema(description = "环境名称")
    @NotBlank(message = "环境名称不能为空")
    @Size(min = 0, max = 50, message = "环境名称长度不能超过50个字符")
    private String envName;

    @Schema(description = "环境描述")
    @Size(min = 0, max = 200, message = "环境描述长度不能超过200个字符")
    private String envDesc;
}
