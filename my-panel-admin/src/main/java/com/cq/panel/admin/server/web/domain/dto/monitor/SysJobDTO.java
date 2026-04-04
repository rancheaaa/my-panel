package com.cq.panel.admin.server.web.domain.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.cq.panel.admin.server.common.constant.ScheduleConstants;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;

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
    private String invokeTarget;

    // 任务类型，1-内置方法 2-HTTP接口
    @Schema(description = "任务类型", example = "1")
    @NotNull(message = "任务类型不能为空")
    private Integer jobType;

    @Schema(description = "内置方法全限定名", example = "com.cq.panel.admin.server.task.AppTask.backupSystemAllTable")
    @NotBlank(message = "内置方法不能为空", groups = SysJobGroup.InternalGroup.class)
    @Size(max = 255, message = "内置方法长度不能超过255个字符")
    @Null(message = "HTTP接口模式下内置方法必须为空", groups = SysJobGroup.HttpGroup.class)
    private String methodName;

    @Schema(description = "HTTP接口URL", example = "http://localhost:8080/api/test")
    @NotBlank(message = "HTTP接口URL不能为空", groups = SysJobGroup.HttpGroup.class)
    @Size(max = 500, message = "HTTP接口URL长度不能超过500个字符")
    @Pattern(regexp = "^(http|https)://.*", message = "URL必须以http或https开头", groups = SysJobGroup.HttpGroup.class)
    @Null(message = "内置方法模式下HTTP接口URL必须为空", groups = SysJobGroup.InternalGroup.class)
    private String httpUrl;

    @Schema(description = "HTTP请求方式", example = "POST")
    @NotBlank(message = "HTTP请求方式不能为空", groups = SysJobGroup.HttpGroup.class)
    @Pattern(regexp = "^(GET|POST|PUT|DELETE)$", message = "请求方式只能是GET/POST/PUT/DELETE", groups = SysJobGroup.HttpGroup.class)
    private String httpMethod;

    @Schema(description = "HTTP请求头(JSON)", example = "{\"Content-Type\": \"application/json\"}")
    private String httpHeaders;

    @Schema(description = "HTTP请求体", example = "{\"id\": \"{jobId}\"}")
    private String httpBody;

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

    @Schema(description = "备注", example = "备注信息")
    @Size(min = 0, max = 500, message = "备注不能超过500个字符")
    private String remark;
}

