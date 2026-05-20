package com.cq.agent.batch.tracker;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.agent.util.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import lombok.Getter;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class FileBatchCompletionTracker {

    private static final Logger logger = LoggerFactory.getLogger(FileBatchCompletionTracker.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    @Getter
    private final Path pendingDirPath;

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, FileBatchState> batchStateMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> fileToBatchIndex = new ConcurrentHashMap<>();

    public FileBatchCompletionTracker(String pendingDir) {
        this.pendingDirPath = Path.of(pendingDir);
        ensureDirectoryExists();
        loadExistingBatchesFromDisk();
    }

    private void loadExistingBatchesFromDisk() {
        if (!Files.exists(pendingDirPath)) {
            return;
        }
        AtomicInteger loadedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        try (Stream<Path> stream = Files.list(pendingDirPath)) {
            stream.filter(p -> p.toString().endsWith(".json") && p.getFileName().toString().startsWith("fb-"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            FileBatchState state = GSON.fromJson(content, FileBatchState.class);
                            if (state != null && state.getFileBatchId() != null) {
                                putToMemory(state);
                                loadedCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            logger.warn("加载残留批次文件失败: {}", p, e);
                            failedCount.incrementAndGet();
                        }
                    });
        } catch (IOException e) {
            logger.warn("扫描pending目录加载批次失败: {}", pendingDirPath, e);
        }
        if (loadedCount.get() > 0 || failedCount.get() > 0) {
            logger.info("从磁盘加载文件批次到内存完成: loaded={}, failed={}", loadedCount.get(), failedCount.get());
        }
    }

    private void putToMemory(FileBatchState state) {
        if (state == null || state.getFileBatchId() == null) {
            return;
        }
        String batchKey = String.valueOf(state.getFileBatchId());
        batchStateMap.put(batchKey, state);
        buildFileIndex(state);
    }

    private void removeFromMemory(Long fileBatchId) {
        if (fileBatchId == null) return;
        String batchKey = String.valueOf(fileBatchId);
        FileBatchState removed = batchStateMap.remove(batchKey);
        if (removed != null && removed.getSource() != null) {
            removeFileIndex(removed.getSource().getFilePath(), removed.getTaskId());
        }
    }

    private void buildFileIndex(FileBatchState state) {
        if (state == null || state.getSource() == null) return;
        String filePath = state.getSource().getFilePath();
        Long taskId = state.getTaskId();
        if (filePath != null) {
            String indexKey = buildFileIndexKey(filePath, taskId);
            fileToBatchIndex.put(indexKey, String.valueOf(state.getFileBatchId()));
        }
    }

    private void removeFileIndex(String filePath, Long taskId) {
        if (filePath == null) return;
        fileToBatchIndex.remove(buildFileIndexKey(filePath, taskId));
    }

    private static String buildFileIndexKey(String filePath, Long taskId) {
        if (taskId != null) {
            return filePath + "|" + taskId;
        }
        return filePath;
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
        if (localFilePath == null) {
            return false;
        }
        String indexKey = buildFileIndexKey(localFilePath, taskId);
        String batchIdStr = fileToBatchIndex.get(indexKey);
        if (batchIdStr == null) {
            return false;
        }
        FileBatchState state = batchStateMap.get(batchIdStr);
        if (state == null) {
            fileToBatchIndex.remove(indexKey, batchIdStr);
            return false;
        }
        return !"COMPLETED".equals(state.getStatus());
    }

    public Long findActiveBatchIdForFile(String localFilePath, Long taskId) {
        if (localFilePath == null) {
            return null;
        }
        String indexKey = buildFileIndexKey(localFilePath, taskId);
        String batchIdStr = fileToBatchIndex.get(indexKey);
        if (batchIdStr == null) {
            return null;
        }
        try {
            Long batchId = Long.parseLong(batchIdStr);
            FileBatchState state = batchStateMap.get(batchIdStr);
            if (state == null || "COMPLETED".equals(state.getStatus())) {
                return null;
            }
            return batchId;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isTargetCompletedInBatch(String localFilePath, Long taskId, String targetAgentKey) {
        if (localFilePath == null || targetAgentKey == null) {
            return false;
        }
        String indexKey = buildFileIndexKey(localFilePath, taskId);
        String batchIdStr = fileToBatchIndex.get(indexKey);
        if (batchIdStr == null) {
            return false;
        }
        FileBatchState state = batchStateMap.get(batchIdStr);
        if (state == null || state.getTargets() == null || "COMPLETED".equals(state.getStatus())) {
            return false;
        }
        return state.getTargets().stream()
                .anyMatch(t -> "COMPLETED".equals(t.getStatus())
                        && matchesTargetAgentKey(t.getAgentName(), t.getAgentId(), targetAgentKey));
    }

    private boolean matchesTargetAgentKey(String agentName, String agentId, String targetAgentKey) {
        if (targetAgentKey == null) {
            return false;
        }
        if (agentName != null && (agentName.equals(targetAgentKey)
                || agentName.endsWith("@" + targetAgentKey))) {
            return true;
        }
        return agentId != null && (agentId.equals(targetAgentKey)
                || agentId.endsWith("@" + targetAgentKey));
    }

    public void initFileBatch(Long fileBatchId, Long scanBatchId, ScannedFile scannedFile, AgentTaskConfig config,
            List<TargetAgentInfo> routedTargets) throws IOException {
        logger.info("初始化文件批次: fileBatchId={}, scanBatchId={}", fileBatchId, scanBatchId);
        FileBatchState state = buildInitialState(fileBatchId, scanBatchId, scannedFile, config, routedTargets);
        writeState(state);
        putToMemory(state);

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
            putToMemory(state);

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
            putToMemory(state);
        } finally {
            lock.unlock();
        }
    }

    public void deleteFileBatch(Long fileBatchId) throws IOException {
        Path jsonPath = resolveJsonPath(fileBatchId);
        AtomicFileWriter.deleteIfExists(jsonPath);
        removeFromMemory(fileBatchId);
        locks.remove(String.valueOf(fileBatchId));
        logger.info("删除文件批次: fileBatchId={}", fileBatchId);
    }

    public FileBatchState loadFileBatch(Long fileBatchId) throws IOException {
        Path jsonPath = resolveJsonPath(fileBatchId);
        if (!Files.exists(jsonPath)) {
            throw new IOException("文件批次不存在: " + jsonPath);
        }
        String content = Files.readString(jsonPath);
        return GSON.fromJson(content, FileBatchState.class);
    }

    public List<FileBatchState> recoverPendingBatches() {
        List<FileBatchState> result = new ArrayList<>();
        for (FileBatchState state : batchStateMap.values()) {
            if (!"COMPLETED".equals(state.getStatus())) {
                result.add(state);
            }
        }
        logger.info("恢复残留批次完成(内存): count={}", result.size());
        return result;
    }

    public int getMemorySize() {
        return batchStateMap.size();
    }

    public int getFileIndexSize() {
        return fileToBatchIndex.size();
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
        sourceInfo.setFilePath(scannedFile.getOriginalAbsolutePath());
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
