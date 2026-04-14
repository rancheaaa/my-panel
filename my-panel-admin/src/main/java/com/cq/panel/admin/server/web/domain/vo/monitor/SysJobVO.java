package com.cq.panel.admin.server.web.domain.vo.monitor;

import com.cq.panel.admin.server.annotation.Excel;
import com.fasterxml.jackson.annotation.JsonFormat;
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

    @Schema(description = "任务类型", example = "1")
    @Excel(name = "任务类型", readConverterExp = "1=内置方法,2=HTTP接口")
    private Integer jobType;

    @Schema(description = "内置方法全限定名")
    @Excel(name = "内置方法")
    private String methodName;

    @Schema(description = "HTTP接口URL")
    @Excel(name = "HTTP接口URL")
    private String httpUrl;

    @Schema(description = "HTTP请求方式")
    @Excel(name = "HTTP请求方式")
    private String httpMethod;

    @Schema(description = "HTTP请求头(JSON)")
    @Excel(name = "HTTP请求头")
    private String httpHeaders;

    @Schema(description = "HTTP请求体")
    @Excel(name = "HTTP请求体")
    private String httpBody;

    @Schema(description = "负载均衡策略")
    @Excel(name = "负载均衡策略")
    private String loadBalanceStrategy;

    @Schema(description = "脚本名称")
    @Excel(name = "脚本名称")
    private String scriptName;

    @Schema(description = "脚本类型")
    @Excel(name = "脚本类型", readConverterExp = "python=Python脚本,shell=Shell脚本,cmd=CMD脚本,powershell=PowerShell脚本,sql=SQL脚本")
    private String scriptType;

    @Schema(description = "脚本内容")
    @Excel(name = "脚本内容")
    private String scriptContent;

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

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "更新者")
    private String updateBy;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @Schema(description = "备注")
    private String remark;
}