package com.cq.panel.admin.server.web.domain.vo.batch;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Schema(description = "批量传输任务详情VO")
public class BatchTaskDetailVO
{
    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务描述")
    private String taskDescription;

    @Schema(description = "任务状态码")
    private String status;

    @Schema(description = "任务状态描述")
    private String statusLabel;

    @Schema(description = "源Agent ID")
    private String sourceAgentId;

    @Schema(description = "源目录")
    private String sourceDir;

    @Schema(description = "目标节点目录(分号分隔, 与targetAgents一一对应)")
    private String targetDirs;

    @Schema(description = "是否保持目录结构")
    private Integer preserveDirStructure;

    @Schema(description = "包含通配符(JSON)")
    private String includePatterns;

    @Schema(description = "排除通配符(JSON)")
    private String excludePatterns;

    @Schema(description = "单任务最大带宽(KB/s)")
    private Integer maxBandwidthKbS;

    @Schema(description = "是否启用自动重试")
    private Integer retryEnabled;

    @Schema(description = "重试保留天数")
    private Integer retryMaxDays;

    @Schema(description = "重试间隔(分钟)")
    private Integer retryIntervalMin;

    @Schema(description = "传输后操作")
    private String postTransferAction;

    @Schema(description = "备份目录")
    private String backupDir;

    @Schema(description = "备份模式")
    private String backupMode;

    @Schema(description = "传输模式")
    private String transferMode;

    @Schema(description = "路由策略")
    private String routingStrategy;

    @Schema(description = "路由策略配置JSON")
    private String routingConfig;

    @Schema(description = "待传输文件总数")
    private Integer totalFiles;

    @Schema(description = "待传输总大小(字节)")
    private Long totalSizeBytes;

    @Schema(description = "已完成文件数")
    private Integer transferredFiles;

    @Schema(description = "已传输大小(字节)")
    private Long transferredSizeBytes;

    @Schema(description = "失败文件数")
    private Integer failedFiles;

    @Schema(description = "已后处理文件数")
    private Integer postProcessFiles;

    @Schema(description = "后处理失败文件数")
    private Integer postProcessFailed;

    @Schema(description = "进度百分比")
    private BigDecimal progressPercent;

    @Schema(description = "当前速率(MB/s)")
    private BigDecimal currentSpeedMBps;

    @Schema(description = "预计剩余时间(分钟)")
    private BigDecimal estimatedRemainingMin;

    @Schema(description = "目标Agent级进度列表(三层进度中间层)")
    private List<TargetProgress> targetProgress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间")
    private Date completedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "后处理完成时间")
    private Date postProcessedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "是否可暂停")
    private Boolean canPause;

    @Schema(description = "是否可取消")
    private Boolean canCancel;

    @Schema(description = "是否可修改配置")
    private Boolean canConfig;

    @Schema(description = "是否可删除")
    private Boolean canDelete;

    @Schema(description = "是否可重试")
    private Boolean canRetry;

    @Data
    @Schema(description = "目标Agent级聚合进度")
    public static class TargetProgress
    {
        @Schema(description = "目标Agent ID")
        private String targetAgentId;

        @Schema(description = "目标Agent名称")
        private String targetAgentName;

        @Schema(description = "该目标的总子任务数")
        private Integer totalSubtasks;

        @Schema(description = "该目标已完成子任务数")
        private Integer completedSubtasks;

        @Schema(description = "该目标失败子任务数")
        private Integer failedSubtasks;

        @Schema(description = "该目标进度百分比")
        private BigDecimal progressPercent;

        @Schema(description = "该目标已传输大小(字节)")
        private Long transferredSizeBytes;

        @Schema(description = "该目标当前速率(MB/s)")
        private BigDecimal currentSpeedMBps;
    }
}
