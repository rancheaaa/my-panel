package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.common.constant.ScheduleConstants;
import com.cq.panel.admin.server.common.utils.JobLogUtil;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.quartz.CronUtils;
import com.cq.panel.admin.server.quartz.JobInvokeUtil;
import com.cq.panel.admin.server.quartz.ScheduleUtils;
import com.cq.panel.admin.server.repository.domain.SysJob;
import com.cq.panel.admin.server.repository.mapper.SysJobMapper;
import com.cq.panel.admin.server.repository.service.ISysJobService;
import com.cq.panel.admin.server.web.exception.ServiceException;
import com.cq.panel.admin.server.web.exception.job.TaskException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import java.util.List;

/**
 * 定时任务调度信息 服务层
 * 
 * @author cq
 */
@Slf4j
@Service
public class SysJobServiceImpl implements ISysJobService
{
    private final Scheduler scheduler;

    private final SysJobMapper jobMapper;

    private final TaskExecutor threadPoolTaskExecutor;

    public SysJobServiceImpl(Scheduler scheduler, SysJobMapper jobMapper, TaskExecutor threadPoolTaskExecutor) {
        this.scheduler = scheduler;
        this.jobMapper = jobMapper;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }

    /**
     * 项目启动时，初始化定时器 主要是防止手动修改数据库导致未同步到定时任务处理（注：不能手动修改数据库ID和任务组名，否则会导致脏数据）
     */
    @PostConstruct
    public void init() throws SchedulerException, TaskException
    {
        scheduler.clear();
        List<SysJob> jobList = jobMapper.selectJobAll();
        for (SysJob job : jobList)
        {
            ScheduleUtils.createScheduleJob(scheduler, job);
        }
    }

    /**
     * 获取quartz调度器的计划任务列表
     * 
     * @param job 调度信息
     * @return 调度任务集合
     */
    @Override
    public List<SysJob> selectJobList(SysJob job)
    {
        return jobMapper.selectJobList(job);
    }

    /**
     * 通过调度任务ID查询调度信息
     * 
     * @param jobId 调度任务ID
     * @return 调度任务对象信息
     */
    @Override
    public SysJob selectJobById(Long jobId)
    {
        return jobMapper.selectJobById(jobId);
    }

    /**
     * 暂停任务
     * 
     * @param job 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int pauseJob(SysJob job) throws SchedulerException
    {
        job.setStatus(ScheduleConstants.Status.PAUSE.getValue());
        int rows = jobMapper.updateJob(job);
        if (rows > 0)
        {
            scheduler.pauseJob(ScheduleUtils.getJobKey(job));
        }
        return rows;
    }

    /**
     * 恢复任务
     * 
     * @param job 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resumeJob(SysJob job) throws SchedulerException
    {
        job.setStatus(ScheduleConstants.Status.NORMAL.getValue());
        int rows = jobMapper.updateJob(job);
        if (rows > 0)
        {
            scheduler.resumeJob(ScheduleUtils.getJobKey(job));
        }
        return rows;
    }

    /**
     * 删除任务后，所对应的trigger也将被删除
     * 
     * @param job 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteJob(SysJob job) throws SchedulerException
    {
        Long jobId = job.getJobId();
        int rows = jobMapper.deleteJobById(jobId);
        if (rows > 0)
        {
            scheduler.deleteJob(ScheduleUtils.getJobKey(job));
        }
        return rows;
    }

    /**
     * 批量删除调度信息
     * 
     * @param jobIds 需要删除的任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteJobByIds(Long[] jobIds) throws SchedulerException
    {
        for (Long jobId : jobIds)
        {
            SysJob job = jobMapper.selectJobById(jobId);
            final int deleteRows = deleteJob(job);
            log.info("删除任务ID：{}，任务组名：{}，任务状态：{}，删除结果：{}", jobId, job.getJobGroup(), job.getStatus(), deleteRows);
        }
    }

    /**
     * 任务调度状态修改
     * 
     * @param job 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(SysJob job) throws SchedulerException
    {
        int rows = 0;
        String status = job.getStatus();
        if (ScheduleConstants.Status.NORMAL.getValue().equals(status))
        {
            rows = resumeJob(job);
        }
        else if (ScheduleConstants.Status.PAUSE.getValue().equals(status))
        {
            rows = pauseJob(job);
        }
        return rows;
    }

    /**
     * 查询任务组名列表（用于自动完成）
     * 
     * @param jobGroup 任务组名（支持模糊查询）
     * @return 任务组名列表
     */
    @Override
    public List<String> selectJobGroupList(String jobGroup)
    {
        return jobMapper.selectJobGroupList(jobGroup);
    }
    
