# FileBatch Completion Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在一对多批量文件传输场景下，确保所有目标节点全部成功接收文件后，才执行传输后操作（DELETE/BACKUP），使用JSON文件持久化追踪状态。

**Architecture:** 新增 `FileBatchCompletionTracker` 类，以 `fileBatchId` 为维度用JSON文件追踪同一文件发往多个目标Agent的整体完成状态。修改 `BatchUploadListener` 的 onComplete/onError 回调，通过 tracker 判断是否全部完成后才执行后置操作。一对一场景完全不受影响。

**Tech Stack:** Java 21, Gson (已有), AtomicFileWriter (已有), JUnit 5, @TempDir

---

### Task 1: 创建 FileBatchState 数据模型

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/tracker/FileBatchState.java`
- Test: `agent/src/test/java/com/cq/agent/batch/tracker/FileBatchStateTest.java`

- [ ] **Step 1: 编写 FileBatchStateTest 失败测试**

```java
package com.cq.agent.batch.tracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileBatchState - 文件批次状态数据模型")
class FileBatchStateTest {

    @Test
    @DisplayName("应该正确构建完整的FileBatchState对象")
    void shouldConstructCompleteState() {
        FileBatchState state = new FileBatchState();
        state.setFileBatchId(12345L);
        state.setTaskId(1001L);
        state.setTaskName("测试任务");
        state.setScanBatchId(999L);

        FileBatchState.SourceInfo source = new FileBatchState.SourceInfo();
        source.setAgentId("src-agent");
        source.setAgentName("root@10.0.0.1:7777");
        source.setSourceDir("/data/logs");
        source.setFilePath("/data/logs/app.log");
        source.setFileName("app.log");
        source.setFileSizeBytes(1024L);
        source.setLastModified("2026-05-18 10:00:00.000");
        state.setSource(source);

        FileBatchState.TransferConfigInfo tc = new FileBatchState.TransferConfigInfo();
        tc.setTransferMode("ONE_TO_MANY");
        tc.setRoutingStrategy("BROADCAST");
        tc.setPostTransferAction("DELETE");
        state.setTransferConfig(tc);

        assertEquals(12345L, state.getFileBatchId());
        assertEquals("DELETE", state.getTransferConfig().getPostTransferAction());
        assertEquals("app.log", state.getSource().getFileName());
    }

    @Test
    @DisplayName("TargetProgress应该正确记录单节点信息")
    void shouldHoldTargetProgress() {
        FileBatchState.TargetProgress tp = new FileBatchState.TargetProgress();
        tp.setAgentId("target-001");
        tp.setAgentName("root@10.0.0.2:7777");
        tp.setTargetDir("/data/remote");
        tp.setTargetPath("/data/remote/app.log");
        tp.setStatus("PENDING");

        assertEquals("PENDING", tp.getStatus());
        assertNull(tp.getCompletedAt());
    }

    @Test
    @DisplayName("Summary应该正确计算汇总统计")
    void shouldCalculateSummary() {
        FileBatchState.Summary summary = new FileBatchState.Summary();
        summary.setTotalTargets(3);
        summary.setCompletedCount(2);
        summary.setFailedCount(0);
        summary.setPendingCount(1);

        assertEquals(3, summary.getTotalTargets());
        assertEquals(1, summary.getPendingCount());
    }
}
```

- [ ] **Step 2: 运行测试确认失败（编译错误）**

Run: `mvn test -pl agent -Dtest=FileBatchStateTest -DfailIfNoTests=false`
Expected: COMPILATION_ERROR - class FileBatchState not found

- [ ] **Step 3: 实现 FileBatchState.java**

```java
package com.cq.agent.batch.tracker;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class FileBatchState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long fileBatchId;
    private Long taskId;
    private String taskName;
    private Long scanBatchId;

    private SourceInfo source;
    private TransferConfigInfo transferConfig;
    private List<TargetProgress> targets;
    private Summary summary;

    private String status;
    private String createTime;
    private String updateTime;

    @Data
    public static class SourceInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String agentId;
        private String agentName;
        private String sourceDir;
        private String filePath;
        private String fileName;
        private long fileSizeBytes;
        private String lastModified;
    }

    @Data
    public static class TransferConfigInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String transferMode;
        private String routingStrategy;
        private String postTransferAction;
        private String backupDir;
        private String backupMode;
    }

    @Data
    public static class TargetProgress implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String agentId;
        private String agentName;
        private String targetDir;
        private String targetPath;
        private String status;
        private String completedAt;
    }

    @Data
    public static class Summary implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private int totalTargets;
        private int completedCount;
        private int failedCount;
        private int pendingCount;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl agent -Dtest=FileBatchStateTest -DfailIfNoTests=false`
Expected: BUILD SUCCESS, all tests PASS

- [ ] **Step 5: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/tracker/FileBatchState.java agent/src/test/java/com/cq/agent/batch/tracker/FileBatchStateTest.java
git commit -m "feat(agent): add FileBatchState data model for file batch completion tracking"
```

