package com.cq.panel.admin.server.common.utils.quartz;

import com.cq.panel.admin.server.repository.domain.SysJob;
import org.quartz.JobExecutionContext;

/**
 * 定时任务处理（允许并发执行）
 * 
 * @author cq
 *
 */
public class QuartzJobExecution extends AbstractQuartzJob
{
    @Override
    protected void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception
    {
        JobInvokeUtil.invokeMethod(sysJob);
    }
}
