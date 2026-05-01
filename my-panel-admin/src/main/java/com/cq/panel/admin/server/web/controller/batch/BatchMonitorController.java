package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.repository.domain.AgentQueueSnapshot;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchAlertEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.service.IAgentQueueSnapshotService;
import com.cq.panel.admin.server.repository.service.IAgentRegistryService;
import com.cq.panel.admin.server.repository.service.IBatchAlertEventService;
import com.cq.panel.admin.server.repository.service.IBatchTransferTaskService;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.converter.batch.BatchTransferConverter;
import com.cq.panel.admin.server.web.domain.vo.base.PageVO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentQueueStatusVO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchAlertEventVO;
import com.cq.panel.admin.server.web.domain.vo.batch.BatchDashboardVO;
import com.cq.panel.authlite.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Tag(name = "批量传输监控", description = "批量文件传输监控相关接口")
@RestController
@RequestMapping("/batch/monitor")
public class BatchMonitorController extends BaseController
{
    private final IAgentQueueSnapshotService agentQueueSnapshotService;
    private final IBatchAlertEventService batchAlertEventService;
    private final IBatchTransferTaskService batchTransferTaskService;
    private final IAgentRegistryService agentRegistryService;
    private final BatchTransferConverter batchTransferConverter;

    public BatchMonitorController(IAgentQueueSnapshotService agentQueueSnapshotService,
                                  IBatchAlertEventService batchAlertEventService,
                                  IBatchTransferTaskService batchTransferTaskService,
                                  IAgentRegistryService agentRegistryService,
                                  BatchTransferConverter batchTransferConverter)
    {
        this.agentQueueSnapshotService = agentQueueSnapshotService;
        this.batchAlertEventService = batchAlertEventService;
        this.batchTransferTaskService = batchTransferTaskService;
        this.agentRegistryService = agentRegistryService;
        this.batchTransferConverter = batchTransferConverter;
    }

    @RequirePermission("batch:monitor:view")
    @Operation(summary = "查询Agent队列实时状态")
    @GetMapping("/agents/{agentId}/queue-status")
    public Result<AgentQueueStatusVO> getAgentQueueStatus(@PathVariable String agentId)
    {
        AgentQueueSnapshot snapshot = agentQueueSnapshotService.selectLatestByAgentId(agentId);
        if (snapshot == null)
        {
            return Result.success(new AgentQueueStatusVO());
        }
        AgentQueueStatusVO vo = new AgentQueueStatusVO();
        vo.setAgentId(snapshot.getAgentId());
        vo.setSnapshotTime(snapshot.getSnapshotTime());
        vo.setSendQueueDepth(snapshot.getSendQueueDepth());
        vo.setSendQueuePeakDepth(snapshot.getSendQueuePeakDepth());
        vo.setSendQueueUtilizationPct(snapshot.getSendQueueUtilizationPct());
        vo.setSendQueueAvgWaitMs(snapshot.getSendQueueAvgWaitMs());
        vo.setRetryQueueDepth(snapshot.getRetryQueueDepth());
        vo.setRetryQueuePeakDepth(snapshot.getRetryQueuePeakDepth());
        vo.setCongestionLevel(snapshot.getCongestionLevel());
        vo.setIsCongested(snapshot.getIsCongested());
        vo.setProcessingRatePerSec(snapshot.getProcessingRatePerSec());
        return Result.success(vo);
    }

