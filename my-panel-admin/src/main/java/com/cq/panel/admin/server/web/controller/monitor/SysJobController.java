package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobDTO;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysJobVO;
import com.cq.panel.admin.server.web.converter.monitor.SysJobConverter;
import com.cq.panel.admin.server.web.exception.ServiceException;
import com.github.pagehelper.PageInfo;
import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.web.exception.job.TaskException;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.task.quartz.CronUtils;
import com.cq.panel.admin.server.task.quartz.ScheduleUtils;
import com.cq.panel.admin.server.repository.domain.SysJob;
import com.cq.panel.admin.server.repository.service.ISysJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.quartz.SchedulerException;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 调度任务信息操作处理
 * 
 * @author cq
 */
@Tag(name = "调度任务", description = "定时任务管理接口")
@RestController
@RequestMapping("/monitor/job")
public class SysJobController extends BaseController
{
    private final ISysJobService jobService;

    private final SysJobConverter jobConverter;

    public SysJobController(ISysJobService jobService, SysJobConverter jobConverter) {
        this.jobService = jobService;
        this.jobConverter = jobConverter;
    }

    /**
     * 查询定时任务列表
     */
    @Operation(summary = "查询定时任务列表", description = "获取定时任务列表，支持分页和条件查询")
    @RequirePermission("monitor:job:list")
    @GetMapping("/list")
    public Result<PageVO<SysJobVO>> list(@Parameter(description = "查询条件") SysJobQueryDTO query)
    {
        startPage();
        SysJob sysJob = jobConverter.toEntity(query);
        List<SysJob> list = jobService.selectJobList(sysJob);
        List<SysJobVO> voList = jobConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }

