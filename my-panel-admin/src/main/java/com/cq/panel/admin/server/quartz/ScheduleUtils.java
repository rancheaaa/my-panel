package com.cq.panel.admin.server.quartz;

import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.common.constant.ScheduleConstants;
import com.cq.panel.admin.server.web.exception.job.TaskException;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.common.utils.spring.SpringUtils;
import com.cq.panel.admin.server.repository.domain.SysJob;
import org.quartz.*;

/**
 * 定时任务工具类
 * 
 * @author cq
 *
 */
public class ScheduleUtils
{
    /**
     * 得到quartz任务类
     *
     * @param sysJob 执行计划
     * @return 具体执行任务类
     */
    private static Class<? extends Job> getQuartzJobClass(SysJob sysJob)
    {
        boolean isConcurrent = "0".equals(sysJob.getConcurrent());
        return isConcurrent ? QuartzJobExecution.class : QuartzDisallowConcurrentExecution.class;
    }

    /**
     * 构建任务触发对象
     */
    public static TriggerKey getTriggerKey(SysJob sysJob)
    {
        return TriggerKey.triggerKey(
                sysJob.getJobGroup() + "_" + sysJob.getJobName() + "_triggerKey_" + sysJob.getJobId(),
                sysJob.getJobGroup());
    }

    /**
     * 构建任务键对象
     */
    public static JobKey getJobKey(SysJob sysJob)
    {
        return JobKey.jobKey(
                sysJob.getJobGroup() + "_" + sysJob.getJobName() + "_jobKey_" + sysJob.getJobId(),
                sysJob.getJobGroup());
    }

    /**
     * 创建定时任务
     */
    public static void createScheduleJob(Scheduler scheduler, SysJob job) throws SchedulerException, TaskException
    {
        Class<? extends Job> jobClass = getQuartzJobClass(job);
        // 构建job信息
        JobDetail jobDetail = JobBuilder.newJob(jobClass).withIdentity(getJobKey(job)).withDescription(job.getRemark()).build();

        // 表达式调度构建器
        CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(job.getCronExpression());
        cronScheduleBuilder = handleCronScheduleMisfirePolicy(job, cronScheduleBuilder);

        // 按新的cronExpression表达式构建一个新的trigger
        CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(getTriggerKey(job)).withDescription(job.getRemark())
                .withSchedule(cronScheduleBuilder).build();

        // 放入参数，运行时的方法可以获取
        jobDetail.getJobDataMap().put(ScheduleConstants.TASK_PROPERTIES, job);

        // 判断是否存在
        if (scheduler.checkExists(getJobKey(job)))
        {
            // 防止创建时存在数据问题 先移除，然后在执行创建操作
            scheduler.deleteJob(getJobKey(job));
        }

        // 判断任务是否过期
        if (MyStringUtils.isNotNull(CronUtils.getNextExecution(job.getCronExpression())))
        {
            // 执行调度任务
            scheduler.scheduleJob(jobDetail, trigger);
        }

        // 暂停任务
        if (job.getStatus().equals(ScheduleConstants.Status.PAUSE.getValue()))
        {
            scheduler.pauseJob(ScheduleUtils.getJobKey(job));
        }
    }

    /**
     * 设置定时任务策略
     */
    public static CronScheduleBuilder handleCronScheduleMisfirePolicy(SysJob job, CronScheduleBuilder cb)
            throws TaskException
    {
        return switch (job.getMisfirePolicy()) {
            case ScheduleConstants.MISFIRE_DEFAULT -> cb;
            case ScheduleConstants.MISFIRE_IGNORE_MISFIRES -> cb.withMisfireHandlingInstructionIgnoreMisfires();
            case ScheduleConstants.MISFIRE_FIRE_AND_PROCEED -> cb.withMisfireHandlingInstructionFireAndProceed();
            case ScheduleConstants.MISFIRE_DO_NOTHING -> cb.withMisfireHandlingInstructionDoNothing();
            default -> throw new TaskException("The task misfire policy '" + job.getMisfirePolicy()
                    + "' cannot be used in cron schedule tasks", TaskException.Code.CONFIG_ERROR);
        };
    }

    /**
     * 检查包名是否为白名单配置
     * 
     * @param invokeTarget 目标字符串
     * @return 结果
     */
    public static boolean whiteList(String invokeTarget)
    {
        String packageName = MyStringUtils.substringBefore(invokeTarget, "(");
        int count = MyStringUtils.countMatches(packageName, ".");
        if (count > 1)
        {
            return MyStringUtils.containsAnyIgnoreCase(invokeTarget, Constants.JOB_WHITELIST_STR);
        }
        Object obj = SpringUtils.getBean(MyStringUtils.split(invokeTarget, ".")[0]);
        String beanPackageName = obj.getClass().getPackage().getName();
        return MyStringUtils.containsAnyIgnoreCase(beanPackageName, Constants.JOB_WHITELIST_STR)
                && !MyStringUtils.containsAnyIgnoreCase(beanPackageName, Constants.JOB_ERROR_STR);
    }
}
