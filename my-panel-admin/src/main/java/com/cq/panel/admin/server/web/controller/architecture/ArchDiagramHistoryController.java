package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchDiagramHistory;
import com.cq.panel.admin.server.repository.service.IArchDiagramHistoryService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramHistoryDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramHistoryVO;
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
 * 架构图版本历史 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/history")
@RequiredArgsConstructor
@Tag(name = "架构图版本历史", description = "架构图版本历史相关接口")
public class ArchDiagramHistoryController extends BaseController {
    
    private final IArchDiagramHistoryService historyService;
    private final ArchConverter converter;

    @Operation(summary = "查询版本历史列表", description = "分页查询版本历史列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchDiagramHistoryVO>> list(
            @Parameter(description = "查询参数") ArchDiagramHistoryDTO dto) {
        startPage();
        ArchDiagramHistory history = converter.toEntity(dto);
        List<ArchDiagramHistory> list = historyService.selectArchDiagramHistoryList(history);
        return Result.success(new PageInfo<>(converter.toHistoryVOList(list)));
    }

    @Operation(summary = "查询架构图版本历史", description = "根据架构图ID查询版本历史列表")
    @GetMapping("/diagram/{diagramId}")
    public Result<List<ArchDiagramHistoryVO>> getByDiagramId(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long diagramId) {
        return Result.success(converter.toHistoryVOList(historyService.selectHistoryByDiagramId(diagramId)));
    }

    @Operation(summary = "获取当前版本", description = "获取架构图的当前版本信息")
    @GetMapping("/current/{diagramId}")
    public Result<ArchDiagramHistoryVO> getCurrent(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long diagramId) {
        return Result.success(converter.toHistoryVO(historyService.selectCurrentVersion(diagramId)));
    }

    @Operation(summary = "获取版本详细信息", description = "根据ID获取版本详细信息")
    @GetMapping("/{id}")
    public Result<ArchDiagramHistoryVO> get(
            @Parameter(description = "历史ID", required = true) @PathVariable Long id) {
        return Result.success(converter.toHistoryVO(historyService.selectArchDiagramHistoryById(id)));
    }

    @Operation(summary = "创建版本快照", description = "为架构图创建版本快照")
    @PostMapping("/snapshot")
    @Log(title = "架构图版本管理", businessType = BusinessType.INSERT)
    public Result<ArchDiagramHistoryVO> createSnapshot(
            @Parameter(description = "架构图ID", required = true) @RequestParam Long diagramId,
            @Parameter(description = "版本号", required = true) @RequestParam String version,
            @Parameter(description = "版本名称") @RequestParam(required = false) String versionName,
            @Parameter(description = "变更摘要") @RequestParam(required = false) String changeSummary) {
        historyService.createSnapshot(diagramId, version, versionName, changeSummary);
        return Result.success(converter.toHistoryVO(historyService.selectCurrentVersion(diagramId)));
    }

    @Operation(summary = "恢复到指定版本", description = "将架构图恢复到指定版本")
    @PostMapping("/restore/{historyId}")
    @Log(title = "架构图版本管理", businessType = BusinessType.UPDATE)
    public Result<Void> restore(
            @Parameter(description = "历史ID", required = true) @PathVariable Long historyId) {
        historyService.restoreVersion(historyId);
        return Result.success();
    }

    @Operation(summary = "删除版本历史", description = "根据ID删除版本历史")
    @DeleteMapping("/{ids}")
    @Log(title = "架构图版本管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "历史ID数组", required = true) @PathVariable Long[] ids) {
        historyService.deleteArchDiagramHistoryByIds(ids);
        return Result.success();
    }
}