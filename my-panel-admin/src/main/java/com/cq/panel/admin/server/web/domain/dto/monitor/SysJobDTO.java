package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.cq.panel.admin.server.common.constant.ScheduleConstants;

@Data
@Schema(description = "定时任务传输对象")
public class SysJobDTO {

    @Schema(description = "任务序号", example = "1")
    private Long jobId;

    @Schema(description = "任务名称", example = "系统默认定时任务")
    @NotBlank(message = "任务名称不能为空")
    @Size(min = 0, max = 64, message = "任务名称不能超过64个字符")
    private String jobName;

    @Schema(description = "任务组名", example = "DEFAULT")
    private String jobGroup;

    @Schema(description = "调用目标字符串", example = "ryTask.ryParams('ry')")
    @NotBlank(message = "调用目标字符串不能为空")
    @Size(min = 0, max = 500, message = "调用目标字符串长度不能超过500个字符")
    private String invokeTarget;

    @Schema(description = "Cron执行表达式", example = "0/10 * * * * ?")
    @NotBlank(message = "Cron执行表达式不能为空")
    @Size(min = 0, max = 255, message = "Cron执行表达式不能超过255个字符")
    private String cronExpression;

    @Schema(description = "计划策略", example = "1")
    private String misfirePolicy = ScheduleConstants.MISFIRE_DEFAULT;

    @Schema(description = "并发执行", example = "0")
    private String concurrent;

    @Schema(description = "任务状态", example = "0")
    private String status;
}

