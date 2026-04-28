package com.cq.panel.admin.server.web.domain.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Agent命令执行历史查询参数")
public class AgentCommandHistoryQueryDTO {
    @Schema(description = "Agent节点ID")
    private String agentId;

    @Schema(description = "Agent节点名称")
    private String agentName;

    @Schema(description = "Agent IP地址")
    private String agentIp;

    @Schema(description = "命令执行状态（0-成功 1-失败 2-超时 3-未知）")
    private Integer commandStatus;

    @Schema(description = "操作用户ID")
    private Long userId;

    @Schema(description = "开始时间")
    private String beginTime;

    @Schema(description = "结束时间")
    private String endTime;
}
