package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchDiagramShare;
import com.cq.panel.admin.server.repository.service.IArchDiagramShareService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramShareDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramShareVO;
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
 * 架构图分享 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/share")
@RequiredArgsConstructor
@Tag(name = "架构图分享", description = "架构图分享相关接口")
public class ArchDiagramShareController extends BaseController {
    
    private final IArchDiagramShareService shareService;
    private final ArchConverter converter;

    @Operation(summary = "查询分享列表", description = "分页查询分享列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchDiagramShareVO>> list(
            @Parameter(description = "查询参数") ArchDiagramShareDTO dto) {
        startPage();
        ArchDiagramShare share = converter.toEntity(dto);
        List<ArchDiagramShare> list = shareService.selectArchDiagramShareList(share);
        PageInfo<ArchDiagramShare> entityPageInfo = new PageInfo<>(list);
        List<ArchDiagramShareVO> voList = converter.toShareVOList(list);
        PageInfo<ArchDiagramShareVO> voPageInfo = new PageInfo<>(voList);
        voPageInfo.setTotal(entityPageInfo.getTotal());
        return Result.success(voPageInfo);
    }

    @Operation(summary = "查询架构图分享", description = "根据架构图ID查询分享列表")
    @GetMapping("/diagram/{diagramId}")
    public Result<List<ArchDiagramShareVO>> getByDiagramId(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long diagramId) {
        return Result.success(converter.toShareVOList(shareService.selectSharesByDiagramId(diagramId)));
    }

    @Operation(summary = "根据分享码获取分享信息", description = "通过分享码获取分享信息")
    @GetMapping("/code/{shareCode}")
    public Result<ArchDiagramShareVO> getByCode(
            @Parameter(description = "分享码", required = true) @PathVariable String shareCode) {
        return Result.success(converter.toShareVO(shareService.selectArchDiagramShareByCode(shareCode)));
    }

    @Operation(summary = "验证分享有效性", description = "验证分享码是否有效")
    @GetMapping("/validate/{shareCode}")
    public Result<Boolean> validate(
            @Parameter(description = "分享码", required = true) @PathVariable String shareCode) {
        return Result.success(shareService.validateShare(shareCode));
    }

    @Operation(summary = "获取分享详细信息", description = "根据ID获取分享详细信息")
    @GetMapping("/{id}")
    public Result<ArchDiagramShareVO> get(
            @Parameter(description = "分享ID", required = true) @PathVariable Long id) {
        return Result.success(converter.toShareVO(shareService.selectArchDiagramShareById(id)));
    }

    @Operation(summary = "创建分享", description = "创建新的分享")
    @PostMapping
    @Log(title = "架构图分享管理", businessType = BusinessType.INSERT)
    public Result<ArchDiagramShareVO> add(@Valid @RequestBody ArchDiagramShareDTO dto) {
        shareService.insertArchDiagramShare(converter.toEntity(dto));
        return Result.success(converter.toShareVO(shareService.selectArchDiagramShareByCode(dto.getShareCode())));
    }

    @Operation(summary = "修改分享", description = "修改分享信息")
    @PutMapping
    @Log(title = "架构图分享管理", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ArchDiagramShareDTO dto) {
        shareService.updateArchDiagramShare(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "撤销分享", description = "撤销指定的分享")
    @PostMapping("/revoke/{id}")
    @Log(title = "架构图分享管理", businessType = BusinessType.UPDATE)
    public Result<Void> revoke(
            @Parameter(description = "分享ID", required = true) @PathVariable Long id) {
        shareService.revokeShare(id);
        return Result.success();
    }

    @Operation(summary = "删除分享", description = "根据ID删除分享")
    @DeleteMapping("/{ids}")
    @Log(title = "架构图分享管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "分享ID数组", required = true) @PathVariable Long[] ids) {
        shareService.deleteArchDiagramShareByIds(ids);
        return Result.success();
    }
}