    @RequirePermission("batch:monitor:view")
    @Operation(summary = "查询Agent队列趋势数据")
    @GetMapping("/agents/{agentId}/queue-trend")
    public Result<List<AgentQueueStatusVO>> getAgentQueueTrend(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "60") int minutes)
    {
        Date since = new Date(System.currentTimeMillis() - minutes * 60 * 1000L);
        List<AgentQueueSnapshot> snapshots = agentQueueSnapshotService.selectByAgentIdAndTimeRange(agentId, since, new Date());
        List<AgentQueueStatusVO> voList = new ArrayList<>();
        for (AgentQueueSnapshot s : snapshots)
        {
            AgentQueueStatusVO vo = new AgentQueueStatusVO();
            vo.setAgentId(s.getAgentId());
            vo.setSnapshotTime(s.getSnapshotTime());
            vo.setSendQueueDepth(s.getSendQueueDepth());
            vo.setSendQueuePeakDepth(s.getSendQueuePeakDepth());
            vo.setSendQueueUtilizationPct(s.getSendQueueUtilizationPct());
            vo.setSendQueueAvgWaitMs(s.getSendQueueAvgWaitMs());
            vo.setRetryQueueDepth(s.getRetryQueueDepth());
            vo.setRetryQueuePeakDepth(s.getRetryQueuePeakDepth());
            vo.setCongestionLevel(s.getCongestionLevel());
            vo.setIsCongested(s.getIsCongested());
            vo.setProcessingRatePerSec(s.getProcessingRatePerSec());
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @RequirePermission("batch:monitor:view")
    @Operation(summary = "查询全局监控仪表盘")
    @GetMapping("/dashboard")
    public Result<BatchDashboardVO> getDashboard()
    {
        BatchDashboardVO dashboard = new BatchDashboardVO();

        BatchDashboardVO.Overview overview = new BatchDashboardVO.Overview();
        BatchDashboardVO.AgentOverview agentOverview = new BatchDashboardVO.AgentOverview();
        List<AgentRegistry> allAgents = agentRegistryService.selectAgentRegistryList(new AgentRegistry());
        int totalRegistered = allAgents.size();
        int online = 0;
        int busy = 0;
        for (AgentRegistry a : allAgents)
        {
            if (a.getNodeStatus() != null && a.getNodeStatus() == 1) online++;
        }
        agentOverview.setTotalRegistered(totalRegistered);
        agentOverview.setOnline(online);
        agentOverview.setOffline(totalRegistered - online);
        overview.setAgents(agentOverview);

        BatchDashboardVO.TaskOverview taskOverview = new BatchDashboardVO.TaskOverview();
        List<BatchTransferTask> allTasks = batchTransferTaskService.selectList(new BatchTransferTask());
        Date todayStart = getTodayStart();
        int activeCount = 0;
        int pausedCount = 0;
        int completedToday = 0;
        int failedToday = 0;
        for (BatchTransferTask t : allTasks)
        {
            String s = t.getStatus();
            if ("TRANSFERRING".equals(s) || "SCANNING".equals(s) || "POST_PROCESSING".equals(s)) activeCount++;
            if ("PAUSED".equals(s)) pausedCount++;
            if ("COMPLETED".equals(s) && t.getCompletedAt() != null && t.getCompletedAt().after(todayStart)) completedToday++;
            if (("FAILED".equals(s) || "PARTIAL_FAILED".equals(s)) && t.getCompletedAt() != null && t.getCompletedAt().after(todayStart)) failedToday++;
        }
        taskOverview.setActive(activeCount);
        taskOverview.setPaused(pausedCount);
        taskOverview.setCompletedToday(completedToday);
        taskOverview.setFailedToday(failedToday);
        overview.setTasks(taskOverview);

        BatchDashboardVO.PerformanceOverview perfOverview = new BatchDashboardVO.PerformanceOverview();
        perfOverview.setGlobalThroughputMBps(BigDecimal.ZERO);
        perfOverview.setTodayTransferredGB(BigDecimal.ZERO);
        perfOverview.setAvgTaskDurationMin(BigDecimal.ZERO);
        overview.setPerformance(perfOverview);
        dashboard.setOverview(overview);

        List<BatchDashboardVO.AgentHealthGridItem> healthGrid = new ArrayList<>();
        for (AgentRegistry agent : allAgents)
        {
            BatchDashboardVO.AgentHealthGridItem item = new BatchDashboardVO.AgentHealthGridItem();
            item.setAgentId(agent.getId());
            item.setAgentName(agent.getNodeName());
            item.setOnlineStatus(agent.getNodeStatus() != null && agent.getNodeStatus() == 1 ? "ONLINE" : "OFFLINE");
            AgentQueueSnapshot snapshot = agentQueueSnapshotService.selectLatestByAgentId(agent.getId());
            if (snapshot != null)
            {
                BatchDashboardVO.QueueStatusInfo sendInfo = new BatchDashboardVO.QueueStatusInfo();
                sendInfo.setDepth(snapshot.getSendQueueDepth());
                sendInfo.setCapacity(snapshot.getSendQueueCapacity());
                sendInfo.setUtilizationPct(snapshot.getSendQueueUtilizationPct());
                item.setSendQueueStatus(sendInfo);

                BatchDashboardVO.QueueStatusInfo retryInfo = new BatchDashboardVO.QueueStatusInfo();
                retryInfo.setDepth(snapshot.getRetryQueueDepth());
                retryInfo.setCapacity(snapshot.getRetryQueueCapacity());
                item.setRetryQueueStatus(retryInfo);

                String health = "HEALTHY";
                if ("CRITICAL".equals(snapshot.getCongestionLevel())) health = "CRITICAL";
                else if ("WARNING".equals(snapshot.getCongestionLevel())) health = "WARNING";
                item.setOverallHealth(health);
                item.setActiveTaskCount(0);
                if (snapshot.getSendQueueDepth() != null && snapshot.getSendQueueDepth() > 100) busy++;
            }
            else
            {
                item.setOverallHealth(agent.getNodeStatus() != null && agent.getNodeStatus() == 1 ? "HEALTHY" : "OFFLINE");
            }
            healthGrid.add(item);
        }
        agentOverview.setBusy(busy);
        dashboard.setAgentHealthGrid(healthGrid);

        List<BatchDashboardVO.ActiveTaskSummary> activeTaskSummaries = new ArrayList<>();
        for (BatchTransferTask t : allTasks)
        {
            String s = t.getStatus();
            if ("TRANSFERRING".equals(s) || "SCANNING".equals(s) || "POST_PROCESSING".equals(s) || "PAUSED".equals(s))
            {
                BatchDashboardVO.ActiveTaskSummary summary = new BatchDashboardVO.ActiveTaskSummary();
                summary.setTaskId(t.getId());
                summary.setTaskName(t.getTaskName());
                summary.setStatus(t.getStatus());
                summary.setProgressPercent(calcProgress(t.getTotalFiles(), t.getTransferredFiles()));
                summary.setSourceAgentId(t.getSourceAgentId());
                summary.setCurrentSpeedMBps(BigDecimal.ZERO);
                summary.setStartedAt(t.getStartedAt());
                activeTaskSummaries.add(summary);
            }
        }
        dashboard.setActiveTasksSummary(activeTaskSummaries);

        List<BatchAlertEvent> recentAlerts = batchAlertEventService.selectRecent(10);
        dashboard.setRecentAlerts(batchTransferConverter.toAlertVOList(recentAlerts));
        return Result.success(dashboard);
    }

    @RequirePermission("batch:monitor:view")
    @Operation(summary = "查询告警事件列表")
    @GetMapping("/alerts")
    public Result<PageVO<BatchAlertEventVO>> listAlerts(
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) Integer isResolved,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        List<BatchAlertEvent> alerts;
        if (isResolved != null && isResolved == 0)
        {
            alerts = batchAlertEventService.selectUnresolved();
        }
        else
        {
            alerts = batchAlertEventService.selectRecent(100);
        }
        List<BatchAlertEventVO> voList = batchTransferConverter.toAlertVOList(alerts);
        return Result.success(new PageVO<>(voList, (long) voList.size()));
    }

    @RequirePermission("batch:monitor:view")
    @Operation(summary = "标记告警已解决")
    @PutMapping("/alerts/{alertId}/resolve")
    public Result<Void> resolveAlert(@PathVariable Long alertId, @RequestBody(required = false) String resolutionNote)
    {
        String resolvedBy = "";
        try
        {
            resolvedBy = getUsername();
        }
        catch (Exception ignored)
        {
        }
        batchAlertEventService.resolve(alertId, resolvedBy, resolutionNote);
        return Result.success();
    }

    private Date getTodayStart()
    {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private BigDecimal calcProgress(Integer total, Integer done)
    {
        if (total == null || total == 0 || done == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(done)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
    }
}
