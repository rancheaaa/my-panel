package com.cq.panel.admin.server.web.domain.vo.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

/**
 * Agent注册信息 VO
 * 
 * @author cq
 */
@Data
@Schema(description = "Agent注册信息VO")
public class AgentRegistryVO {
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

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    private Date updateTime;
}