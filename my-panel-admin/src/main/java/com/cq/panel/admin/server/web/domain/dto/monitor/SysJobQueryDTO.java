package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "定时任务查询对象")
public class SysJobQueryDTO {

    @Schema(description = "任务编号", example = "1")
    private Long jobId;

    @Schema(description = "任务名称", example = "系统默认定时任务")
    private String jobName;

    @Schema(description = "任务组名", example = "DEFAULT")
    private String jobGroup;

    @Schema(description = "任务状态", example = "0")
    private String status;

    // 1-内置方法 2-HTTP接口
    @Schema(description = "任务类型", example = "1")
    private Integer jobType;
}