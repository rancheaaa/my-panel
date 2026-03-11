package com.cq.panel.admin.server.web.domain.dto.rc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 注册中心节点 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "注册中心节点DTO")
public class RcNodeDTO {
    @Schema(description = "节点ID")
    private Long id;

    @Schema(description = "环境ID")
    @NotNull(message = "环境不能为空")
    private Long envId;

    @Schema(description = "项目ID")
    @NotNull(message = "项目不能为空")
    private Long projectId;

    @Schema(description = "节点IP")
    @NotBlank(message = "节点IP不能为空")
    private String nodeIp;

    @Schema(description = "节点端口")
    @NotNull(message = "节点端口不能为空")
    private Integer nodePort;

    @Schema(description = "状态（0在线 1离线）")
    private String status;
}
