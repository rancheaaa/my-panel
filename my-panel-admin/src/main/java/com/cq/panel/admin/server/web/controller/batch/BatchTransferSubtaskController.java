package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.BatchTransferSubtask;
import com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.converter.batch.BatchTransferSubtaskConverter;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTransferSubtaskQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchTransferSubtaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cq.panel.authlite.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletResponse;
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
    public Result<Map<String, Object>> list(BatchTransferSubtaskQueryDTO query) {
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        List<BatchTransferSubtask> list = subtaskMapper.selectPageList(
                query.getTaskId(), query.getStatus(), query.getSourceFilePath(), query.getTargetFilePath(), query.getTargetAgentId(),
                query.getSourceAgentId(), query.getFileName(), query.getScanBatchId(), query.getFileBatchId(),
                query.getSourceAgentName(), query.getTargetAgentName(),
                offset, query.getPageSize());
        long total = subtaskMapper.countByCondition(
                query.getTaskId(), query.getStatus(), query.getSourceFilePath(), query.getTargetFilePath(), query.getTargetAgentId(),
                query.getSourceAgentId(), query.getFileName(), query.getScanBatchId(), query.getFileBatchId(),
                query.getSourceAgentName(), query.getTargetAgentName());

        List<BatchTransferSubtaskVO> voList = subtaskConverter.toVOList(list);

        Map<String, Object> result = new HashMap<>();
        result.put("data", voList);
        result.put("total", total);
        result.put("pageNum", query.getPageNum());
        result.put("pageSize", query.getPageSize());
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

    /**
     * 导出批量传输子任务数据
     */
    @Operation(summary = "导出子任务数据", description = "导出子任务列表数据为Excel文件，支持按条件筛选导出和按ID列表导出")
    @Log(title = "批量传输子任务", businessType = BusinessType.EXPORT)
    @RequirePermission("batch:subtask:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response,
                       BatchTransferSubtaskQueryDTO query,
                       @Parameter(description = "需要导出的子任务ID列表，逗号分隔，为空则导出全部") @RequestParam(required = false) String ids) {
        List<BatchTransferSubtask> list;
        if (ids != null && !ids.isEmpty()) {
            List<Long> idList = java.util.Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .toList();
            BatchTransferSubtask example = new BatchTransferSubtask();
            list = subtaskMapper.selectList(example).stream()
                    .filter(subtask -> idList.contains(subtask.getId()))
                    .toList();
        } else {
            BatchTransferSubtask example = new BatchTransferSubtask();
            example.setTaskId(query.getTaskId());
            example.setStatus(query.getStatus());
            example.setSourcePath(query.getSourceFilePath());
            example.setTargetPath(query.getTargetFilePath());
            example.setTargetAgentId(query.getTargetAgentId());
            example.setSourceAgentId(query.getSourceAgentId());
            example.setFileName(query.getFileName());
            example.setScanBatchId(query.getScanBatchId());
            example.setFileBatchId(query.getFileBatchId());
            example.setSourceAgentName(query.getSourceAgentName());
            example.setTargetAgentName(query.getTargetAgentName());
            list = subtaskMapper.selectList(example);
            logger.info("导出子任务: 查询条件={}, 结果数={}", query, list.size());
        }
        ExcelUtil<BatchTransferSubtask> util = new ExcelUtil<>(BatchTransferSubtask.class);
        util.exportExcel(response, list, "批量传输子任务数据");
    }
}
