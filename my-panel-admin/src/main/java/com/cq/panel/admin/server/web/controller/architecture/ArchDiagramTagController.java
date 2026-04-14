package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchDiagramTag;
import com.cq.panel.admin.server.repository.service.IArchDiagramTagService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramTagDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramTagVO;
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
 * 架构图标签 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/tag")
@RequiredArgsConstructor
@Tag(name = "架构图标签", description = "架构图标签相关接口")
public class ArchDiagramTagController extends BaseController {
    
    private final IArchDiagramTagService tagService;
    private final ArchConverter converter;

    @Operation(summary = "查询标签列表", description = "分页查询标签列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchDiagramTagVO>> list(
            @Parameter(description = "查询参数") ArchDiagramTagDTO dto) {
        startPage();
        ArchDiagramTag tag = converter.toEntity(dto);
        List<ArchDiagramTag> list = tagService.selectArchDiagramTagList(tag);
        PageInfo<ArchDiagramTag> entityPageInfo = new PageInfo<>(list);
        List<ArchDiagramTagVO> voList = converter.toTagVOList(list);
        PageInfo<ArchDiagramTagVO> voPageInfo = new PageInfo<>(voList);
        voPageInfo.setTotal(entityPageInfo.getTotal());
        return Result.success(voPageInfo);
    }

    @Operation(summary = "查询所有标签", description = "查询所有启用的标签")
    @GetMapping("/all")
    public Result<List<ArchDiagramTagVO>> all() {
        return Result.success(converter.toTagVOList(tagService.selectArchDiagramTagAll()));
    }

    @Operation(summary = "获取标签详细信息", description = "根据ID获取标签详细信息")
    @GetMapping("/{id}")
    public Result<ArchDiagramTagVO> get(
            @Parameter(description = "标签ID", required = true) @PathVariable Long id) {
        return Result.success(converter.toTagVO(tagService.selectArchDiagramTagById(id)));
    }

    @Operation(summary = "新增标签", description = "创建新的标签")
    @PostMapping
    @Log(title = "架构图标签管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ArchDiagramTagDTO dto) {
        if (!tagService.checkTagNameUnique(converter.toEntity(dto))) {
            return Result.error("新增标签'" + dto.getTagName() + "'失败，标签名称已存在");
        }
        tagService.insertArchDiagramTag(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "修改标签", description = "修改标签信息")
    @PutMapping
    @Log(title = "架构图标签管理", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ArchDiagramTagDTO dto) {
        if (!tagService.checkTagNameUnique(converter.toEntity(dto))) {
            return Result.error("修改标签'" + dto.getTagName() + "'失败，标签名称已存在");
        }
        tagService.updateArchDiagramTag(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "删除标签", description = "根据ID删除标签")
    @DeleteMapping("/{ids}")
    @Log(title = "架构图标签管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "标签ID数组", required = true) @PathVariable Long[] ids) {
        tagService.deleteArchDiagramTagByIds(ids);
        return Result.success();
    }


}