package com.cq.panel.admin.server.quartz;

import com.cq.panel.admin.server.repository.domain.SysJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

/**
 * 定时任务处理（禁止并发执行）
 * 
 * @author cq
 *
 */
@DisallowConcurrentExecution
public class QuartzDisallowConcurrentExecution extends AbstractQuartzJob
{
    @Override
    protected void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception
    {
        String result = JobInvokeUtil.invokeMethod(sysJob);
        context.put("selectedUrl", result);
    }
}
