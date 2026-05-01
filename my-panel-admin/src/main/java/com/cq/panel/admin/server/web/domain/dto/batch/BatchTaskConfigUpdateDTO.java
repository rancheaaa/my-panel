package com.cq.panel.admin.server.web.domain.dto.batch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "批量传输任务配置更新DTO")
public class BatchTaskConfigUpdateDTO
{
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
