package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 配置中心查询 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "配置中心查询DTO")
public class RcConfigQueryDTO {
    @Schema(description = "环境ID")
    private Long envId;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "导出格式 (excel, txt, properties, yml, yaml, json)")
    private String exportFormat;
}
