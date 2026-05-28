package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.service.IBatchTransferTaskService;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTransferTaskCreateDTO;
import com.cq.panel.admin.server.web.domain.dto.batch.BatchTransferTaskQueryDTO;
import com.cq.panel.admin.server.service.batch.AgentDirectoryChecker;
import com.cq.panel.admin.server.web.converter.batch.BatchTransferTaskQueryConverter;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskListWithStatusVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cq.panel.authlite.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 批量传输任务 Controller
 * 提供任务的CRUD、状态管理、查询统计等REST API接口
 */
@Tag(name = "批量传输任务管理", description = "批量传输任务相关接口")
@RestController
@RequestMapping("/batch/task")
public class BatchTransferTaskController extends BaseController {

    private final IBatchTransferTaskService batchTransferTaskService;
    private final AgentDirectoryChecker directoryChecker;
    private final BatchTransferTaskQueryConverter queryConverter;

    public BatchTransferTaskController(IBatchTransferTaskService batchTransferTaskService,
                                       AgentDirectoryChecker directoryChecker,
                                       BatchTransferTaskQueryConverter queryConverter) {
        this.batchTransferTaskService = batchTransferTaskService;
        this.directoryChecker = directoryChecker;
        this.queryConverter = queryConverter;
    }

    /**
     * 创建批量传输任务
     */
    @Operation(summary = "创建批量传输任务", description = "创建新的批量传输任务配置")
    @Log(title = "批量传输任务", businessType = BusinessType.INSERT)
    @RequirePermission("batch:task:add")
    @PostMapping
    public Result<Long> create(@Validated @RequestBody BatchTransferTaskCreateDTO dto) {
        try {
            Long taskId = batchTransferTaskService.createTask(dto, getUsername());
            return Result.success(taskId);
        } catch (IllegalArgumentException e) {
            return Result.error("参数错误: " + e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        } catch (SecurityException e) {
            return Result.error("安全检测失败: " + e.getMessage());
        }
    }

    /**
     * 更新批量传输任务
     */
    @Operation(summary = "更新批量传输任务", description = "更新指定ID的批量传输任务配置")
    @Log(title = "批量传输任务", businessType = BusinessType.UPDATE)
    @RequirePermission("batch:task:edit")
    @PutMapping("/{taskId}")
    public Result<Void> update(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId,
            @Validated @RequestBody BatchTransferTaskCreateDTO dto) {
        try {
            batchTransferTaskService.updateTask(taskId, dto, getUsername());
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error("参数错误: " + e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除批量传输任务（逻辑删除）
     */
    @Operation(summary = "删除批量传输任务", description = "批量删除指定的传输任务")
    @Log(title = "批量传输任务", businessType = BusinessType.DELETE)
    @RequirePermission("batch:task:remove")
    @DeleteMapping("/{taskIds}")
    public Result<Void> delete(
            @Parameter(description = "任务ID数组，逗号分隔", required = true) @PathVariable String taskIds) {
        try {
            List<Long> idList = parseIds(taskIds);
            batchTransferTaskService.deleteTasks(idList);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error("参数错误: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取任务详情
     */
    @Operation(summary = "获取任务详情", description = "根据任务ID获取完整的任务配置信息")
    @RequirePermission("batch:task:query")
    @GetMapping("/{taskId}")
    public Result<BatchTransferTask> getById(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId) {
        BatchTransferTask task = batchTransferTaskService.getTaskById(taskId);
        if (task == null) {
            return Result.error("任务不存在: " + taskId);
        }
        return Result.success(task);
    }

    @Operation(summary = "查询任务列表（带节点状态）", description = "查询任务列表，同时返回源节点和目标节点的在线状态及目录是否存在")
    @RequirePermission("batch:task:list")
    @GetMapping("/list-with-status")
    public Result<Map<String, Object>> listWithStatus(BatchTransferTaskQueryDTO query) {
        BatchTransferTask domain = queryConverter.toDomain(query);
        int pageNum = query.getPageNum();
        int pageSize = query.getPageSize();
        List<TaskListWithStatusVO> list = batchTransferTaskService.getTaskListWithNodeStatus(domain, pageNum, pageSize);
        long total = batchTransferTaskService.countByCondition(domain);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("data", list);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return Result.success(result);
    }

    /**
     * 导出批量传输任务数据
     */
    @Operation(summary = "导出任务数据", description = "导出任务列表数据为Excel文件，支持按条件筛选导出和按ID列表导出")
    @Log(title = "批量传输任务", businessType = BusinessType.EXPORT)
    @RequirePermission("batch:task:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response,
                       @Parameter(description = "查询参数") BatchTransferTaskQueryDTO query,
                       @Parameter(description = "需要导出的任务ID列表，为空则导出全部") @RequestParam(required = false) List<Long> ids) {
        List<BatchTransferTask> list;
        if (ids != null && !ids.isEmpty()) {
            list = ids.stream()
                    .map(batchTransferTaskService::getTaskById)
                    .filter(task -> task != null)
                    .toList();
        } else {
            BatchTransferTask domain = queryConverter.toDomain(query);
            list = batchTransferTaskService.getTaskList(domain, 1, Integer.MAX_VALUE);
        }
        ExcelUtil<BatchTransferTask> util = new ExcelUtil<>(BatchTransferTask.class);
        util.exportExcel(response, list, "批量传输任务数据");
    }

    /**
     * 获取全局任务统计信息
     */
    @Operation(summary = "获取全局任务统计", description = "获取各状态的任务数量统计")
    @RequirePermission("batch:task:list")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        Map<String, Object> stats = batchTransferTaskService.getStatistics();
        return Result.success(stats);
    }

    /**
     * 获取单个任务的统计信息（含子任务状态分布）
     */
    @Operation(summary = "获取单任务统计", description = "获取指定任务的子任务状态分布统计")
    @RequirePermission("batch:task:statistics")
    @GetMapping("/{taskId}/statistics")
    public Result<Map<String, Object>> taskStatistics(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId) {
        try {
            Map<String, Object> stats = batchTransferTaskService.getTaskStatistics(taskId);
            return Result.success(stats);
        } catch (IllegalArgumentException e) {
            return Result.error("参数错误: " + e.getMessage());
        }
    }

    /**
     * 检查目录是否存在
     */
    @Operation(summary = "检查目录是否存在", description = "通过调用Agent接口检查指定目录是否存在")
    @RequirePermission("batch:task:query")
    @GetMapping("/check-dir")
    public Result<Map<String, Object>> checkDirectory(
            @Parameter(description = "Agent节点ID", required = true) @RequestParam String agentId,
            @Parameter(description = "目录路径", required = true) @RequestParam String dirPath) {
        
        Boolean exists = directoryChecker.checkDirectoryExists(agentId, dirPath);
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("agentId", agentId);
        result.put("dirPath", dirPath);
        result.put("exists", exists);
        
        if (exists == null) {
            result.put("status", "unknown");
            result.put("message", "无法检查（Agent离线或网络异常）");
        } else if (exists) {
            result.put("status", "exists");
            result.put("message", "目录存在");
        } else {
            result.put("status", "not_exists");
            result.put("message", "目录不存在");
        }
        
        return Result.success(result);
    }

    // ==================== 状态管理接口 ====================

    /**
     * 启动任务
     */
    @Operation(summary = "启动任务", description = "将READY或PAUSED状态的任务启动为RUNNING")
    @Log(title = "批量传输任务-启动", businessType = BusinessType.UPDATE)
    @RequirePermission("batch:task:start")
    @PutMapping("/{taskId}/start")
    public Result<Void> start(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId) {
        try {
            batchTransferTaskService.startTask(taskId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error("参数错误: " + e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 暂停任务
     */
    @Operation(summary = "暂停任务", description = "将RUNNING状态的任务暂停为PAUSED")
    @Log(title = "批量传输任务-暂停", businessType = BusinessType.UPDATE)
    @RequirePermission("batch:task:pause")
    @PutMapping("/{taskId}/pause")
    public Result<Void> pause(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId) {
        try {
            batchTransferTaskService.pauseTask(taskId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error("参数错误: " + e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 恢复任务
     */
    @Operation(summary = "恢复任务", description = "将PAUSED状态的任务恢复为RUNNING")
    @Log(title = "批量传输任务-恢复", businessType = BusinessType.UPDATE)
    @RequirePermission("batch:task:resume")
    @PutMapping("/{taskId}/resume")
    public Result<Void> resume(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId) {
        try {
            batchTransferTaskService.resumeTask(taskId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error("参数错误: " + e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 停止任务
     */
    @Operation(summary = "停止任务", description = "将RUNNING或PAUSED状态的任务停止为READY")
    @Log(title = "批量传输任务-停止", businessType = BusinessType.UPDATE)
    @RequirePermission("batch:task:stop")
    @DeleteMapping("/{taskId}/stop")
    public Result<Void> stop(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId) {
        try {
            batchTransferTaskService.stopTask(taskId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error("参数错误: " + e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解析逗号分隔的ID字符串为Long列表
     */
    private List<Long> parseIds(String idsStr) {
        if (idsStr == null || idsStr.trim().isEmpty()) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        
        String[] parts = idsStr.split(",");
        List<Long> idList = new java.util.ArrayList<>();
        
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                try {
                    idList.add(Long.parseLong(part));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("无效的任务ID格式: " + part);
                }
            }
        }
        
        if (idList.isEmpty()) {
            throw new IllegalArgumentException("至少需要提供一个有效的任务ID");
        }
        
        return idList;
    }
}
