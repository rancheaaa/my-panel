package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.converter.batch.BatchTransferSubtaskConverter;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTransferSubtaskVO;
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
    private final BatchTransferSubtaskConverter subtaskConverter;

    public BatchTransferSubtaskController(BatchTransferSubtaskMapper subtaskMapper,
                                          BatchTransferSubtaskConverter subtaskConverter) {
        this.subtaskMapper = subtaskMapper;
        this.subtaskConverter = subtaskConverter;
    }

    @Operation(summary = "查询子任务列表", description = "分页查询文件传输明细列表，支持多条件过滤")
    @RequirePermission("batch:subtask:list")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @Parameter(description = "任务ID(精确匹配)") @RequestParam(required = false) Long taskId,
            @Parameter(description = "状态(精确匹配): QUEUED/SENDING/COMPLETED/FAILED/RETRYING") @RequestParam(required = false) String status,
            @Parameter(description = "源文件路径(模糊搜索)") @RequestParam(required = false) String sourceFilePath,
            @Parameter(description = "目标文件路径(模糊搜索)") @RequestParam(required = false) String targetFilePath,
            @Parameter(description = "目标Agent ID(模糊搜索)") @RequestParam(required = false) String targetAgentId,
            @Parameter(description = "源Agent ID(模糊搜索)") @RequestParam(required = false) String sourceAgentId,
            @Parameter(description = "源节点名称(模糊搜索), 如 dell@172.20.10.5:7777") @RequestParam(required = false) String sourceAgentName,
            @Parameter(description = "目标节点名称(模糊搜索), 如 cq@172.19.200.130:7778") @RequestParam(required = false) String targetAgentName,
            @Parameter(description = "文件名(模糊搜索)") @RequestParam(required = false) String fileName,
            @Parameter(description = "扫描批次ID(精确匹配)") @RequestParam(required = false) Long scanBatchId,
            @Parameter(description = "文件批次ID(精确匹配)") @RequestParam(required = false) Long fileBatchId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer pageSize) {

        int offset = (pageNum - 1) * pageSize;
        List<BatchTransferSubtask> list = subtaskMapper.selectPageList(
                taskId, status, sourceFilePath, targetFilePath, targetAgentId,
                sourceAgentId, fileName, scanBatchId, fileBatchId, sourceAgentName, targetAgentName,
                offset, pageSize);
        long total = subtaskMapper.countByCondition(
                taskId, status, sourceFilePath, targetFilePath, targetAgentId,
                sourceAgentId, fileName, scanBatchId, fileBatchId, sourceAgentName, targetAgentName);

        List<BatchTransferSubtaskVO> voList = subtaskConverter.toVOList(list);

        Map<String, Object> result = new HashMap<>();
        result.put("data", voList);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return Result.success(result);
    }

    @Operation(summary = "获取子任务详情", description = "根据子任务ID获取详细信息")
    @RequirePermission("batch:subtask:query")
    @GetMapping("/{id}")
    public Result<BatchTransferSubtaskVO> getById(
            @Parameter(description = "子任务ID", required = true) @PathVariable Long id) {
        List<BatchTransferSubtask> list = subtaskMapper.selectPageList(id, null, null, null, null, null, null, null, null, null, null, 0, 1);
        if (list == null || list.isEmpty()) {
            return Result.error("子任务不存在: " + id);
        }
        return Result.success(subtaskConverter.toVO(list.getFirst()));
    }

    @Operation(summary = "根据任务ID查询子任务", description = "查询指定任务下的所有文件传输明细")
    @RequirePermission("batch:subtask:list")
    @GetMapping("/task/{taskId}")
    public Result<List<BatchTransferSubtaskVO>> getByTaskId(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId) {
        List<BatchTransferSubtask> list = subtaskMapper.selectPageList(
                taskId, null, null, null, null, null, null, null, null, null, null, 0, 1000);
        List<BatchTransferSubtaskVO> voList = subtaskConverter.toVOList(list);
        return Result.success(voList);
    }
}
