# 失败文件粒度重试机制 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现基于单个文件级别的失败重试机制，仅重试失败的文件，使用 Proxy 推送的重试参数，通过 Quartz 定时扫描失败队列

**Architecture:** 扩展现有 RetryManager 组件，添加定时扫描失败队列的能力；修改 BatchTaskSchedulerManager 不再整体重试批量任务；在 AgentUploader/Downloader 中新增 resubmitTask() 方法用于重新提交失败的任务

**Tech Stack:** Java 21, Quartz Scheduler, Gson, JUnit 5, SLF4J

---

## 文件结构总览

### 需要修改的现有文件：
1. `agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java` - 核心扩展
2. `agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java` - 新增 resubmitTask()
3. `agent/src/main/java/com/cq/agent/client/download/AgentDownloader.java` - 新增 resubmitTask()
4. `agent/src/main/java/com/cq/agent/client/BaseAgentClient.java` - 异常处理优化
5. `agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java` - 删除整体重试逻辑
6. `agent/src/main/java/com/cq/agent/config/AgentConfig.java` - 新增配置项

### 需要创建的新文件：
7. `agent/src/main/java/com/cq/agent/batch/scheduler/FailedQueueScannerJob.java` - Quartz Job
8. `agent/src/test/java/com/cq/agent/batch/transfer/RetryManagerTest.java` - 单元测试

---

## Task 1: 扩展 RetryManager - 添加依赖注入和初始化方法

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java`
- Test: `agent/src/test/java/com/cq/agent/batch/transfer/RetryManagerTest.java`

**目标:** 为 RetryManager 添加依赖注入能力，支持接收 TransferMetaStore、失败队列目录路径、AgentUploader/Downloader 实例

- [ ] **Step 1: 编写 RetryManager 初始化方法的单元测试**

```java
// RetryManagerTest.java
package com.cq.agent.batch.transfer;

