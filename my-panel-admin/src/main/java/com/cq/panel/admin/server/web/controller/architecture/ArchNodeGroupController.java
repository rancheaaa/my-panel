package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.repository.domain.ArchNodeGroup;
import com.cq.panel.admin.server.repository.service.IArchNodeGroupService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchNodeGroupDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchNodeGroupVO;
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
 * 节点分组 Controller
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch/nodeGroup")
@RequiredArgsConstructor
@Tag(name = "节点分组管理", description = "节点分组相关接口")
public class ArchNodeGroupController extends BaseController {
    
    private final IArchNodeGroupService nodeGroupService;
    private final ArchConverter converter;

    @Operation(summary = "查询节点分组列表", description = "分页查询节点分组列表")
    @GetMapping("/list")
    public Result<PageInfo<ArchNodeGroupVO>> list(
            @Parameter(description = "查询参数") ArchNodeGroupDTO dto) {
        startPage();
        ArchNodeGroup nodeGroup = converter.toEntity(dto);
        List<ArchNodeGroup> list = nodeGroupService.selectArchNodeGroupList(nodeGroup);
        PageInfo<ArchNodeGroup> entityPageInfo = new PageInfo<>(list);
        List<ArchNodeGroupVO> voList = converter.toNodeGroupVOList(list);
        PageInfo<ArchNodeGroupVO> voPageInfo = new PageInfo<>(voList);
        voPageInfo.setTotal(entityPageInfo.getTotal());
        return Result.success(voPageInfo);
    }

    @Operation(summary = "查询架构图分组", description = "根据架构图ID查询分组列表")
    @GetMapping("/diagram/{diagramId}")
    public Result<List<ArchNodeGroupVO>> getByDiagramId(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long diagramId) {
        return Result.success(converter.toNodeGroupVOList(nodeGroupService.selectGroupsByDiagramId(diagramId)));
    }

    @Operation(summary = "获取分组详细信息", description = "根据ID获取分组详细信息")
    @GetMapping("/{id}")
    public Result<ArchNodeGroupVO> get(
            @Parameter(description = "分组ID", required = true) @PathVariable Long id) {
        return Result.success(converter.toNodeGroupVO(nodeGroupService.selectArchNodeGroupById(id)));
    }

    @Operation(summary = "新增节点分组", description = "创建新的节点分组")
    @PostMapping
    @Log(title = "节点分组管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ArchNodeGroupDTO dto) {
        nodeGroupService.insertArchNodeGroup(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "修改节点分组", description = "修改节点分组信息")
    @PutMapping
    @Log(title = "节点分组管理", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ArchNodeGroupDTO dto) {
        nodeGroupService.updateArchNodeGroup(converter.toEntity(dto));
        return Result.success();
    }

    @Operation(summary = "删除节点分组", description = "根据ID删除节点分组")
    @DeleteMapping("/{ids}")
    @Log(title = "节点分组管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(
            @Parameter(description = "分组ID数组", required = true) @PathVariable Long[] ids) {
        nodeGroupService.deleteArchNodeGroupByIds(ids);
        return Result.success();
    }
}