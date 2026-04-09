package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchNodeType;
import com.cq.panel.admin.server.repository.service.IArchNodeTypeService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchNodeTypeDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchNodeTypeVO;
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
 * 节点类型 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/nodeType")
@RequiredArgsConstructor
@Tag(name = "节点类型管理", description = "节点类型相关接口")
public class ArchNodeTypeController extends BaseController {
    
    private final IArchNodeTypeService nodeTypeService;
    private final ArchConverter converter;

    @Operation(summary = "查询节点类型列表", description = "分页查询节点类型列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchNodeTypeVO>> list(
            @Parameter(description = "查询参数") ArchNodeTypeDTO dto) {
        startPage();
        ArchNodeType nodeType = converter.toEntity(dto);
        List<ArchNodeType> list = nodeTypeService.selectArchNodeTypeList(nodeType);
        return Result.success(new PageInfo<>(converter.toNodeTypeVOList(list)));
    }

    @Operation(summary = "查询所有节点类型", description = "查询所有启用的节点类型")
    @GetMapping("/all")
    public Result<List<ArchNodeTypeVO>> all() {
        return Result.success(converter.toNodeTypeVOList(nodeTypeService.selectArchNodeTypeAll()));
    }

    @Operation(summary = "获取节点类型详细信息", description = "根据ID获取节点类型详细信息")
    @GetMapping("/{id}")
    public Result<ArchNodeTypeVO> get(
            @Parameter(description = "节点类型ID", required = true) @PathVariable Long id) {
        return Result.success(converter.toNodeTypeVO(nodeTypeService.selectArchNodeTypeById(id)));
    }

    @Operation(summary = "新增节点类型", description = "创建新的节点类型")
    @PostMapping
    @Log(title = "节点类型管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ArchNodeTypeDTO dto) {
        if (!nodeTypeService.checkTypeCodeUnique(converter.toEntity(dto))) {
            return Result.error("新增节点类型'" + dto.getTypeName() + "'失败，类型编码已存在");
        }
        nodeTypeService.insertArchNodeType(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "修改节点类型", description = "修改节点类型信息")
    @PutMapping
    @Log(title = "节点类型管理", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ArchNodeTypeDTO dto) {
        if (!nodeTypeService.checkTypeCodeUnique(converter.toEntity(dto))) {
            return Result.error("修改节点类型'" + dto.getTypeName() + "'失败，类型编码已存在");
        }
        nodeTypeService.updateArchNodeType(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "删除节点类型", description = "根据ID删除节点类型")
    @DeleteMapping("/{ids}")
    @Log(title = "节点类型管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "节点类型ID数组", required = true) @PathVariable Long[] ids) {
        nodeTypeService.deleteArchNodeTypeByIds(ids);
        return Result.success();
    }
}