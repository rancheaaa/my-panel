package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchDiagramFavorite;
import com.cq.panel.admin.server.repository.service.IArchDiagramFavoriteService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramFavoriteDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramFavoriteVO;
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
 * 架构图收藏 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/favorite")
@RequiredArgsConstructor
@Tag(name = "架构图收藏", description = "架构图收藏相关接口")
public class ArchDiagramFavoriteController extends BaseController {
    
    private final IArchDiagramFavoriteService favoriteService;
    private final ArchConverter converter;

    @Operation(summary = "查询收藏列表", description = "分页查询收藏列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchDiagramFavoriteVO>> list(
            @Parameter(description = "查询参数") ArchDiagramFavoriteDTO dto) {
        startPage();
        ArchDiagramFavorite favorite = converter.toEntity(dto);
        List<ArchDiagramFavorite> list = favoriteService.selectArchDiagramFavoriteList(favorite);
        PageInfo<ArchDiagramFavorite> entityPageInfo = new PageInfo<>(list);
        List<ArchDiagramFavoriteVO> voList = converter.toFavoriteVOList(list);
        PageInfo<ArchDiagramFavoriteVO> voPageInfo = new PageInfo<>(voList);
        voPageInfo.setTotal(entityPageInfo.getTotal());
        return Result.success(voPageInfo);
    }

    @Operation(summary = "查询用户收藏", description = "根据用户ID查询收藏列表")
    @GetMapping("/user/{userId}")
    public Result<List<ArchDiagramFavoriteVO>> getByUserId(
            @Parameter(description = "用户ID", required = true) @PathVariable Long userId) {
        return Result.success(converter.toFavoriteVOList(favoriteService.selectFavoritesByUserId(userId)));
    }

    @Operation(summary = "查询架构图收藏", description = "根据架构图ID查询收藏用户列表")
    @GetMapping("/diagram/{diagramId}")
    public Result<List<ArchDiagramFavoriteVO>> getByDiagramId(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long diagramId) {
        return Result.success(converter.toFavoriteVOList(favoriteService.selectFavoritesByDiagramId(diagramId)));
    }

    @Operation(summary = "检查是否已收藏", description = "检查用户是否已收藏指定架构图")
    @GetMapping("/check")
    public Result<Boolean> check(
            @Parameter(description = "用户ID", required = true) @RequestParam Long userId,
            @Parameter(description = "架构图ID", required = true) @RequestParam Long diagramId) {
        return Result.success(favoriteService.selectFavoriteByUserAndDiagram(userId, diagramId) != null);
    }

    @Operation(summary = "新增收藏", description = "创建新的收藏")
    @PostMapping
    @Log(title = "架构图收藏管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ArchDiagramFavoriteDTO dto) {
        favoriteService.insertArchDiagramFavorite(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "取消收藏", description = "取消指定的收藏")
    @PostMapping("/cancel")
    @Log(title = "架构图收藏管理", businessType = BusinessType.DELETE)
    public Result<Void> cancel(
            @Parameter(description = "用户ID", required = true) @RequestParam Long userId,
            @Parameter(description = "架构图ID", required = true) @RequestParam Long diagramId) {
        favoriteService.cancelFavorite(userId, diagramId);
        return Result.success();
    }

    @Operation(summary = "删除收藏", description = "根据ID删除收藏")
    @DeleteMapping("/{ids}")
    @Log(title = "架构图收藏管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "收藏ID数组", required = true) @PathVariable Long[] ids) {
        favoriteService.deleteArchDiagramFavoriteByIds(ids);
        return Result.success();
    }
}