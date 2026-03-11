package com.cq.panel.admin.server.web.domain.vo.monitor;

import com.cq.panel.admin.server.common.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "定时任务视图对象")
public class SysJobVO {

    @Schema(description = "任务序号", example = "1")
    @Excel(name = "任务序号", cellType = Excel.ColumnType.NUMERIC)
    private Long jobId;

    @Schema(description = "任务名称", example = "系统默认定时任务")
    @Excel(name = "任务名称")
    private String jobName;

    @Schema(description = "任务组名", example = "DEFAULT")
    @Excel(name = "任务组名")
    private String jobGroup;

    @Schema(description = "调用目标字符串", example = "ryTask.ryParams('ry')")
    @Excel(name = "调用目标字符串")
    private String invokeTarget;

    @Schema(description = "执行表达式", example = "0/10 * * * * ?")
    @Excel(name = "执行表达式")
    private String cronExpression;

    @Schema(description = "计划策略", example = "1")
    @Excel(name = "计划策略", readConverterExp = "0=默认,1=立即触发执行,2=触发一次执行,3=不触发立即执行")
    private String misfirePolicy;

    @Schema(description = "并发执行", example = "0")
    @Excel(name = "并发执行", readConverterExp = "0=允许,1=禁止")
    private String concurrent;

    @Schema(description = "任务状态", example = "0")
    @Excel(name = "任务状态", readConverterExp = "0=正常,1=暂停")
    private String status;
    
    @Schema(description = "下次执行时间")
    private Date nextValidTime;
}

