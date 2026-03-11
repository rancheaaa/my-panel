package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 环境管理查询 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "环境管理查询DTO")
public class RcEnvQueryDTO {
    @Schema(description = "环境名称")
    private String envName;
}
