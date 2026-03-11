package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 配置导入 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "配置导入DTO")
public class RcConfigImportDTO {
    @Schema(description = "环境ID")
    @NotNull(message = "环境不能为空")
    private Long envId;

    @Schema(description = "项目ID")
    @NotNull(message = "项目不能为空")
    private Long projectId;

    @Schema(description = "配置内容")
    @NotBlank(message = "配置内容不能为空")
    private String content;

    @Schema(description = "格式 (properties/yaml)")
    private String format;
}