    /**
     * 验证任务信息（根据jobType验证各字段）
     * 
     * @param job 任务信息
     */
    @Override
    public void validateJob(SysJob job)
    {
        Integer jobType = job.getJobType();
        if (Integer.valueOf(1).equals(jobType))
        {
            if (MyStringUtils.isEmpty(job.getMethodName()))
            {
                throw new ServiceException("内置方法不能为空");
            }
            if (MyStringUtils.isNotEmpty(job.getHttpUrl()))
            {
                throw new ServiceException("内置方法模式下，HTTP接口URL必须为空");
            }
            if (MyStringUtils.isNotEmpty(job.getScriptName()))
            {
                throw new ServiceException("内置方法模式下，脚本名称必须为空");
            }
        }
        else if (Integer.valueOf(2).equals(jobType))
        {
            if (MyStringUtils.isEmpty(job.getHttpUrl()))
            {
                throw new ServiceException("HTTP接口URL不能为空");
            }
            if (MyStringUtils.isNotEmpty(job.getMethodName()))
            {
                throw new ServiceException("HTTP接口模式下，内置方法必须为空");
            }
            if (MyStringUtils.isNotEmpty(job.getScriptName()))
            {
                throw new ServiceException("HTTP接口模式下，脚本名称必须为空");
            }
        }
        else if (Integer.valueOf(3).equals(jobType))
        {
            if (MyStringUtils.isEmpty(job.getScriptName()))
            {
                throw new ServiceException("脚本名称不能为空");
            }
            if (MyStringUtils.isEmpty(job.getScriptType()))
            {
                throw new ServiceException("脚本类型不能为空");
            }
            if (MyStringUtils.isEmpty(job.getScriptContent()))
            {
                throw new ServiceException("脚本内容不能为空");
            }
            if (MyStringUtils.isNotEmpty(job.getMethodName()))
            {
                throw new ServiceException("脚本模式下，内置方法必须为空");
            }
            if (MyStringUtils.isNotEmpty(job.getHttpUrl()))
            {
                throw new ServiceException("脚本模式下，HTTP接口URL必须为空");
            }
        }
    }
    
    /**
     * 验证调用目标字符串（安全检查）
     * 
     * @param jobName 任务名称
     * @param invokeTarget 调用目标
     */
    @Override
    public void validateInvokeTarget(String jobName, String invokeTarget)
    {
        if (MyStringUtils.contains(invokeTarget, Constants.LOOKUP_RMI))
        {
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串不允许'rmi'调用");
        }
        else if (MyStringUtils.containsAnyIgnoreCase(invokeTarget, new String[] { Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS }))
        {
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串不允许'ldap(s)'调用");
        }
        else if (MyStringUtils.containsAnyIgnoreCase(invokeTarget, new String[] { Constants.HTTP, Constants.HTTPS }))
        {
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串不允许'http(s)'调用");
        }
        else if (MyStringUtils.containsAnyIgnoreCase(invokeTarget, Constants.JOB_ERROR_STR))
        {
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串存在违规");
        }
        else if (!ScheduleUtils.whiteList(invokeTarget))
        {
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串不在白名单内");
        }
    }
    
