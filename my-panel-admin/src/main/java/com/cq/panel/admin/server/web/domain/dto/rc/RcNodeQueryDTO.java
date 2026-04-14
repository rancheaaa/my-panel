package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 注册中心节点查询 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "注册中心节点查询DTO")
public class RcNodeQueryDTO {
    @Schema(description = "环境ID")
    private Long envId;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "节点IP")
    private String nodeIp;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "服务所在区域")
    private String zone;
}