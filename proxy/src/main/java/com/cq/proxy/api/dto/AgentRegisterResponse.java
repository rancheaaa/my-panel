package com.cq.proxy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent注册响应 DTO
 * 
 * @author cq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent注册响应DTO")
public class AgentRegisterResponse {
    
    @Schema(description = "节点ID")
    private String id;

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

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private String createTime;

    @Schema(description = "更新时间")
    private String updateTime;
}