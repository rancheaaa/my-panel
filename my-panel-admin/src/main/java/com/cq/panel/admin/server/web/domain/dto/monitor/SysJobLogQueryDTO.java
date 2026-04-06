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

    @Schema(description = "触发类型")
    private String triggerType;

    @Schema(description = "开始时间起")
    private String startTimeStart;

    @Schema(description = "开始时间止")
    private String startTimeEnd;

    @Schema(description = "结束时间起")
    private String endTimeStart;

    @Schema(description = "结束时间止")
    private String endTimeEnd;
}