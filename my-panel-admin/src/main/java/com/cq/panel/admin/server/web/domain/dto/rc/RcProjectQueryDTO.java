package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应用管理查询 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "应用管理查询DTO")
public class RcProjectQueryDTO {
    @Schema(description = "应用名称")
    private String projectName;
}
