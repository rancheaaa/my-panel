package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.repository.service.IBatchStatisticsService;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.batch.SubtaskSummaryVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskSummaryVO;
import com.cq.panel.authlite.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 批量传输统计Controller
 *
 * @author cq
 */
@Tag(name = "批量传输统计", description = "批量传输任务和文件明细的汇总统计接口")
@RestController
@RequestMapping("/batch/statistics")
public class BatchStatisticsController extends BaseController {

    private final IBatchStatisticsService batchStatisticsService;

    public BatchStatisticsController(IBatchStatisticsService batchStatisticsService) {
        this.batchStatisticsService = batchStatisticsService;
    }

    @RequirePermission("batch:subtask:list")
    @Operation(summary = "获取传输明细文件汇总统计", description = "返回传输文件的总体汇总信息，包括总数量、各状态分布、文件大小、传输进度等")
    @GetMapping("/subtask-summary")
    public Result<SubtaskSummaryVO> getSubtaskSummary() {
        return Result.success(batchStatisticsService.getSubtaskSummary());
    }

    @RequirePermission("batch:task:list")
    @Operation(summary = "获取传输任务汇总统计", description = "返回传输任务的总体汇总信息，包括任务总数、各状态分布、活跃任务数等")
    @GetMapping("/task-summary")
    public Result<TaskSummaryVO> getTaskSummary() {
        return Result.success(batchStatisticsService.getTaskSummary());
    }

    @RequirePermission("batch:subtask:list")
    @Operation(summary = "获取全部汇总统计", description = "一次性返回传输任务和文件明细的所有汇总统计信息")
    @GetMapping("/summary")
    public Result<Map<String, Object>> getFullSummary() {
        Map<String, Object> result = new HashMap<>();
        result.put("subtaskSummary", batchStatisticsService.getSubtaskSummary());
        result.put("taskSummary", batchStatisticsService.getTaskSummary());
        return Result.success(result);
    }
}
