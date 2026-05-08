package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.repository.domain.AgentQueueSnapshot;
import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.domain.BatchAlertEvent;
import com.cq.panel.admin.server.repository.domain.BatchTransferTask;
import com.cq.panel.admin.server.repository.service.IAgentQueueSnapshotService;
import com.cq.panel.admin.server.repository.service.IAgentRegistryService;
import com.cq.panel.admin.server.repository.service.IBatchAlertEventService;
import com.cq.panel.admin.server.repository.service.IBatchTransferTaskService;
import com.cq.panel.admin.server.service.batch.BatchTransferService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

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
    private final JdbcTemplate jdbcTemplate;
    private final BatchTransferService batchTransferService;

    public BatchMonitorController(IAgentQueueSnapshotService agentQueueSnapshotService,
                                  IBatchAlertEventService batchAlertEventService,
                                  IBatchTransferTaskService batchTransferTaskService,
                                  IAgentRegistryService agentRegistryService,
                                  BatchTransferConverter batchTransferConverter,
                                  JdbcTemplate jdbcTemplate,
                                  BatchTransferService batchTransferService)
    {
        this.agentQueueSnapshotService = agentQueueSnapshotService;
        this.batchAlertEventService = batchAlertEventService;
        this.batchTransferTaskService = batchTransferTaskService;
        this.agentRegistryService = agentRegistryService;
        this.batchTransferConverter = batchTransferConverter;
        this.jdbcTemplate = jdbcTemplate;
        this.batchTransferService = batchTransferService;
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
        vo.setSendQueueCapacity(snapshot.getSendQueueCapacity());
        vo.setSendQueueUtilizationPct(snapshot.getSendQueueUtilizationPct());
        vo.setSendQueueAvgWaitMs(snapshot.getSendQueueAvgWaitMs());
        vo.setRetryQueueDepth(snapshot.getRetryQueueDepth());
        vo.setRetryQueuePeakDepth(snapshot.getRetryQueuePeakDepth());
        vo.setRetryQueueCapacity(snapshot.getRetryQueueCapacity());
        vo.setCongestionLevel(snapshot.getCongestionLevel());
        vo.setCongestionReason(snapshot.getCongestionReason());
        vo.setIsCongested(snapshot.getIsCongested());
        vo.setProcessingRatePerSec(snapshot.getProcessingRatePerSec());
        vo.setSuccessRatePct(snapshot.getSuccessRatePct());
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
            vo.setSendQueueCapacity(s.getSendQueueCapacity());
            vo.setSendQueueUtilizationPct(s.getSendQueueUtilizationPct());
            vo.setSendQueueAvgWaitMs(s.getSendQueueAvgWaitMs());
            vo.setRetryQueueDepth(s.getRetryQueueDepth());
            vo.setRetryQueuePeakDepth(s.getRetryQueuePeakDepth());
            vo.setRetryQueueCapacity(s.getRetryQueueCapacity());
            vo.setCongestionLevel(s.getCongestionLevel());
            vo.setCongestionReason(s.getCongestionReason());
            vo.setIsCongested(s.getIsCongested());
            vo.setProcessingRatePerSec(s.getProcessingRatePerSec());
            vo.setSuccessRatePct(s.getSuccessRatePct());
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
        Date todayStart = getTodayStart();

        // --- Agent概览 ---
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

        // --- 任务概览 ---
        BatchDashboardVO.TaskOverview taskOverview = new BatchDashboardVO.TaskOverview();
        List<BatchTransferTask> allTasks = batchTransferTaskService.selectList(new BatchTransferTask());
        int activeCount = 0;
        int pausedCount = 0;
        for (BatchTransferTask t : allTasks)
        {
            String s = t.getStatus();
            if ("RUNNING".equals(s)) activeCount++;
            if ("PAUSED".equals(s)) pausedCount++;
        }
        taskOverview.setActive(activeCount);
        taskOverview.setPaused(pausedCount);

        // 今日完成/失败子任务数（从子任务表实时统计）
        try
        {
            Map<String, Object> todayStats = jdbcTemplate.queryForMap(
                    "SELECT " +
                            "SUM(CASE WHEN status = 'COMPLETED' AND completed_at >= ? THEN 1 ELSE 0 END) as completed_today, " +
                            "SUM(CASE WHEN status = 'FAILED' AND update_time >= ? THEN 1 ELSE 0 END) as failed_today " +
                            "FROM batch_transfer_subtask", todayStart, todayStart);
            taskOverview.setCompletedToday(((Number) todayStats.get("completed_today")).intValue());
            taskOverview.setFailedToday(((Number) todayStats.get("failed_today")).intValue());
        }
        catch (Exception e)
        {
            taskOverview.setCompletedToday(0);
            taskOverview.setFailedToday(0);
        }
        overview.setTasks(taskOverview);

        // --- 性能概览 ---
        BatchDashboardVO.PerformanceOverview perfOverview = new BatchDashboardVO.PerformanceOverview();
        try
        {
            // 全局吞吐量: 所有COMPLETED子任务的总字节 / 总耗时
            Map<String, Object> throughputStats = jdbcTemplate.queryForMap(
                    "SELECT COALESCE(SUM(file_size_bytes), 0) as total_bytes, " +
                            "COALESCE(SUM(duration_ms), 0) as total_ms " +
                            "FROM batch_transfer_subtask WHERE status = 'COMPLETED' AND duration_ms > 0");
            long totalBytes = ((Number) throughputStats.get("total_bytes")).longValue();
            long totalMs = ((Number) throughputStats.get("total_ms")).longValue();
            if (totalMs > 0)
            {
                double mbps = (totalBytes / (1024.0 * 1024.0)) / (totalMs / 1000.0);
                perfOverview.setGlobalThroughputMBps(BigDecimal.valueOf(mbps).setScale(2, RoundingMode.HALF_UP));
            }
            else
            {
                perfOverview.setGlobalThroughputMBps(BigDecimal.ZERO);
            }

            // 今日传输量
            Object todayTransferredBytes = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(file_size_bytes), 0) FROM batch_transfer_subtask " +
                            "WHERE status = 'COMPLETED' AND completed_at >= ?", Long.class, todayStart);
            double todayGB = ((Number) todayTransferredBytes).longValue() / (1024.0 * 1024.0 * 1024.0);
            perfOverview.setTodayTransferredGB(BigDecimal.valueOf(todayGB).setScale(2, RoundingMode.HALF_UP));

            // 平均任务耗时（已停止任务的平均耗时）
            Object avgDuration = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(AVG(duration_ms), 0) FROM batch_transfer_subtask " +
                            "WHERE status = 'COMPLETED' AND duration_ms > 0", Long.class);
            double avgMin = ((Number) avgDuration).longValue() / 60000.0;
            perfOverview.setAvgTaskDurationMin(BigDecimal.valueOf(avgMin).setScale(1, RoundingMode.HALF_UP));
        }
        catch (Exception e)
        {
            perfOverview.setGlobalThroughputMBps(BigDecimal.ZERO);
            perfOverview.setTodayTransferredGB(BigDecimal.ZERO);
            perfOverview.setAvgTaskDurationMin(BigDecimal.ZERO);
        }
        overview.setPerformance(perfOverview);
        overview.setAgents(agentOverview);
        dashboard.setOverview(overview);

        // --- Agent健康网格 ---
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

        // --- 活跃任务摘要（真实进度） ---
        List<BatchDashboardVO.ActiveTaskSummary> activeTaskSummaries = new ArrayList<>();
        for (BatchTransferTask t : allTasks)
        {
            String s = t.getStatus();
            if ("RUNNING".equals(s) || "PAUSED".equals(s))
            {
                BatchDashboardVO.ActiveTaskSummary summary = new BatchDashboardVO.ActiveTaskSummary();
                summary.setTaskId(t.getId());
                summary.setTaskName(t.getTaskName());
                summary.setStatus(t.getStatus());
                summary.setSourceAgentId(t.getSourceAgentId());
                summary.setStartedAt(t.getStartedAt());

                // 从子任务表实时计算进度
                try
                {
                    Map<String, Object> taskStats = jdbcTemplate.queryForMap(
                            "SELECT COUNT(*) as total, " +
                                    "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
                                    "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN speed_bytes_per_sec ELSE 0 END), 0) as total_speed, " +
                                    "SUM(CASE WHEN status = 'COMPLETED' AND speed_bytes_per_sec > 0 THEN 1 ELSE 0 END) as speed_count " +
                                    "FROM batch_transfer_subtask WHERE task_id = ?", t.getId());
                    int total = ((Number) taskStats.get("total")).intValue();
                    int completed = ((Number) taskStats.get("completed")).intValue();
                    summary.setProgressPercent(calcProgress(total, completed));

                    long totalSpeed = ((Number) taskStats.get("total_speed")).longValue();
                    int speedCount = ((Number) taskStats.get("speed_count")).intValue();
                    if (speedCount > 0)
                    {
                        double avgSpeedMBps = (totalSpeed / (double) speedCount) / (1024.0 * 1024.0);
                        summary.setCurrentSpeedMBps(BigDecimal.valueOf(avgSpeedMBps).setScale(2, RoundingMode.HALF_UP));
                    }
                    else
                    {
                        summary.setCurrentSpeedMBps(BigDecimal.ZERO);
                    }
                }
                catch (Exception e)
                {
                    summary.setProgressPercent(BigDecimal.ZERO);
                    summary.setCurrentSpeedMBps(BigDecimal.ZERO);
                }
                activeTaskSummaries.add(summary);
            }
        }
        dashboard.setActiveTasksSummary(activeTaskSummaries);

        // --- 最近告警 ---
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

    @RequirePermission("batch:monitor:view")
    @Operation(summary = "查询操作日志")
    @GetMapping("/operation-logs")
    public Result<List<Map<String, Object>>> getOperationLogs(
            @RequestParam(required = false) Long taskId,
            @RequestParam(defaultValue = "50") int limit)
    {
        List<Map<String, Object>> logs;
        if (taskId != null)
        {
            logs = jdbcTemplate.queryForList(
                    "SELECT * FROM batch_transfer_operation_log WHERE task_id = ? ORDER BY operation_time DESC LIMIT ?",
                    taskId, limit);
        }
        else
        {
            logs = jdbcTemplate.queryForList(
                    "SELECT * FROM batch_transfer_operation_log ORDER BY operation_time DESC LIMIT ?", limit);
        }
        return Result.success(logs);
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
