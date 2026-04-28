package com.cq.panel.admin.server.common.utils;

import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.common.utils.spring.SpringUtils;
import com.cq.panel.admin.server.repository.domain.SysJob;
import com.cq.panel.admin.server.repository.domain.SysJobLog;
import com.cq.panel.admin.server.repository.service.ISysJobLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;

/**
 * 定时任务日志工具类
 *
 * @author cq
 */
public class JobLogUtil
{
    private static final Logger logger = LoggerFactory.getLogger(JobLogUtil.class);
    /**
     * 构建任务日志信息
     *
     * @param sysJob 系统任务
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param e 异常信息
     * @param triggerType 触发类型（0定时触发 1手动触发）
     * @return 任务日志信息字符串
     */
    public static String buildJobMessage(SysJob sysJob, Date startTime, Date endTime, Exception e, String triggerType, String selectedUrl)
    {
        long runMs = endTime.getTime() - startTime.getTime();
        
        StringBuilder jobMessage = new StringBuilder();
        jobMessage.append("任务ID：").append(sysJob.getJobId()).append("\n");
        jobMessage.append("任务名称：").append(sysJob.getJobName()).append("\n");
        jobMessage.append("任务分组：").append(sysJob.getJobGroup()).append("\n");
        jobMessage.append("触发类型：").append("0".equals(triggerType) ? "定时触发" : "1".equals(triggerType) ? "手动触发" : "未知触发").append("\n");
        jobMessage.append("任务类型：");
        if (sysJob.getJobType() != null) {
            if (sysJob.getJobType() == 1) {
                jobMessage.append("内置方法\n");
                jobMessage.append("调用方法：").append(sysJob.getMethodName() != null ? sysJob.getMethodName() : "").append("\n");
            } else if (sysJob.getJobType() == 2) {
                jobMessage.append("HTTP接口\n");
                jobMessage.append("接口URL：").append(sysJob.getHttpUrl() != null ? sysJob.getHttpUrl() : "").append("\n");
                jobMessage.append("最终请求ip：").append(selectedUrl != null ? selectedUrl : "").append("\n");
                jobMessage.append("请求方式：").append(sysJob.getHttpMethod() != null ? sysJob.getHttpMethod() : "").append("\n");
                jobMessage.append("负载均衡策略：").append(sysJob.getLoadBalanceStrategy() != null ? sysJob.getLoadBalanceStrategy() : "roundRobin").append("\n");
                jobMessage.append("请求头：").append(sysJob.getHttpHeaders() != null ? sysJob.getHttpHeaders() : "").append("\n");
                jobMessage.append("请求体：").append(sysJob.getHttpBody() != null ? sysJob.getHttpBody() : "").append("\n");
            } else if (sysJob.getJobType() == 3) {
                jobMessage.append("脚本\n");
                jobMessage.append("脚本名称：").append(sysJob.getScriptName() != null ? sysJob.getScriptName() : "").append("\n");
                jobMessage.append("脚本类型：").append(sysJob.getScriptType() != null ? sysJob.getScriptType() : "").append("\n");
                jobMessage.append("脚本内容：").append(sysJob.getScriptContent() != null ? sysJob.getScriptContent() : "").append("\n");
            } else {
                jobMessage.append("未知类型\n");
            }
        } else {
            jobMessage.append("兼容模式\n");
            jobMessage.append("调用目标：").append(sysJob.getInvokeTarget() != null ? sysJob.getInvokeTarget() : "").append("\n");
        }
        jobMessage.append("Cron表达式：").append(sysJob.getCronExpression() != null ? sysJob.getCronExpression() : "").append("\n");
        jobMessage.append("开始时间：").append(MyDateUtils.parseDateToStr(MyDateUtils.YYYY_MM_DD_HH_MM_SS, startTime)).append("\n");
        jobMessage.append("结束时间：").append(MyDateUtils.parseDateToStr(MyDateUtils.YYYY_MM_DD_HH_MM_SS, endTime)).append("\n");
        jobMessage.append("执行耗时：").append(runMs).append("毫秒\n");
        jobMessage.append("执行状态：").append(e == null ? "成功" : "失败");
        
        return jobMessage.toString();
    }

    /**
     * 创建并保存任务日志
     *
     * @param sysJob 系统任务
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param e 异常信息
     * @param triggerType 触发类型（0定时触发 1手动触发）
     * @param selectedUrl 选中的URL（HTTP接口）
     */
    public static void createAndSaveJobLog(SysJob sysJob, Date startTime, Date endTime, Exception e, String triggerType, String selectedUrl)
    {
        String triggerTypeName = "0".equals(triggerType) ? "定时触发" : "1".equals(triggerType) ? "手动触发" : "未知触发";
        logger.info("任务日志 - 任务名称: {}, 触发类型: {}, 开始时间: {}", 
            sysJob.getJobName(), triggerTypeName, MyDateUtils.parseDateToStr(MyDateUtils.YYYY_MM_DD_HH_MM_SS, startTime));
        
        SysJobLog sysJobLog = new SysJobLog();
        sysJobLog.setJobName(sysJob.getJobName());
        sysJobLog.setJobGroup(sysJob.getJobGroup());
        sysJobLog.setInvokeTarget(sysJob.getInvokeTarget());
        sysJobLog.setStartTime(startTime);
        sysJobLog.setEndTime(endTime);
        sysJobLog.setJobMessage(buildJobMessage(sysJob, startTime, endTime, e, triggerType, selectedUrl));
        sysJobLog.setTriggerType(triggerType);
        
        if (e != null)
        {
            sysJobLog.setStatus(Constants.FAIL);
            String errorMsg = MyStringUtils.substring(ExceptionUtil.getExceptionMessage(e), 0, 2000);
            sysJobLog.setExceptionInfo(errorMsg);
            logger.error("任务执行失败 - 任务名称: {}, 触发类型: {}, 异常信息: {}", 
                sysJob.getJobName(), triggerTypeName, errorMsg);
        }
        else
        {
            sysJobLog.setStatus(Constants.SUCCESS);
            logger.info("任务执行成功 - 任务名称: {}, 触发类型: {}, 结束时间: {}", 
                sysJob.getJobName(), triggerTypeName, MyDateUtils.parseDateToStr(MyDateUtils.YYYY_MM_DD_HH_MM_SS, endTime));
        }

        SpringUtils.getBean(ISysJobLogService.class).addJobLog(sysJobLog);
    }

    /**
     * 创建并保存任务日志（自动计算停止时间）
     *
     * @param sysJob 系统任务
     * @param startTime 开始时间
     * @param e 异常信息
     * @param triggerType 触发类型（0定时触发 1手动触发）
     */
    public static void createAndSaveJobLog(SysJob sysJob, Date startTime, Exception e, String triggerType, String selectedUrl)
    {
        createAndSaveJobLog(sysJob, startTime, new Date(), e, triggerType, selectedUrl);
    }
}