    /**
     * 新增定时任务（包含完整业务逻辑）
     * 
     * @param job 任务信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addJob(SysJob job) throws SchedulerException, TaskException
    {
        validateJob(job);
        
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            throw new ServiceException("新增任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }
        
        Integer jobType = job.getJobType();
        if (jobType == null || jobType == 0 || jobType == 1)
        {
            String target = (jobType != null && jobType == 1) ? job.getMethodName() : job.getInvokeTarget();
            
            if (MyStringUtils.isEmpty(target))
            {
                if (jobType == null || jobType == 0)
                {
                    throw new ServiceException("调用目标不能为空");
                }
            }
            else
            {
                validateInvokeTarget(job.getJobName(), target);
            }
            
            if (jobType != null && jobType == 1)
            {
                job.setInvokeTarget(job.getMethodName());
            }
        }
        else if (jobType == 2)
        {
            job.setInvokeTarget(job.getHttpUrl());
        }
        else if (jobType == 3)
        {
            job.setInvokeTarget(job.getScriptName());
        }
        final int insertRows = jobMapper.insertJob(job);
        if (insertRows <= 0)
        {
            throw new ServiceException("新增任务'" + job.getJobName() + "'失败，插入数据库失败");
        }
        
        job.setStatus(ScheduleConstants.Status.PAUSE.getValue());
        ScheduleUtils.createScheduleJob(scheduler, job);
        
        return insertRows;
    }
    
    /**
     * 修改定时任务（包含完整业务逻辑）
     * 
     * @param job 任务信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateJobWithValidation(SysJob job) throws SchedulerException, TaskException
    {
        validateJob(job);
        
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            throw new ServiceException("修改任务'" + job.getJobName() + "'失败，Cron表达式不正确" + job.getCronExpression());
        }
        
        Integer jobType = job.getJobType();
        if (jobType == 1)
        {
            String target = job.getMethodName();
            if (MyStringUtils.isNotEmpty(target))
            {
                validateInvokeTarget(job.getJobName(), target);
            }
            job.setInvokeTarget(job.getMethodName());
        }
        else if (jobType == 2)
        {
            job.setInvokeTarget(job.getHttpUrl());
        }
        
        final int updateRows = jobMapper.updateJob(job);
        if (updateRows <= 0)
        {
            throw new ServiceException("修改任务'" + job.getJobName() + "'失败，更新数据库失败");
        }

        // 判断是否存在
        JobKey jobKey = ScheduleUtils.getJobKey(job);
        if (scheduler.checkExists(jobKey))
        {
            // 防止创建时存在数据问题 先移除，然后在执行创建操作
            scheduler.deleteJob(jobKey);
        }
        ScheduleUtils.createScheduleJob(scheduler, job);
        
        return updateRows;
    }
    
    /**
     * 修改定时任务状态（包含完整业务逻辑）
     * 
     * @param job 任务信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeStatusWithValidation(SysJob job) throws SchedulerException
    {
        SysJob newJob = jobMapper.selectJobById(job.getJobId());
        newJob.setStatus(job.getStatus());
        final int changeRows = changeStatus(newJob);
        if (changeRows <= 0)
        {
            throw new ServiceException("任务状态修改失败");
        }
        return changeRows;
    }
    
    /**
     * 立即执行任务（包含完整业务逻辑）
     * 
     * @param jobId 任务ID
     */
    @Override
    public void runJobImmediately(Long jobId)
    {
        if (jobId == null)
        {
            throw new ServiceException("任务ID不能为空");
        }
        
        SysJob job = jobMapper.selectJobById(jobId);
        if (job == null)
        {
            throw new ServiceException("任务不存在或已过期！");
        }
        
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            throw new ServiceException("任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }
        
        threadPoolTaskExecutor.execute(() -> {
            try
            {
                String selectedUrl = JobInvokeUtil.invokeMethod(job);
                JobLogUtil.createAndSaveJobLog(job, new Date(), null, "1", selectedUrl);
            }
            catch (Exception e)
            {
                log.error("执行定时任务 {} 失败", job, e);
                JobLogUtil.createAndSaveJobLog(job, new Date(), e, "1", null);
            }
        });
    }
}