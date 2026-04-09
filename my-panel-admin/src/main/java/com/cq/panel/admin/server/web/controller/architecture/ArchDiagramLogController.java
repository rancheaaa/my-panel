package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchDiagramLog;
import com.cq.panel.admin.server.repository.service.IArchDiagramLogService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramLogDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramLogVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 架构图操作日志 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/log")
@RequiredArgsConstructor
@Tag(name = "架构图操作日志", description = "架构图操作日志相关接口")
public class ArchDiagramLogController extends BaseController {
    
    private final IArchDiagramLogService logService;
    private final ArchConverter converter;

    @Operation(summary = "查询操作日志列表", description = "分页查询操作日志列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchDiagramLogVO>> list(
            @Parameter(description = "查询参数") ArchDiagramLogDTO dto) {
        startPage();
        ArchDiagramLog log = converter.toEntity(dto);
        List<ArchDiagramLog> list = logService.selectArchDiagramLogList(log);
        return Result.success(new PageInfo<>(converter.toLogVOList(list)));
    }

    @Operation(summary = "查询架构图操作日志", description = "根据架构图ID查询操作日志列表")
    @GetMapping("/diagram/{diagramId}")
    public Result<List<ArchDiagramLogVO>> getByDiagramId(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long diagramId) {
        return Result.success(converter.toLogVOList(logService.selectLogsByDiagramId(diagramId)));
    }

    @Operation(summary = "获取操作日志详细信息", description = "根据ID获取操作日志详细信息")
    @GetMapping("/{id}")
    public Result<ArchDiagramLogVO> get(
            @Parameter(description = "日志ID", required = true) @PathVariable Long id) {
        return Result.success(converter.toLogVO(logService.selectArchDiagramLogById(id)));
    }

    @Operation(summary = "新增操作日志", description = "创建新的操作日志")
    @PostMapping
    @Log(title = "架构图操作日志管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ArchDiagramLogDTO dto) {
        logService.insertArchDiagramLog(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "清空操作日志", description = "清空指定架构图的操作日志")
    @DeleteMapping("/clear/{diagramId}")
    @Log(title = "架构图操作日志管理", businessType = BusinessType.DELETE)
    public Result<Void> clear(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long diagramId) {
        logService.clearLogsByDiagramId(diagramId);
        return Result.success();
    }

    @Operation(summary = "删除操作日志", description = "根据ID删除操作日志")
    @DeleteMapping("/{ids}")
    @Log(title = "架构图操作日志管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "日志ID数组", required = true) @PathVariable Long[] ids) {
        logService.deleteArchDiagramLogByIds(ids);
        return Result.success();
    }
}