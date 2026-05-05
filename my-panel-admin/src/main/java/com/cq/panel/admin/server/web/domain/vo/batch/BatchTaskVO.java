package com.cq.panel.admin.server.web.domain.vo.batch;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "批量传输任务列表VO")
public class BatchTaskVO
{
    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务描述")
    private String taskDescription;

    @Schema(description = "源Agent ID")
    private String sourceAgentId;

    @Schema(description = "源目录")
    private String sourceDir;

    @Schema(description = "目标节点目录(分号分隔, 与targetAgents一一对应)")
    private String targetDirs;

    @Schema(description = "任务状态码 (DRAFT/RUNNING/PAUSED/STOPPED)")
    private String status;

    @Schema(description = "任务状态描述")
    private String statusLabel;

    @Schema(description = "传输模式")
    private String transferMode;

    @Schema(description = "路由策略")
    private String routingStrategy;

    @Schema(description = "传输后操作")
    private String postTransferAction;

    @Schema(description = "扫描到的文件总数")
    private Integer totalFiles;

    @Schema(description = "扫描到的总大小(字节)")
    private Long totalSizeBytes;

    @Schema(description = "子任务总数")
    private Integer totalSubtasks;

    @Schema(description = "已完成子任务数")
    private Integer completedSubtasks;

    @Schema(description = "失败子任务数")
    private Integer failedSubtasks;

    @Schema(description = "运行中子任务数")
    private Integer runningSubtasks;

    @Schema(description = "排队中子任务数")
    private Integer queuedSubtasks;

    @Schema(description = "子任务进度百分比(0-100)")
    private BigDecimal subtaskProgressPercent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后活动时间")
    private Date lastActivityAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private String createBy;
}
