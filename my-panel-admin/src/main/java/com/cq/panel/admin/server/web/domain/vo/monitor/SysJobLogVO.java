package com.cq.panel.admin.server.web.domain.vo.monitor;

import com.cq.panel.admin.server.common.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "定时任务调度日志视图对象")
public class SysJobLogVO {

    @Schema(description = "日志序号")
    @Excel(name = "日志序号")
    private Long jobLogId;

    @Schema(description = "任务名称")
    @Excel(name = "任务名称")
    private String jobName;

    @Schema(description = "任务组名")
    @Excel(name = "任务组名")
    private String jobGroup;

    @Schema(description = "调用目标字符串")
    @Excel(name = "调用目标字符串")
    private String invokeTarget;

    @Schema(description = "日志信息")
    @Excel(name = "日志信息")
    private String jobMessage;

    @Schema(description = "触发类型")
    @Excel(name = "触发类型", readConverterExp = "0=定时触发,1=手动触发")
    private String triggerType;

    @Schema(description = "执行状态")
    @Excel(name = "执行状态", readConverterExp = "0=正常,1=失败")
    private String status;

    @Schema(description = "异常信息")
    @Excel(name = "异常信息")
    private String exceptionInfo;

    @Schema(description = "开始时间")
    @Excel(name = "开始时间")
    private String startTime;

    @Schema(description = "结束时间")
    @Excel(name = "结束时间")
    private String endTime;
}