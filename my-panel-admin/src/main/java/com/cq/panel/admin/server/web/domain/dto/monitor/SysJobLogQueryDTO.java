package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "定时任务日志查询对象")
public class SysJobLogQueryDTO {

    @Schema(description = "任务名称")
    private String jobName;

    @Schema(description = "任务组名")
    private String jobGroup;

    @Schema(description = "执行状态")
    private String status;
}

