package com.cq.panel.admin.server.web.controller.monitor;

import com.cq.panel.admin.server.repository.service.IServerService;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.monitor.ServerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务器监控
 * 
 * @author cq
 */
@Tag(name = "服务器监控", description = "服务器监控信息接口")
@RestController
@RequestMapping("/monitor/server")
public class ServerController
{
    @Autowired
    private IServerService serverService;

    @PreAuthorize("@ss.hasPermi('monitor:server:list')")
    @Operation(summary = "获取服务器监控信息", description = "获取服务器CPU、内存、JVM、磁盘等监控信息")
    @GetMapping()
    public Result<ServerVO> getInfo() throws Exception
    {
        return Result.success(serverService.getServerInfo());
    }
}


