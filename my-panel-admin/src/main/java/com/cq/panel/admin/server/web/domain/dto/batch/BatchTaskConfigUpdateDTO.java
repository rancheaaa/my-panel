package com.cq.panel.admin.server.web.domain.dto.batch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "批量传输任务配置更新DTO")
public class BatchTaskConfigUpdateDTO
{
    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务描述")
    private String taskDescription;

    @Schema(description = "源Agent ID")
    private String sourceAgentId;

    @Schema(description = "源目录")
    private String sourceDir;

    @Schema(description = "目标Agent ID列表")
    private List<String> targetAgents;

    @Schema(description = "目标节点目录(分号分隔)")
    private String targetDirs;

    @Schema(description = "包含模式")
    private List<String> includePatterns;

    @Schema(description = "排除模式")
    private List<String> excludePatterns;

    @Schema(description = "传输模式")
    private String transferMode;

    @Schema(description = "路由策略")
    private String routingStrategy;

    @Schema(description = "区域路由配置")
    private String routingConfig;

    @Schema(description = "传输后操作")
    private String postTransferAction;

    @Schema(description = "备份目录")
    private String backupDir;

    @Schema(description = "备份模式")
    private String backupMode;

    @Schema(description = "是否保持目录结构")
    private Boolean preserveDirStructure;

    @Min(1)
    @Max(30)
    @Schema(description = "重试保留天数")
    private Integer retryMaxDays;

    @Min(60)
    @Max(86400)
    @Schema(description = "扫描间隔(秒)")
    private Integer scanFrequencySec;

    @Min(100)
    @Max(100000)
    @Schema(description = "单次最大扫描文件数")
    private Integer maxScanFiles;

    @Min(1)
    @Schema(description = "单任务最大带宽(KB/s)")
    private Integer maxBandwidthKbS;

    @Schema(description = "是否启用自动重试")
    private Boolean retryEnabled;

    @Min(5)
    @Max(1440)
    @Schema(description = "重试间隔(分钟)")
    private Integer retryIntervalMin;
}
