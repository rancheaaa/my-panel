package com.cq.panel.admin.server.repository.domain;

import com.cq.panel.admin.server.common.annotation.Excel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

/**
 * 定时任务调度日志表 sys_job_log
 * 
 * @author cq
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class SysJobLog extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** ID */
    @Excel(name = "日志序号")
    private Long jobLogId;

    /** 任务名称 */
    @Excel(name = "任务名称")
    private String jobName;

    /** 任务组名 */
    @Excel(name = "任务组名")
    private String jobGroup;

    /** 调用目标字符串 */
    @Excel(name = "调用目标字符串")
    private String invokeTarget;

    /** 日志信息 */
    @Excel(name = "日志信息")
    private String jobMessage;

    /** 触发类型（0定时触发 1手动触发） */
    @Excel(name = "触发类型", readConverterExp = "0=定时触发,1=手动触发")
    private String triggerType;

    /** 执行状态（0正常 1失败） */
    @Excel(name = "执行状态", readConverterExp = "0=正常,1=失败")
    private String status;

    /** 异常信息 */
    @Excel(name = "异常信息")
    private String exceptionInfo;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间 */
    private Date endTime;

    /** 开始时间起（用于查询） */
    private transient Date startTimeStart;

    /** 开始时间止（用于查询） */
    private transient Date startTimeEnd;

    /** 结束时间起（用于查询） */
    private transient Date endTimeStart;

    /** 结束时间止（用于查询） */
    private transient Date endTimeEnd;
}