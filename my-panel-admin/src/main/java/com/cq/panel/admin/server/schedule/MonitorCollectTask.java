package com.cq.panel.admin.server.schedule;

import com.cq.panel.admin.server.repository.service.IMonitorDashboardService;
import com.cq.panel.admin.server.repository.service.ISysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component("monitorCollectTask")
public class MonitorCollectTask {
    private static final int MIN_INTERVAL_MS = 1000;
    private static final int DEFAULT_INTERVAL_MS = 10000;

    private final IMonitorDashboardService monitorDashboardService;
    private final ISysConfigService sysConfigService;
    private volatile long lastCollectTs = 0L;

    public MonitorCollectTask(IMonitorDashboardService monitorDashboardService, ISysConfigService sysConfigService) {
        this.monitorDashboardService = monitorDashboardService;
        this.sysConfigService = sysConfigService;
    }

    @Scheduled(fixedDelay = 3000)
    public void collect() {
        long now = System.currentTimeMillis();
        int intervalMs = getCollectIntervalMs();
        if (now - lastCollectTs < intervalMs) {
            return;
        }
        lastCollectTs = now;
        monitorDashboardService.collectSnapshotAndPersist();
    }

    @Scheduled(cron = "${monitor.cleanup.cron:0 0 3 * * ?}")
    public void cleanup() {
        int deleted = monitorDashboardService.cleanupHistory();
        if (deleted > 0) {
            log.info("monitor cleanup completed, deleted {} rows", deleted);
        }
    }

    private int getCollectIntervalMs() {
        try {
            String value = sysConfigService.selectConfigByKey("sys.monitor.collectIntervalMs");
            if (value == null || value.isBlank()) {
                return DEFAULT_INTERVAL_MS;
            }
            return Math.max(MIN_INTERVAL_MS, Integer.parseInt(value));
        } catch (Exception e) {
            return DEFAULT_INTERVAL_MS;
        }
    }
}
