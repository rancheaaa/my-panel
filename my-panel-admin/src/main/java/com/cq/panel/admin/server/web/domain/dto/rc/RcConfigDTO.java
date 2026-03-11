package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 配置中心 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "配置中心DTO")
public class RcConfigDTO {
    @Schema(description = "配置ID")
    private Long id;

    @Schema(description = "环境ID")
    @NotNull(message = "环境不能为空")
    private Long envId;

    @Schema(description = "项目ID")
    @NotNull(message = "项目不能为空")
    private Long projectId;

    @Schema(description = "配置键")
    @NotBlank(message = "配置键不能为空")
    @Size(min = 0, max = 200, message = "配置键长度不能超过200个字符")
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @Schema(description = "配置描述")
    @Size(min = 0, max = 200, message = "配置描述长度不能超过200个字符")
    private String configDesc;
}
