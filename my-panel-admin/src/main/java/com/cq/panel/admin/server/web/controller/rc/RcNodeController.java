package com.cq.panel.admin.server.web.controller.rc;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.RcNode;
import com.cq.panel.admin.server.repository.service.IRcNodeService;
import com.cq.panel.admin.server.web.domain.dto.rc.RcNodeDTO;
import com.cq.panel.admin.server.web.domain.dto.rc.RcNodeQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.rc.RcNodeVO;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.rc.RcNodeConverter;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 注册中心 Controller
 * 
 * @author cq
 */
@Tag(name = "注册中心", description = "注册中心相关接口")
@RestController
@RequestMapping("/rc/node")
public class RcNodeController extends BaseController
{
    private final IRcNodeService rcNodeService;

    private final RcNodeConverter rcNodeConverter;

    public RcNodeController(IRcNodeService rcNodeService, RcNodeConverter rcNodeConverter) {
        this.rcNodeService = rcNodeService;
        this.rcNodeConverter = rcNodeConverter;
    }

    /**
     * 查询注册中心节点列表
     */
    @Operation(summary = "查询注册中心节点列表", description = "根据条件分页获取注册中心节点列表")
    @RequirePermission("rc:registryCenter:list")
    @GetMapping("/list")
    public Result<PageVO<RcNodeVO>> list(@Parameter(description = "查询参数") RcNodeQueryDTO query)
    {
        startPage();
        RcNode rcNode = rcNodeConverter.toEntity(query);
        List<RcNode> list = rcNodeService.selectRcNodeList(rcNode);
        List<RcNodeVO> voList = rcNodeConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }

    /**
     * 导出注册中心节点列表
     */
    @Operation(summary = "导出注册中心节点列表", description = "导出符合条件的注册中心节点数据")
    @Log(title = "注册中心", businessType = BusinessType.EXPORT)
    @RequirePermission("rc:registryCenter:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") RcNodeQueryDTO query)
    {
        RcNode rcNode = rcNodeConverter.toEntity(query);
        List<RcNode> list = rcNodeService.selectRcNodeList(rcNode);
        ExcelUtil<RcNode> util = new ExcelUtil<>(RcNode.class);
        util.exportExcel(response, list, "注册中心数据");
    }

    /**
     * 获取注册中心节点详细信息
     */
    @Operation(summary = "获取注册中心节点详细信息", description = "根据节点ID获取注册中心节点详细信息")
    @RequirePermission("rc:registryCenter:query")
    @GetMapping(value = "/{id}")
    public Result<RcNodeVO> getInfo(@Parameter(description = "节点ID", required = true) @PathVariable("id") Long id)
    {
        return Result.success(rcNodeConverter.toVO(rcNodeService.selectRcNodeById(id)));
    }

    /**
     * 新增注册中心节点
     */
    @Operation(summary = "新增注册中心节点", description = "新增注册中心节点信息")
    @RequirePermission("rc:registryCenter:add")
    @Log(title = "注册中心", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody RcNodeDTO dto)
    {
        RcNode rcNode = rcNodeConverter.toEntity(dto);
        if (!rcNodeService.checkNodeUnique(rcNode))
        {
            return Result.error("新增节点'" + rcNode.getNodeIp() + ":" + rcNode.getNodePort() + "'失败，该节点已存在");
        }
        rcNodeService.insertRcNode(rcNode);
        return Result.success();
    }

    /**
     * 修改注册中心节点
     */
    @Operation(summary = "修改注册中心节点", description = "修改注册中心节点信息")
    @RequirePermission("rc:registryCenter:edit")
    @Log(title = "注册中心", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody RcNodeDTO dto)
    {
        RcNode rcNode = rcNodeConverter.toEntity(dto);
        if (!rcNodeService.checkNodeUnique(rcNode))
        {
            return Result.error("修改节点'" + rcNode.getNodeIp() + ":" + rcNode.getNodePort() + "'失败，该节点已存在");
        }
        rcNodeService.updateRcNode(rcNode);
        return Result.success();
    }

    /**
     * 删除注册中心节点
     */
    @Operation(summary = "删除注册中心节点", description = "批量删除注册中心节点")
    @RequirePermission("rc:registryCenter:remove")
    @Log(title = "注册中心", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public Result<Void> remove(@Parameter(description = "节点ID数组", required = true) @PathVariable Long[] ids)
    {
        rcNodeService.deleteRcNodeByIds(ids);
        return Result.success();
    }
}
