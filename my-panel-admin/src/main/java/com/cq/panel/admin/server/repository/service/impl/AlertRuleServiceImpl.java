package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertEvent;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertRule;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorMetricSample;
import com.cq.panel.admin.server.repository.mapper.MonitorAlertEventMapper;
import com.cq.panel.admin.server.repository.mapper.MonitorAlertRuleMapper;
import com.cq.panel.admin.server.repository.mapper.MonitorMetricMapper;
import com.cq.panel.admin.server.repository.service.IAlertRuleService;
import com.cq.panel.admin.server.web.domain.dto.monitor.MonitorAlertRuleSaveDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlertRuleServiceImpl implements IAlertRuleService {
    private final MonitorAlertRuleMapper monitorAlertRuleMapper;
    private final MonitorAlertEventMapper monitorAlertEventMapper;
    private final MonitorMetricMapper monitorMetricMapper;

    private final Map<Long, Instant> ruleViolationStartTime = new ConcurrentHashMap<>();
    private final Map<Long, Instant> ruleLastAlertTime = new ConcurrentHashMap<>();

    public AlertRuleServiceImpl(MonitorAlertRuleMapper monitorAlertRuleMapper,
            MonitorAlertEventMapper monitorAlertEventMapper,
            MonitorMetricMapper monitorMetricMapper) {
        this.monitorAlertRuleMapper = monitorAlertRuleMapper;
        this.monitorAlertEventMapper = monitorAlertEventMapper;
        this.monitorMetricMapper = monitorMetricMapper;
    }

    @Override
    public List<MonitorAlertRule> listAlertRules() {
        return monitorAlertRuleMapper.selectAll();
    }

    @Override
    public void saveAlertRule(MonitorAlertRuleSaveDTO dto, String operator) {
        MonitorAlertRule rule = new MonitorAlertRule();
        rule.setId(dto.getId());
        rule.setRuleName(dto.getRuleName());
        rule.setMetricCategory(dto.getMetricCategory());
        rule.setMetricName(dto.getMetricName());
        rule.setMetricScope(nvl(dto.getMetricScope()));
        rule.setOperator(MyStringUtils.isEmpty(dto.getOperator()) ? "GT" : dto.getOperator());
        rule.setThresholdValue(dto.getThresholdValue());
        rule.setDurationSeconds(Optional.ofNullable(dto.getDurationSeconds()).orElse(0));
        rule.setSeverity(MyStringUtils.isEmpty(dto.getSeverity()) ? "warning" : dto.getSeverity());
        rule.setEnabled(MyStringUtils.isEmpty(dto.getEnabled()) ? "1" : dto.getEnabled());
        rule.setDescription(nvl(dto.getDescription()));
        rule.setUpdateBy(nvl(operator));

        if (dto.getId() == null) {
            rule.setCreateBy(nvl(operator));
            monitorAlertRuleMapper.insert(rule);
        } else {
            monitorAlertRuleMapper.update(rule);
        }
    }

    @Override
    public void deleteAlertRule(Long id) {
        monitorAlertRuleMapper.deleteById(id);
        ruleViolationStartTime.remove(id);
        ruleLastAlertTime.remove(id);
    }

    @Override
    public List<MonitorAlertEvent> listAlertEvents(String range, Integer limit) {
        Date end = new Date();
        Date begin = Date.from(end.toInstant().minus(parseGranularityDuration(range)));
        int safeLimit = limit == null ? 500 : Math.max(1, Math.min(limit, 5000));
        return monitorAlertEventMapper.selectByTimeRange(begin, end, safeLimit);
    }

    @Override
    public void updateAlertEventStatus(Long id, String status) {
        monitorAlertEventMapper.updateStatus(id, status);
    }

    @Override
    public void evaluateAlertRules() {
        List<MonitorMetricSample> latestSamples = monitorMetricMapper.selectLatestAll(null, null);
        if (latestSamples.isEmpty()) {
            return;
        }

        Map<String, Double> latestValueMap = latestSamples.stream()
                .collect(Collectors.toMap(
                        s -> buildMetricKey(s.getMetricCategory(), s.getMetricName(), nvl(s.getMetricScope())),
                        MonitorMetricSample::getMetricValue,
                        (existing, replacement) -> replacement));

        Date now = new Date();
        Instant nowInstant = now.toInstant();
        List<MonitorAlertRule> rules = monitorAlertRuleMapper.selectEnabledRules();

        for (MonitorAlertRule rule : rules) {
            String key = buildMetricKey(rule.getMetricCategory(), rule.getMetricName(), nvl(rule.getMetricScope()));
            Double currentValue = latestValueMap.get(key);
            if (currentValue == null) {
                continue;
            }
            boolean violated = compare(currentValue, rule.getOperator(), rule.getThresholdValue());
            if (!violated) {
                ruleViolationStartTime.remove(rule.getId());
                continue;
            }

            Instant begin = ruleViolationStartTime.computeIfAbsent(rule.getId(), k -> nowInstant);
            long continuousSeconds = Duration.between(begin, nowInstant).getSeconds();
            int requiredSeconds = Math.max(0, Optional.ofNullable(rule.getDurationSeconds()).orElse(0));
            if (continuousSeconds < requiredSeconds) {
                continue;
            }

            Instant lastAlert = ruleLastAlertTime.get(rule.getId());
            if (lastAlert != null
                    && Duration.between(lastAlert, nowInstant).getSeconds() < Math.max(10, requiredSeconds)) {
                continue;
            }

            MonitorAlertEvent event = new MonitorAlertEvent();
            event.setRuleId(rule.getId());
            event.setRuleName(rule.getRuleName());
            event.setMetricCategory(rule.getMetricCategory());
            event.setMetricName(rule.getMetricName());
            event.setMetricScope(nvl(rule.getMetricScope()));
            event.setSeverity(rule.getSeverity());
            event.setObservedValue(round2(currentValue));
            event.setThresholdValue(rule.getThresholdValue());
            event.setTriggerTime(now);
            event.setStatus("open");
            event.setDetail("metric=" + key + ", current=" + currentValue + ", threshold=" + rule.getThresholdValue()
                    + ", operator=" + rule.getOperator());
            monitorAlertEventMapper.insert(event);
            ruleLastAlertTime.put(rule.getId(), nowInstant);
        }
    }

    @Override
    public Map<String, Object> buildAlertSummary() {
        List<MonitorAlertEvent> recent = listAlertEvents("1h", 200);
        List<MonitorAlertEvent> pendingEvents = recent.stream()
                .filter(it -> "open".equalsIgnoreCase(it.getStatus()))
                .toList();
        long critical = pendingEvents.stream().filter(it -> "critical".equalsIgnoreCase(it.getSeverity())).count();
        long warning = pendingEvents.stream().filter(it -> "warning".equalsIgnoreCase(it.getSeverity())).count();
        Map<String, Object> summary = new HashMap<>();
        summary.put("critical", critical);
        summary.put("warning", warning);
        summary.put("total", pendingEvents.size());
        return summary;
    }

    private boolean compare(Double value, String operator, Double threshold) {
        if (value == null || threshold == null) {
            return false;
        }
        String op = MyStringUtils.isEmpty(operator) ? "GT" : operator.toUpperCase(Locale.ROOT);
        return switch (op) {
            case "GT" -> value > threshold;
            case "GTE" -> value >= threshold;
            case "LT" -> value < threshold;
            case "LTE" -> value <= threshold;
            case "EQ" -> Double.compare(value, threshold) == 0;
            case "NE" -> Double.compare(value, threshold) != 0;
            default -> false;
        };
    }

    private Duration parseGranularityDuration(String range) {
        if (MyStringUtils.isEmpty(range)) {
            return Duration.ofHours(1);
        }
        String text = range.toLowerCase(Locale.ROOT).trim();
        if (text.matches("\\d+[mhd]")) {
            long n = Long.parseLong(text.substring(0, text.length() - 1));
            char unit = text.charAt(text.length() - 1);
            return switch (unit) {
                case 'm' -> Duration.ofMinutes(n);
                case 'h' -> Duration.ofHours(n);
                case 'd' -> Duration.ofDays(n);
                default -> Duration.ofHours(1);
            };
        }
        return Duration.ofHours(1);
    }

    private String buildMetricKey(String category, String metricName, String scope) {
        return category + "|" + metricName + "|" + nvl(scope);
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
