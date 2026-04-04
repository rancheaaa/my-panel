package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import com.cq.panel.admin.server.common.constant.ScheduleConstants;
import com.cq.panel.admin.server.task.quartz.CronUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;
import com.cq.panel.admin.server.common.utils.StringUtils;

import jakarta.validation.constraints.NotNull;

/**
 * 定时任务调度表 sys_job
 * 
 * @author cq
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class SysJob extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    @Excel(name = "任务序号", cellType = Excel.ColumnType.NUMERIC)
    private Long jobId;

    /** 任务名称 */
    @Excel(name = "任务名称")
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 64, message = "任务名称不能超过64个字符")
    private String jobName;

    /** 任务组名 */
    @Excel(name = "任务组名")
    private String jobGroup;

    /** 调用目标字符串 */
    @Excel(name = "调用目标字符串")
    @NotBlank(message = "调用目标字符串不能为空")
    @Size(max = 1500, message = "调用目标字符串长度不能超过1500个字符")
    private String invokeTarget;

    /** 任务类型（1-内置方法 2-HTTP接口） */
    @Excel(name = "任务类型", readConverterExp = "1=内置方法,2=HTTP接口")
    @NotNull(message = "任务类型不能为空")
    private Integer jobType;

    /** 内置方法全限定名 */
    @Excel(name = "内置方法")
    @Size(max = 255, message = "内置方法全限定名长度不能超过255个字符")
    private String methodName;

    /** HTTP接口URL */
    @Excel(name = "HTTP接口URL")
    @Size(max = 500, message = "HTTP接口URL长度不能超过500个字符")
    private String httpUrl;

    /** HTTP请求方式 */
    @Excel(name = "HTTP请求方式")
    @Size(max = 10, message = "HTTP请求方式长度不能超过10个字符")
    private String httpMethod;

    /** HTTP请求头(JSON) */
    @Excel(name = "HTTP请求头")
    private String httpHeaders;

    /** HTTP请求体 */
    @Excel(name = "HTTP请求体")
    private String httpBody;

    /** 负载均衡策略 */
    @Excel(name = "负载均衡策略")
    private String loadBalanceStrategy;

    /** cron执行表达式 */
    @Excel(name = "执行表达式 ")
    @NotBlank(message = "Cron执行表达式不能为空")
    @Size(max = 255, message = "Cron执行表达式不能超过255个字符")
    private String cronExpression;

    /** cron计划策略 */
    @Excel(name = "计划策略 ", readConverterExp = "0=默认,1=立即触发执行,2=触发一次执行,3=不触发立即执行")
    private String misfirePolicy = ScheduleConstants.MISFIRE_DEFAULT;

    /** 是否并发执行（0允许 1禁止） */
    @Excel(name = "并发执行", readConverterExp = "0=允许,1=禁止")
    private String concurrent;

    /** 任务状态（0正常 1暂停） */
    @Excel(name = "任务状态", readConverterExp = "0=正常,1=暂停")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public Date getNextValidTime()
    {
        if (StringUtils.isNotEmpty(cronExpression))
        {
            return CronUtils.getNextExecution(cronExpression);
        }
        return null;
    }
}