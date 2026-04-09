package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchDiagramTemplate;
import com.cq.panel.admin.server.repository.service.IArchDiagramTemplateService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramTemplateDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramTemplateVO;
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
 * 架构图模板 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/template")
@RequiredArgsConstructor
@Tag(name = "架构图模板", description = "架构图模板相关接口")
public class ArchDiagramTemplateController extends BaseController {
    
    private final IArchDiagramTemplateService templateService;
    private final ArchConverter converter;

    @Operation(summary = "查询模板列表", description = "分页查询模板列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchDiagramTemplateVO>> list(
            @Parameter(description = "查询参数") ArchDiagramTemplateDTO dto) {
        startPage();
        ArchDiagramTemplate template = converter.toEntity(dto);
        List<ArchDiagramTemplate> list = templateService.selectArchDiagramTemplateList(template);
        PageInfo<ArchDiagramTemplate> entityPageInfo = new PageInfo<>(list);
        List<ArchDiagramTemplateVO> voList = converter.toTemplateVOList(list);
        PageInfo<ArchDiagramTemplateVO> voPageInfo = new PageInfo<>(voList);
        voPageInfo.setTotal(entityPageInfo.getTotal());
        return Result.success(voPageInfo);
    }

    @Operation(summary = "查询所有模板", description = "查询所有启用的模板")
    @GetMapping("/all")
    public Result<List<ArchDiagramTemplateVO>> all() {
        return Result.success(converter.toTemplateVOList(templateService.selectArchDiagramTemplateAll()));
    }

    @Operation(summary = "获取模板详细信息", description = "根据ID获取模板详细信息")
    @GetMapping("/{id}")
    public Result<ArchDiagramTemplateVO> get(
            @Parameter(description = "模板ID", required = true) @PathVariable Long id) {
        return Result.success(converter.toTemplateVO(templateService.selectArchDiagramTemplateById(id)));
    }

    @Operation(summary = "新增模板", description = "创建新的模板")
    @PostMapping
    @Log(title = "架构图模板管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ArchDiagramTemplateDTO dto) {
        templateService.insertArchDiagramTemplate(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "修改模板", description = "修改模板信息")
    @PutMapping
    @Log(title = "架构图模板管理", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ArchDiagramTemplateDTO dto) {
        templateService.updateArchDiagramTemplate(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "删除模板", description = "根据ID删除模板")
    @DeleteMapping("/{ids}")
    @Log(title = "架构图模板管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "模板ID数组", required = true) @PathVariable Long[] ids) {
        templateService.deleteArchDiagramTemplateByIds(ids);
        return Result.success();
    }

    @Operation(summary = "使用模板", description = "使用模板创建架构图")
    @PostMapping("/use/{templateId}")
    @Log(title = "架构图模板管理", businessType = BusinessType.OTHER)
    public Result<Void> use(
            @Parameter(description = "模板ID", required = true) @PathVariable Long templateId) {
        templateService.useTemplate(templateId);
        return Result.success();
    }

    @Operation(summary = "评分模板", description = "对模板进行评分")
    @PostMapping("/rate/{templateId}")
    @Log(title = "架构图模板管理", businessType = BusinessType.OTHER)
    public Result<Void> rate(
            @Parameter(description = "模板ID", required = true) @PathVariable Long templateId,
            @Parameter(description = "评分", required = true) @RequestParam Double rating) {
        templateService.rateTemplate(templateId, rating);
        return Result.success();
    }
}