---

### Task 2: 创建 FileBatchCompletionTracker 核心逻辑

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/tracker/FileBatchCompletionTracker.java`
- Test: `agent/src/test/java/com/cq/agent/batch/tracker/FileBatchCompletionTrackerTest.java`

- [ ] **Step 1: 编写核心功能的失败测试（init + markCompleted + markFailed + delete）**

```java
package com.cq.agent.batch.tracker;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileBatchCompletionTracker - 文件批次完成状态追踪器")
class FileBatchCompletionTrackerTest {

    @TempDir
    Path tempDir;

    private FileBatchCompletionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new FileBatchCompletionTracker(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清理临时目录中的所有文件
        if (Files.exists(tempDir)) {
            try (var entries = Files.list(tempDir)) {
                entries.forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    private ScannedFile createScannedFile(String name, long size) {
        ScannedFile sf = new ScannedFile();
        sf.setFileName(name);
        sf.setFileSize(size);
        sf.setAbsolutePath("/data/" + name);
        sf.setLastModified(System.currentTimeMillis());
        return sf;
    }

    private AgentTaskConfig createConfig(String postAction) {
        AgentTaskConfig config = new AgentTaskConfig();
        config.setTaskId(1L);
        config.setTaskName("测试任务");
        config.setSourceAgentId("src-agent");
        config.setSourceAgentName("root@10.0.0.1:7777");
        config.setSourceDir("/data");

        TransferConfig tc = new TransferConfig();
        tc.setTransferMode("ONE_TO_MANY");
        tc.setRoutingStrategy("BROADCAST");
        tc.setPostTransferAction(postAction);
        config.setTransferConfig(tc);
        return config;
    }

    private TargetAgentInfo createTarget(String id, String dir) {
        TargetAgentInfo t = new TargetAgentInfo();
        t.setAgentId(id);
        t.setAgentName("root@10.0.0.2:7777");
        t.setTargetDir(dir);
        return t;
    }

    @Test
    @DisplayName("initFileBatch 应该创建JSON文件且status为PENDING")
    void shouldCreateJsonFileOnInit() throws Exception {
        ScannedFile file = createScannedFile("app.log", 100);
        AgentTaskConfig config = createConfig("DELETE");
        List<TargetAgentInfo> targets = List.of(
            createTarget("t1", "/remote"),
            createTarget("t2", "/remote"),
            createTarget("t3", "/remote")
        );

        tracker.initFileBatch(100L, file, config, targets);

        Path jsonFile = tempDir.resolve("fb-100.json");
        assertTrue(Files.exists(jsonFile), "JSON文件应该存在");

        String content = Files.readString(jsonFile);
        assertTrue(content.contains("\"fileBatchId\":100"), "应包含fileBatchId");
        assertTrue(content.contains("\"status\":\"PENDING\""), "初始status应为PENDING");
        assertTrue(content.contains("\"totalTargets\":3"), "应有3个target");
    }

    @Test
    @DisplayName("markCompleted 应该更新对应target为COMPLETED并返回false（未全部完成）")
    void shouldMarkTargetCompletedAndReturnFalse() throws Exception {
        setupInit(100L, 3);

        boolean allDone = tracker.markCompleted(100L, "t1");

        assertFalse(allDone, "只完成1个，不应返回true");

        FileBatchState state = tracker.loadFileBatch(100L);
        assertEquals("COMPLETED", state.getTargets().getFirst().getStatus());
        assertEquals(1, state.getSummary().getCompletedCount());
        assertEquals("PENDING", state.getStatus());
    }

    @Test
    @DisplayName("所有target markCompleted 后应返回true并status变为COMPLETED")
    void shouldReturnTrueWhenAllCompleted() throws Exception {
        setupInit(200L, 2);

        assertFalse(tracker.markCompleted(200L, "t1"));
        boolean allDone = tracker.markCompleted(200L, "t2");

        assertTrue(allDone, "全部完成应返回true");

        FileBatchState state = tracker.loadFileBatch(200L);
        assertEquals("COMPLETED", state.getStatus());
        assertEquals(2, state.getSummary().getCompletedCount());
    }

    @Test
    @DisplayName("markFailed(isFinalFailure=true) 应将整体status设为FAILED")
    void shouldMarkFinalFailureAsFailedStatus() throws Exception {
        setupInit(300L, 2);

        tracker.markCompleted(300L, "t1");
        tracker.markFailed(300L, "t2", true);

        FileBatchState state = tracker.loadFileBatch(300L);
        assertEquals("FAILED", state.getStatus());
        assertEquals("FINAL_FAILURE", state.getTargets().get(1).getStatus());
    }

    @Test
    @DisplayName("markFailed(isFinalFailure=false) 应保持PENDING状态（可重试）")
    void shouldKeepPendingOnNonFinalFailure() throws Exception {
        setupInit(400L, 2);

        tracker.markCompleted(400L, "t1");
        tracker.markFailed(400L, "t2", false);

        FileBatchState state = tracker.loadFileBatch(400L);
        assertEquals("PENDING", state.getStatus(), "非终态失败应保持PENDING");
        assertEquals("FAILED", state.getTargets().get(1).getStatus());
    }

    @Test
    @DisplayName("重试成功后从FAILED回滚到COMPLETED")
    void shouldRollbackFromFailedToCompletedOnRetry() throws Exception {
        setupInit(500L, 2);

        tracker.markCompleted(500L, "t1");
        tracker.markFailed(500L, "t2", false);  // 非终态失败
        tracker.markCompleted(500L, "t2");       // 重试成功

        boolean allDone = tracker.markCompleted(500L, "t2"); // 重复调用安全

        FileBatchState state = tracker.loadFileBatch(500L);
        assertEquals("COMPLETED", state.getTargets().get(1).getStatus());
        assertEquals(2, state.getSummary().getCompletedCount());
    }

    @Test
    @DisplayName("deleteFileBatch 应该删除JSON文件")
    void shouldDeleteJsonFile() throws Exception {
        setupInit(600L, 2);

        tracker.deleteFileBatch(600L);

        Path jsonFile = tempDir.resolve("fb-600.json");
        assertFalse(Files.exists(jsonFile), "JSON文件应被删除");
    }

    @Test
    @DisplayName("recoverPendingBatches 应返回残留的未完成批次")
    void shouldRecoverPendingBatches() throws Exception {
        setupInit(700L, 3);
        tracker.markCompleted(700L, "t1");

        List<FileBatchState> pending = tracker.recoverPendingBatches();

        assertEquals(1, pending.size());
        assertEquals(700L, pending.getFirst().getFileBatchId());
        assertEquals("PENDING", pending.getFirst().getStatus());
    }

    @Test
    @DisplayName("并发markCompleted应该线程安全")
    void shouldBeThreadSafeForConcurrentMarkCompleted() throws Exception {
        setupInit(800L, 3);

        Thread[] threads = new Thread[3];
        String[] ids = {"t1", "t2", "t3"};
        for (int i = 0; i < 3; i++) {
            final String id = ids[i];
            threads[i] = new Thread(() -> {
                try { tracker.markCompleted(800L, id); } catch (Exception e) { throw new RuntimeException(e); }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join(5000);

        FileBatchState state = tracker.loadFileBatch(800L);
        assertEquals(3, state.getSummary().getCompletedCount());
    }

    // ===== helper methods =====

    private void setupInit(Long fileBatchId, int targetCount) throws Exception {
        ScannedFile file = createScannedFile("test.log", 50);
        AgentTaskConfig config = createConfig("DELETE");
        java.util.List<TargetAgentInfo> targets = new java.util.ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            targets.add(createTarget("t" + (i + 1), "/remote" + i));
        }
        tracker.initFileBatch(fileBatchId, file, config, targets);
    }
}
```

- [ ] **Step 2: 运行测试确认编译失败**

Run: `mvn test -pl agent -Dtest=FileBatchCompletionTrackerTest -DfailIfNoTests=false`
Expected: COMPILATION_ERROR

- [ ] **Step 3: 实现 FileBatchCompletionTracker.java**

核心实现要点：
- 使用 `ConcurrentHashMap<String, Object>` 作为 per-fileBatchId 锁
- 使用 Gson 序列化/反序列化（与 TransferMetaStore 相同风格）
- 使用 AtomicFileWriter.writeAtomically() 原子写入
- initFileBatch: 构建 FileBatchState → 写入 fb-{id}.json
- markCompleted: 读JSON → 找到匹配target → 设为COMPLETED → 重算summary/status → 写回 → 判断是否全部完成
- markFailed: 读JSON → 找到匹配target → 根据isFinalFailure设为FAILED/FINAL_FAILURE → 如果是终态失败则整体status=FAILED → 写回
- recoverPendingBatches: 扫描目录所有 .json → 反序列化 → 过滤出 status != COMPLETED
- deleteFileBatch: 直接删除文件

```java
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class FileBatchCompletionTracker {

    private static final Logger log = LoggerFactory.getLogger(FileBatchCompletionTracker.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final String pendingDir;
    private final Path pendingDirPath;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public FileBatchCompletionTracker(String pendingDir) {
        this.pendingDir = pendingDir;
        this.pendingDirPath = Path.of(pendingDir);
        ensureDirectoryExists();
    }

    public void initFileBatch(Long fileBatchId, ScannedFile scannedFile,
            AgentTaskConfig config, List<TargetAgentInfo> routedTargets) throws Exception {
        getLock(fileBatchId).lock();
        try {
            FileBatchState state = buildInitialState(fileBatchId, scannedFile, config, routedTargets);
            writeState(state);
            log.info("初始化文件批次追踪: fileBatchId={}, fileName={}, totalTargets={}",
                    fileBatchId, scannedFile.getFileName(), routedTargets.size());
        } finally {
            getLock(fileBatchId).unlock();
        }
    }

    public synchronized boolean markCompleted(Long fileBatchId, String targetAgentId) throws Exception {
        getLock(fileBatchId).lock();
        try {
            FileBatchState state = loadFileBatch(fileBatchId);
            if (state == null) return false;

            Optional<FileBatchState.TargetProgress> found = state.getTargets().stream()
                    .filter(t -> targetAgentId.equals(t.getAgentId()))
                    .findFirst();

            if (found.isEmpty()) {
                log.warn("markCompleted: 未找到targetAgentId={}, fileBatchId={}", targetAgentId, fileBatchId);
                return false;
            }

            FileBatchState.TargetProgress target = found.get();
            if (!"COMPLETED".equals(target.getStatus())) {
                target.setStatus("COMPLETED");
                target.setCompletedAt(FORMATTER.format(LocalDateTime.now()));
            }

            recalculateAndSave(state);
            boolean allDone = "COMPLETED".equals(state.getStatus())
                    && state.getSummary().getCompletedCount() == state.getSummary().getTotalTargets();

            if (allDone) {
                log.info("文件批次全部完成: fileBatchId={}, 准备执行后置操作", fileBatchId);
            } else {
                log.debug("文件批次部分完成: fileBatchId={}/{}, done={}",
                        fileBatchId, state.getSummary().getTotalTargets(),
                        state.getSummary().getCompletedCount());
            }
            return allDone;
        } finally {
            getLock(fileBatchId).unlock();
        }
    }

    public void markFailed(Long fileBatchId, String targetAgentId, boolean isFinalFailure) throws Exception {
        getLock(fileBatchId).lock();
        try {
            FileBatchState state = loadFileBatch(fileBatchId);
            if (state == null) return;

            Optional<FileBatchState.TargetProgress> found = state.getTargets().stream()
                    .filter(t -> targetAgentId.equals(t.getAgentId()))
                    .findFirst();

            if (found.isEmpty()) return;

            FileBatchState.TargetProgress target = found.get();
            target.setStatus(isFinalFailure ? "FINAL_FAILURE" : "FAILED");

            if (isFinalFailure) {
                state.setStatus("FAILED");
                log.warn("文件批次标记为最终失败: fileBatchId={}, failedTarget={}",
                        fileBatchId, targetAgentId);
            } else {
                recalculateAndSave(state);
                log.debug("文件批次节点临时失败(可重试): fileBatchId={}, failedTarget={}",
                        fileBatchId, targetAgentId);
            }
        } finally {
            getLock(fileBatchId).unlock();
        }
    }

    public void deleteFileBatch(Long fileBatchId) throws Exception {
        Path jsonPath = resolveJsonPath(fileBatchId);
        AtomicFileWriter.deleteIfExists(jsonPath);
        log.info("文件批次追踪文件已删除: fileBatchId={}", fileBatchId);
    }

    public FileBatchState loadFileBatch(Long fileBatchId) throws Exception {
        Path jsonPath = resolveJsonPath(fileBatchId);
        if (!Files.exists(jsonPath)) return null;
        String json = Files.readString(jsonPath);
        return GSON.fromJson(json, FileBatchState.class);
    }

    public List<FileBatchState> recoverPendingBatches() throws Exception {
        List<FileBatchState> result = new ArrayList<>();
        if (!Files.exists(pendingDirPath)) return result;

        try (Stream<Path> paths = Files.list(pendingDirPath)) {
            paths.filter(p -> p.toString().endsWith(".json") && Files.isRegularFile(p))
                 .forEach(p -> {
                     try {
                         FileBatchState state = GSON.fromJson(Files.readString(p), FileBatchState.class);
                         if (state != null && !"COMPLETED".equals(state.getStatus())) {
                             result.add(state);
                         }
                     } catch (Exception e) {
                         log.warn("恢复文件批次时解析失败: path={}, error={}", p, e.getMessage());
                     }
                 });
        }
        return result;
    }

    // ===== 内部方法 =====

    private FileBatchState buildInitialState(Long fileBatchId, ScannedFile scannedFile,
            AgentTaskConfig config, List<TargetAgentInfo> routedTargets) {
        FileBatchState state = new FileBatchState();
        state.setFileBatchId(fileBatchId);
        state.setTaskId(config.getTaskId());
        state.setTaskName(config.getTaskName());
        state.setStatus("PENDING");
        state.setCreateTime(FORMATTER.format(LocalDateTime.now()));
        state.setUpdateTime(state.getCreateTime());

        FileBatchState.SourceInfo source = new FileBatchState.SourceInfo();
        source.setAgentId(config.getSourceAgentId());
        source.setAgentName(config.getSourceAgentName());
        source.setSourceDir(config.getSourceDir());
        source.setFilePath(scannedFile.getAbsolutePath());
        source.setFileName(scannedFile.getFileName());
        source.setFileSizeBytes(scannedFile.getFileSize());
        source.setLastModified(FORMATTER.format(
                java.time.Instant.ofEpochMilli(scannedFile.getLastModified())
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()));
        state.setSource(source);

        TransferConfig tc = config.getTransferConfig();
        if (tc != null) {
            FileBatchState.TransferConfigInfo tci = new FileBatchState.TransferConfigInfo();
            tci.setTransferMode(tc.getTransferMode());
            tci.setRoutingStrategy(tc.getRoutingStrategy());
            tci.setPostTransferAction(tc.getPostTransferAction());
            tci.setBackupDir(tc.getBackupDir());
            tci.setBackupMode(tc.getBackupMode());
            state.setTransferConfig(tci);
        }

        List<FileBatchState.TargetProgress> targetList = new ArrayList<>();
        for (TargetAgentInfo tai : routedTargets) {
            FileBatchState.TargetProgress tp = new FileBatchState.TargetProgress();
            tp.setAgentId(tai.getAgentId());
            tp.setAgentName(tai.getAgentName());
            tp.setTargetDir(tai.getTargetDir());
            tp.setTargetPath(tai.getTargetDir() != null
                    ? tai.getTargetDir() + "/" + scannedFile.getFileName()
                    : null);
            tp.setStatus("PENDING");
            targetList.add(tp);
        }
        state.setTargets(targetList);

        FileBatchState.Summary summary = new FileBatchState.Summary();
        summary.setTotalTargets(routedTargets.size());
        summary.setCompletedCount(0);
        summary.setFailedCount(0);
        summary.setPendingCount(routedTargets.size());
        state.setSummary(summary);

        return state;
    }

    private void recalculateAndSave(FileBatchState state) throws Exception {
        int total = state.getSummary().getTotalTargets();
        long completed = state.getTargets().stream()
                .filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long failed = state.getTargets().stream()
                .filter(t -> "FINAL_FAILURE".equals(t.getStatus())).count();
        state.getSummary().setCompletedCount((int) completed);
        state.getSummary().setFailedCount((int) failed);
        state.getSummary().setPendingCount(total - (int) completed - (int) failed);

        if (failed > 0) {
            state.setStatus("FAILED");
        } else if (completed >= total) {
            state.setStatus("COMPLETED");
        } else {
            state.setStatus("PENDING");
        }
        state.setUpdateTime(FORMATTER.format(LocalDateTime.now()));

        writeState(state);
    }

    private void writeState(FileBatchState state) throws Exception {
        Path jsonPath = resolveJsonPath(state.getFileBatchId());
        AtomicFileWriter.writeAtomically(jsonPath, GSON.toJson(state));
    }

    private Path resolveJsonPath(Long fileBatchId) {
        return pendingDirPath.resolve("fb-" + fileBatchId + ".json");
    }

    private java.util.concurrent.locks.Lock getLock(Long fileBatchId) {
        return locks.computeIfAbsent("fb-" + fileBatchId, k -> new java.util.concurrent.ReentrantLock());
    }

    private void ensureDirectoryExists() {
        if (!Files.exists(pendingDirPath)) {
            try {
                Files.createDirectories(pendingDirPath);
                log.info("文件批次追踪目录已创建: {}", pendingDirPath);
            } catch (Exception e) {
                throw new RuntimeException("无法创建文件批次追踪目录: " + pendingDirPath, e);
            }
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl agent -Dtest=FileBatchCompletionTrackerTest -DfailIfNoTests=false`
Expected: BUILD SUCCESS, all tests PASS

- [ ] **Step 5: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/tracker/FileBatchCompletionTracker.java agent/src/test/java/com/cq/agent/batch/tracker/FileBatchCompletionTrackerTest.java
git commit -m "feat(agent): add FileBatchCompletionTracker with JSON persistence for one-to-many transfer"
```

---

### Task 3: 修改 AgentConfig 添加配置项

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/config/AgentConfig.java` (约第246行附近)

- [ ] **Step 1: 添加 filebatchPendingDir 字段和默认值**

在 `AgentConfig.java` 中，找到 `uploadFailRetryQueueDir` 和 `downloadFailRetryQueueDir` 配置之后（约第246行），添加：

```java
// File batch completion tracking directory (one-to-many transfer)
this.filebatchPendingDir = getStringProperty("filebatch.pending.dir",
        "/tmp/my-panel/admin/data/transfers/filebatchPending");
```

同时在类中添加字段声明和 getter 方法（与其他 queue dir 字段保持一致的风格）。

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl agent -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add agent/src/main/java/com/cq/agent/config/AgentConfig.java
git commit -m "feat(agent): add filebatch.pending.dir config to AgentConfig"
```

---

### Task 4: 修改 BatchUploadListener 集成 Tracker

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/batch/scheduler/BatchUploadListener.java`
- Test: `agent/src/test/java/com/cq/agent/batch/scheduler/BatchUploadListenerTrackerIntegrationTest.java`

- [ ] **Step 1: 编写集成测试验证 onComplete/onError 通过 tracker 交互**

```java
package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.scanner.ScannedFile;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.TargetAgentInfo;
import com.cq.panel.common.dto.batch.TransferConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchUploadListener + Tracker 集成测试")
class BatchUploadListenerTrackerIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("一对一场景：onComplete 应直接执行后置操作（不经过tracker）")
    void shouldDirectlyExecutePostActionForOneToOne() {
        // 当只有1个target时，listener的onComplete行为不变
        // 此测试验证一对一不受影响
        // 具体实现见后续步骤
    }

    @Test
    @DisplayName("一对多场景：onComplete 不应在首个节点完成时执行后置操作")
    void shouldNotExecutePostActionOnFirstCompleteForOneToMany() throws Exception {
        FileBatchCompletionTracker tracker = new FileBatchCompletionTracker(
                tempDir.resolve("pending").toString());

        Long fileBatchId = 999L;
        ScannedFile file = createFile("test.log");
        AgentTaskConfig config = createConfig("DELETE");
        List<TargetAgentInfo> targets = List.of(createTarget("a"), createTarget("b"));

        tracker.initFileBatch(fileBatchId, file, config, targets);

        boolean firstDone = tracker.markCompleted(fileBatchId, "a");
        assertFalse(firstDone, "第一个节点完成不应触发后置操作");
    }

    @Test
    @DisplayName("一对多场景：所有节点完成后才允许执行后置操作")
    void shouldAllowPostActionOnlyAfterAllComplete() throws Exception {
        FileBatchCompletionTracker tracker = new FileBatchCompletionTracker(
                tempDir.resolve("pending").toString());

        Long fileBatchId = 888L;
        ScannedFile file = createFile("test.log");
        AgentTaskConfig config = createConfig("DELETE");
        List<TargetAgentInfo> targets = List.of(createTarget("a"), createTarget("b"));

        tracker.initFileBatch(fileBatchId, file, config, targets);

        tracker.markCompleted(fileBatchId, "a");
        boolean allDone = tracker.markCompleted(fileBatchId, "b");
        assertTrue(allDone, "全部完成后应返回true");
    }

    @Test
    @DisplayName("一对多场景：任一节点最终失败则不执行后置操作")
    void shouldNotExecutePostActionOnAnyFinalFailure() throws Exception {
        FileBatchCompletionTracker tracker = new FileBatchCompletionTracker(
                tempDir.resolve("pending").toString());

        Long fileBatchId = 777L;
        ScannedFile file = createFile("test.log");
        AgentTaskConfig config = createConfig("DELETE");
        List<TargetAgentInfo> targets = List.of(createTarget("a"), createTarget("b"));

        tracker.initFileBatch(fileBatchId, file, config, targets);
        tracker.markCompleted(fileBatchId, "a");
        tracker.markFailed(fileBatchId, "b", true);

        var state = tracker.loadFileBatch(fileBatchId);
        assertEquals("FAILED", state.getStatus());
    }

    private ScannedFile createFile(String name) {
        ScannedFile f = new ScannedFile();
        f.setFileName(name);
        f.setFileSize(100);
        f.setAbsolutePath("/data/" + name);
        f.setLastModified(System.currentTimeMillis());
        return f;
    }

    private AgentTaskConfig createConfig(String action) {
        AgentTaskConfig c = new AgentTaskConfig();
        c.setTaskId(1L);
        c.setSourceAgentId("src");
        c.setSourceAgentName("src-name");
        c.setSourceDir("/data");
        TransferConfig tc = new TransferConfig();
        tc.setTransferMode("ONE_TO_MANY");
        tc.setRoutingStrategy("BROADCAST");
        tc.setPostTransferAction(action);
        c.setTransferConfig(tc);
        return c;
    }

    private TargetAgentInfo createTarget(String id) {
        TargetAgentInfo t = new TargetAgentInfo();
        t.setAgentId(id);
        t.setAgentName(id + "-name");
        t.setTargetDir("/remote");
        return t;
    }
}
```

- [ ] **Step 2: 运行测试确认编译失败（listener 尚未注入 tracker）**

Run: `mvn test -pl agent -Dtest=BatchUploadListenerTrackerIntegrationTest -DfailIfNoTests=false`
Expected: 测试可编译（纯 tracker 测试），但 listener 集成部分待后续步骤

- [ ] **Step 3: 修改 BatchUploadListener.java**

具体改动点：

**(a) 新增字段** — 在现有字段区域添加：
```java
private final FileBatchCompletionTracker fileBatchTracker;
```

**(b) 修改构造函数** — 添加 tracker 参数（保持向后兼容的重载）：
```java
public BatchUploadListener(Long taskId, ScannedFile scannedFile,
        AgentTaskConfig config, TargetAgentInfo targetAgent, ProgressReporter progressReporter,
        String successQueueDir, String sendingQueueDir,
        Long scanBatchId, Long fileBatchId,
        FileBatchCompletionTracker fileBatchTracker) {  // 新增参数
    // ... 现有赋值逻辑不变 ...
    this.fileBatchTracker = fileBatchTracker;  // 新增
    // ... 其余不变 ...
}
```

保留原构造函数作为向后兼容（内部调用新构造函数，传 null）。

**(c) 修改 onComplete()** — 将第320行的直接调用改为条件判断：
```java
// 原代码:
executePostTransferAction();

// 改为:
if (fileBatchTracker != null && fileBatchId != null) {
    boolean allDone = fileBatchTracker.markCompleted(fileBatchId,
            targetAgent != null ? targetAgent.getAgentId() : null);
    if (allDone) {
        executePostTransferAction();
        fileBatchTracker.deleteFileBatch(fileBatchId);
    }
} else {
    executePostTransferAction();  // 一对一或无tracker时走原有逻辑
}
```

**(d) 修改 onError()** — 在第340行后添加 tracker 通知：
```java
// 在 progressReporter.reportFailed(event) 之后添加:
if (fileBatchTracker != null && fileBatchId != null) {
    fileBatchTracker.markFailed(fileBatchId,
            targetAgent != null ? targetAgent.getAgentId() : null,
            false);  // onError 时先标记为非终态失败
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn compile -pl agent -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: 运行全部测试确保无回归**

Run: `mvn test -pl agent -Dtest="BatchUploadListener*,FileBatchCompletion*" -DfailIfNoTests=false`
Expected: 所有相关测试 PASS

- [ ] **Step 6: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/scheduler/BatchUploadListener.java agent/src/test/java/com/cq/agent/batch/scheduler/BatchUploadListenerTrackerIntegrationTest.java
git commit -m "feat(agent): integrate FileBatchCompletionTracker into BatchUploadListener for one-to-many transfer"
```

---

### Task 5: 修改 BatchTaskSchedulerManager 初始化追踪文件

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java`

- [ ] **Step 1: 添加 tracker 字段和 setter**

在字段声明区域（约第38行附近）添加：
```java
private FileBatchCompletionTracker fileBatchTracker;
```

添加 setter 方法：
```java
public void setFileBatchTracker(FileBatchCompletionTracker fileBatchTracker) {
    this.fileBatchTracker = fileBatchTracker;
    log.info("📊 已设置FileBatchCompletionTracker");
}
```

- [ ] **Step 2: 修改 processScannedFiles() 中内层循环之前初始化追踪**

在第326行 `List<TargetAgentInfo> routedTargets = router.route(...)` 之后、第329行 `for (TargetAgentInfo targetAgent : routedTargets)` 循环 **之前**，插入：

```java
if (routedTargets.size() > 1 && fileBatchTracker != null) {
    try {
        fileBatchTracker.initFileBatch(fileBatchId, scannedFile, config, routedTargets);
    } catch (Exception e) {
        log.warn("⚠️ 初始化文件批次追踪失败: fileBatchId={}, error={}", fileBatchId, e.getMessage());
    }
}
```

- [ ] **Step 3: 修改 BatchUploadListener 构造调用处传入 tracker**

在第347-351行 `new BatchUploadListener(...)` 调用处，末尾追加 `fileBatchTracker` 参数：
```java
UploadListener listener = new BatchUploadListener(
        taskId, scannedFile, config, targetAgent, progressReporter,
        agentConfig != null ? agentConfig.getUploadSuccessQueueDir() : null,
        agentConfig != null ? agentConfig.getUploadSendingQueueDir() : null,
        scanBatchId, fileBatchId,
        fileBatchTracker);  // 新增参数
```

- [ ] **Step 4: 编译验证**

Run: `mvn compile -pl agent -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: 运行现有 BatchTaskSchedulerManager 测试确保无回归**

Run: `mvn test -pl agent -Dtest="BatchTaskSchedulerManager*|BatchUploadListener*" -DfailIfNoTests=false`
Expected: 所有测试 PASS

- [ ] **Step 6: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java
git commit -m "feat(agent): wire FileBatchCompletionTracker into BatchTaskSchedulerManager for one-to-many init"
```

---

### Task 6: 修改 AgentApplication 组装 Tracker

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/AgentApplication.java`

- [ ] **Step 1: 在 connectComponentsToScheduler 方法中创建并注入 tracker**

在 `connectComponentsToScheduler` 方法中（约第149行），在设置 `progressReporter` 之后添加：

```java
FileBatchCompletionTracker fileBatchTracker = new FileBatchCompletionTracker(
        config.getFilebatchPendingDir());
taskSchedulerManager.setFileBatchTracker(fileBatchTracker);
logger.info("✅ FileBatchCompletionTracker initialized: dir={}", config.getFilebatchPendingDir());
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl agent -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行 agent 模块全部测试**

Run: `mvn test -pl agent -DfailIfNoTests=false`
Expected: BUILD SUCCESS, 全部测试 PASS

- [ ] **Step 4: 提交**

```bash
git add agent/src/main/java/com/cq/agent/AgentApplication.java
git commit -m "feat(agent): wire FileBatchCompletionTracker in AgentApplication startup"
```

---

### Task 7: 最终端到端验证与清理

**Files:**
- 无新建文件
- 涉及上述所有已修改文件的回归验证

- [ ] **Step 1: 运行 agent 模块完整测试套件**

Run: `mvn test -pl agent -DfailIfNoTests=false`
Expected: BUILD SUCCESS, 100% pass rate

- [ ] **Step 2: 编译完整项目确保无级联影响**

Run: `mvn compile -pl agent -am -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: 检查是否有残留的孤立 JSON 文件需要处理（启动恢复场景验证思路）**

验证：手动检查 `recoverPendingBatches()` 能正确扫描到 PENDING 和 FAILED 状态的文件。

- [ ] **Step 4: 最终提交（如有遗漏的小修复）**

```bash
git add -A
git commit -m "feat(agent): complete FileBatchCompletionTracker integration with e2e verification"
```

---

## 实现顺序依赖关系

```
Task 1 (FileBatchState)
   ↓
Task 2 (FileBatchCompletionTracker) ← 依赖 Task 1
   ↓
Task 3 (AgentConfig) ← 可并行
   ↓
Task 4 (BatchUploadListener) ← 依赖 Task 1, 2
   ↓
Task 5 (BatchTaskSchedulerManager) ← 依赖 Task 4
   ↓
Task 6 (AgentApplication) ← 依赖 Task 3, 5
   ↓
Task 7 (E2E 验证) ← 依赖全部
```
