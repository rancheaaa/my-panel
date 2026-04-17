package com.cq.panel.admin.server.web.domain.vo.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "Agent命令执行历史VO")
public class AgentCommandHistoryVO {
    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "Agent节点ID")
    private String agentId;

    @Schema(description = "Agent节点名称")
    private String agentName;

    @Schema(description = "Agent IP地址")
    private String agentIp;

    @Schema(description = "Agent端口")
    private Integer agentPort;

    @Schema(description = "执行的命令内容")
    private String command;

    @Schema(description = "命令执行状态（0-成功 1-失败 2-超时 3-未知）")
    private Integer commandStatus;

    @Schema(description = "进程退出码")
    private Integer exitCode;

    @Schema(description = "标准输出内容")
    private String output;

    @Schema(description = "错误输出内容")
    private String error;

    @Schema(description = "执行耗时（毫秒）")
    private Long executeTime;

    @Schema(description = "命令提交时间")
    private Date submitTime;

    @Schema(description = "命令开始执行时间")
    private Date startTime;

    @Schema(description = "命令完成时间")
    private Date endTime;

    @Schema(description = "操作用户名")
    private String userName;
}
