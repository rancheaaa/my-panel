package com.cq.panel.admin.server.web.controller.agent;

import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.repository.domain.AgentCommandHistory;
import com.cq.panel.admin.server.repository.service.IAgentCommandHistoryService;
import com.cq.panel.admin.server.web.domain.dto.agent.AgentCommandHistoryQueryDTO;
import com.cq.panel.admin.server.web.domain.vo.agent.AgentCommandHistoryVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.converter.agent.AgentCommandHistoryConverter;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cq.panel.authlite.annotation.RequirePermission;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "Agent命令执行历史", description = "Agent命令执行历史相关接口")
@RestController
@RequestMapping("/agent/command-history")
public class AgentCommandHistoryController extends BaseController
{
    private final IAgentCommandHistoryService agentCommandHistoryService;
    private final AgentCommandHistoryConverter converter;

    public AgentCommandHistoryController(IAgentCommandHistoryService agentCommandHistoryService,
                                         AgentCommandHistoryConverter converter) {
        this.agentCommandHistoryService = agentCommandHistoryService;
        this.converter = converter;
    }

    @Operation(summary = "查询命令历史列表", description = "根据条件分页查询命令执行历史")
    @RequirePermission("agent:registry:list")
    @GetMapping("/list")
    public Result<PageVO<AgentCommandHistoryVO>> list(@Parameter(description = "查询参数") AgentCommandHistoryQueryDTO query) {
        startPage();
        AgentCommandHistory entity = new AgentCommandHistory();
        entity.setAgentId(query.getAgentId());
        entity.setAgentName(query.getAgentName());
        entity.setCommandStatus(query.getCommandStatus());
        entity.setUserId(query.getUserId());
        if (query instanceof java.util.Map) {
            // handled by base controller params
        }
        List<AgentCommandHistory> list = agentCommandHistoryService.selectList(entity);
        return Result.success(new PageVO<>(converter.toVOList(list), new PageInfo<>(list).getTotal()));
    }

    @Operation(summary = "获取命令详情", description = "根据ID获取单条命令执行详情")
    @RequirePermission("agent:registry:query")
    @GetMapping("/{id}")
    public Result<AgentCommandHistoryVO> getInfo(@PathVariable Long id) {
        return Result.success(converter.toVO(agentCommandHistoryService.selectById(id)));
    }

    @Operation(summary = "删除命令历史", description = "批量删除命令执行记录")
    @RequirePermission("agent:registry:remove")
    @DeleteMapping("/{ids}")
    public Result<Void> remove(@PathVariable Long[] ids) {
        agentCommandHistoryService.deleteByIds(ids);
        return Result.success();
    }
}
