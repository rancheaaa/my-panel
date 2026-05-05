package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.BatchTransferStatistics;
import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.service.IBatchTransferSubtaskService;
import com.cq.panel.admin.server.repository.service.IBatchTransferTaskService;
import com.cq.panel.admin.server.service.batch.BatchTransferService;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.converter.batch.BatchTransferConverter;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskConfigUpdateDTO;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskCreateDTO;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTaskQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTaskDetailVO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTaskVO;
import com.cq.panel.authlite.annotation.RequirePermission;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "批量传输任务管理", description = "批量文件传输任务相关接口")
@RestController
@RequestMapping("/batch/tasks")
public class BatchTransferController extends BaseController
{
    private final BatchTransferService batchTransferService;
    private final IBatchTransferTaskService batchTransferTaskService;
    private final IBatchTransferSubtaskService batchTransferSubtaskService;
    private final BatchTransferConverter batchTransferConverter;

    public BatchTransferController(BatchTransferService batchTransferService,
                                   IBatchTransferTaskService batchTransferTaskService,
                                   IBatchTransferSubtaskService batchTransferSubtaskService,
                                   BatchTransferConverter batchTransferConverter)
    {
        this.batchTransferService = batchTransferService;
        this.batchTransferTaskService = batchTransferTaskService;
        this.batchTransferSubtaskService = batchTransferSubtaskService;
        this.batchTransferConverter = batchTransferConverter;
    }

    @RequirePermission("batch:task:create")
    @Log(title = "批量传输任务", businessType = BusinessType.INSERT)
    @Operation(summary = "创建批量传输任务")
    @PostMapping
    public Result<Long> add(@Validated @RequestBody BatchTaskCreateDTO dto)
    {
        String operatorId = getOperatorId();
        String operatorName = getOperatorName();
        Long taskId = batchTransferService.createTask(dto, operatorId, operatorName);
        return Result.success(taskId);
    }

    @RequirePermission("batch:task:start")
    @Log(title = "批量传输任务", businessType = BusinessType.UPDATE)
    @Operation(summary = "启动批量传输任务")
    @PutMapping("/{taskId}/start")
    public Result<Void> start(@PathVariable Long taskId)
    {
        String operatorId = getOperatorId();
        String operatorName = getOperatorName();
        batchTransferService.startTask(taskId, operatorId, operatorName);
        return Result.success();
    }

    @RequirePermission("batch:task:pause")
    @Log(title = "批量传输任务", businessType = BusinessType.UPDATE)
    @Operation(summary = "暂停批量传输任务")
    @PutMapping("/{taskId}/pause")
    public Result<Void> pause(@PathVariable Long taskId)
    {
        String operatorId = getOperatorId();
        String operatorName = getOperatorName();
        batchTransferService.pauseTask(taskId, operatorId, operatorName);
        return Result.success();
    }

    @RequirePermission("batch:task:resume")
    @Log(title = "批量传输任务", businessType = BusinessType.UPDATE)
    @Operation(summary = "恢复批量传输任务")
    @PutMapping("/{taskId}/resume")
    public Result<Void> resume(@PathVariable Long taskId)
    {
        String operatorId = getOperatorId();
        String operatorName = getOperatorName();
        batchTransferService.resumeTask(taskId, operatorId, operatorName);
        return Result.success();
    }

    @RequirePermission("batch:task:cancel")
    @Log(title = "批量传输任务", businessType = BusinessType.UPDATE)
    @Operation(summary = "停止批量传输任务")
    @PutMapping("/{taskId}/stop")
    public Result<Void> stop(@PathVariable Long taskId)
    {
        String operatorId = getOperatorId();
        String operatorName = getOperatorName();
        batchTransferService.cancelTask(taskId, operatorId, operatorName);
        return Result.success();
    }

    @RequirePermission("batch:task:update")
    @Log(title = "批量传输任务", businessType = BusinessType.UPDATE)
    @Operation(summary = "动态调整任务配置")
    @PutMapping("/{taskId}/config")
    public Result<Void> updateConfig(@PathVariable Long taskId, @Validated @RequestBody BatchTaskConfigUpdateDTO dto)
    {
        String operatorId = getOperatorId();
        String operatorName = getOperatorName();
        batchTransferService.updateConfig(taskId, dto, operatorId, operatorName);
        return Result.success();
    }

