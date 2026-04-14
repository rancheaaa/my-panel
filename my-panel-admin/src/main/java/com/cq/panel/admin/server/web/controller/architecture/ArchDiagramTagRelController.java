package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchDiagramTagRel;
import com.cq.panel.admin.server.repository.service.IArchDiagramTagRelService;
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
 * 架构图标签关联 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/tag/rel")
@RequiredArgsConstructor
@Tag(name = "架构图标签关联", description = "架构图标签关联相关接口")
public class ArchDiagramTagRelController extends BaseController {
    
    private final IArchDiagramTagRelService tagRelService;

    @Operation(summary = "查询标签关联列表", description = "分页查询标签关联列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchDiagramTagRel>> list(
            @Parameter(description = "查询参数") ArchDiagramTagRel rel) {
        startPage();
        List<ArchDiagramTagRel> list = tagRelService.selectArchDiagramTagRelList(rel);
        return Result.success(new PageInfo<>(list));
    }

    @Operation(summary = "根据节点ID查询标签ID列表", description = "根据节点ID查询关联的标签ID列表")
    @GetMapping("/node/{nodeId}")
    public Result<List<Long>> getTagIdsByNodeId(
            @Parameter(description = "节点ID", required = true) @PathVariable Long nodeId) {
        return Result.success(tagRelService.selectTagIdsByNodeId(nodeId));
    }

    @Operation(summary = "新增标签关联", description = "创建新的标签关联")
    @PostMapping
    @Log(title = "架构图标签关联管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ArchDiagramTagRel rel) {
        tagRelService.insertArchDiagramTagRel(rel);
        return Result.success();
    }

    @Operation(summary = "批量新增标签关联", description = "批量创建标签关联")
    @PostMapping("/batch")
    @Log(title = "架构图标签关联管理", businessType = BusinessType.INSERT)
    public Result<Void> batchAdd(@RequestBody List<ArchDiagramTagRel> relList) {
        tagRelService.batchInsertArchDiagramTagRel(relList);
        return Result.success();
    }

    @Operation(summary = "删除标签关联", description = "根据ID删除标签关联")
    @DeleteMapping("/{ids}")
    @Log(title = "架构图标签关联管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "标签关联ID数组", required = true) @PathVariable Long[] ids) {
        tagRelService.deleteArchDiagramTagRelByIds(ids);
        return Result.success();
    }

    @Operation(summary = "根据节点ID删除标签关联", description = "根据节点ID删除所有关联的标签")
    @DeleteMapping("/node/{nodeId}")
    @Log(title = "架构图标签关联管理", businessType = BusinessType.DELETE)
    public Result<Void> removeByNodeId(
            @Parameter(description = "节点ID", required = true) @PathVariable Long nodeId) {
        tagRelService.deleteArchDiagramTagRelByNodeId(nodeId);
        return Result.success();
    }
}
