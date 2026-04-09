package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchDiagramComment;
import com.cq.panel.admin.server.repository.service.IArchDiagramCommentService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramCommentDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramCommentVO;
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
 * 架构图评论 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/comment")
@RequiredArgsConstructor
@Tag(name = "架构图评论", description = "架构图评论相关接口")
public class ArchDiagramCommentController extends BaseController {
    
    private final IArchDiagramCommentService commentService;
    private final ArchConverter converter;

    @Operation(summary = "查询评论列表", description = "分页查询评论列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchDiagramCommentVO>> list(
            @Parameter(description = "查询参数") ArchDiagramCommentDTO dto) {
        startPage();
        ArchDiagramComment comment = converter.toEntity(dto);
        List<ArchDiagramComment> list = commentService.selectArchDiagramCommentList(comment);
        return Result.success(new PageInfo<>(converter.toCommentVOList(list)));
    }

    @Operation(summary = "查询架构图评论", description = "根据架构图ID查询评论列表")
    @GetMapping("/diagram/{diagramId}")
    public Result<List<ArchDiagramCommentVO>> getByDiagramId(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long diagramId) {
        return Result.success(converter.toCommentVOList(commentService.selectCommentsByDiagramId(diagramId)));
    }

    @Operation(summary = "查询节点评论", description = "根据节点ID查询评论列表")
    @GetMapping("/node/{nodeId}")
    public Result<List<ArchDiagramCommentVO>> getByNodeId(
            @Parameter(description = "节点ID", required = true) @PathVariable Long nodeId) {
        return Result.success(converter.toCommentVOList(commentService.selectCommentsByNodeId(nodeId)));
    }

    @Operation(summary = "查询边缘评论", description = "根据边缘ID查询评论列表")
    @GetMapping("/edge/{edgeId}")
    public Result<List<ArchDiagramCommentVO>> getByEdgeId(
            @Parameter(description = "边缘ID", required = true) @PathVariable Long edgeId) {
        return Result.success(converter.toCommentVOList(commentService.selectCommentsByEdgeId(edgeId)));
    }

    @Operation(summary = "查询回复列表", description = "根据父评论ID查询回复列表")
    @GetMapping("/replies/{parentCommentId}")
    public Result<List<ArchDiagramCommentVO>> getReplies(
            @Parameter(description = "父评论ID", required = true) @PathVariable Long parentCommentId) {
        return Result.success(converter.toCommentVOList(commentService.selectRepliesByParentId(parentCommentId)));
    }

    @Operation(summary = "获取评论详细信息", description = "根据ID获取评论详细信息")
    @GetMapping("/{id}")
    public Result<ArchDiagramCommentVO> get(
            @Parameter(description = "评论ID", required = true) @PathVariable Long id) {
        return Result.success(converter.toCommentVO(commentService.selectArchDiagramCommentById(id)));
    }

    @Operation(summary = "新增评论", description = "创建新的评论")
    @PostMapping
    @Log(title = "架构图评论管理", businessType = BusinessType.INSERT)
    public Result<ArchDiagramCommentVO> add(@Valid @RequestBody ArchDiagramCommentDTO dto) {
        commentService.insertArchDiagramComment(converter.toEntity(dto));
        return Result.success(converter.toCommentVO(commentService.selectArchDiagramCommentById(dto.getId())));
    }

    @Operation(summary = "修改评论", description = "修改评论信息")
    @PutMapping
    @Log(title = "架构图评论管理", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ArchDiagramCommentDTO dto) {
        commentService.updateArchDiagramComment(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "点赞评论", description = "为评论增加点赞数")
    @PostMapping("/like/{id}")
    @Log(title = "架构图评论管理", businessType = BusinessType.UPDATE)
    public Result<Void> like(
            @Parameter(description = "评论ID", required = true) @PathVariable Long id) {
        commentService.incrementLikeCount(id);
        return Result.success();
    }

    @Operation(summary = "标记为已解决", description = "将评论标记为已解决")
    @PostMapping("/resolve/{id}")
    @Log(title = "架构图评论管理", businessType = BusinessType.UPDATE)
    public Result<Void> resolve(
            @Parameter(description = "评论ID", required = true) @PathVariable Long id) {
        commentService.markAsResolved(id);
        return Result.success();
    }

    @Operation(summary = "删除评论", description = "根据ID删除评论")
    @DeleteMapping("/{ids}")
    @Log(title = "架构图评论管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "评论ID数组", required = true) @PathVariable Long[] ids) {
        commentService.deleteArchDiagramCommentByIds(ids);
        return Result.success();
    }
}