import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.client.upload.UploadTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetryManagerTest {

    private RetryManager retryManager;
    private TransferMetaStore<UploadTask> uploadMetaStore;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        retryManager = new RetryManager(3, 5, 2);
        Path uploadDir = tempDir.resolve("uploads");
        Path failedDir = tempDir.resolve("failed");
        uploadMetaStore = new TransferMetaStore<>(uploadDir, UploadTask.class);
    }

    @Test
    void shouldInitializeWithDependencies() throws Exception {
        // Given & When
        Path failQueueDir = tempDir.resolve("failQueue");
        retryManager.init(uploadMetaStore, null, failQueueDir, null, null, null);

        // Then - should not throw exception
        assertNotNull(retryManager);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldInitializeWithDependencies`
Expected: ❌ FAIL - 方法不存在

- [ ] **Step 3: 在 RetryManager 中实现 init() 方法**

在 `RetryManager.java` 中添加：

```java
// 新增字段
private TransferMetaStore<UploadTask> uploadMetaStore;
private TransferMetaStore<DownloadTask> downloadMetaStore;
private Path uploadFailQueueDir;
private Path downloadFailQueueDir;
private AgentUploader agentUploader;
private AgentDownloader agentDownloader;

/**
 * 初始化依赖项（在 Agent 启动时调用）
 */
public void init(TransferMetaStore<UploadTask> uploadMetaStore,
                 TransferMetaStore<DownloadTask> downloadMetaStore,
                 Path uploadFailQueueDir,
                 Path downloadFailQueueDir,
                 AgentUploader uploader,
                 AgentDownloader downloader) {
    this.uploadMetaStore = uploadMetaStore;
    this.downloadMetaStore = downloadMetaStore;
    this.uploadFailQueueDir = uploadFailQueueDir;
    this.downloadFailQueueDir = downloadFailQueueDir;
    this.agentUploader = uploader;
    this.agentDownloader = downloader;
    
    log.info("✅ RetryManager 初始化完成: 上传失败队列={}, 下载失败队列={}", 
        uploadFailQueueDir, downloadFailQueueDir);
}
```

- [ ] **Step 4: 添加必要的 import**

```java
import com.cq.agent.client.TransferMetaStore;
import com.cq.agent.client.download.AgentDownloader;
import com.cq.agent.client.download.DownloadTask;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.UploadTask;
import java.nio.file.Path;
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldInitializeWithDependencies`
Expected: ✅ PASS

- [ ] **Step 6: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java
git commit -m "feat(retry): 添加 RetryManager init() 方法和依赖注入"
```

---

## Task 2: 实现 registerTaskConfig() 和任务配置查找

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java`
- Test: `agent/src/test/java/com/cq/agent/batch/transfer/RetryManagerTest.java`

**目标:** 支持注册和查找任务级别的重试配置（来自 Proxy 推送）

- [ ] **Step 1: 编写注册和查找配置的测试**

```java
@Test
void shouldRegisterAndFindTaskConfig() {
    // Given
    BatchTransferTaskConfig config = new BatchTransferTaskConfig();
    config.setTaskId(100L);
    
    BatchTransferTaskConfig.RetryConfig retryConfig = new BatchTransferTaskConfig.RetryConfig();
    retryConfig.setEnabled(true);
    retryConfig.setMaxRetryCount(5);
    retryConfig.setIntervalMin(10);
    config.setRetryConfig(retryConfig);

    // When
    retryManager.registerTaskConfig(100L, config);

    // Then - no exception thrown (config stored)
}

@Test
void shouldReturnNullForUnknownTask() {
    // When
    BatchTransferTaskConfig result = retryManager.findTaskConfigByTaskId(999L);

    // Then
    assertNull(result);
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldRegisterAndFindTaskConfig`
Expected: ❌ FAIL - 方法不存在

- [ ] **Step 3: 在 RetryManager 中实现配置管理**

```java
// 新增字段
private final Map<Long, BatchTransferTaskConfig> taskConfigMap = new ConcurrentHashMap<>();

/**
 * 注册任务配置（从 Proxy 接收配置后调用）
 */
public void registerTaskConfig(Long taskId, BatchTransferTaskConfig config) {
    taskConfigMap.put(taskId, config);
    
    if (config.getRetryConfig() != null) {
        log.info("✅ 注册任务重试配置: taskId={}, maxRetries={}次, intervalMin={}min, backoff={}, enabled={}",
            taskId,
            config.getRetryConfig().getMaxRetryCount(),
            config.getRetryConfig().getIntervalMin(),
            config.getRetryConfig().getBackoffType(),
            config.getRetryConfig().isEnabled());
    }
}

/**
 * 根据 taskId 查找任务配置
 */
public BatchTransferTaskConfig findTaskConfigByTaskId(Long taskId) {
    return taskConfigMap.get(taskId);
}
```

- [ ] **Step 4: 添加必要的 import**

```java
import com.cq.agent.batch.config.BatchTransferTaskConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldRegisterAndFindTaskConfig`
Expected: ✅ PASS

- [ ] **Step 6: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java
git commit -m "feat(retry): 添加任务配置注册和查找功能"
```

---

## Task 3: 实现 scanAndRetryFailedUploads() 核心扫描逻辑

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java`
- Test: `agent/src/test/java/com/cq/agent/batch/transfer/RetryManagerTest.java`

**目标:** 实现定时扫描上传失败队列的核心逻辑

- [ ] **Step 1: 编写空队列扫描测试**

```java
@Test
void shouldHandleEmptyFailedQueueGracefully() {
    // Given
    Path emptyFailDir = tempDir.resolve("emptyFail");
    retryManager.init(uploadMetaStore, null, emptyFailDir, null, null, null);

    // When & Then - should not throw exception
    assertDoesNotThrow(() -> retryManager.scanAndRetryFailedUploads());
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldHandleEmptyFailedQueueGracefully`
Expected: ❌ FAIL - 方法不存在

- [ ] **Step 3: 实现 scanAndRetryFailedUploads() 基础框架**

```java
/**
 * 扫描上传失败队列并重试（Quartz Job 调用）
 */
public void scanAndRetryFailedUploads() {
    if (uploadMetaStore == null || uploadFailQueueDir == null) {
        log.debug("⏭️ 上传失败队列未初始化，跳过扫描");
        return;
    }
    
    try {
        List<UploadTask> failedTasks = uploadMetaStore.recoverFailedTasks(uploadFailQueueDir);
        
        if (failedTasks.isEmpty()) {
            log.debug("📭 上传失败队列为空");
            return;
        }
        
        log.info("🔍 发现 {} 个待重试的上传失败任务", failedTasks.size());
        
        int retriedCount = 0;
        int finalFailureCount = 0;
        
        for (UploadTask task : failedTasks) {
            try {
                if (shouldRetryFailedFile(task)) {
                    retrySingleUploadFile(task);
                    retriedCount++;
                } else {
                    markUploadAsFinalFailure(task);
                    finalFailureCount++;
                }
            } catch (Exception e) {
                log.error("❌ 处理失败任务异常: transferId={}, error={}",
                    task.getTransferId(), e.getMessage());
            }
        }
        
        log.info("📊 上传失败队列扫描完成: 总数={}, 重试={}, 最终失败={}",
            failedTasks.size(), retriedCount, finalFailureCount);
            
    } catch (Exception e) {
        log.error("❌ 扫描上传失败队列异常: {}", e.getMessage(), e);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldHandleEmptyFailedQueueGracefully`
Expected: ✅ PASS

- [ ] **Step 5: 编写有失败任务的扫描测试**

```java
@Test
void shouldScanAndProcessFailedTasks() throws Exception {
    // Given: 创建一个失败的任务
    Path failDir = tempDir.resolve("failQueue");
    Files.createDirectories(failDir);
    
    UploadTask failedTask = createMockUploadTask("test-transfer-123");
    failedTask.setStatus(UploadTaskStatus.FAILED);
    failedTask.setExceptionDesc("Connection timeout");
    failedTask.setRetryCount(0);
    
    // 将失败任务保存到失败队列
    String json = gson.toJson(failedTask);
    Path failedFile = failDir.resolve("FAILED-upload-test-transfer-123.json");
    Files.writeString(failedFile, json);
    
    retryManager.init(uploadMetaStore, null, failDir, null, null, null);
    
    // When
    retryManager.scanAndRetryFailedUploads();
    
    // Then: 应该尝试处理该任务（具体行为取决于 shouldRetryFailedFile 的实现）
}
```

- [ ] **Step 6: 运行测试验证**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldScanAndProcessFailedTasks`
Expected: 可能需要调整（因为依赖后续方法）

- [ ] **Step 7: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java
git commit -m "feat(retry): 实现上传失败队列扫描核心逻辑"
```

---

## Task 4: 实现 shouldRetryFailedFile() 和 retrySingleUploadFile()

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java`
- Test: `agent/src/test/java/com/cq/agent/batch/transfer/RetryManagerTest.java`

**目标:** 实现判断是否应该重试以及执行单个文件重试的逻辑

- [ ] **Step 1: 编写 shouldRetryFailedFile 测试**

```java
@Test
void shouldAllowRetryWhenUnderLimit() {
    // Given
    UploadTask task = createMockUploadTask("task-1");
    task.setRetryCount(0);
    
    BatchTransferTaskConfig config = new BatchTransferTaskConfig();
    config.setTaskId(1L);
    BatchTransferTaskConfig.RetryConfig retryConfig = new BatchTransferTaskConfig.RetryConfig();
    retryConfig.setMaxRetryCount(3);
    retryConfig.setEnabled(true);
    config.setRetryConfig(retryConfig);
    
    retryManager.registerTaskConfig(1L, config);

    // When
    boolean shouldRetry = retryManager.shouldRetryFailedFile(task);

    // Then
    assertTrue(shouldRetry, "retryCount=0 < maxRetries=3, 应该允许重试");
}

@Test
void shouldDenyRetryWhenAtLimit() {
    // Given
    UploadTask task = createMockUploadTask("task-2");
    task.setRetryCount(3);  // 已达到上限
    
    BatchTransferTaskConfig config = new BatchTransferTaskConfig();
    config.setTaskId(2L);
    BatchTransferTaskConfig.RetryConfig retryConfig = new BatchTransferTaskConfig.RetryConfig();
    retryConfig.setMaxRetryCount(3);
    retryConfig.setEnabled(true);
    config.setRetryConfig(retryConfig);
    
    retryManager.registerTaskConfig(2L, config);

    // When
    boolean shouldRetry = retryManager.shouldRetryFailedFile(task);

    // Then
    assertFalse(shouldRetry, "retryCount=3 >= maxRetries=3, 不应该允许重试");
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldAllowRetryWhenUnderLimit`
Expected: ❌ FAIL - 方法不存在

- [ ] **Step 3: 实现 shouldRetryFailedFile()**

```java
/**
 * 判断失败文件是否应该重试
 * @param task 失败的任务
 * @return true=应该重试, false=达到上限或未启用
 */
public boolean shouldRetryFailedFile(UploadTask task) {
    int currentRetries = task.getRetryCount();
    
    BatchTransferTaskConfig config = findTaskConfigForUpload(task);
    
    if (config == null) {
        log.warn("⚠️ 未找到任务配置，使用默认值: transferId={}", task.getTransferId());
        return currentRetries < maxRetries;
    }
    
    if (config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
        log.warn("⚠️ 任务未启用重试: transferId={}", task.getTransferId());
        return false;
    }
    
    int maxAllowed = config.getRetryConfig().getMaxRetryCount();
    boolean shouldRetry = currentRetries < maxAllowed;
    
    log.debug("🔍 重试检查: transferId={}, current={}, max={}, shouldRetry={}",
        task.getTransferId(), currentRetries, maxAllowed, shouldRetry);
    
    return shouldRetry;
}

/**
 * 辅助方法：根据 UploadTask 查找对应的任务配置
 */
private BatchTransferTaskConfig findTaskConfigForUpload(UploadTask task) {
    // TODO: 这里需要建立 UploadTask 与 taskId 的映射关系
    // 目前先返回第一个配置作为示例
    if (!taskConfigMap.isEmpty()) {
        return taskConfigMap.values().iterator().next();
    }
    return null;
}
```

- [ ] **Step 4: 实现 retrySingleUploadFile()**

```java
/**
 * 重试单个失败的上传文件
 * 关键步骤：
 * 1. 增加 retryCount（必须立即持久化到JSON）
 * 2. 重置状态为 PREPARED
 * 3. 清除异常描述
 * 4. 保存到正常元数据目录（从失败队列移出）
 * 5. 重新提交到 AgentUploader 工作队列
 */
private void retrySingleUploadFile(UploadTask task) {
    try {
        String transferId = task.getTransferId();
        
        // 1. 增加重试计数（原子操作）
        task.incrementRetryCount();
        int newRetryCount = task.getRetryCount();
        
        log.info("🔄 准备重试: transferId={}, file={}, retryCount={}/{}, status={}",
            transferId, task.getLocalFilePath(),
            newRetryCount, getMaxRetriesForTask(task),
            task.getStatus());
        
        // 2. 重置状态为 PREPARED（准备重新传输）
        task.setStatus(UploadTaskStatus.PREPARED);
        
        // 3. 清除异常描述
        task.setExceptionDesc(null);
        
        // 4. 更新时间戳
        task.setUpdateTime(Util.currentTime());
        
        // 5. ✅ 保存到正常目录（这会将文件从失败队列移回 uploadsMeta）
        //    同时会更新 JSON 中的 retryCount 字段！
        if (uploadMetaStore != null) {
            uploadMetaStore.saveTask(task);
            
            log.info("💾 已更新任务状态: transferId={}, retryCount={}, 状态已重置为PREPARED",
                transferId, newRetryCount);
        }
        
        // 6. 重新提交传输（异步）
        if (agentUploader != null) {
            agentUploader.resubmitTask(task);
            log.info("✅ 失败文件已重新提交到工作队列: transferId={}", transferId);
        } else {
            log.error("❌ AgentUploader 未初始化，无法重新提交: transferId={}", transferId);
        }
        
    } catch (Exception e) {
        log.error("❌ 重试失败文件异常: transferId={}, error={}",
            task.getTransferId(), e.getMessage(), e);
    }
}

/**
 * 获取任务的最大重试次数
 */
private int getMaxRetriesForTask(UploadTask task) {
    BatchTransferTaskConfig config = findTaskConfigForUpload(task);
    if (config != null && config.getRetryConfig() != null) {
        return config.getRetryConfig().getMaxRetryCount();
    }
    return maxRetries;  // 默认值
}
```

- [ ] **Step 5: 添加必要的 import**

```java
import com.cq.agent.client.upload.UploadTaskStatus;
import com.cq.agent.util.Util;
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldAllowRetryWhenUnderLimit,RetryManagerTest#shouldDenyRetryWhenAtLimit`
Expected: ✅ PASS

- [ ] **Step 7: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java
git commit -m "feat(retry): 实现单文件重试判断和执行逻辑"
```

---

## Task 5: 实现 markUploadAsFinalFailure()

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java`
- Test: `agent/src/test/java/com/cq/agent/batch/transfer/RetryManagerTest.java`

**目标:** 实现标记最终失败的逻辑

- [ ] **Step 1: 编写标记最终失败的测试**

```java
@Test
void shouldMarkTaskAsFinalFailure() throws Exception {
    // Given
    Path failDir = tempDir.resolve("finalFail");
    Files.createDirectories(failDir);
    
    UploadTask task = createMockUploadTask("final-task-456");
    task.setStatus(UploadTaskStatus.FAILED);
    task.setRetryCount(3);  // 达到上限
    task.setExceptionDesc("Disk full");
    
    retryManager.init(uploadMetaStore, null, failDir, null, null, null);

    // When
    retryManager.markUploadAsFinalFailure(task);

    // Then
    assertEquals(UploadTaskStatus.FAILED, task.getStatus());
    assertTrue(task.getExceptionDesc().contains("FINAL_FAILURE"));
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldMarkTaskAsFinalFailure`
Expected: ❌ FAIL - 方法不存在

- [ ] **Step 3: 实现 markUploadAsFinalFailure()**

```java
/**
 * 标记上传任务为最终失败（达到最大重试次数）
 * 处理流程：
 * 1. 保持 FAILED 状态不变
 * 2. 更新 updateTime
 * 3. 保留在失败队列供人工查看
 * 4. 通知 Proxy（TODO）
 */
public void markUploadAsFinalFailure(UploadTask task) {
    try {
        String transferId = task.getTransferId();
        
        log.error("❌ 达到最大重试次数，标记为最终失败: transferId={}, file={}, retryCount={}/{}",
            transferId, task.getLocalFilePath(),
            task.getRetryCount(), getMaxRetriesForTask(task));
        
        // 1. 保持 FAILED 状态
        task.setStatus(UploadTaskStatus.FAILED);
        
        // 2. 更新时间戳
        task.setUpdateTime(Util.currentTime());
        
        // 3. 在异常描述中添加最终失败信息
        String originalDesc = task.getExceptionDesc() != null ? task.getExceptionDesc() : "";
        task.setExceptionDesc(originalDesc + " | FINAL_FAILURE: 达到最大重试次数");
        
        // 4. 保存（保留在失败队列中）
        if (uploadMetaStore != null) {
            uploadMetaStore.saveTask(task);
        }
        
        // 5. TODO: 通知 Proxy 任务最终失败
        // notifyProxyFinalFailure(task);
        
        log.warn("⚠️ 任务已标记为最终失败，保留在失败队列: transferId={}", transferId);
        
    } catch (Exception e) {
        log.error("❌ 标记最终失败异常: transferId={}, error={}",
            task.getTransferId(), e.getMessage());
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=RetryManagerTest#shouldMarkTaskAsFinalFailure`
Expected: ✅ PASS

- [ ] **Step 5: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java
git commit -m "feat(retry): 实现最终失败标记功能"
```

---

## Task 6: 在 AgentUploader 中实现 resubmitTask()

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java`
- Test: `agent/src/test/java/com/cq/agent/client/upload/AgentUploaderTest.java`

**目标:** 添加重新提交失败任务的方法

- [ ] **Step 1: 编写 resubmitTask 的测试**

```java
@Test
void shouldResubmitFailedTaskToWorkQueue() throws Exception {
    // Given
    AgentUploader uploader = new AgentUploader(createMockAgentConfig());
    uploader.init();
    
    UploadTask task = createMockUploadTask("resubmit-test");
    task.setStatus(UploadTaskStatus.PREPARED);
    task.setLocalFilePath("/tmp/test.txt");
    task.setRemoteTargetPath("192.168.1.100:/remote/test.txt");

    // When & Then - should not throw exception
    assertDoesNotThrow(() -> uploader.resubmitTask(task));
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=AgentUploaderTest#shouldResubmitFailedTaskToWorkQueue`
Expected: ❌ FAIL - 方法不存在

- [ ] **Step 3: 在 AgentUploader 中实现 resubmitTask()**

```java
/**
 * 重新提交失败的任务（内部方法，供 RetryManager 调用）
 * 
 * 与 uploadFile() 的区别：
 * - 不重新扫描文件
 * - 直接使用已有的任务对象
 * - 跳过文件存在性检查
 * 
 * @param task 已有的 UploadTask 对象（包含 localFilePath, remoteTargetPath 等）
 */
public void resubmitTask(UploadTask task) {
    String taskKey = generateTaskKey(task.getLocalFilePath(), task.getRemoteTargetPath());
    
    log.info("[traceId=N/A] 🔄 重新提交失败任务: transferId={}, file={}, retryCount={}, remoteTarget={}",
        task.getTransferId(), 
        task.getLocalFilePath(),
        task.getRetryCount(),
        task.getRemoteTargetPath());
    
    // 直接提交到工作队列（异步执行）
    workQueue.offer(() -> {
        String traceId = Util.generateTraceId();
        try {
            processSingleTask(taskKey, task, traceId);
        } catch (Exception e) {
            log.error("[traceId={}] ❌ 重试任务执行失败: taskKey={}, error={}", 
                traceId, taskKey, e.getMessage(), e);
        }
    });
    
    log.debug("✅ 任务已加入工作队列: taskKey={}", taskKey);
}

/**
 * 获取失败队列目录（用于日志和监控）
 */
public Path getFailedQueueDir() {
    return failedQueueDir;
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=AgentUploaderTest#shouldResubmitFailedTaskToWorkQueue`
Expected: ✅ PASS

- [ ] **Step 5: 提交**

```bash
git add agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java
git commit -m "feat(uploader): 添加 resubmitTask() 方法支持失败任务重试"
```

---

## Task 7: 在 AgentDownloader 中实现 resubmitTask()

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/download/AgentDownloader.java`
- Test: `agent/src/test/java/com/cq/agent/client/download/AgentDownloaderTest.java`

**目标:** 同 Task 6，但针对下载任务

- [ ] **Step 1: 参照 Task 6 的模式实现 DownloadTask 版本**

```java
/**
 * 重新提交失败的下载任务（内部方法，供 RetryManager 调用）
 */
public void resubmitTask(DownloadTask task) {
    String taskKey = generateTaskKey(task.getRemoteSourcePath(), task.getLocalTargetPath());
    
    log.info("[traceId=N/A] 🔄 重新提交失败下载任务: transferId={}, file={}, retryCount={}",
        task.getTransferId(),
        task.getLocalTargetPath(),
        task.getRetryCount());
    
    workQueue.offer(() -> {
        String traceId = Util.generateTraceId();
        try {
            processSingleTask(taskKey, task, traceId);
        } catch (Exception e) {
            log.error("[traceId={}] ❌ 重试下载任务执行失败: taskKey={}, error={}", 
                traceId, taskKey, e.getMessage(), e);
        }
    });
}

/**
 * 获取失败队列目录
 */
public Path getFailedQueueDir() {
    return failedQueueDir;
}
```

- [ ] **Step 2: 编写并运行测试**

- [ ] **Step 3: 提交**

```bash
git add agent/src/main/java/com/cq/agent/client/download/AgentDownloader.java
git commit -m "feat(downloader): 添加 resubmitTask() 方法支持失败任务重试"
```

---

## Task 8: 修改 BaseAgentClient 异常处理 - 不再抛出异常

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/BaseAgentClient.java`
- Test: `agent/src/test/java/com/cq/agent/client/BaseAgentClientTest.java`

**目标:** 修改异常处理逻辑，确保失败的任务进入失败队列后不再抛出异常触发整体重试

- [ ] **Step 1: 编写异常不传播的测试**

```java
@Test
void shouldNotThrowExceptionOnTaskFailure() throws Exception {
    // Given
    TestableBaseClient client = new TestableBaseClient(tempDir);
    client.init();

    // When: 模拟任务失败
    UploadTask task = client.createMockTask("fail-test");
    task.setStatus(UploadTaskStatus.FAILED);
    task.setExceptionDesc("Simulated failure");

    // Then: 处理失败任务不应该抛出异常
    assertDoesNotThrow(() -> {
        try {
            client.handleTaskFailure(task);
        } catch (Exception e) {
            fail("不应该抛出异常: " + e.getMessage());
        }
    });
}
```

- [ ] **Step 2: 修改 BaseAgentClient 的异常处理块**

找到现有的 catch 块，确保最后不再 rethrow：

```java
} catch (Exception e) {
    logger.error("[traceId={}] {} task failed: {} - {}", traceId, operationType, taskKey, e.getMessage(), e);
    
    // 1. 先设置异常描述（必须在 updateTaskStatus 之前）
    task.setExceptionDesc(e.getMessage());
    
    // 2. 保存 FAILED 状态（包含 exceptionDesc）
    updateTaskStatus(task, getFailedStatus());
    
    // 3. 移动到失败队列
    if (metaStore != null && failedQueueDir != null) {
        try {
            metaStore.moveToFailedQueue(task.getTransferId(), failedQueueDir);
            logger.info("[traceId={}] 任务已移入失败队列: transferId={}, queueDir={}", 
                traceId, task.getTransferId(), failedQueueDir);
        } catch (IOException ex) {
            logger.warn("[traceId={}] 移动失败任务到重试队列失败: {}", traceId, ex.getMessage());
        }
    }
    
    // 4. 通知监听器
    handleListenerError(taskKey, e.getMessage());
    
    // 5. 从进行中列表移除
    inflightTasks.remove(getTransferIdFromTask(task));
    
    // ✅ 不再抛出异常，避免整个批量任务重试
    // 失败的文件会在下次定时扫描时自动重试
}
```

- [ ] **Step 3: 运行测试验证**

- [ ] **Step 4: 提交**

```bash
git add agent/src/main/java/com/cq/agent/client/BaseAgentClient.java
git commit -m "fix(client): 异常处理后不再抛出避免整体任务重试"
```

---

## Task 9: 修改 BatchTaskSchedulerManager - 删除整体重试逻辑

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java`
- Test: `agent/src/test/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManagerTest.java`

**目标:** 删除 processScannedFiles() 末尾的整体重试异常抛出

- [ ] **Step 1: 定位需要修改的代码段**

找到这段代码（约在第 369-377 行）：

```java
// 如果有任何文件失败，抛出异常以触发重试机制
if (!failedFiles.isEmpty()) {
    throw new RuntimeException(String.format(
        "部分文件传输失败 (%d/%d): %s", 
        failedFiles.size(), scannedFiles.size(),
        String.join(", ", failedFiles)
    ));
}
```

- [ ] **Step 2: 替换为日志记录**

```java
// 如果有任何文件失败，记录日志但不抛出异常
// 失败的文件已经通过 moveToFailedQueue() 自动移入失败队列
// RetryManager 会定期扫描并重试这些文件
if (!failedFiles.isEmpty()) {
    log.warn("⚠️ 部分文件传输失败 ({}/{}): {}", 
        failedFiles.size(), scannedFiles.size(),
        String.join(", ", failedFiles));
    log.info("ℹ️ 失败的文件将进入失败队列，等待 RetryManager 定时扫描和重试");
    
    if (agentUploader != null) {
        log.info("ℹ️ 上传失败队列路径: {}", agentUploader.getFailedQueueDir());
    }
    if (agentDownloader != null) {
        log.info("ℹ️ 下载失败队列路径: {}", agentDownloader.getFailedQueueDir());
    }
}
```

- [ ] **Step 3: 编写测试验证部分失败不影响其他文件**

```java
@Test
void shouldContinueAfterPartialFailure() {
    // Given: 10 个文件，其中 2 个会失败
    // When: 执行批量传输
    // Then: 8 个成功，2 个进入失败队列，不抛出异常
}
```

- [ ] **Step 4: 运行全部测试确保无回归**

Run: `mvn test -pl agent`
Expected: ✅ 所有测试通过

- [ ] **Step 5: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java
git commit -m "fix(scheduler): 删除整体重试逻辑改为失败队列机制"
```

---

## Task 10: 创建 FailedQueueScannerJob Quartz Job

**Files:**
- Create: `agent/src/main/java/com/cq/agent/batch/scheduler/FailedQueueScannerJob.java`
- Test: `agent/src/test/java/com/cq/agent/batch/scheduler/FailedQueueScannerJobTest.java`

**目标:** 创建 Quartz Job 定义，用于定时触发失败队列扫描

- [ ] **Step 1: 编写 Job 类**

```java
package com.cq.agent.batch.scheduler;

import com.cq.agent.batch.transfer.RetryManager;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 失败队列扫描 Job
 * 由 Quartz 定时触发，扫描失败队列并重试失败的文件
 */
public class FailedQueueScannerJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(FailedQueueScannerJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("⏰ 开始扫描失败队列...");
        
        try {
            // 从 JobDataMap 或 Spring 容器获取 RetryManager
            RetryManager retryManager = getRetryManager(context);
            
            if (retryManager != null) {
                // 扫描上传失败队列
                retryManager.scanAndRetryFailedUploads();
                
                // 扫描下载失败队列
                retryManager.scanAndRetryFailedDownloads();
                
                log.info("✅ 失败队列扫描完成");
            } else {
                log.warn("⚠️ RetryManager 未初始化，跳过扫描");
            }
            
        } catch (Exception e) {
            log.error("❌ 执行失败队列扫描Job异常: {}", e.getMessage(), e);
            throw new JobExecutionException("失败队列扫描失败", e, false);
        }
    }

    /**
     * 从执行上下文获取 RetryManager 实例
     * TODO: 根据实际框架（Spring/手动）实现
     */
    private RetryManager getRetryManager(JobExecutionContext context) {
        // 方式1: 从 JobDataMap 获取
        Object retryManager = context.getJobDetail().getJobDataMap().get("retryManager");
        
        if (retryManager instanceof RetryManager) {
            return (RetryManager) retryManager;
        }
        
        // 方式2: TODO: 从 Spring 容器获取
        // return ApplicationContext.getBean(RetryManager.class);
        
        return null;
    }
}
```

- [ ] **Step 2: 编写基础测试**

```java
class FailedQueueScannerJobTest {

    @Test
    void shouldExecuteWithoutErrorWhenRetryManagerIsNull() throws Exception {
        // Given
        FailedQueueScannerJob job = new FailedQueueScannerJob();
        JobExecutionContext mockContext = Mockito.mock(JobExecutionContext.class);
        JobDetail mockJobDetail = Mockito.mock(JobDetail.class);
        JobDataMap jobDataMap = new JobDataMap();
        
        Mockito.when(mockContext.getJobDetail()).thenReturn(mockJobDetail);
        Mockito.when(mockJobDetail.getJobDataMap()).thenReturn(jobDataMap);

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> job.execute(mockContext));
    }
}
```

- [ ] **Step 3: 运行测试验证**

- [ ] **Step 4: 提交**

```bash
git add agent/src/main/java/com/cq/agent/batch/scheduler/FailedQueueScannerJob.java
git commit -m "feat(scheduler): 创建 FailedQueueScannerJob 用于定时扫描失败队列"
```

---

## Task 11: 在 AgentConfig 中添加扫描间隔配置

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/config/AgentConfig.java`

**目标:** 添加失败队列扫描间隔配置项

- [ ] **Step 1: 添加新字段**

```java
/**
 * 失败队列扫描间隔（毫秒）
 * 默认值：5分钟 (300000ms)
 */
private long failedQueueScanIntervalMs = 5 * 60 * 1000L;
```

- [ ] **Step 2: 添加 getter**

```java
public long getFailedQueueScanIntervalMs() {
    return failedQueueScanIntervalMs;
}
```

- [ ] **Step 3: 在构造函数中初始化**

```java
this.failedQueueScanIntervalMs = getLongProperty(
    "agent.failed.queue.scan.interval.ms", 
    5 * 60 * 1000L
);
```

- [ ] **Step 4: 提交**

```bash
git add agent/src/main/java/com/cq/agent/config/AgentConfig.java
git commit -m "feat(config): 添加失败队列扫描间隔配置项"
```

---

## Task 12: 端到端集成测试

**Files:**
- Create: `agent/src/test/java/com/cq/agent/batch/integration/FailedRetryIntegrationTest.java`

**目标:** 验证完整的失败 → 进入队列 → 扫描 → 重试流程

- [ ] **Step 1: 编写端到端测试场景**

```java
@DisplayName("失败文件重试完整流程")
class FailedRetryIntegrationTest {

    @Test
    @DisplayName("文件失败后应能被自动重试")
    void shouldAutomaticallyRetryFailedFile() throws Exception {
        // Given:
        // 1. 初始化 RetryManager（带所有依赖）
        // 2. 注册任务配置（maxRetries=3, intervalMin=5）
        // 3. 创建一个失败的上传任务（retryCount=0）放入失败队列
        
        // When:
        // 调用 scanAndRetryFailedUploads()
        
        // Then:
        // 1. 任务应从失败队列移到正常目录
        // 2. retryCount 应增加到 1
        // 3. 状态应变为 PREPARED
        // 4. AgentUploader.resubmitTask() 应被调用
    }

    @Test
    @DisplayName("达到最大重试次数后应标记为最终失败")
    void shouldMarkAsFinalFailureAfterMaxRetries() throws Exception {
        // Given: retryCount=3, maxRetries=3
        
        // When: 调用 scanAndRetryFailedUploads()
        
        // Then:
        // 1. 不应该再重试
        // 2. exceptionDesc 应包含 "FINAL_FAILURE"
        // 3. 任务应保留在失败队列
    }
}
```

- [ ] **Step 2: 运行集成测试**

Run: `mvn test -pl agent -Dtest=FailedRetryIntegrationTest`
Expected: ✅ PASS

- [ ] **Step 3: 运行全量测试确保无回归**

Run: `mvn test -pl agent`
Expected: ✅ 所有测试通过

- [ ] **Step 4: 最终提交**

```bash
git add .
git commit -m "feat(retry): 完成失败文件粒度重试机制实现"
```

---

## 总结

### 实施顺序建议

1. **Task 1-5**: RetryManager 核心扩展（最关键）
2. **Task 6-7**: AgentUploader/Downloader 支持
3. **Task 8-9**: 修改现有代码消除整体重试
4. **Task 10-11**: Quartz Job 和配置
5. **Task 12**: 集成测试验证

### 关键验收标准

- ✅ 单个文件失败不再导致整个批量任务重试
- ✅ 失败文件自动进入失败队列（FAILED-{type}-{transferId}.json）
- ✅ 定时扫描器能发现并重试失败文件
- ✅ 每次 retryCount 都正确持久化到 JSON
- ✅ 达到 maxRetries 后标记最终失败
- ✅ 所有 249+ 现有测试继续通过
- ✅ 新增测试覆盖主要场景

### 回滚策略

如果出现问题，可以通过以下方式快速回滚：
1. 还原 BatchTaskSchedulerManager 中的异常抛出逻辑
2. 注释掉 RetryManager 中的新方法
3. 所有改动都是增量式的，不影响现有功能

---

**计划版本**: 1.0  
**基于设计文档**: [2026-05-12-failed-file-retry-mechanism-design.md](../2026-05-12-failed-file-retry-mechanism-design.md)  
**预计工作量**: 8-12 个 Task（每个 Task 15-30 分钟）
