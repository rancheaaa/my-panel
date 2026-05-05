package com.cq.panel.admin.server.web.domain.dto.batch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "创建批量传输任务DTO")
public class BatchTaskCreateDTO
{
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200)
    @Schema(description = "任务名称")
    private String taskName;

    @Size(max = 500)
    @Schema(description = "任务描述")
    private String taskDescription;

    @NotBlank(message = "源Agent不能为空")
    @Schema(description = "源Agent ID")
    private String sourceAgentId;

    @NotBlank(message = "源目录不能为空")
    @Schema(description = "源目录绝对路径")
    private String sourceDir;

    @NotBlank(message = "目标节点目录不能为空")
    @Schema(description = "目标节点目录(分号分隔, 与targetAgents一一对应, 例: /data/backup;/data/logs)")
    private String targetDirs;

    @Schema(description = "包含通配符列表")
    private List<String> includePatterns;

    @Schema(description = "排除通配符列表")
    private List<String> excludePatterns;

    @Min(60)
    @Max(86400)
    @Schema(description = "扫描间隔(秒), 仅cron为空时生效")
    private Integer scanFrequencySec = 300;

    @Size(max = 100)
    @Schema(description = "定时扫描Cron表达式, 如 \"0 */5 * * * ?\" 表示每5分钟扫描. 为空则使用scanFrequencySec轮询")
    private String scanCronExpression;

    @Min(100)
    @Max(100000)
    @Schema(description = "单次最大扫描文件数")
    private Integer maxScanFiles = 10000;

    @NotEmpty(message = "目标Agent不能为空")
    @Size(max = 20)
    @Schema(description = "目标Agent ID列表")
    private List<String> targetAgents;

    @Min(1)
    @Schema(description = "单任务最大带宽(KB/s)")
    private Integer maxBandwidthKbS;

    @Schema(description = "是否启用自动重试")
    private Boolean retryEnabled = true;

    @Min(1)
    @Max(30)
    @Schema(description = "重试保留天数")
    private Integer retryMaxDays = 7;

    @Min(5)
    @Max(1440)
    @Schema(description = "重试间隔(分钟)")
    private Integer retryIntervalMin = 30;

    @Pattern(regexp = "NONE|DELETE|BACKUP")
    @Schema(description = "传输后操作: NONE/DELETE/BACKUP")
    private String postTransferAction = "NONE";

    @Schema(description = "备份目录绝对路径")
    private String backupDir;

    @Pattern(regexp = "COPY|MOVE")
    @Schema(description = "备份模式: COPY/MOVE")
    private String backupMode = "COPY";

    @Schema(description = "是否保持原始目录结构")
    private Boolean preserveDirStructure = true;

    @Pattern(regexp = "ONE_TO_ONE|ONE_TO_MANY")
    @Schema(description = "传输模式: ONE_TO_ONE/ONE_TO_MANY")
    private String transferMode = "ONE_TO_MANY";

    @Pattern(regexp = "BROADCAST|SINGLE|ROUND_ROBIN|REGION_BASED|RANDOM")
    @Schema(description = "路由策略: BROADCAST/SINGLE/ROUND_ROBIN/REGION_BASED/RANDOM")
    private String routingStrategy = "BROADCAST";

    @Schema(description = "路由策略配置JSON")
    private String routingConfig;
}