    /**
     * 导出定时任务列表
     */
    @Operation(summary = "导出定时任务列表", description = "导出符合条件的定时任务数据")
    @RequirePermission("monitor:job:export")
    @Log(title = "定时任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询条件") SysJobQueryDTO query)
    {
        SysJob sysJob = jobConverter.toEntity(query);
        List<SysJob> list = jobService.selectJobList(sysJob);
        ExcelUtil<SysJob> util = new ExcelUtil<>(SysJob.class);
        util.exportExcel(response, list, "定时任务");
    }

    /**
     * 获取定时任务详细信息
     */
    @Operation(summary = "获取定时任务详细信息", description = "根据任务ID获取详细信息")
    @RequirePermission("monitor:job:query")
    @GetMapping(value = "/{jobId}")
    public Result<SysJobVO> getInfo(@Parameter(description = "任务ID", required = true) @PathVariable("jobId") Long jobId)
    {
        return Result.success(jobConverter.toVO(jobService.selectJobById(jobId)));
    }

    /**
     * 新增定时任务
     */
    @Operation(summary = "新增定时任务", description = "创建新的定时任务")
    @RequirePermission("monitor:job:add")
    @Log(title = "定时任务", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysJobDTO dto) throws SchedulerException, TaskException
    {
        validateJob(dto);
        SysJob job = jobConverter.toEntity(dto);
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            throw new ServiceException("新增任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }
        
        // 只有在内置方法或原有逻辑下校验 invokeTarget
        if (job.getJobType() == null || job.getJobType() == 0 || job.getJobType() == 1) {
            String target = job.getJobType() == 1 ? job.getMethodName() : job.getInvokeTarget();
            if (StringUtils.isEmpty(target)) {
                // 如果是 jobType=1, validateJob 已经校验了 methodName 不能为空
                // 这里只是为了兼容原有逻辑的 invokeTarget 校验
                if (job.getJobType() == null || job.getJobType() == 0) {
                     throw new ServiceException("调用目标不能为空");
                }
            } else {
                validateInvokeTarget(job.getJobName(), target);
            }
            
            // 如果是 jobType=1, invokeTarget 应该存入一个占位符或 methodName，因为数据库该字段 NOT NULL
            if (job.getJobType() == 1) {
                job.setInvokeTarget(job.getMethodName());
            }
        } else if (job.getJobType() == 2) {
            // HTTP 模式，invokeTarget 设为 URL
            job.setInvokeTarget(job.getHttpUrl());
        }

        job.setCreateBy(getUsername());
        final int insertRows = jobService.insertJob(job);
        if (insertRows <= 0) {
            throw new ServiceException("新增任务'" + job.getJobName() + "'失败，插入数据库失败");
        }
        return Result.success();
    }

    /**
     * 修改定时任务
     */
    @Operation(summary = "修改定时任务", description = "修改现有的定时任务信息")
    @RequirePermission("monitor:job:edit")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SysJobDTO dto) throws SchedulerException, TaskException
    {
        validateJob(dto);
        SysJob job = jobConverter.toEntity(dto);
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            throw new ServiceException("修改任务'" + job.getJobName() + "'失败，Cron表达式不正确" + job.getCronExpression());
        }

        if (job.getJobType() == null || job.getJobType() == 0 || job.getJobType() == 1) {
            String target = job.getJobType() == 1 ? job.getMethodName() : job.getInvokeTarget();
            if (StringUtils.isNotEmpty(target)) {
                validateInvokeTarget(job.getJobName(), target);
            }
            if (job.getJobType() == 1) {
                job.setInvokeTarget(job.getMethodName());
            }
        } else if (job.getJobType() == 2) {
            job.setInvokeTarget(job.getHttpUrl());
        }

        job.setUpdateBy(getUsername());
        final int updateRows = jobService.updateJob(job);
        if (updateRows <= 0) {
            throw new ServiceException("修改任务'" + job.getJobName() + "'失败，更新数据库失败");
        }
        else return Result.success();
    }

    private void validateJob(SysJobDTO dto) {
        if (dto.getJobType() == 1) {
            if (StringUtils.isEmpty(dto.getMethodName())) {
                throw new ServiceException("内置方法不能为空");
            }
            if (StringUtils.isNotEmpty(dto.getHttpUrl())) {
                throw new ServiceException("内置方法模式下，HTTP接口URL必须为空");
            }
        } else if (dto.getJobType() == 2) {
            if (StringUtils.isEmpty(dto.getHttpUrl())) {
                throw new ServiceException("HTTP接口URL不能为空");
            }
            if (StringUtils.isNotEmpty(dto.getMethodName())) {
                throw new ServiceException("HTTP接口模式下，内置方法必须为空");
            }
        }
    }

    private void validateInvokeTarget(String jobName, String invokeTarget) {
        if (StringUtils.contains(invokeTarget, Constants.LOOKUP_RMI))
        {
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串不允许'rmi'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(invokeTarget, new String[] { Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS }))
        {
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串不允许'ldap(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(invokeTarget, new String[] { Constants.HTTP, Constants.HTTPS }))
        {
            // 如果是 jobType=1, 这里不允许输入 http，因为它是反射调用
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串不允许'http(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(invokeTarget, Constants.JOB_ERROR_STR))
        {
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串存在违规");
        }
        else if (!ScheduleUtils.whiteList(invokeTarget))
        {
            throw new ServiceException("任务'" + jobName + "'失败，目标字符串不在白名单内");
        }
    }

    /**
     * 定时任务状态修改
     */
    @Operation(summary = "定时任务状态修改", description = "修改定时任务的启用/停用状态")
    @RequirePermission("monitor:job:changeStatus")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public Result<Void> changeStatus(@RequestBody SysJobDTO dto) throws SchedulerException
    {
        SysJob job = jobConverter.toEntity(dto);
        SysJob newJob = jobService.selectJobById(job.getJobId());
        newJob.setStatus(job.getStatus());
        final int changeRows = jobService.changeStatus(newJob);
        if (changeRows <= 0) {
            throw new ServiceException("任务状态修改失败");
        }
        return Result.success();
    }

    /**
     * 定时任务立即执行一次
     */
    @Operation(summary = "定时任务立即执行一次", description = "立即执行一次选定的定时任务")
    @RequirePermission("monitor:job:changeStatus")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping("/run")
    public Result<Void> run(@RequestBody SysJobDTO dto) throws SchedulerException
    {
        if (dto.getJobId() == null)
        {
            throw new ServiceException("任务ID不能为空");
        }

        SysJob job = jobService.selectJobById(dto.getJobId());
        if (job == null)
        {
            throw new ServiceException("任务不存在或已过期！");
        }

        if (!jobService.checkCronExpressionIsValid(job.getCronExpression()))
        {
            throw new ServiceException("任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }

        boolean result = jobService.run(job);
        if (!result)
        {
            throw new ServiceException("任务不存在或已过期！");
        }
        return Result.success();
    }

    /**
     * 删除定时任务
     */
    @Operation(summary = "删除定时任务", description = "批量删除定时任务")
    @RequirePermission("monitor:job:remove")
    @Log(title = "定时任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{jobIds}")
    public Result<Void> remove(@Parameter(description = "任务ID串", required = true) @PathVariable Long[] jobIds) throws SchedulerException, TaskException
    {
        jobService.deleteJobByIds(jobIds);
        return Result.success();
    }
}






