package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AccessToken管理查询 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "AccessToken管理查询DTO")
public class RcAccessTokenQueryDTO {
    @Schema(description = "Token值")
    private String tokenValue;

    @Schema(description = "状态（0启用 1禁用）")
    private String status;
}
