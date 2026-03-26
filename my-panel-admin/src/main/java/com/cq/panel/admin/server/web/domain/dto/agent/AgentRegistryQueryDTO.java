package com.cq.panel.admin.server.web.domain.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Agent注册信息查询 DTO
 * 
 * @author cq
 */
@Data
@Schema(description = "Agent注册信息查询DTO")
public class AgentRegistryQueryDTO {
    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "操作系统")
    private String osType;

    @Schema(description = "应用ID")
    private String appId;

    @Schema(description = "Agent IP")
    private String agentIp;

    @Schema(description = "Agent端口")
    private Integer agentPort;

    @Schema(description = "节点是否启用")
    private Integer nodeEnabled;

    @Schema(description = "节点状态")
    private Integer nodeStatus;
}