    @RequirePermission("batch:task:view")
    @Operation(summary = "查询批量传输任务列表")
    @GetMapping
    public Result<PageVO<BatchTaskVO>> list(BatchTaskQueryDTO query)
    {
        startPage();
        List<BatchTransferTask> list = batchTransferService.listTasks(query);
        List<BatchTaskVO> voList = batchTransferConverter.toVOList(list);
        enrichTaskVOList(voList);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }

    @RequirePermission("batch:task:view")
    @Operation(summary = "查询批量传输任务详情")
    @GetMapping("/{taskId}")
    public Result<BatchTaskDetailVO> getInfo(@PathVariable Long taskId)
    {
        BatchTransferTask task = batchTransferService.getTaskDetail(taskId);
        if (task == null)
        {
            return Result.error("任务不存在");
        }
        BatchTaskDetailVO vo = new BatchTaskDetailVO();
        copyTaskToDetailVO(task, vo);
        enrichDetailVO(vo, task);
        return Result.success(vo);
    }

    @RequirePermission("batch:task:retry")
    @Log(title = "批量传输任务", businessType = BusinessType.UPDATE)
    @Operation(summary = "手动重试子任务")
    @PostMapping("/{taskId}/subtasks/{subtaskId}/retry")
    public Result<Void> retrySubtask(@PathVariable Long taskId, @PathVariable Long subtaskId)
    {
        String operatorId = getOperatorId();
        String operatorName = getOperatorName();
        batchTransferService.retrySubtask(taskId, subtaskId, operatorId, operatorName);
        return Result.success();
    }

    @RequirePermission("batch:task:view")
    @Operation(summary = "查询子任务列表")
    @GetMapping("/{taskId}/subtasks")
    public Result<List<BatchTransferSubtask>> listSubtasks(@PathVariable Long taskId)
    {
        List<BatchTransferSubtask> subtasks = batchTransferSubtaskService.selectByTaskId(taskId);
        return Result.success(subtasks);
    }

    @RequirePermission("batch:task:view")
    @Operation(summary = "查询子任务统计摘要(从子任务实时计算)")
    @GetMapping("/{taskId}/subtasks/summary")
    public Result<BatchTaskDetailVO.SubtaskSummary> getSubtaskSummary(@PathVariable Long taskId)
    {
        BatchTaskDetailVO.SubtaskSummary summary = batchTransferService.getSubtaskSummary(taskId);
        return Result.success(summary);
    }

    @RequirePermission("batch:task:view")
    @Operation(summary = "查询传输统计信息(从统计表读取)")
    @GetMapping("/{taskId}/statistics")
    public Result<BatchTransferStatistics> getStatistics(@PathVariable Long taskId)
    {
        BatchTransferStatistics stats = batchTransferService.getStatistics(taskId);
        return Result.success(stats);
    }

    @RequirePermission("batch:task:view")
    @Operation(summary = "刷新并获取最新统计信息")
    @PutMapping("/{taskId}/statistics/refresh")
    public Result<BatchTransferStatistics> refreshStatistics(@PathVariable Long taskId)
    {
        BatchTransferStatistics stats = batchTransferService.refreshStatistics(taskId);
        return Result.success(stats);
    }

    @RequirePermission("batch:task:remove")
    @Log(title = "批量传输任务", businessType = BusinessType.DELETE)
    @Operation(summary = "删除批量传输任务")
    @DeleteMapping("/{ids}")
    public Result<Void> remove(@PathVariable Long[] ids)
    {
        batchTransferService.deleteTasks(ids);
        return Result.success();
    }

    private String getOperatorId()
    {
        try { return String.valueOf(getUserId()); } catch (Exception e) { return ""; }
    }

    private String getOperatorName()
    {
        try { return getUsername(); } catch (Exception e) { return ""; }
    }

