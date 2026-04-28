package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.common.utils.JobLogUtil;
import com.cq.panel.admin.server.quartz.JobInvokeUtil;
import com.cq.panel.admin.server.web.domain.dto.monitor.MethodValidationDTO;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobDTO;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.monitor.MethodInfoVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.MethodValidationVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysJobVO;
import com.cq.panel.admin.server.web.converter.monitor.SysJobConverter;
import com.cq.panel.admin.server.web.exception.ServiceException;
import com.cq.panel.admin.server.service.IMethodScannerService;
import com.github.pagehelper.PageInfo;
import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.web.exception.job.TaskException;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.quartz.CronUtils;
import com.cq.panel.admin.server.quartz.ScheduleUtils;
import com.cq.panel.admin.server.repository.domain.SysJob;
import com.cq.panel.admin.server.repository.service.ISysJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.quartz.SchedulerException;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.core.task.TaskExecutor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
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

    private final TaskExecutor threadPoolTaskExecutor;

    private final IMethodScannerService methodScannerService;

    public SysJobController(ISysJobService jobService, SysJobConverter jobConverter, TaskExecutor threadPoolTaskExecutor, IMethodScannerService methodScannerService) {
        this.jobService = jobService;
        this.jobConverter = jobConverter;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
        this.methodScannerService = methodScannerService;
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
     * 查询任务组名列表（用于自动完成）
     */
    @Operation(summary = "查询任务组名列表", description = "获取任务组名列表，支持模糊查询")
    @GetMapping("/jobGroups")
    public Result<List<String>> getJobGroups(@Parameter(description = "任务组名（支持模糊查询）") @RequestParam(required = false) String jobGroup)
    {
        return Result.success(jobService.selectJobGroupList(jobGroup));
    }

    /**
     * 扫描内置方法列表
     */
    @Operation(summary = "扫描内置方法列表", description = "扫描task包下所有组件的public方法，返回可调用的内置方法列表")
    @GetMapping("/methods")
    public Result<List<MethodInfoVO>> scanMethods()
    {
        return Result.success(methodScannerService.scanTaskMethods());
    }

    /**
     * 验证内置方法
     */
    @Operation(summary = "验证内置方法", description = "校验内置方法字符串是否正确，包括方法是否存在、入参类型是否正确等")
    @PostMapping("/validateMethod")
    public Result<MethodValidationVO> validateMethod(@Validated @RequestBody MethodValidationDTO dto)
    {
        if (MyStringUtils.isEmpty(dto.getParameterValues())) {
            return Result.success(methodScannerService.validateMethod(dto.getMethodName()));
        } else {
            return Result.success(methodScannerService.validateMethodWithParameters(dto.getMethodName(), dto.getParameterValues()));
        }
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

            if (MyStringUtils.isEmpty(target)) {
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
        } else if (job.getJobType() == 3) {
            // 脚本模式，invokeTarget 设为脚本名称
            job.setInvokeTarget(job.getScriptName());
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
        if (job == null) {
            throw new ServiceException("修改任务失败，任务信息解析失败");
        }
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            throw new ServiceException("修改任务'" + job.getJobName() + "'失败，Cron表达式不正确" + job.getCronExpression());
        }

        if (job.getJobType() == null || job.getJobType() == 0 || job.getJobType() == 1) {
            String target = job.getJobType() == 1 ? job.getMethodName() : job.getInvokeTarget();
            if (MyStringUtils.isNotEmpty(target)) {
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
            if (MyStringUtils.isEmpty(dto.getMethodName())) {
                throw new ServiceException("内置方法不能为空");
            }
            if (MyStringUtils.isNotEmpty(dto.getHttpUrl())) {
                throw new ServiceException("内置方法模式下，HTTP接口URL必须为空");
            }
            if (MyStringUtils.isNotEmpty(dto.getScriptName())) {
                throw new ServiceException("内置方法模式下，脚本名称必须为空");
            }
        } else if (dto.getJobType() == 2) {
            if (MyStringUtils.isEmpty(dto.getHttpUrl())) {
                throw new ServiceException("HTTP接口URL不能为空");
            }
            if (MyStringUtils.isNotEmpty(dto.getMethodName())) {
                throw new ServiceException("HTTP接口模式下，内置方法必须为空");
            }
            if (MyStringUtils.isNotEmpty(dto.getScriptName())) {
                throw new ServiceException("HTTP接口模式下，脚本名称必须为空");
            }
        } else if (dto.getJobType() == 3) {
            if (MyStringUtils.isEmpty(dto.getScriptName())) {
                throw new ServiceException("脚本名称不能为空");
            }
            if (MyStringUtils.isEmpty(dto.getScriptType())) {
                throw new ServiceException("脚本类型不能为空");
            }
            if (MyStringUtils.isEmpty(dto.getScriptContent())) {
                throw new ServiceException("脚本内容不能为空");
            }
            if (MyStringUtils.isNotEmpty(dto.getMethodName())) {
                throw new ServiceException("脚本模式下，内置方法必须为空");
            }
            if (MyStringUtils.isNotEmpty(dto.getHttpUrl())) {
                throw new ServiceException("脚本模式下，HTTP接口URL必须为空");
            }
        }
    }

    private void validateInvokeTarget(String jobName, String invokeTarget) {
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
            // 如果是 jobType=1, 这里不允许输入 http，因为它是反射调用
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

        threadPoolTaskExecutor.execute(() -> {
            try {
                String selectedUrl = JobInvokeUtil.invokeMethod(job);
                // 手动触发，传入"1"
                insertSysJobLog(job, new Date(), null, "1", selectedUrl);
            } catch (Exception e) {
                logger.error("执行定时任务 {} 失败", job, e);
                // 手动触发，传入"1"
                insertSysJobLog(job, new Date(), e, "1", null);
            }
        });
        return Result.success();
    }

    private void insertSysJobLog(SysJob sysJob, Date startTime, Exception e, String triggerType, String selectedUrl) {
        JobLogUtil.createAndSaveJobLog(sysJob, startTime, e, triggerType, selectedUrl);
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