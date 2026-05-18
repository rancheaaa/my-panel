package com.cq.agent.batch.tracker;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.agent.util.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class FileBatchCompletionTracker {

    private static final Logger logger = LoggerFactory.getLogger(FileBatchCompletionTracker.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private final Path pendingDirPath;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FileBatchCompletionTracker(String pendingDir) {
        this.pendingDirPath = Path.of(pendingDir);
        ensureDirectoryExists();
    }

    public boolean initFileBatchIfAbsent(Long fileBatchId, Long scanBatchId, ScannedFile scannedFile,
            AgentTaskConfig config, List<TargetAgentInfo> routedTargets) throws IOException {
        if (existsActiveBatchForFile(scannedFile.getAbsolutePath(), config.getTaskId())) {
            logger.info("文件已存在活跃批次，跳过重复初始化: filePath={}, taskId={}",
                    scannedFile.getAbsolutePath(), config.getTaskId());
            return false;
        }

        initFileBatch(fileBatchId, scanBatchId, scannedFile, config, routedTargets);
        return true;
    }

    public boolean existsActiveBatchForFile(String localFilePath, Long taskId) {
        if (localFilePath == null || !Files.exists(pendingDirPath)) {
            return false;
        }

        try (Stream<Path> stream = Files.list(pendingDirPath)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json") && p.getFileName().toString().startsWith("fb-"))
                    .anyMatch(p -> {
                        try {
                            FileBatchState state = GSON.fromJson(Files.readString(p), FileBatchState.class);
                            if (state == null || state.getSource() == null) {
                                return false;
                            }
                            if ("COMPLETED".equals(state.getStatus())) {
                                return false;
                            }
                            if (!localFilePath.equals(state.getSource().getFilePath())) {
                                return false;
                            }
                            if (taskId != null && state.getTaskId() != null
                                    && !taskId.equals(state.getTaskId())) {
                                return false;
                            }
                            return true;
                        } catch (Exception e) {
                            logger.warn("检查活跃批次文件失败: {}", p, e);
                            return false;
                        }
                    });
        } catch (IOException e) {
            logger.warn("扫描pending目录失败: {}", pendingDirPath, e);
            return false;
        }
    }

    public Long findActiveBatchIdForFile(String localFilePath, Long taskId) {
        if (localFilePath == null || !Files.exists(pendingDirPath)) {
            return null;
        }

        try (Stream<Path> stream = Files.list(pendingDirPath)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json") && p.getFileName().toString().startsWith("fb-"))
                    .map(p -> {
                        try {
                            FileBatchState state = GSON.fromJson(Files.readString(p), FileBatchState.class);
                            if (state == null || state.getSource() == null) {
                                return null;
                            }
                            if ("COMPLETED".equals(state.getStatus())) {
                                return null;
                            }
                            if (!localFilePath.equals(state.getSource().getFilePath())) {
                                return null;
                            }
                            if (taskId != null && state.getTaskId() != null
                                    && !taskId.equals(state.getTaskId())) {
                                return null;
                            }
                            return state.getFileBatchId();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(id -> id != null)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            logger.warn("扫描pending目录失败: {}", pendingDirPath, e);
            return null;
        }
    }

    public boolean isTargetCompletedInBatch(String localFilePath, Long taskId, String targetAgentKey) {
        if (localFilePath == null || targetAgentKey == null || !Files.exists(pendingDirPath)) {
            return false;
        }

        try (Stream<Path> stream = Files.list(pendingDirPath)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json") && p.getFileName().toString().startsWith("fb-"))
                    .anyMatch(p -> {
                        try {
                            FileBatchState state = GSON.fromJson(Files.readString(p), FileBatchState.class);
                            if (state == null || state.getSource() == null) {
                                return false;
                            }
                            if ("COMPLETED".equals(state.getStatus())) {
                                return false;
                            }
                            if (!localFilePath.equals(state.getSource().getFilePath())) {
                                return false;
                            }
                            if (taskId != null && state.getTaskId() != null
                                    && !taskId.equals(state.getTaskId())) {
                                return false;
                            }
                            if (state.getTargets() == null) {
                                return false;
                            }
                            return state.getTargets().stream()
                                    .anyMatch(t -> "COMPLETED".equals(t.getStatus())
                                            && matchesTargetAgentKey(t.getAgentName(), t.getAgentId(), targetAgentKey));
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
    }

    public boolean existsFileInPendingBatch(String localFilePath, String targetAgentKey) {
        return existsFileInPendingBatch(localFilePath, targetAgentKey, 30);
    }

    public boolean existsFileInPendingBatch(String localFilePath, String targetAgentKey, int pendingTimeoutMinutes) {
        if (localFilePath == null || targetAgentKey == null) {
            return false;
        }

        if (!Files.exists(pendingDirPath)) {
            return false;
        }

        try (Stream<Path> stream = Files.list(pendingDirPath)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json") && p.getFileName().toString().startsWith("fb-"))
                    .anyMatch(p -> {
                        try {
                            FileBatchState state = GSON.fromJson(Files.readString(p), FileBatchState.class);
                            if (state == null || state.getSource() == null) {
                                return false;
                            }

                            if ("COMPLETED".equals(state.getStatus())) {
                                return false;
                            }

                            if (!localFilePath.equals(state.getSource().getFilePath())) {
                                return false;
                            }

                            if (state.getTargets() == null || state.getTargets().isEmpty()) {
                                return false;
                            }

                            if (isBatchExpired(state, pendingTimeoutMinutes)) {
                                logger.info("批次已超时，视为失效: filePath={}, updateTime={}",
                                        localFilePath, state.getUpdateTime());
                                return false;
                            }

                            return state.getTargets().stream()
                                    .anyMatch(t -> "PENDING".equals(t.getStatus())
                                            && matchesTargetAgentKey(t.getAgentName(), t.getAgentId(), targetAgentKey));

                        } catch (Exception e) {
                            logger.warn("检查pending批次文件失败: {}", p, e);
                            return false;
                        }
                    });
        } catch (IOException e) {
            logger.warn("扫描pending目录失败: {}", pendingDirPath, e);
            return false;
        }
    }

    private boolean isBatchExpired(FileBatchState state, int timeoutMinutes) {
        if (timeoutMinutes <= 0) {
            return false;
        }
        String updateTimeStr = state.getUpdateTime();
        if (updateTimeStr == null || updateTimeStr.isEmpty()) {
            return false;
        }
        try {
            LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr, FORMATTER);
            long updateEpoch = updateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long now = System.currentTimeMillis();
            long timeoutMs = (long) timeoutMinutes * 60 * 1000;
            return (now - updateEpoch) > timeoutMs;
        } catch (Exception e) {
            logger.warn("解析批次更新时间失败: {}", updateTimeStr, e);
            return false;
        }
    }

    private boolean matchesTargetAgentKey(String agentName, String agentId, String targetAgentKey) {
        if (targetAgentKey == null) {
            return false;
        }
        if (agentName != null && (agentName.equals(targetAgentKey)
                || agentName.endsWith("@" + targetAgentKey))) {
            return true;
        }
        if (agentId != null && (agentId.equals(targetAgentKey)
                || agentId.endsWith("@" + targetAgentKey))) {
            return true;
        }
        return false;
    }

    public void initFileBatch(Long fileBatchId, Long scanBatchId, ScannedFile scannedFile, AgentTaskConfig config,
            List<TargetAgentInfo> routedTargets) throws IOException {
        logger.info("初始化文件批次: fileBatchId={}, scanBatchId={}", fileBatchId, scanBatchId);
        FileBatchState state = buildInitialState(fileBatchId, scanBatchId, scannedFile, config, routedTargets);
        writeState(state);

        logger.info("文件批次初始化完成: fileBatchId={}, totalTargets={}",
                fileBatchId, state.getSummary().getTotalTargets());
    }

    public boolean markCompleted(Long fileBatchId, String targetAgentId) throws IOException {
        ReentrantLock lock = getLock(fileBatchId);
        lock.lock();
        try {
            FileBatchState state = loadFileBatch(fileBatchId);

            FileBatchState.TargetProgress target = findTarget(state, targetAgentId);
            target.setStatus("COMPLETED");
            target.setCompletedAt(LocalDateTime.now().format(FORMATTER));

            recalculateAndSave(state);

            boolean allCompleted = state.getSummary().getCompletedCount() == state.getSummary().getTotalTargets()
                    && "COMPLETED".equals(state.getStatus());

            logger.info("标记目标完成: fileBatchId={}, targetAgentId={}, allCompleted={}",
                    fileBatchId, targetAgentId, allCompleted);

            return allCompleted;
        } finally {
            lock.unlock();
        }
    }

    public void markFailed(Long fileBatchId, String targetAgentId, boolean isFinalFailure) throws IOException {
        ReentrantLock lock = getLock(fileBatchId);
        lock.lock();
        try {
            FileBatchState state = loadFileBatch(fileBatchId);

            FileBatchState.TargetProgress target = findTarget(state, targetAgentId);
            target.setStatus(isFinalFailure ? "FINAL_FAILURE" : "FAILED");

            if (isFinalFailure) {
                state.setStatus("FAILED");
                logger.warn("标记终态失败: fileBatchId={}, targetAgentId={}", fileBatchId, targetAgentId);
            } else {
                logger.info("标记非终态失败(可重试): fileBatchId={}, targetAgentId={}", fileBatchId, targetAgentId);
            }

            recalculateSummary(state);
            writeState(state);
        } finally {
            lock.unlock();
        }
    }

    public void deleteFileBatch(Long fileBatchId) throws IOException {
        Path jsonPath = resolveJsonPath(fileBatchId);
        AtomicFileWriter.deleteIfExists(jsonPath);
        locks.remove(String.valueOf(fileBatchId));
        logger.info("删除文件批次: fileBatchId={}", fileBatchId);
    }

    public Path getPendingDirPath() {
        return pendingDirPath;
    }

    public FileBatchState loadFileBatch(Long fileBatchId) throws IOException {
        Path jsonPath = resolveJsonPath(fileBatchId);
        if (!Files.exists(jsonPath)) {
            throw new IOException("文件批次不存在: " + jsonPath);
        }
        String content = Files.readString(jsonPath);
        return GSON.fromJson(content, FileBatchState.class);
    }

    public List<FileBatchState> recoverPendingBatches() throws IOException {
        List<FileBatchState> result = new ArrayList<>();

        try (Stream<Path> stream = Files.list(pendingDirPath)) {
            stream.filter(p -> p.toString().endsWith(".json") && p.getFileName().toString().startsWith("fb-"))
                    .forEach(p -> {
                        try {
                            FileBatchState state = GSON.fromJson(Files.readString(p), FileBatchState.class);
                            if (!"COMPLETED".equals(state.getStatus())) {
                                result.add(state);
                            }
                        } catch (IOException e) {
                            logger.warn("恢复残留批次时读取失败: {}", p, e);
                        }
                    });
        }

        logger.info("恢复残留批次完成: count={}", result.size());
        return result;
    }

    private FileBatchState buildInitialState(Long fileBatchId, Long scanBatchId, ScannedFile scannedFile,
            AgentTaskConfig config,
            List<TargetAgentInfo> routedTargets) {
        FileBatchState state = new FileBatchState();
        state.setFileBatchId(fileBatchId);
        state.setScanBatchId(scanBatchId);
        state.setTaskId(config.getTaskId());
        state.setTaskName(config.getTaskName());
        state.setStatus("PENDING");
        String now = LocalDateTime.now().format(FORMATTER);
        state.setCreateTime(now);
        state.setUpdateTime(now);

        FileBatchState.SourceInfo sourceInfo = new FileBatchState.SourceInfo();
        sourceInfo.setAgentId(config.getSourceAgentId());
        sourceInfo.setAgentName(config.getSourceAgentName());
        sourceInfo.setSourceDir(config.getSourceDir());
        sourceInfo.setFilePath(scannedFile.getAbsolutePath());
        sourceInfo.setFileName(scannedFile.getFileName());
        sourceInfo.setFileSizeBytes(scannedFile.getFileSize());
        long ts = scannedFile.getLastModified();
        sourceInfo.setLastModified(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).format(FORMATTER));
        state.setSource(sourceInfo);

        TransferConfig tc = config.getTransferConfig();
        if (tc != null) {
            FileBatchState.TransferConfigInfo tcInfo = new FileBatchState.TransferConfigInfo();
            tcInfo.setTransferMode(tc.getTransferMode());
            tcInfo.setRoutingStrategy(tc.getRoutingStrategy());
            tcInfo.setPostTransferAction(tc.getPostTransferAction());
            tcInfo.setBackupDir(tc.getBackupDir());
            tcInfo.setBackupMode(tc.getBackupMode());
            state.setTransferConfig(tcInfo);
        }

        List<FileBatchState.TargetProgress> targets = new ArrayList<>();
        for (TargetAgentInfo tai : routedTargets) {
            FileBatchState.TargetProgress tp = new FileBatchState.TargetProgress();
            tp.setAgentId(tai.getAgentId());
            tp.setAgentName(tai.getAgentName());
            tp.setTargetDir(tai.getTargetDir());
            tp.setTargetPath(tai.getTargetDir() + "/" + scannedFile.getFileName());
            tp.setStatus("PENDING");
            targets.add(tp);
        }
        state.setTargets(targets);

        FileBatchState.Summary summary = new FileBatchState.Summary();
        summary.setTotalTargets(routedTargets.size());
        summary.setCompletedCount(0);
        summary.setFailedCount(0);
        summary.setPendingCount(routedTargets.size());
        state.setSummary(summary);

        return state;
    }

    private void recalculateAndSave(FileBatchState state) throws IOException {
        recalculateSummary(state);
        determineStatus(state);
        state.setUpdateTime(LocalDateTime.now().format(FORMATTER));
        writeState(state);
    }

    private void recalculateSummary(FileBatchState state) {
        int completed = 0;
        int failed = 0;
        int pending = 0;

        for (FileBatchState.TargetProgress tp : state.getTargets()) {
            switch (tp.getStatus()) {
                case "COMPLETED" -> completed++;
                case "FAILED", "FINAL_FAILURE" -> failed++;
                default -> pending++;
            }
        }

        state.getSummary().setCompletedCount(completed);
        state.getSummary().setFailedCount(failed);
        state.getSummary().setPendingCount(pending);
    }

    private void determineStatus(FileBatchState state) {
        boolean hasFinalFailure = state.getTargets().stream()
                .anyMatch(t -> "FINAL_FAILURE".equals(t.getStatus()));
        if (hasFinalFailure) {
            state.setStatus("FAILED");
            return;
        }

        boolean allCompleted = state.getTargets().stream()
                .allMatch(t -> "COMPLETED".equals(t.getStatus()));
        if (allCompleted) {
            state.setStatus("COMPLETED");
            return;
        }

        state.setStatus("PENDING");
    }

    private void writeState(FileBatchState state) throws IOException {
        Path jsonPath = resolveJsonPath(state.getFileBatchId());
        String json = GSON.toJson(state);
        AtomicFileWriter.writeAtomically(jsonPath, json);
    }

    private Path resolveJsonPath(Long fileBatchId) {
        return pendingDirPath.resolve("fb-" + fileBatchId + ".json");
    }

    private ReentrantLock getLock(Long fileBatchId) {
        return locks.computeIfAbsent(String.valueOf(fileBatchId), k -> new ReentrantLock());
    }

    private FileBatchState.TargetProgress findTarget(FileBatchState state, String targetAgentId) {
        return state.getTargets().stream()
                .filter(t -> t.getAgentId().equals(targetAgentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未找到目标agent: fileBatchId=" + state.getFileBatchId() + ", agentId=" + targetAgentId));
    }

    private void ensureDirectoryExists() {
        if (!Files.exists(pendingDirPath)) {
            try {
                Files.createDirectories(pendingDirPath);
                logger.info("创建追踪目录: {}", pendingDirPath);
            } catch (IOException e) {
                throw new RuntimeException("创建追踪目录失败: " + pendingDirPath, e);
            }
        }
    }
}
