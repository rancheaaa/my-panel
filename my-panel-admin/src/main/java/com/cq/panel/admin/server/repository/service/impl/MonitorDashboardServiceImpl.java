package com.cq.panel.admin.server.repository.service.impl;

import com.alibaba.druid.pool.DruidDataSource;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.common.utils.IpUtils;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertEvent;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorAlertRule;
import com.cq.panel.admin.server.repository.domain.monitor.MonitorMetricSample;
import com.cq.panel.admin.server.repository.mapper.MonitorAlertEventMapper;
import com.cq.panel.admin.server.repository.mapper.MonitorAlertRuleMapper;
import com.cq.panel.admin.server.repository.mapper.MonitorMetricMapper;
import com.cq.panel.admin.server.repository.service.IMonitorDashboardService;
import com.cq.panel.admin.server.repository.service.ISysConfigService;
import com.cq.panel.admin.server.web.domain.dto.monitor.MetricTrendQueryDTO;
import com.cq.panel.admin.server.web.domain.dto.monitor.MonitorAlertRuleSaveDTO;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSProcess;
import javax.sql.DataSource;
import java.net.InetAddress;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MonitorDashboardServiceImpl implements IMonitorDashboardService {
    private static final int DEFAULT_MAX_POINTS = 2000;
    private static final int DEFAULT_RETENTION_DAYS = 7;

    private final MonitorMetricMapper monitorMetricMapper;
    private final MonitorAlertRuleMapper monitorAlertRuleMapper;
    private final MonitorAlertEventMapper monitorAlertEventMapper;
    private final ISysConfigService sysConfigService;
    private final ApplicationContext applicationContext;

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
    private final ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
    private final OperatingSystemMXBean osMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    private final SystemInfo systemInfo = new SystemInfo();
    private final Map<String, Long> gcMaxPauseMillis = new ConcurrentHashMap<>();
    private final Map<Long, Instant> ruleViolationStartTime = new ConcurrentHashMap<>();
    private final Map<Long, Instant> ruleLastAlertTime = new ConcurrentHashMap<>();
    private volatile long[] previousCpuTicks = null;
    private volatile Date lastSampleTime;

    public MonitorDashboardServiceImpl(MonitorMetricMapper monitorMetricMapper,
            MonitorAlertRuleMapper monitorAlertRuleMapper,
            MonitorAlertEventMapper monitorAlertEventMapper,
            ISysConfigService sysConfigService,
            ApplicationContext applicationContext) {
        this.monitorMetricMapper = monitorMetricMapper;
        this.monitorAlertRuleMapper = monitorAlertRuleMapper;
        this.monitorAlertEventMapper = monitorAlertEventMapper;
        this.sysConfigService = sysConfigService;
        this.applicationContext = applicationContext;
    }

    @Override
    public Map<String, Object> collectSnapshotAndPersist() {
        Date now = new Date();
        List<MonitorMetricSample> samples = new ArrayList<>();
        Map<String, Double> latestValueMap = new HashMap<>();

        String serviceId = resolveServiceId();
        String serviceIpPort = resolveServiceIpPort();

        // 任何单项采集失败都只记日志，不影响其他采集逻辑。
        safeCollect(() -> collectHeap(samples, latestValueMap, now, serviceId, serviceIpPort), "heap");
        safeCollect(() -> collectProcessMemory(samples, latestValueMap, now, serviceId, serviceIpPort),
                "process_memory");
        safeCollect(() -> collectGc(samples, latestValueMap, now, serviceId, serviceIpPort), "gc");
        safeCollect(() -> collectThread(samples, latestValueMap, now, serviceId, serviceIpPort), "thread");
        safeCollect(() -> collectCpuAndLoad(samples, latestValueMap, now, serviceId, serviceIpPort), "cpu/load");
        safeCollect(() -> collectDbPool(samples, latestValueMap, now, serviceId, serviceIpPort), "db_pool");
        safeCollect(() -> collectClassLoading(samples, latestValueMap, now, serviceId, serviceIpPort), "class_loading");

        if (!samples.isEmpty()) {
            monitorMetricMapper.batchInsert(samples);
            lastSampleTime = now;
            evaluateAlertRules(latestValueMap, now);
        }

        return buildOverviewFromCurrent(latestValueMap, now);
    }

    @Override
    public Map<String, Object> getDashboardOverview(String serviceId, String serviceIpPort) {
        Map<String, Object> categories = queryLatestByCategories(serviceId, serviceIpPort);
        Map<String, Object> basicStats = buildBasicStatsFromCategories(categories);
        Date sampleTime = Optional.ofNullable(lastSampleTime).orElseGet(Date::new);
        Map<String, Object> overview = new HashMap<>();
        overview.put("sampleTime", sampleTime);
        overview.put("categories", categories);
        overview.put("basicStats", basicStats);
        overview.put("alertSummary", buildAlertSummary());
        return overview;
    }

    private Map<String, Object> buildBasicStatsFromCategories(Map<String, Object> categories) {
        Map<String, Object> basicStats = new HashMap<>();
        basicStats.put("processRss", findMetricValue(categories, "process_memory", "process_rss_bytes", ""));
        basicStats.put("cpuUsage", findMetricValue(categories, "cpu", "cpu_usage_pct", ""));
        basicStats.put("cpuCore", findMetricValue(categories, "cpu", "cpu_cores", ""));
        basicStats.put("heapUsage", findMetricValue(categories, "heap", "heap_usage_pct", ""));
        basicStats.put("threadTotal", findMetricValue(categories, "thread", "thread_total", ""));
        basicStats.put("virtualThreadTotal", findMetricValue(categories, "thread", "thread_virtual_total", ""));
        basicStats.put("load1m", findMetricValue(categories, "system_load", "load_avg_1m", ""));
        basicStats.put("gcCollector", simplifyGcCollectorName(
                garbageCollectorMXBeans.stream()
                        .map(GarbageCollectorMXBean::getName)
                        .toList()));
        return basicStats;
    }

    @SuppressWarnings("unchecked")
    private double findMetricValue(Map<String, Object> categories, String category, String metricName, String scope) {
        List<MonitorMetricSample> metrics = (List<MonitorMetricSample>) categories.get(category);
        if (metrics == null || metrics.isEmpty()) {
            return 0D;
        }
        String key = buildMetricKey(category, metricName, scope);
        return metrics.stream()
                .filter(m -> key
                        .equals(buildMetricKey(m.getMetricCategory(), m.getMetricName(), nvl(m.getMetricScope()))))
                .map(MonitorMetricSample::getMetricValue)
                .findFirst()
                .orElse(0D);
    }

    @Override
    public Map<String, Object> getTrend(MetricTrendQueryDTO queryDTO) {
        Instant endInstant = Optional.ofNullable(queryDTO.getEndTime()).orElseGet(Instant::now);
        Instant beginInstant = Optional.ofNullable(queryDTO.getBeginTime())
                .orElseGet(() -> endInstant.minus(Duration.ofHours(1)));
        Date endTime = Date.from(endInstant);
        Date beginTime = Date.from(beginInstant);
        String category = MyStringUtils.isEmpty(queryDTO.getCategory()) ? "cpu" : queryDTO.getCategory();
        long bucketMillis = parseGranularityMillis(queryDTO.getGranularity());

        int configMaxPoints = parseIntConfig("sys.monitor.maxPoints", DEFAULT_MAX_POINTS);
        int limit = queryDTO.getLimit() == null ? configMaxPoints : Math.min(queryDTO.getLimit(), configMaxPoints);

        List<MonitorMetricSample> rawSamples = monitorMetricMapper.selectByTimeRange(
                category, queryDTO.getMetricNames(), beginTime, endTime,
                queryDTO.getServiceId(), queryDTO.getServiceIpPort(), limit);

        Map<String, List<MonitorMetricSample>> seriesGroup = rawSamples.stream()
                .collect(Collectors.groupingBy(item -> item.getMetricName() + "|" + nvl(item.getMetricScope()) + "|"
                        + nvl(item.getMetricUnit()) + "|" + nvl(item.getTagJson())));

        List<Map<String, Object>> series = new LinkedList<>();
        for (Map.Entry<String, List<MonitorMetricSample>> entry : seriesGroup.entrySet()) {
            List<MonitorMetricSample> aggregated = aggregateByBucket(entry.getValue(), bucketMillis);
            if (aggregated.isEmpty()) {
                continue;
            }
            if (aggregated.size() > configMaxPoints) {
                aggregated = aggregated.subList(aggregated.size() - configMaxPoints, aggregated.size());
            }
            MonitorMetricSample first = aggregated.getFirst();
            Map<String, Object> oneSeries = new LinkedHashMap<>();
            oneSeries.put("category", first.getMetricCategory());
            oneSeries.put("metricName", first.getMetricName());
            oneSeries.put("metricScope", first.getMetricScope());
            oneSeries.put("metricUnit", first.getMetricUnit());
            oneSeries.put("tagJson", first.getTagJson());
            oneSeries.put("points", aggregated.stream().map(item -> {
                Map<String, Object> point = new HashMap<>();
                point.put("time", item.getSampleTime());
                point.put("value", item.getMetricValue());
                return point;
            }).collect(Collectors.toList()));
            series.add(oneSeries);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("granularity", queryDTO.getGranularity());
        result.put("beginTime", beginInstant);
        result.put("endTime", endInstant);
        result.put("series", series);
        return result;
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
    public int cleanupHistory() {
        int retentionDays = parseIntConfig("sys.monitor.retentionDays", DEFAULT_RETENTION_DAYS);
        Date cutoff = Date.from(Instant.now().minus(Duration.ofDays(Math.max(1, retentionDays))));
        return monitorMetricMapper.deleteBefore(cutoff);
    }

    @Override
    public void updateAlertEventStatus(Long id, String status) {
        monitorAlertEventMapper.updateStatus(id, status);
    }

    private void collectHeap(List<MonitorMetricSample> samples, Map<String, Double> latestValueMap, Date now,
            String serviceId, String serviceIpPort) {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        addSample(samples, latestValueMap, now, "heap", "heap_heap_used_bytes", "", heapUsage.getUsed(), "bytes", null,
                serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "heap", "heap_committed_bytes", "", heapUsage.getCommitted(), "bytes",
                null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "heap", "heap_max_bytes", "", heapUsage.getMax(), "bytes", null,
                serviceId, serviceIpPort);
        if (heapUsage.getMax() > 0) {
            addSample(samples, latestValueMap, now, "heap", "heap_usage_pct", "",
                    heapUsage.getUsed() * 100D / heapUsage.getMax(), "percent", null, serviceId, serviceIpPort);
        }

        long youngUsed = 0L, youngCommitted = 0L, youngMax = 0L;
        for (MemoryPoolMXBean poolMXBean : memoryPoolMXBeans) {
            MemoryUsage usage = poolMXBean.getUsage();
            if (usage == null) {
                continue;
            }
            String scope = classifyPoolScope(poolMXBean.getName());
            if ("code".equals(scope) || "general".equals(scope)) {
                continue;
            }
            addSample(samples, latestValueMap, now, "heap", "pool_used_bytes", scope, usage.getUsed(), "bytes", null,
                    serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "heap", "pool_committed_bytes", scope, usage.getCommitted(),
                    "bytes", null, serviceId, serviceIpPort);
            if (usage.getMax() > 0) {
                addSample(samples, latestValueMap, now, "heap", "pool_usage_pct", scope,
                        usage.getUsed() * 100D / usage.getMax(), "percent", null, serviceId, serviceIpPort);
            }
            if ("eden".equals(scope) || "survivor".equals(scope)) {
                youngUsed += usage.getUsed();
                youngCommitted += usage.getCommitted();
                youngMax += usage.getMax();
            }
        }
        if (youngUsed > 0 || youngCommitted > 0) {
            addSample(samples, latestValueMap, now, "heap", "pool_used_bytes", "young", youngUsed, "bytes", null,
                    serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "heap", "pool_committed_bytes", "young", youngCommitted, "bytes",
                    null, serviceId, serviceIpPort);
            long denom = Math.max(youngMax, youngCommitted);
            if (denom > 0) {
                addSample(samples, latestValueMap, now, "heap", "pool_usage_pct", "young",
                        youngUsed * 100D / denom, "percent", null, serviceId, serviceIpPort);
            }
        }
    }

    private void collectProcessMemory(List<MonitorMetricSample> samples, Map<String, Double> latestValueMap, Date now,
            String serviceId, String serviceIpPort) {
        try {
            int pid = systemInfo.getOperatingSystem().getProcessId();
            OSProcess currentProcess = systemInfo.getOperatingSystem().getProcess(pid);
            long processRss = currentProcess.getResidentSetSize();
            addSample(samples, latestValueMap, now, "process_memory", "process_rss_bytes", "", processRss, "bytes",
                    null, serviceId, serviceIpPort);
        } catch (Exception e) {
            log.warn("collect process memory failed: {}", e.getMessage());
        }

        long totalPhysical = osMXBean.getTotalMemorySize();
        long freePhysical = osMXBean.getFreeMemorySize();
        if (totalPhysical > 0) {
            addSample(samples, latestValueMap, now, "process_memory", "os_total_memory_bytes", "", totalPhysical,
                    "bytes", null, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "process_memory", "os_available_memory_bytes", "",
                    freePhysical, "bytes", null, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "process_memory", "os_used_pct", "",
                    (totalPhysical - freePhysical) * 100D / totalPhysical, "percent", null, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "process_memory", "os_available_pct", "",
                    freePhysical * 100D / totalPhysical, "percent", null, serviceId, serviceIpPort);
        }
    }

    private void collectGc(List<MonitorMetricSample> samples, Map<String, Double> latestValueMap, Date now,
            String serviceId, String serviceIpPort) {
        for (GarbageCollectorMXBean gcBean : garbageCollectorMXBeans) {
            long count = Math.max(0L, gcBean.getCollectionCount());
            long timeMs = Math.max(0L, gcBean.getCollectionTime());
            String scope = classifyGcScope(gcBean.getName());
            String tag = "{\"gcName\":\"" + escape(gcBean.getName()) + "\"}";
            double avg = count == 0 ? 0D : (double) timeMs / count;
            long maxObserved = Math.max(gcMaxPauseMillis.getOrDefault(gcBean.getName(), 0L), Math.round(avg));
            gcMaxPauseMillis.put(gcBean.getName(), maxObserved);

            addSample(samples, latestValueMap, now, "gc", "gc_count_total", scope, count, "count", tag, serviceId,
                    serviceIpPort);
            addSample(samples, latestValueMap, now, "gc", "gc_time_total_ms", scope, timeMs, "ms", tag, serviceId,
                    serviceIpPort);
            addSample(samples, latestValueMap, now, "gc", "gc_avg_pause_ms", scope, avg, "ms", tag, serviceId,
                    serviceIpPort);
            addSample(samples, latestValueMap, now, "gc", "gc_max_pause_ms", scope, maxObserved, "ms", tag, serviceId,
                    serviceIpPort);
        }
    }

    private void collectThread(List<MonitorMetricSample> samples, Map<String, Double> latestValueMap, Date now,
            String serviceId, String serviceIpPort) {
        ThreadInfo[] infos = threadMXBean.dumpAllThreads(false, false);
        int blocked = 0;
        int waiting = 0;
        int runnable = 0;
        int timedWaiting = 0;
        int terminated = 0;
        for (ThreadInfo info : infos) {
            if (info == null || info.getThreadState() == null) {
                continue;
            }
            switch (info.getThreadState()) {
                case BLOCKED -> blocked++;
                case WAITING -> waiting++;
                case RUNNABLE -> runnable++;
                case TIMED_WAITING -> timedWaiting++;
                case TERMINATED -> terminated++;
                default -> {
                }
            }
        }

        Set<Thread> allThreads = Thread.getAllStackTraces().keySet();
        long virtualCount = allThreads.stream().filter(Thread::isVirtual).count();
        long platformCount = allThreads.size() - virtualCount;

        addSample(samples, latestValueMap, now, "thread", "thread_total", "", infos.length, "count", null, serviceId,
                serviceIpPort);
        addSample(samples, latestValueMap, now, "thread", "thread_platform_total", "", platformCount, "count", null,
                serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "thread", "thread_virtual_total", "", virtualCount, "count", null,
                serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "thread", "thread_blocked", "", blocked, "count", null, serviceId,
                serviceIpPort);
        addSample(samples, latestValueMap, now, "thread", "thread_waiting", "", waiting, "count", null, serviceId,
                serviceIpPort);
        addSample(samples, latestValueMap, now, "thread", "thread_runnable", "", runnable, "count", null, serviceId,
                serviceIpPort);
        addSample(samples, latestValueMap, now, "thread", "thread_timed_waiting", "", timedWaiting, "count", null,
                serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "thread", "thread_terminated", "", terminated, "count", null, serviceId,
                serviceIpPort);

        allThreads.stream()
                .sorted(Comparator.comparing(Thread::getName))
                .limit(20)
                .forEach(thread -> {
                    String tag = "{\"threadId\":" + thread.threadId() +
                            ",\"threadName\":\"" + escape(thread.getName()) +
                            "\",\"state\":\"" + thread.getState() +
                            "\",\"virtual\":" + thread.isVirtual() + "}";
                    addSample(samples, latestValueMap, now, "thread", "thread_info",
                            thread.getState().name().toLowerCase(Locale.ROOT), thread.threadId(), "id", tag, serviceId,
                            serviceIpPort);
                });
    }

    private void collectCpuAndLoad(List<MonitorMetricSample> samples, Map<String, Double> latestValueMap, Date now,
            String serviceId, String serviceIpPort) {
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        long[] currentTicks = processor.getSystemCpuLoadTicks();
        long[] prev = previousCpuTicks;
        previousCpuTicks = currentTicks;

        addSample(samples, latestValueMap, now, "cpu", "cpu_cores", "", processor.getLogicalProcessorCount(), "count",
                null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "cpu", "cpu_usage_pct", "", osMXBean.getCpuLoad() * 100D, "percent",
                null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "cpu", "cpu_process_usage_pct", "", osMXBean.getProcessCpuLoad() * 100D,
                "percent", null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "cpu", "cpu_process_time_ns", "", osMXBean.getProcessCpuTime(), "ns",
                null, serviceId, serviceIpPort);

        if (prev != null && prev.length == currentTicks.length) {
            long user = currentTicks[CentralProcessor.TickType.USER.getIndex()]
                    - prev[CentralProcessor.TickType.USER.getIndex()];
            long sys = currentTicks[CentralProcessor.TickType.SYSTEM.getIndex()]
                    - prev[CentralProcessor.TickType.SYSTEM.getIndex()];
            long idle = currentTicks[CentralProcessor.TickType.IDLE.getIndex()]
                    - prev[CentralProcessor.TickType.IDLE.getIndex()];
            long total = Math.max(1, user + sys + idle);
            addSample(samples, latestValueMap, now, "cpu", "cpu_user_time_pct", "", user * 100D / total, "percent",
                    null, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "cpu", "cpu_system_time_pct", "", sys * 100D / total, "percent",
                    null, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "cpu", "cpu_idle_time_pct", "", idle * 100D / total, "percent",
                    null, serviceId, serviceIpPort);
        }

        OperatingSystem os = systemInfo.getOperatingSystem();
        double[] loadAvg = processor.getSystemLoadAverage(3);
        addSample(samples, latestValueMap, now, "system_load", "load_avg_1m", "",
                loadAvg.length > 0 ? safeNonNegative(loadAvg[0]) : 0D, "value", null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "system_load", "load_avg_5m", "",
                loadAvg.length > 1 ? safeNonNegative(loadAvg[1]) : 0D, "value", null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "system_load", "load_avg_15m", "",
                loadAvg.length > 2 ? safeNonNegative(loadAvg[2]) : 0D, "value", null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "system_load", "os_process_count", "", os.getProcessCount(), "count",
                null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "system_load", "os_thread_count", "", os.getThreadCount(), "count",
                null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "system_load", "run_queue_length", "", threadMXBean.getThreadCount(),
                "count", null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "system_load", "blocked_process_count", "",
                threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds()).length, "count", null, serviceId,
                serviceIpPort);
    }

    private void collectDbPool(List<MonitorMetricSample> samples, Map<String, Double> latestValueMap, Date now,
            String serviceId, String serviceIpPort) {
        Map<String, DataSource> dsMap = applicationContext.getBeansOfType(DataSource.class);
        for (Map.Entry<String, DataSource> entry : dsMap.entrySet()) {
            if (!(entry.getValue() instanceof DruidDataSource druidDataSource)) {
                continue;
            }
            String poolName = MyStringUtils.isEmpty(druidDataSource.getName()) ? entry.getKey()
                    : druidDataSource.getName();
            String tag = "{\"pool\":\"" + escape(poolName) + "\"}";
            addSample(samples, latestValueMap, now, "db_pool", "pool_active_connections", poolName,
                    druidDataSource.getActiveCount(), "count", tag, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "db_pool", "pool_max_active", poolName,
                    druidDataSource.getMaxActive(), "count", tag, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "db_pool", "pool_min_idle", poolName, druidDataSource.getMinIdle(),
                    "count", tag, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "db_pool", "pool_idle_connections", poolName,
                    druidDataSource.getPoolingCount(), "count", tag, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "db_pool", "pool_pending_threads", poolName,
                    druidDataSource.getNotEmptyWaitThreadCount(), "count", tag, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "db_pool", "pool_create_count_total", poolName,
                    druidDataSource.getCreateCount(), "count", tag, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "db_pool", "pool_close_count_total", poolName,
                    druidDataSource.getCloseCount(), "count", tag, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "db_pool", "pool_connect_error_count_total", poolName,
                    druidDataSource.getConnectErrorCount(), "count", tag, serviceId, serviceIpPort);
            addSample(samples, latestValueMap, now, "db_pool", "pool_wait_millis_avg", poolName,
                    druidDataSource.getNotEmptyWaitMillis(), "ms", tag, serviceId, serviceIpPort);
        }
    }

    private void collectClassLoading(List<MonitorMetricSample> samples, Map<String, Double> latestValueMap, Date now,
            String serviceId, String serviceIpPort) {
        addSample(samples, latestValueMap, now, "class_loading", "class_loaded_count", "",
                classLoadingMXBean.getLoadedClassCount(), "count", null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "class_loading", "class_total_loaded", "",
                classLoadingMXBean.getTotalLoadedClassCount(), "count", null, serviceId, serviceIpPort);
        addSample(samples, latestValueMap, now, "class_loading", "class_unloaded_count", "",
                classLoadingMXBean.getUnloadedClassCount(), "count", null, serviceId, serviceIpPort);
    }

    private void evaluateAlertRules(Map<String, Double> latestValueMap, Date now) {
        List<MonitorAlertRule> rules = monitorAlertRuleMapper.selectEnabledRules();
        Instant nowInstant = now.toInstant();
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

    private Map<String, Object> buildOverviewFromCurrent(Map<String, Double> latestValueMap, Date now) {
        Map<String, Object> basicStats = new HashMap<>();
        basicStats.put("processRss", latestValueMap.getOrDefault(
                buildMetricKey("process_memory", "process_rss_bytes", ""), 0D));
        basicStats.put("cpuUsage", latestValueMap.getOrDefault(buildMetricKey("cpu", "cpu_usage_pct", ""), 0D));
        basicStats.put("cpuCore", latestValueMap.getOrDefault(buildMetricKey("cpu", "cpu_cores", ""), 0D));
        basicStats.put("heapUsage", latestValueMap.getOrDefault(buildMetricKey("heap", "heap_usage_pct", ""), 0D));
        basicStats.put("threadTotal", latestValueMap.getOrDefault(buildMetricKey("thread", "thread_total", ""), 0D));
        basicStats.put("virtualThreadTotal",
                latestValueMap.getOrDefault(buildMetricKey("thread", "thread_virtual_total", ""), 0D));
        basicStats.put("load1m", latestValueMap.getOrDefault(buildMetricKey("system_load", "load_avg_1m", ""), 0D));
        basicStats.put("gcCollector", simplifyGcCollectorName(
                garbageCollectorMXBeans.stream()
                        .map(GarbageCollectorMXBean::getName)
                        .toList()));

        Map<String, Object> result = new HashMap<>();
        result.put("sampleTime", now);
        result.put("basicStats", basicStats);
        result.put("alertSummary", buildAlertSummary());
        return result;
    }

    private Map<String, Object> queryLatestByCategories(String serviceId, String serviceIpPort) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String category : List.of("heap", "process_memory", "gc", "thread", "cpu", "system_load", "db_pool",
                "class_loading")) {
            List<MonitorMetricSample> metrics = monitorMetricMapper.selectLatestByCategory(category, serviceId,
                    serviceIpPort);
            map.put(category, metrics);
        }
        return map;
    }

    @Override
    public List<Map<String, String>> listServiceInstances() {
        return monitorMetricMapper.selectDistinctServiceInstances();
    }

    private Map<String, Object> buildAlertSummary() {
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

    private void addSample(List<MonitorMetricSample> samples,
            Map<String, Double> latestValueMap,
            Date sampleTime,
            String category,
            String name,
            String scope,
            double value,
            String unit,
            String tagJson,
            String serviceId,
            String serviceIpPort) {
        MonitorMetricSample sample = new MonitorMetricSample();
        sample.setMetricCategory(category);
        sample.setMetricName(name);
        sample.setMetricScope(nvl(scope));
        sample.setMetricValue(round2(value));
        sample.setMetricUnit(nvl(unit));
        sample.setTagJson(tagJson);
        sample.setServiceId(serviceId);
        sample.setServiceIpPort(serviceIpPort);
        sample.setSampleTime(sampleTime);
        samples.add(sample);
        latestValueMap.put(buildMetricKey(category, name, nvl(scope)), value);
    }

    private String resolveServiceId() {
        String serviceId = applicationContext.getEnvironment().getProperty("spring.application.name");
        if (MyStringUtils.isEmpty(serviceId)) {
            serviceId = sysConfigService.selectConfigByKey("sys.monitor.serviceId");
        }
        if (MyStringUtils.isEmpty(serviceId)) {
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                String hostname = localHost.getHostName();
                int pid = systemInfo.getOperatingSystem().getProcessId();
                serviceId = hostname + "-" + pid;
            } catch (Exception e) {
                serviceId = "unknown-" + System.currentTimeMillis();
            }
        }
        return serviceId;
    }

    private volatile String cachedServiceIpPort = null;

    private String resolveServiceIpPort() {
        if (cachedServiceIpPort != null) {
            return cachedServiceIpPort;
        }
        synchronized (this) {
            if (cachedServiceIpPort != null) {
                return cachedServiceIpPort;
            }
            try {
                String hostAddress = getLocalIpAddress();
                int port = applicationContext.getEnvironment().getProperty("server.port", Integer.class, 8080);
                cachedServiceIpPort = hostAddress + ":" + port;
                log.info("Resolved service IP:Port = {}", cachedServiceIpPort);
                return cachedServiceIpPort;
            } catch (Exception e) {
                log.warn("Failed to resolve service IP, using default 127.0.0.1:8080", e);
                cachedServiceIpPort = "127.0.0.1:8080";
                return cachedServiceIpPort;
            }
        }
    }

    private String getLocalIpAddress() {
        try {
            return IpUtils.getLocalHost();
        } catch (Exception e) {
            log.warn("Failed to get local IP via IpUtils, using 127.0.0.1", e);
            return "127.0.0.1";
        }
    }

    private List<MonitorMetricSample> aggregateByBucket(List<MonitorMetricSample> source, long bucketMillis) {
        if (bucketMillis <= 0) {
            return source;
        }
        Map<Long, List<MonitorMetricSample>> grouped = source.stream().collect(Collectors.groupingBy(item -> {
            long ts = item.getSampleTime().getTime();
            return ts - (ts % bucketMillis);
        }));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<MonitorMetricSample> bucket = entry.getValue();
                    MonitorMetricSample first = bucket.getFirst();
                    double avg = bucket.stream().mapToDouble(MonitorMetricSample::getMetricValue).average().orElse(0D);
                    MonitorMetricSample one = new MonitorMetricSample();
                    one.setMetricCategory(first.getMetricCategory());
                    one.setMetricName(first.getMetricName());
                    one.setMetricScope(first.getMetricScope());
                    one.setMetricUnit(first.getMetricUnit());
                    one.setTagJson(first.getTagJson());
                    one.setSampleTime(new Date(entry.getKey()));
                    one.setMetricValue(round2(avg));
                    return one;
                })
                .sorted(Comparator.comparing(MonitorMetricSample::getSampleTime))
                .collect(Collectors.toList());
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

    private long parseGranularityMillis(String granularity) {
        if (MyStringUtils.isEmpty(granularity)) {
            return Duration.ofMinutes(1).toMillis();
        }
        String text = granularity.toLowerCase(Locale.ROOT).trim();
        if (text.matches("\\d+[smhd]")) {
            long n = Long.parseLong(text.substring(0, text.length() - 1));
            char unit = text.charAt(text.length() - 1);
            return switch (unit) {
                case 's' -> Duration.ofSeconds(n).toMillis();
                case 'm' -> Duration.ofMinutes(n).toMillis();
                case 'h' -> Duration.ofHours(n).toMillis();
                case 'd' -> Duration.ofDays(n).toMillis();
                default -> Duration.ofMinutes(1).toMillis();
            };
        }
        return switch (text) {
            case "10s" -> Duration.ofSeconds(10).toMillis();
            case "30s" -> Duration.ofSeconds(30).toMillis();
            case "1m" -> Duration.ofMinutes(1).toMillis();
            case "5m" -> Duration.ofMinutes(5).toMillis();
            case "15m" -> Duration.ofMinutes(15).toMillis();
            case "1h" -> Duration.ofHours(1).toMillis();
            case "1d" -> Duration.ofDays(1).toMillis();
            case "7d" -> Duration.ofDays(7).toMillis();
            default -> Duration.ofMinutes(1).toMillis();
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

    private int parseIntConfig(String configKey, int defaultValue) {
        try {
            String value = sysConfigService.selectConfigByKey(configKey);
            if (MyStringUtils.isEmpty(value)) {
                return defaultValue;
            }
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String classifyGcScope(String gcName) {
        String lower = gcName.toLowerCase(Locale.ROOT);
        if (lower.contains("young") || lower.contains("scavenge")) {
            return "young";
        }
        if (lower.contains("old") || lower.contains("mark")) {
            return "old";
        }
        return "general";
    }

    private String classifyPoolScope(String poolName) {
        String lower = poolName.toLowerCase(Locale.ROOT);
        if (lower.contains("eden")) {
            return "eden";
        }
        if (lower.contains("survivor") || lower.contains("s0") || lower.contains("s1")) {
            return "survivor";
        }
        if (lower.contains("old") || lower.contains("tenured")) {
            return "old";
        }
        if (lower.contains("metaspace")) {
            return "metaspace";
        }
        return "general";
    }

    private void safeCollect(Runnable runnable, String name) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.warn("monitor collect {} failed: {}", name, e.getMessage(), e);
        }
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

    private String simplifyGcCollectorName(List<String> gcNames) {
        if (gcNames == null || gcNames.isEmpty()) {
            return "Unknown";
        }
        Set<String> uniqueNames = new LinkedHashSet<>(gcNames);
        Set<String> simplified = new LinkedHashSet<>();
        for (String name : uniqueNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("g1")) {
                simplified.add("G1");
            } else if (lower.contains("zgc")) {
                simplified.add("ZGC");
            } else if (lower.contains("shenandoah")) {
                simplified.add("Shenandoah");
            } else if (lower.contains("concurrentmarksweep") || lower.contains("parnew")) {
                simplified.add("CMS");
            } else if (lower.contains("ps scavenge") || lower.contains("ps marksweep")) {
                simplified.add("Parallel");
            } else if (lower.equals("copy") || lower.equals("marksweepcompact")) {
                simplified.add("Serial");
            } else {
                simplified.add(name);
            }
        }
        return String.join(", ", simplified);
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private double safeNonNegative(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0D;
        }
        return value;
    }
}
