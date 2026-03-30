package com.cq.panel.admin.server.web.controller.agent;

import com.cq.panel.admin.server.common.annotation.Log;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.common.utils.poi.ExcelUtil;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.service.IAgentRegistryService;
import com.cq.panel.admin.server.web.domain.dto.agent.AgentRegistryDTO;
import com.cq.panel.admin.server.web.domain.dto.agent.AgentRegistryQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.agent.AgentRegistryVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.agent.AgentRegistryConverter;
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
 * Agent注册信息 Controller
 * 
 * @author cq
 */
@Tag(name = "Agent注册管理", description = "Agent注册信息相关接口")
@RestController
@RequestMapping("/agent/registry")
public class AgentRegistryController extends BaseController
{
    private final IAgentRegistryService agentRegistryService;

    private final AgentRegistryConverter agentRegistryConverter;

    public AgentRegistryController(IAgentRegistryService agentRegistryService, AgentRegistryConverter agentRegistryConverter) {
        this.agentRegistryService = agentRegistryService;
        this.agentRegistryConverter = agentRegistryConverter;
    }

    /**
     * 查询Agent注册信息列表
     */
    @Operation(summary = "查询Agent注册信息列表", description = "根据条件分页获取Agent注册信息列表")
    @RequirePermission("agent:registry:list")
    @GetMapping("/list")
    public Result<PageVO<AgentRegistryVO>> list(@Parameter(description = "查询参数") AgentRegistryQueryDTO query)
    {
        startPage();
        AgentRegistry agentRegistry = agentRegistryConverter.toEntity(query);
        List<AgentRegistry> list = agentRegistryService.selectAgentRegistryList(agentRegistry);
        List<AgentRegistryVO> voList = agentRegistryConverter.toVOList(list);
        return Result.success(new PageVO<>(voList, new PageInfo<>(list).getTotal()));
    }

    /**
     * 导出Agent注册信息列表
     */
    @Operation(summary = "导出Agent注册信息列表", description = "导出符合条件的Agent注册信息数据")
    @Log(title = "Agent注册信息", businessType = BusinessType.EXPORT)
    @RequirePermission("agent:registry:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Parameter(description = "查询参数") AgentRegistryQueryDTO query)
    {
        AgentRegistry agentRegistry = agentRegistryConverter.toEntity(query);
        List<AgentRegistry> list = agentRegistryService.selectAgentRegistryList(agentRegistry);
        ExcelUtil<AgentRegistry> util = new ExcelUtil<>(AgentRegistry.class);
        util.exportExcel(response, list, "Agent注册信息数据");
    }

    /**
     * 获取Agent注册信息详细信息
     */
    @Operation(summary = "获取Agent注册信息详细信息", description = "根据节点ID获取Agent注册信息详细信息")
    @RequirePermission("agent:registry:query")
    @GetMapping(value = "/{id}")
    public Result<AgentRegistryVO> getInfo(@Parameter(description = "节点ID", required = true) @PathVariable("id") String id)
    {
        return Result.success(agentRegistryConverter.toVO(agentRegistryService.selectAgentRegistryById(id)));
    }

    /**
     * 新增Agent注册信息
     */
    @Operation(summary = "新增Agent注册信息", description = "新增Agent注册信息")
    @RequirePermission("agent:registry:add")
    @Log(title = "Agent注册信息", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Void> add(@Validated @RequestBody AgentRegistryDTO dto)
    {
        AgentRegistry agentRegistry = agentRegistryConverter.toEntity(dto);
        agentRegistryService.insertAgentRegistry(agentRegistry);
        return Result.success();
    }

    /**
     * 修改Agent注册信息
     */
    @Operation(summary = "修改Agent注册信息", description = "修改Agent注册信息")
    @RequirePermission("agent:registry:edit")
    @Log(title = "Agent注册信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody AgentRegistryDTO dto)
    {
        AgentRegistry agentRegistry = agentRegistryConverter.toEntity(dto);
        agentRegistryService.updateAgentRegistry(agentRegistry);
        return Result.success();
    }

    /**
     * 删除Agent注册信息
     */
    @Operation(summary = "删除Agent注册信息", description = "批量删除Agent注册信息")
    @RequirePermission("agent:registry:remove")
    @Log(title = "Agent注册信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public Result<Void> remove(@Parameter(description = "节点ID数组", required = true) @PathVariable String[] ids)
    {
        agentRegistryService.deleteAgentRegistryByIds(ids);
        return Result.success();
    }

    /**
     * Agent注册接口
     */
    @Operation(summary = "Agent注册", description = "Agent客户端注册接口")
    @PostMapping("/register")
    public Result<AgentRegistryVO> register(@Validated @RequestBody AgentRegistryDTO dto)
    {
        AgentRegistry agentRegistry = agentRegistryConverter.toEntity(dto);
        AgentRegistry result = agentRegistryService.registerAgent(agentRegistry);
        return Result.success(agentRegistryConverter.toVO(result));
    }

    /**
     * Agent心跳接口
     */
    @Operation(summary = "Agent心跳", description = "Agent客户端心跳接口")
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@Parameter(description = "Agent IP", required = true) @RequestParam String agentIp,
                                  @Parameter(description = "Agent端口", required = true) @RequestParam Integer agentPort)
    {
        boolean success = agentRegistryService.heartbeat(agentIp, agentPort);
        return success ? Result.success() : Result.error("Agent不存在");
    }

    /**
     * 下线超时节点
     */
    @Operation(summary = "下线超时节点", description = "下线超时的Agent节点")
    @RequirePermission("agent:registry:offline")
    @Log(title = "Agent注册信息", businessType = BusinessType.UPDATE)
    @PostMapping("/offline")
    public Result<Integer> offline(@Parameter(description = "超时时间（秒）", required = true) @RequestParam Integer timeoutSeconds)
    {
        int count = agentRegistryService.offlineTimeoutNodes(timeoutSeconds);
        return Result.success(count);
    }
}