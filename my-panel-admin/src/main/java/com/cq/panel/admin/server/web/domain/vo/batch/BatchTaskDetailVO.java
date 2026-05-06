package com.cq.panel.admin.server.web.domain.vo.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferStatistics;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Schema(description = "批量传输任务详情VO")
public class BatchTaskDetailVO {
    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务描述")
    private String taskDescription;

    @Schema(description = "任务状态码 (DRAFT/RUNNING/PAUSED/STOPPED)")
    private String status;

    @Schema(description = "任务状态描述")
    private String statusLabel;

    @Schema(description = "源Agent ID")
    private String sourceAgentId;

    @Schema(description = "源目录")
    private String sourceDir;

    @Schema(description = "目标Agent ID列表(JSON格式)")
    private String targetAgents;

    @Schema(description = "目标目录(分号分隔)")
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

    @Schema(description = "扫描到的文件总数")
    private Integer totalFiles;

    @Schema(description = "扫描间隔(秒)")
    private Integer scanFrequencySec;

    @Schema(description = "单次最大扫描文件数")
    private Integer maxScanFiles;

    @Schema(description = "定时扫描Cron表达式")
    private String scanCronExpression;

    @Schema(description = "扫描到的总大小(字节)")
    private Long totalSizeBytes;

    @Schema(description = "子任务统计信息(实时计算)")
    private SubtaskSummary subtaskSummary;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "传输统计信息(来自统计表)")
    private BatchTransferStatistics statistics;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始运行时间")
    private Date startedAt;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "是否可启动")
    private Boolean canStart;

    @Schema(description = "是否可暂停")
    private Boolean canPause;

    @Schema(description = "是否可恢复")
    private Boolean canResume;

    @Schema(description = "是否可删除")
    private Boolean canDelete;

    @Schema(description = "是否可修改配置")
    private Boolean canConfig;

    @Data
    @Schema(description = "子任务聚合统计")
    public static class SubtaskSummary {
        @Schema(description = "总子任务数")
        private Integer totalSubtasks;

        @Schema(description = "已完成数")
        private Integer completedCount;

        @Schema(description = "失败数")
        private Integer failedCount;

        @Schema(description = "运行中数")
        private Integer runningCount;

        @Schema(description = "排队中数")
        private Integer queuedCount;

        @Schema(description = "重试中数")
        private Integer retryingCount;

        @Schema(description = "已取消数")
        private Integer cancelledCount;

        @Schema(description = "整体进度百分比(0-100)")
        private BigDecimal progressPercent;

        @Schema(description = "总大小(字节)")
        private Long totalSizeBytes;

        @Schema(description = "已传输大小(字节)")
        private Long transferredSizeBytes;
    }
}
