package com.cq.panel.admin.server.schedule;

import com.cq.panel.admin.server.repository.service.IAlertRuleService;
import com.cq.panel.admin.server.repository.service.ISysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component("alertRuleEvaluateTask")
public class AlertRuleEvaluateTask {
    private static final int MIN_INTERVAL_MS = 5000;
    private static final int DEFAULT_INTERVAL_MS = 10000;

    private final IAlertRuleService alertRuleService;
    private final ISysConfigService sysConfigService;
    private volatile long lastEvaluateTs = 0L;

    public AlertRuleEvaluateTask(IAlertRuleService alertRuleService, ISysConfigService sysConfigService) {
        this.alertRuleService = alertRuleService;
        this.sysConfigService = sysConfigService;
    }

    @Scheduled(fixedDelay = 5000)
    public void evaluate() {
        long now = System.currentTimeMillis();
        int intervalMs = getEvaluateIntervalMs();
        if (now - lastEvaluateTs < intervalMs) {
            return;
        }
        lastEvaluateTs = now;
        try {
            alertRuleService.evaluateAlertRules();
        } catch (Exception e) {
            log.error("alert rule evaluation failed: {}", e.getMessage(), e);
        }
    }

    private int getEvaluateIntervalMs() {
        try {
            String value = sysConfigService.selectConfigByKey("sys.monitor.alertEvaluateIntervalMs");
            if (value == null || value.isBlank()) {
                return DEFAULT_INTERVAL_MS;
            }
            return Math.max(MIN_INTERVAL_MS, Integer.parseInt(value));
        } catch (Exception e) {
            return DEFAULT_INTERVAL_MS;
        }
    }
}
