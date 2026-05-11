package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "批量传输子任务管理", description = "批量传输子任务（文件传输明细）相关接口")
@RestController
@RequestMapping("/batch/subtask")
public class BatchTransferSubtaskController extends BaseController {

    private final BatchTransferSubtaskMapper subtaskMapper;

    public BatchTransferSubtaskController(BatchTransferSubtaskMapper subtaskMapper) {
        this.subtaskMapper = subtaskMapper;
    }

    @Operation(summary = "查询子任务列表", description = "分页查询文件传输明细列表，支持按任务ID、状态、文件路径等条件过滤")
    @RequirePermission("batch:subtask:list")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @Parameter(description = "任务ID(可选)") @RequestParam(required = false) Long taskId,
            @Parameter(description = "状态(可选): QUEUED/SENDING/COMPLETED/FAILED/RETRYING") @RequestParam(required = false) String status,
            @Parameter(description = "源文件路径(模糊搜索)") @RequestParam(required = false) String sourceFilePath,
            @Parameter(description = "目标Agent ID(模糊搜索)") @RequestParam(required = false) String targetAgentId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer pageSize) {
        
        int offset = (pageNum - 1) * pageSize;
        List<BatchTransferSubtask> list = subtaskMapper.selectPageList(taskId, status, sourceFilePath, targetAgentId, offset, pageSize);
        long total = subtaskMapper.countByCondition(taskId, status, sourceFilePath, targetAgentId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("data", list);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return Result.success(result);
    }

    @Operation(summary = "获取子任务详情", description = "根据子任务ID获取详细信息")
    @RequirePermission("batch:subtask:query")
    @GetMapping("/{id}")
    public Result<BatchTransferSubtask> getById(
            @Parameter(description = "子任务ID", required = true) @PathVariable Long id) {
        List<BatchTransferSubtask> list = subtaskMapper.selectPageList(id, null, null, null, 0, 1);
        if (list == null || list.isEmpty()) {
            return Result.error("子任务不存在: " + id);
        }
        return Result.success(list.get(0));
    }

    @Operation(summary = "根据任务ID查询子任务", description = "查询指定任务下的所有文件传输明细")
    @RequirePermission("batch:subtask:list")
    @GetMapping("/task/{taskId}")
    public Result<List<BatchTransferSubtask>> getByTaskId(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId) {
        List<BatchTransferSubtask> list = subtaskMapper.selectPageList(taskId, null, null, null, 0, 1000);
        return Result.success(list);
    }
}
