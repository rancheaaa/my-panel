package com.cq.panel.admin.server.web.domain.vo.batch;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Schema(description = "Agent队列状态VO")
public class AgentQueueStatusVO
{
    @Schema(description = "Agent ID")
    private String agentId;

    @Schema(description = "Agent名称")
    private String agentName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "快照时间")
    private Date snapshotTime;

    @Schema(description = "发送队列深度")
    private Integer sendQueueDepth;

    @Schema(description = "发送队列峰值深度")
    private Integer sendQueuePeakDepth;

    @Schema(description = "发送队列容量上限")
    private Integer sendQueueCapacity;

    @Schema(description = "发送队列使用率(%)")
    private BigDecimal sendQueueUtilizationPct;

    @Schema(description = "发送队列平均等待时间(ms)")
    private Long sendQueueAvgWaitMs;

    @Schema(description = "重试队列深度")
    private Integer retryQueueDepth;

    @Schema(description = "重试队列峰值深度")
    private Integer retryQueuePeakDepth;

    @Schema(description = "重试队列容量上限")
    private Integer retryQueueCapacity;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近一次计划重试时间")
    private Date retryNextScheduleTime;

    @Schema(description = "堵塞等级: NORMAL/WARNING/CRITICAL")
    private String congestionLevel;

    @Schema(description = "堵塞原因")
    private String congestionReason;

    @Schema(description = "是否堵塞")
    private Integer isCongested;

    @Schema(description = "处理速率(文件/秒)")
    private BigDecimal processingRatePerSec;

    @Schema(description = "成功率(百分比, 过去1小时)")
    private BigDecimal successRatePct;

    @Schema(description = "活跃任务数")
    private Integer activeTasks;

    @Schema(description = "错误原因分布JSON")
    private String errorDistribution;

    @Schema(description = "等待时间最长的Top5任务JSON")
    private String longestWaitingTasks;
}
