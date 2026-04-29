package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.web.domain.dto.monitor.MethodValidationDTO;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobDTO;
import com.cq.panel.admin.server.web.domain.dto.monitor.SysJobQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.monitor.MethodInfoVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.MethodValidationVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.SysJobVO;
import com.cq.panel.admin.server.web.converter.monitor.SysJobConverter;
import com.cq.panel.admin.server.service.IMethodScannerService;
import com.cq.panel.admin.server.web.exception.job.TaskException;
import com.github.pagehelper.PageInfo;
import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
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

    private final IMethodScannerService methodScannerService;

    public SysJobController(ISysJobService jobService, SysJobConverter jobConverter, IMethodScannerService methodScannerService) {
        this.jobService = jobService;
        this.jobConverter = jobConverter;
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
        if (dto.getParameterValues() == null || dto.getParameterValues().isEmpty()) {
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
        SysJob job = jobConverter.toEntity(dto);
        job.setCreateBy(getUsername());
        final int rows = jobService.addJob(job);
        logger.info("新增定时任务影响行数{}", rows);
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
        SysJob job = jobConverter.toEntity(dto);
        job.setUpdateBy(getUsername());
        final int row = jobService.updateJobWithValidation(job);
        logger.info("修改定时任务{}影响行数：{}", job.getJobId(), row);
        return Result.success();
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
        final int rows = jobService.changeStatusWithValidation(job);
        logger.info("修改定时任务状态{}影响行数{}", dto.getJobId(),  rows);
        return Result.success();
    }

    /**
     * 定时任务立即执行一次
     */
    @Operation(summary = "定时任务立即执行一次", description = "立即执行一次选定的定时任务")
    @RequirePermission("monitor:job:changeStatus")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping("/run")
    public Result<Void> run(@RequestBody SysJobDTO dto)
    {
        jobService.runJobImmediately(dto.getJobId());
        return Result.success();
    }

    /**
     * 删除定时任务
     */
    @Operation(summary = "删除定时任务", description = "批量删除定时任务")
    @RequirePermission("monitor:job:remove")
    @Log(title = "定时任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{jobIds}")
    public Result<Void> remove(@Parameter(description = "任务ID串", required = true) @PathVariable Long[] jobIds) throws SchedulerException
    {
        jobService.deleteJobByIds(jobIds);
        return Result.success();
    }
}
