package com.cq.panel.admin.server.web.domain.vo.batch;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Schema(description = "批量传输仪表盘VO")
public class BatchDashboardVO
{
    @Schema(description = "概览数据")
    private Overview overview;

    @Schema(description = "Agent健康网格")
    private List<AgentHealthGridItem> agentHealthGrid;

    @Schema(description = "活跃任务摘要")
    private List<ActiveTaskSummary> activeTasksSummary;

    @Schema(description = "最近告警列表")
    private List<BatchAlertEventVO> recentAlerts;

    @Data
    @Schema(description = "仪表盘概览")
    public static class Overview
    {
        @Schema(description = "Agent概览")
        private AgentOverview agents;

        @Schema(description = "任务概览")
        private TaskOverview tasks;

        @Schema(description = "性能概览")
        private PerformanceOverview performance;
    }

    @Data
    @Schema(description = "Agent概览")
    public static class AgentOverview
    {
        @Schema(description = "已注册Agent总数")
        private Integer totalRegistered;

        @Schema(description = "在线Agent数")
        private Integer online;

        @Schema(description = "离线Agent数")
        private Integer offline;

        @Schema(description = "繁忙Agent数(队列深度>100)")
        private Integer busy;
    }

    @Data
    @Schema(description = "任务概览")
    public static class TaskOverview
    {
        @Schema(description = "活跃任务数")
        private Integer active;

        @Schema(description = "暂停任务数")
        private Integer paused;

        @Schema(description = "今日完成数")
        private Integer completedToday;

        @Schema(description = "今日失败数")
        private Integer failedToday;
    }

    @Data
    @Schema(description = "性能概览")
    public static class PerformanceOverview
    {
        @Schema(description = "全局吞吐量(MB/s)")
        private BigDecimal globalThroughputMBps;

        @Schema(description = "今日传输总量(GB)")
        private BigDecimal todayTransferredGB;

        @Schema(description = "平均任务耗时(分钟)")
        private BigDecimal avgTaskDurationMin;
    }

    @Data
    @Schema(description = "Agent健康网格项")
    public static class AgentHealthGridItem
    {
        @Schema(description = "Agent ID")
        private String agentId;

        @Schema(description = "Agent名称")
        private String agentName;

        @Schema(description = "在线状态: ONLINE/OFFLINE")
        private String onlineStatus;

        @Schema(description = "发送队列状态")
        private QueueStatusInfo sendQueueStatus;

        @Schema(description = "重试队列状态")
        private QueueStatusInfo retryQueueStatus;

        @Schema(description = "整体健康状态: HEALTHY/WARNING/CRITICAL")
        private String overallHealth;

        @Schema(description = "活跃任务数")
        private Integer activeTaskCount;
    }

    @Data
    @Schema(description = "队列状态信息")
    public static class QueueStatusInfo
    {
        @Schema(description = "当前深度")
        private Integer depth;

        @Schema(description = "容量上限")
        private Integer capacity;

        @Schema(description = "使用率(%)")
        private BigDecimal utilizationPct;
    }

    @Data
    @Schema(description = "活跃任务摘要")
    public static class ActiveTaskSummary
    {
        @Schema(description = "任务ID")
        private Long taskId;

        @Schema(description = "任务名称")
        private String taskName;

        @Schema(description = "任务状态")
        private String status;

        @Schema(description = "进度百分比")
        private BigDecimal progressPercent;

        @Schema(description = "源Agent ID")
        private String sourceAgentId;

        @Schema(description = "当前速率(MB/s)")
        private BigDecimal currentSpeedMBps;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @Schema(description = "开始时间")
        private Date startedAt;
    }
}
