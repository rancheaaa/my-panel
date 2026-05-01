package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
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
    private final BatchTransferConverter batchTransferConverter;

    public BatchTransferController(BatchTransferService batchTransferService,
                                   IBatchTransferTaskService batchTransferTaskService,
                                   BatchTransferConverter batchTransferConverter)
    {
        this.batchTransferService = batchTransferService;
        this.batchTransferTaskService = batchTransferTaskService;
        this.batchTransferConverter = batchTransferConverter;
    }

    @RequirePermission("batch:task:create")
    @Log(title = "批量传输任务", businessType = BusinessType.INSERT)
    @Operation(summary = "创建批量传输任务")
    @PostMapping
    public Result<Void> add(@Validated @RequestBody BatchTaskCreateDTO dto)
    {
        String operatorId = getOperatorId();
        String operatorName = getOperatorName();
        batchTransferService.createTask(dto, operatorId, operatorName);
        return Result.success();
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
    @Operation(summary = "取消批量传输任务")
    @PutMapping("/{taskId}/cancel")
    public Result<Void> cancel(@PathVariable Long taskId)
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
            vo.setProgressPercent(calcProgress(vo.getTotalFiles(), vo.getTransferredFiles()));
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
        vo.setTotalSizeBytes(task.getTotalSizeBytes());
        vo.setTransferredFiles(task.getTransferredFiles());
        vo.setTransferredSizeBytes(task.getTransferredSizeBytes());
        vo.setFailedFiles(task.getFailedFiles());
        vo.setPostProcessFiles(task.getPostProcessFiles());
        vo.setPostProcessFailed(task.getPostProcessFailed());
        vo.setProgressPercent(calcProgress(task.getTotalFiles(), task.getTransferredFiles()));
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setPostProcessedAt(task.getPostProcessedAt());
        vo.setCreateTime(task.getCreateTime());
        vo.setCreateBy(task.getCreateBy());
    }

    private void enrichDetailVO(BatchTaskDetailVO vo, BatchTransferTask task)
    {
        String s = task.getStatus();
        vo.setCanPause("TRANSFERRING".equals(s) || "SCANNING".equals(s));
        vo.setCanCancel(!"COMPLETED".equals(s) && !"CANCELLED".equals(s) && !"EXPIRED".equals(s));
        vo.setCanConfig("PENDING".equals(s) || "PAUSED".equals(s) || "TRANSFERRING".equals(s));
        vo.setCanDelete("COMPLETED".equals(s) || "CANCELLED".equals(s) || "FAILED".equals(s) || "EXPIRED".equals(s) || "PARTIAL_FAILED".equals(s));
        vo.setCanRetry("PARTIAL_FAILED".equals(s) || "FAILED".equals(s));
    }

    private java.math.BigDecimal calcProgress(Integer total, Integer done)
    {
        if (total == null || total == 0 || done == null) return java.math.BigDecimal.ZERO;
        return java.math.BigDecimal.valueOf(done)
                .multiply(java.math.BigDecimal.valueOf(100))
                .divide(java.math.BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
    }

    private String getStatusLabel(String status)
    {
        if (status == null) return "";
        return switch (status)
        {
            case "PENDING" -> "待启动";
            case "SCANNING" -> "扫描中";
            case "TRANSFERRING" -> "传输中";
            case "PAUSED" -> "已暂停";
            case "POST_PROCESSING" -> "后处理中";
            case "COMPLETED" -> "已完成";
            case "PARTIAL_FAILED" -> "部分失败";
            case "FAILED" -> "失败";
            case "CANCELLED" -> "已取消";
            case "EXPIRED" -> "已过期";
            default -> status;
        };
    }
}
