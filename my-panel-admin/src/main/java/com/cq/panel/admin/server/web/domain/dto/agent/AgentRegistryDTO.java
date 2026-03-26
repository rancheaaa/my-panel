package com.cq.panel.admin.server.web.domain.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Agent注册信息 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "Agent注册信息DTO")
public class AgentRegistryDTO {
    @Schema(description = "节点ID")
    private String id;

    @Schema(description = "节点名称")
    @NotBlank(message = "节点名称不能为空")
    private String nodeName;

    @Schema(description = "操作系统")
    private String osType;

    @Schema(description = "应用ID")
    private String appId;

    @Schema(description = "Agent IP")
    @NotBlank(message = "Agent IP不能为空")
    private String agentIp;

    @Schema(description = "Agent端口")
    @NotNull(message = "Agent端口不能为空")
    private Integer agentPort;

    @Schema(description = "节点是否启用")
    private Integer nodeEnabled;

    @Schema(description = "节点状态")
    private Integer nodeStatus;

    @Schema(description = "备注")
    private String remark;
}