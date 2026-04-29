package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.web.exception.job.TaskException;
import com.cq.panel.admin.server.repository.domain.SysJob;
import org.quartz.SchedulerException;
import java.util.List;

/**
 * 定时任务调度信息信息 服务层
 * 
 * @author cq
 */
public interface ISysJobService
{
    /**
     * 获取quartz调度器的计划任务
     * 
     * @param job 调度信息
     * @return 调度任务集合
     */
    List<SysJob> selectJobList(SysJob job);

    /**
     * 通过调度任务ID查询调度信息
     * 
     * @param jobId 调度任务ID
     * @return 调度任务对象信息
     */
    SysJob selectJobById(Long jobId);

    /**
     * 暂停任务
     * 
     * @param job 调度信息
     * @return 结果
     */
    int pauseJob(SysJob job) throws SchedulerException;

    /**
     * 恢复任务
     * 
     * @param job 调度信息
     * @return 结果
     */
    int resumeJob(SysJob job) throws SchedulerException;

    /**
     * 删除任务后，所对应的trigger也将被删除
     * 
     * @param job 调度信息
     * @return 结果
     */
    int deleteJob(SysJob job) throws SchedulerException;

    /**
     * 批量删除调度信息
     * 
     * @param jobIds 需要删除的任务ID
     */
    void deleteJobByIds(Long[] jobIds) throws SchedulerException;

    /**
     * 任务调度状态修改
     * 
     * @param job 调度信息
     * @return 结果
     */
    int changeStatus(SysJob job) throws SchedulerException;

    /**
     * 立即运行任务
     * 
     * @param job 调度信息
     * @return 结果
     */
    boolean run(SysJob job) throws SchedulerException;

    /**
     * 新增任务
     * 
     * @param job 调度信息
     * @return 结果
     */
    int insertJob(SysJob job) throws SchedulerException, TaskException;

    /**
     * 更新任务
     * 
     * @param job 调度信息
     * @return 结果
     */
    int updateJob(SysJob job) throws SchedulerException, TaskException;

    /**
     * 校验cron表达式是否有效
     * 
     * @param cronExpression 表达式
     * @return 结果
     */
    boolean checkCronExpressionIsValid(String cronExpression);
    
    /**
     * 查询任务组名列表（用于自动完成）
     * 
     * @param jobGroup 任务组名（支持模糊查询）
     * @return 任务组名列表
     */
    List<String> selectJobGroupList(String jobGroup);
    
    /**
     * 验证任务信息（根据jobType验证各字段）
     * 
     * @param job 任务信息
     */
    void validateJob(SysJob job);
    
    /**
     * 验证调用目标字符串（安全检查）
     * 
     * @param jobName 任务名称
     * @param invokeTarget 调用目标
     */
    void validateInvokeTarget(String jobName, String invokeTarget);
    
    /**
     * 新增定时任务（包含完整业务逻辑）
     * 
     * @param job 任务信息
     * @param username 操作用户
     * @return 结果
     */
    int addJob(SysJob job, String username) throws SchedulerException, TaskException;
    
    /**
     * 修改定时任务（包含完整业务逻辑）
     * 
     * @param job 任务信息
     * @param username 操作用户
     * @return 结果
     */
    int updateJobWithValidation(SysJob job, String username) throws SchedulerException, TaskException;
    
    /**
     * 修改定时任务状态（包含完整业务逻辑）
     * 
     * @param job 任务信息
     * @return 结果
     */
    int changeStatusWithValidation(SysJob job) throws SchedulerException;
    
    /**
     * 立即执行任务（包含完整业务逻辑）
     * 
     * @param jobId 任务ID
     */
    void runJobImmediately(Long jobId);
}