    private void enrichTaskVOList(List<BatchTaskVO> voList)
    {
        for (BatchTaskVO vo : voList)
        {
            vo.setStatusLabel(getStatusLabel(vo.getStatus()));
            try
            {
                BatchTaskDetailVO.SubtaskSummary summary = batchTransferService.getSubtaskSummary(vo.getId());
                vo.setTotalSubtasks(summary.getTotalSubtasks());
                vo.setCompletedSubtasks(summary.getCompletedCount());
                vo.setFailedSubtasks(summary.getFailedCount());
                vo.setRunningSubtasks(summary.getRunningCount());
                vo.setQueuedSubtasks(summary.getQueuedCount());
                vo.setSubtaskProgressPercent(summary.getProgressPercent());
            }
            catch (Exception e)
            {
                vo.setTotalSubtasks(0);
                vo.setCompletedSubtasks(0);
                vo.setFailedSubtasks(0);
                vo.setRunningSubtasks(0);
                vo.setQueuedSubtasks(0);
                vo.setSubtaskProgressPercent(java.math.BigDecimal.ZERO);
            }
        }
    }

    private void copyTaskToDetailVO(BatchTransferTask task, BatchTaskDetailVO vo)
    {
        vo.setId(task.getId());
        vo.setTaskName(task.getTaskName());
        vo.setTaskDescription(task.getTaskDescription());
        vo.setStatus(task.getStatus());
        vo.setStatusLabel(getStatusLabel(task.getStatus()));
        vo.setSourceAgentId(task.getSourceAgentId());
        vo.setSourceDir(task.getSourceDir());
        vo.setTargetDirs(task.getTargetDirs());
        vo.setPreserveDirStructure(task.getPreserveDirStructure());
        vo.setIncludePatterns(task.getIncludePatterns());
        vo.setExcludePatterns(task.getExcludePatterns());
        vo.setMaxBandwidthKbS(task.getMaxBandwidthKbS());
        vo.setRetryEnabled(task.getRetryEnabled());
        vo.setRetryMaxDays(task.getRetryMaxDays());
        vo.setRetryIntervalMin(task.getRetryIntervalMin());
        vo.setPostTransferAction(task.getPostTransferAction());
        vo.setBackupDir(task.getBackupDir());
        vo.setBackupMode(task.getBackupMode());
        vo.setTransferMode(task.getTransferMode());
        vo.setRoutingStrategy(task.getRoutingStrategy());
        vo.setRoutingConfig(task.getRoutingConfig());
        vo.setTotalFiles(task.getTotalFiles());
        vo.setScanFrequencySec(task.getScanFrequencySec());
        vo.setScanCronExpression(task.getScanCronExpression());
        vo.setTotalSizeBytes(task.getTotalSizeBytes());
        vo.setStartedAt(task.getStartedAt());
        vo.setCreateTime(task.getCreateTime());
        vo.setCreateBy(task.getCreateBy());
    }

    private void enrichDetailVO(BatchTaskDetailVO vo, BatchTransferTask task)
    {
        String s = task.getStatus();
        vo.setCanStart(BatchTransferService.STATUS_DRAFT.equals(s) || BatchTransferService.STATUS_PAUSED.equals(s));
        vo.setCanPause(BatchTransferService.STATUS_RUNNING.equals(s));
        vo.setCanResume(BatchTransferService.STATUS_PAUSED.equals(s));
        vo.setCanStop(!BatchTransferService.STATUS_STOPPED.equals(s));
        vo.setCanDelete(BatchTransferService.STATUS_STOPPED.equals(s) || BatchTransferService.STATUS_DRAFT.equals(s));
        vo.setCanConfig(BatchTransferService.STATUS_DRAFT.equals(s) || BatchTransferService.STATUS_PAUSED.equals(s));

        try
        {
            vo.setSubtaskSummary(batchTransferService.getSubtaskSummary(task.getId()));
            vo.setStatistics(batchTransferService.getStatistics(task.getId()));
        }
        catch (Exception e)
        {
            vo.setSubtaskSummary(new BatchTaskDetailVO.SubtaskSummary());
        }
    }

    private String getStatusLabel(String status)
    {
        if (status == null) return "";
        return switch (status)
        {
            case BatchTransferService.STATUS_DRAFT -> "草稿";
            case BatchTransferService.STATUS_RUNNING -> "运行中";
            case BatchTransferService.STATUS_PAUSED -> "已暂停";
            case BatchTransferService.STATUS_STOPPED -> "已停止";
            default -> status;
        };
    }
}
