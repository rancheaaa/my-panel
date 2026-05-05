package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.scanner.BatchFileScanner;
import com.cq.agent.batch.scanner.ScanRequest;
import com.cq.agent.batch.scanner.ScanResponse;
import com.cq.agent.batch.report.ProxyReportClient;
import com.cq.agent.client.upload.PersistentMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

public class BatchScanScheduler {
    private static final Logger log = LoggerFactory.getLogger(BatchScanScheduler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String JOB_GROUP = "BATCH_SCAN";
    private static final String TRIGGER_GROUP = "BATCH_SCAN_TRIGGER";

    private final Scheduler quartzScheduler;
    private final BatchFileScanner scanner;
    private final ProxyReportClient reportClient;
    private final PersistentMap<String, ScanConfig> configStore;
    private final String dataDir;

    public BatchScanScheduler(BatchFileScanner scanner, ProxyReportClient reportClient, String dataDir)
            throws SchedulerException, RocksDBException {
        this.scanner = scanner;
        this.reportClient = reportClient;
        this.dataDir = dataDir;

        File storeDir = new File(dataDir, "batch-schedule");
        storeDir.mkdirs();
        this.configStore = new PersistentMap<>(
                storeDir.getAbsolutePath(), "scan-configs", String.class, ScanConfig.class);

        SchedulerFactory factory = new StdSchedulerFactory();
        this.quartzScheduler = factory.getScheduler();
        this.quartzScheduler.getContext().put("batchScanScheduler", this);
        this.quartzScheduler.start();

        restoreAllJobs();

        log.info("BatchScanScheduler started, restored {} scheduled tasks from persistent store", configStore.size());
    }

    public void shutdown() {
        try {
            if (quartzScheduler != null && !quartzScheduler.isShutdown()) {
                quartzScheduler.shutdown(true);
            }
        } catch (SchedulerException e) {
            log.error("Failed to shutdown scheduler: {}", e.getMessage());
        }
        if (configStore != null) {
            configStore.close();
        }
        log.info("BatchScanScheduler shutdown complete");
    }

    public void upsertTask(Long taskId, String cronExpression, Map<String, Object> scanConfig,
            String proxyBaseUrl, List<Map<String, Object>> targetAgents,
            Map<String, String> targetDirs, Integer preserveDirStructure,
            Long maxBandwidthBytesPerSec) {
        try {
            String key = String.valueOf(taskId);

            ScanConfig config = new ScanConfig();
            config.taskId = taskId;
            config.cronExpression = cronExpression;
            config.scanConfig = scanConfig;
            config.proxyBaseUrl = proxyBaseUrl;
            config.targetAgents = targetAgents;
            config.targetDirs = targetDirs;
            config.preserveDirStructure = preserveDirStructure != null ? preserveDirStructure : 1;
            config.maxBandwidthBytesPerSec = maxBandwidthBytesPerSec;

            configStore.put(key, config);

            JobDetail jobDetail = JobBuilder.newJob(ScanJob.class)
                    .withIdentity("scan-job-" + taskId, JOB_GROUP)
                    .usingJobData("taskId", taskId)
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("scan-trigger-" + taskId, TRIGGER_GROUP)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .build();

            if (quartzScheduler.checkExists(jobDetail.getKey())) {
                quartzScheduler.rescheduleJob(trigger.getKey(), trigger);
                log.info("Rescheduled scan job for task {} with cron: {}", taskId, cronExpression);
            } else {
                quartzScheduler.scheduleJob(jobDetail, trigger);
                log.info("Scheduled new scan job for task {} with cron: {}", taskId, cronExpression);
            }
        } catch (SchedulerException e) {
            log.error("Failed to schedule scan job for task {}: {}", taskId, e.getMessage());
            throw new RuntimeException("Failed to schedule task: " + e.getMessage(), e);
        }
    }

    public void removeTask(Long taskId) {
        try {
            JobKey jobKey = new JobKey("scan-job-" + taskId, JOB_GROUP);
            if (quartzScheduler.checkExists(jobKey)) {
                quartzScheduler.deleteJob(jobKey);
            }
            configStore.remove(String.valueOf(taskId));
            log.info("Removed scheduled scan job for task {}", taskId);
        } catch (SchedulerException e) {
            log.error("Failed to remove scan job for task {}: {}", taskId, e.getMessage());
        }
    }

    public boolean hasScheduledTask(Long taskId) {
        try {
            return quartzScheduler.checkExists(new JobKey("scan-job-" + taskId, JOB_GROUP));
        } catch (SchedulerException e) {
            return false;
        }
    }

    public ScanConfig getTaskConfig(Long taskId) {
        return configStore.get(String.valueOf(taskId));
    }

    private void restoreAllJobs() {
        List<Map.Entry<String, ScanConfig>> entries = configStore.entrySet();
        for (Map.Entry<String, ScanConfig> entry : entries) {
            try {
                ScanConfig config = entry.getValue();
                Long taskId = config.taskId;
                String cron = config.cronExpression;

                JobDetail jobDetail = JobBuilder.newJob(ScanJob.class)
                        .withIdentity("scan-job-" + taskId, JOB_GROUP)
                        .usingJobData("taskId", taskId)
                        .storeDurably()
                        .build();

                CronTrigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity("scan-trigger-" + taskId, TRIGGER_GROUP)
                        .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                        .build();

                if (!quartzScheduler.checkExists(jobDetail.getKey())) {
                    quartzScheduler.scheduleJob(jobDetail, trigger);
                    log.info("Restored scheduled scan job: task={}, cron={}", taskId, cron);
                }
            } catch (Exception e) {
                log.warn("Failed to restore scheduled job for key={}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    @DisallowConcurrentExecution
    @PersistJobDataAfterExecution
    public static class ScanJob implements Job {
        private static final Logger jobLog = LoggerFactory.getLogger(ScanJob.class);

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            Long taskId = context.getJobDetail().getJobDataMap().getLong("taskId");
            BatchScanScheduler scheduler;
            try {
                scheduler = (BatchScanScheduler) context.getScheduler().getContext().get("batchScanScheduler");
            } catch (SchedulerException e) {
                jobLog.error("Failed to get scheduler from context: {}", e.getMessage());
                return;
            }
            if (scheduler == null || taskId == null) {
                jobLog.error("ScanJob executed but scheduler or taskId is null");
                return;
            }

            ScanConfig config = scheduler.getTaskConfig(taskId);
            if (config == null) {
                jobLog.warn("[Cron] No config found for task {}, removing stale job", taskId);
                try {
                    context.getScheduler().deleteJob(context.getJobDetail().getKey());
                } catch (Exception ignored) {
                }
                return;
            }

            jobLog.info("[Cron] Executing scheduled scan for task {}", taskId);

            try {
                ScanRequest request = gson.fromJson(gson.toJson(config.scanConfig), ScanRequest.class);
                ScanResponse response = scheduler.scanner.scan(request);

                if (response.isSuccess() && response.getResult() != null && response.getResult().getFiles() != null
                        && !response.getResult().getFiles().isEmpty()) {
                    jobLog.info("[Cron] Task {} scanned {} files, dispatching...", taskId,
                            response.getResult().getTotalFiles());

                    String baseDir = config.scanConfig != null ? (String) config.scanConfig.get("baseDir") : null;
                    List<Map<String, Object>> subtasks = new java.util.ArrayList<>();
                    for (var file : response.getResult().getFiles()) {
                        Map<String, Object> st = new java.util.LinkedHashMap<>();
                        st.put("taskId", taskId);
                        st.put("filePath", file.getRelativePath());
                        st.put("fileName", file.getRelativePath().contains("/") ? file.getRelativePath().substring(file.getRelativePath().lastIndexOf('/') + 1) : file.getRelativePath());
                        st.put("fileSizeBytes", file.getSizeBytes());
                        if (config.targetAgents != null) {
                            for (Map<String, Object> agent : config.targetAgents) {
                                Map<String, Object> subtaskCopy = new java.util.LinkedHashMap<>(st);
                                subtaskCopy.put("targetAgentId", agent.get("agentId"));
                                subtaskCopy.put("targetAgentApiUrl", agent.get("apiUrl"));
                                subtasks.add(subtaskCopy);
                            }
                        }
                    }

                    Map<String, Object> dispatchRequest = new java.util.LinkedHashMap<>();
                    dispatchRequest.put("dispatchId", "cron-" + taskId + "-" + System.currentTimeMillis());
                    dispatchRequest.put("taskId", taskId);
                    dispatchRequest.put("maxBandwidthBytesPerSec", config.maxBandwidthBytesPerSec);
                    dispatchRequest.put("agentTargetDirs", config.targetDirs);
                    dispatchRequest.put("preserveDirStructure", config.preserveDirStructure != null && config.preserveDirStructure == 1);
                    dispatchRequest.put("sourceBaseDir", baseDir);
                    dispatchRequest.put("subtasks", subtasks);

                    String url = config.proxyBaseUrl + "/api/internal/batch/dispatch";
                    java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                            .version(java.net.http.HttpClient.Version.HTTP_1_1)
                            .connectTimeout(java.time.Duration.ofSeconds(10))
                            .build();
                    String jsonBody = gson.toJson(dispatchRequest);
                    java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                            .timeout(java.time.Duration.ofSeconds(30))
                            .build();
                    java.net.http.HttpResponse<String> httpResponse = client.send(httpRequest,
                            java.net.http.HttpResponse.BodyHandlers.ofString());
                    jobLog.info("[Cron] Task {} dispatch result: {}", taskId, httpResponse.body());
                } else {
                    jobLog.info("[Cron] Task {} scan completed, no matching files found", taskId);
                }
            } catch (Exception e) {
                jobLog.error("[Cron] Scheduled scan failed for task {}: {}", taskId, e.getMessage(), e);
            }
        }
    }

    public static class ScanConfig {
        public Long taskId;
        public String cronExpression;
        public Map<String, Object> scanConfig;
        public String proxyBaseUrl;
        public List<Map<String, Object>> targetAgents;
        public Map<String, String> targetDirs;
        public Integer preserveDirStructure;
        public Long maxBandwidthBytesPerSec;
    }
}
