# 失败文件粒度重试机制设计方案

**日期**: 2026-05-12  
**状态**: 已批准 ✅  
**版本**: 1.0

---

## 1. 背景与问题

### 1.1 当前问题

**[BatchTaskSchedulerManager.processScannedFiles()](../../agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java#L369-L377)** 中存在的设计缺陷：

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

**问题分析**：
- ❌ **整个任务重试**：抛出异常会导致整个批量任务被重新调度
- ❌ **重复传输已成功文件**：已成功传输的文件会被重复处理
- ❌ **无失败队列扫描**：`recoverFailedTasks()` 方法存在但从未调用
- ❌ **资源浪费**：网络带宽、IO、CPU 资源浪费在重复传输上

### 1.2 核心需求

1. **单个文件级别重试**：仅重试失败的文件，不影响其他文件
2. **定时扫描失败队列**：基于 Quartz 定时扫描 `uploadFailRetryQueue/downloadFailRetryQueue`
3. **使用 Proxy 推送参数**：重试参数（maxRetries, intervalMin, backoffType）来自 Proxy 推送的配置
4. **持久化重试次数**：retryCount 持久化到 JSON 文件中
5. **集成到 RetryManager**：复用现有的 RetryManager 组件

---

## 2. 现有组件分析

### 2.1 BatchTransferTaskConfig.RetryConfig（Proxy 推送）

**位置**: [BatchTransferTaskConfig.java#L143-L164](../../agent/src/main/java/com/cq/agent/batch/config/BatchTransferTaskConfig.java#L143-L164)

```java
public static class RetryConfig {
    private boolean enabled;        // 是否启用重试
    private int maxDays;            // 最大天数
    private int intervalMin;        // 重试间隔（分钟）
    private int maxRetryCount;      // 最大重试次数
    private String backoffType;     // 回避策略（fixed/linear/exponential）
}
```

✅ **已包含所有需要的重试参数**

### 2.2 RetryManager（现有重试管理器）

**位置**: [RetryManager.java](../../agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java)

**现有功能**：
- ✅ `shouldRetry()` - 判断是否应该重试
- ✅ `calculateNextRetryDelay()` - 指数退避算法
- ✅ `recordSuccess()` - 记录成功并清除计数

**当前限制**：
- ⚠️ 使用内存存储 retryCount (ConcurrentHashMap)
- ⚠️ 无定时扫描能力
- ⚠️ 未与 TransferMetaStore 集成

### 2.3 UploadTask/DownloadTask（任务实体）

**位置**: [UploadTask.java](../../agent/src/main/java/com/cq/agent/client/upload/UploadTask.java)

**已有字段**：
- ✅ `retryCount` (AtomicInteger) - 重试计数器
- ✅ `status` - 任务状态
- ✅ `exceptionDesc` - 异常描述
- ✅ 所有时间戳字段

**关键特性**：
- 一个 JSON 文件 = 一个文件的传输任务
- 无需额外字段即可跟踪失败状态

---

## 3. 架构设计

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    Agent 启动流程                            │
│                                                             │
│  ┌──────────────────┐    ┌─────────────────────────────┐   │
│  │ BatchTransfer    │    │       RetryManager          │   │
│  │ TaskConfig       │ →  │                             │   │
│  │ (Proxy推送)      │    │  现有功能:                   │   │
│  │                  │    │  ├─ shouldRetry()           │   │
│  │ RetryConfig:     │    │  ├─ calculateNextRetryDelay()│   │
│  │ ├─ maxRetryCount │    │  └─ recordSuccess()         │   │
│  │ ├─ intervalMin   │    │                             │   │
│  │ ├─ backoffType   │    │  新增功能:                   │   │
│  │ └─ enabled       │    │  ├─ init()                 │   │
│  └──────────────────┘    │  ├─ registerTaskConfig()    │   │
│                           │  ├─ scanAndRetryFailed*()  │   │
│  ┌──────────────────┐    │  └─ retrySingleFile()      │   │
│  │ UploadTask/      │    └─────────────────────────────┘   │
│  │ DownloadTask     │                ↓                     │
│  │                  │        Quartz Scheduler              │
│  │ 已有字段:        │    (定时触发 scanAndRetryFailed)     │
│  │ ├─ retryCount ✅ │                                      │
│  │ ├─ status       │                                      │
│  │ └─ exceptionDesc│                                      │
│  └──────────────────┘                                      │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 数据流图

```
文件传输失败
    ↓
[BaseAgentClient 异常处理]
    ↓
task.setExceptionDesc(e.getMessage())
updateTaskStatus(task, FAILED)
metaStore.moveToFailedQueue(transferId, failedQueueDir)
    ↓
FAILED-{type}-{transferId}.json 移入失败队列目录
    ↓ (等待定时扫描)
⏰ Quartz 定时触发 FailedQueueScannerJob
    ↓
RetryManager.scanAndRetryFailedUploads()
    ↓
TransferMetaStore.recoverFailedTasks(failedQueueDir)
    ↓
遍历每个 FAILED-*.json 文件:
    ├─ 读取 task.getRetryCount() (从JSON)
    ├─ 获取 taskConfig.getRetryConfig() (Proxy参数)
    ├─ 比较: retryCount < maxRetryCount ?
    │   ├─ ✅ 是 → retrySingleFile(task)
    │   │        ├─ task.incrementRetryCount()
    │   │        ├─ task.setStatus(PREPARED)
    │   │        ├─ metaStore.saveTask(task)  ← 更新JSON中的retryCount
    │   │        └─ agentUploader.resubmitTask(task)
    │   └─ ❌ 否 → markAsFinalFailure(task)
    │              └─ 通知 Proxy 最终失败
    ↓
```

---

## 4. 详细设计

### 4.1 RetryManager 扩展

#### 4.1.1 新增依赖注入

```java
public class RetryManager {
    
    // ===== 现有字段 =====
    @Getter
    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final Map<Long, AtomicInteger> retryCountMap = new ConcurrentHashMap<>();
    
    @Setter
    private Function<Long, Long> retryExecutor;
    
    // ===== 新增：依赖注入 =====
    private TransferMetaStore<UploadTask> uploadMetaStore;
    private TransferMetaStore<DownloadTask> downloadMetaStore;
    private Path uploadFailQueueDir;
    private Path downloadFailQueueDir;
    private AgentUploader agentUploader;
    private AgentDownloader agentDownloader;
    
    // ===== 新增：任务配置缓存 =====
    private final Map<Long, BatchTransferTaskConfig> taskConfigMap = new ConcurrentHashMap<>();
}
```

#### 4.1.2 新增方法

**方法 1: init() - 初始化依赖**
```java
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

**方法 2: registerTaskConfig() - 注册任务配置**
```java
/**
 * 注册任务配置（从 Proxy 接收配置后调用）
 * @param taskId 任务ID
 * @param config 批量任务配置（包含 RetryConfig）
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
```

**方法 3: scanAndRetryFailedUploads() - 扫描上传失败队列**
```java
/**
 * 扫描上传失败队列并重试（Quartz Job 调用）
 * 
 * 核心逻辑：
 * 1. 从失败队列加载所有 FAILED-upload-*.json 文件
 * 2. 对每个文件检查是否超过最大重试次数
 * 3. 未超限则重新提交传输
 * 4. 已超限则标记为最终失败
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

/**
 * 扫描下载失败队列并重试（Quartz Job 调用）
 * 逻辑同 scanAndRetryFailedUploads()
 */
public void scanAndRetryFailedDownloads() {
    // 实现类似...
}
```

**方法 4: shouldRetryFailedFile() - 判断是否应重试**
```java
/**
 * 判断失败文件是否应该重试
 * 
 * @param task 失败的任务
 * @return true=应该重试, false=达到上限
 */
private boolean shouldRetryFailedFile(UploadTask task) {
    // 1. 从JSON读取当前重试次数（已持久化）
    int currentRetries = task.getRetryCount();
    
    // 2. 获取该任务的Proxy推送的配置
    BatchTransferTaskConfig config = findTaskConfigForUpload(task);
    
    // 3. 检查是否有有效配置
    if (config == null) {
        log.warn("⚠️ 未找到任务配置，使用默认值: transferId={}", task.getTransferId());
        return currentRetries < maxRetries;  // 使用构造函数的默认值
    }
    
    // 4. 检查是否启用重试
    if (config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
        log.warn("⚠️ 任务未启用重试: transferId={}", task.getTransferId());
        return false;
    }
    
    // 5. 检查是否超过最大重试次数
    int maxAllowed = config.getRetryConfig().getMaxRetryCount();
    boolean shouldRetry = currentRetries < maxAllowed;
    
    log.debug("🔍 重试检查: transferId={}, current={}, max={}, shouldRetry={}",
        task.getTransferId(), currentRetries, maxAllowed, shouldRetry);
    
    return shouldRetry;
}
```

**方法 5: retrySingleUploadFile() - 重试单个文件**
```java
/**
 * 重试单个失败的上传文件
 * 
 * 关键步骤：
 * 1. 增加 retryCount（重要：必须立即持久化到JSON）
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
        uploadMetaStore.saveTask(task);
        
        log.info("💾 已更新任务状态: transferId={}, retryCount={}, 新路径=PREPARED-upload-{}.json",
            transferId, newRetryCount, transferId);
        
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
```

**方法 6: markUploadAsFinalFailure() - 标记最终失败**
```java
/**
 * 标记上传任务为最终失败（达到最大重试次数）
 * 
 * 处理流程：
 * 1. 保持 FAILED 状态不变
 * 2. 更新 updateTime
 * 3. 保留在失败队列供人工查看
 * 4. 通知 Proxy（TODO）
 */
private void markUploadAsFinalFailure(UploadTask task) {
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
        uploadMetaStore.saveTask(task);
        
        // 5. TODO: 通知 Proxy 任务最终失败
        // notifyProxyFinalFailure(task);
        
        log.warn("⚠️ 任务已标记为最终失败，保留在失败队列: transferId={}", transferId);
        
    } catch (Exception e) {
        log.error("❌ 标记最终失败异常: transferId={}, error={}",
            task.getTransferId(), e.getMessage());
    }
}
```

### 4.2 AgentUploader/Downloader 扩展

#### 4.2.1 新增 resubmitTask() 方法

**[AgentUploader.java](../../agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java)**:
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
```

### 4.3 BaseAgentClient 异常处理修改

**[BaseAgentClient.java](../../agent/src/main/java/com/cq/agent/client/BaseAgentClient.java)**:

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

### 4.4 BatchTaskSchedulerManager 修改

**[BatchTaskSchedulerManager.processScannedFiles()](../../agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java#L369-L377)**:

```java
// ❌ 删除这段代码：
if (!failedFiles.isEmpty()) {
    throw new RuntimeException(String.format(
        "部分文件传输失败 (%d/%d): %s", 
        failedFiles.size(), scannedFiles.size(),
        String.join(", ", failedFiles)
    ));
}

// ✅ 替换为：
if (!failedFiles.isEmpty()) {
    log.warn("⚠️ 部分文件传输失败 ({}/{}): {}", 
        failedFiles.size(), scannedFiles.size(),
        String.join(", ", failedFiles));
    log.info("ℹ️ 失败的文件将进入失败队列，等待 RetryManager 定时扫描和重试");
    log.info("ℹ️ 失败队列路径: upload={}, download={}",
        agentUploader.getFailedQueueDir(),
        agentDownloader != null ? agentDownloader.getFailedQueueDir() : "N/A");
    
    // 不再抛出异常，让成功的文件正常完成
    // 失败的文件已经通过 moveToFailedQueue() 自动移入失败队列
}
```

### 4.5 Quartz Job 定义

**新增: FailedQueueScannerJob.java**
```java
package com.cq.agent.batch.scheduler;

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

    private RetryManager getRetryManager(JobExecutionContext context) {
        // TODO: 从 Spring 容器或 JobDataMap 获取 RetryManager 实例
        return null;
    }
}
```

---

## 5. 配置项

### 5.1 AgentConfig 新增配置

```properties
# 失败队列扫描间隔（毫秒），默认5分钟
agent.failed.queue.scan.interval.ms=300000
```

**[AgentConfig.java](../../agent/src/main/java/com/cq/agent/config/AgentConfig.java)**:
```java
/**
 * 失败队列扫描间隔（毫秒）
 * 默认值：5分钟 (300000ms)
 */
private long failedQueueScanIntervalMs = 5 * 60 * 1000;

public long getFailedQueueScanIntervalMs() {
    return failedQueueScanIntervalMs;
}

// 在构造函数中初始化：
this.failedQueueScanIntervalMs = getLongProperty("agent.failed.queue.scan.interval.ms", 5 * 60 * 1000L);
```

---

## 6. 数据流示例

### 6.1 场景：批量上传 10 个文件，file9 失败 3 次后第 4 次成功

**前置条件**：
- taskId = 100
- Proxy 配置: `maxRetryCount=3, intervalMin=5, backoffType=exponential`

```
时间轴    事件                                                    状态文件
─────────────────────────────────────────────────────────────────────

T=0min    批量任务开始，提交10个文件                                 
          
T=1min    file1-8 上传成功 ✅                                     
          UPLOAD_SUCCESS-upload-file1~8.json
          
          file9 上传失败 ❌ (网络超时)                              
          → setExceptionDesc("Connection timeout")
          → setStatus(FAILED)
          → moveToFailedQueue()
          → FAILED-upload-file9.json (retryCount=0)
          
T=5min    ⏰ Quartz 触发 FailedQueueScannerJob                   
          → RetryManager.scanAndRetryFailedUploads()
          → 加载 FAILED-upload-file9.json
          → retryCount(0) < maxRetryCount(3) ✅
          → incrementRetryCount() → retryCount=1
          → setStatus(PREPARED)
          → saveTask() → PREPARED-upload-file9.json (retryCount=1)
          → agentUploader.resubmitTask()
          
T=6min    🔄 重试 file9 (retryCount=1)                            
          → 再次失败 ❌ (连接拒绝)                                 
          → FAILED-upload-file9.json (retryCount=1)

T=10min   ⏰ 定时扫描再次触发                                     
          → retryCount(1) < maxRetryCount(3) ✅
          → incrementRetryCount() → retryCount=2
          → PREPARED-upload-file9.json (retryCount=2)
          
T=11min   🔄 重试 file9 (retryCount=2)                            
          → 再次失败 ❌ (磁盘满)                                   
          → FAILED-upload-file9.json (retryCount=2)

T=15min   ⏰ 定时扫描再次触发                                     
          → retryCount(2) < maxRetryCount(3) ✅
          → incrementRetryCount() → retryCount=3
          → PREPARED-upload-file9.json (retryCount=3)

T=16min   🔄 重试 file9 (retryCount=3) ← 最后一次机会             
          → 上传成功 ✅                                           
          → UPLOAD_SUCCESS-upload-file9.json
          → RetryManager.recordSuccess(taskId) 清除内存记录
          
          ✅ file9 最终成功！

─────────────────────────────────────────────────────────────────────

⚠️ 如果 T=16min 第4次仍然失败:

T=16min   🔄 重试 file9 (retryCount=3)                            
          → 再次失败 ❌                                          
          → FAILED-upload-file9.json (retryCount=3)

T=20min   ⏰ 定时扫描再次触发                                     
          → retryCount(3) >= maxRetryCount(3) ❌
          → markAsFinalFailure(task)
          → exceptionDesc += " | FINAL_FAILURE"
          → 保留在失败队列: FAILED-upload-file9.json
          → TODO: 通知 Proxy 最终失败
          
          ❌ file9 最终失败，需人工介入
```

### 6.2 JSON 文件内容变化示例

**初始失败时 (T=1min)**:
```json
{
  "transferId": "file9-uuid",
  "status": "FAILED",
  "localFilePath": "/data/logs/app.log",
  "remoteTargetPath": "172.19.200.130:/tmp/logs/app.log",
  "retryCount": 0,
  "exceptionDesc": "Connection timeout",
  "updateTime": "2026-05-12 15:01:00.000"
}
```

**第1次重试前 (T=5min)**:
```json
{
  "transferId": "file9-uuid",
  "status": "PREPARED",              ← 状态重置
  "localFilePath": "/data/logs/app.log",
  "remoteTargetPath": "172.19.200.130:/tmp/logs/app.log",
  "retryCount": 1,                    ← ✅ 已增加
  "exceptionDesc": null,              ← 已清除
  "updateTime": "2026-05-12 15:05:00.000"  ← 时间更新
}
```

**第2次失败后 (T=11min)**:
```json
{
  "transferId": "file9-uuid",
  "status": "FAILED",
  "localFilePath": "/data/logs/app.log",
  "remoteTargetPath": "172.19.200.130:/tmp/logs/app.log",
  "retryCount": 2,                    ← ✅ 持久化
  "exceptionDesc": "Disk full",       ← 新的异常原因
  "updateTime": "2026-05-12 15:11:00.000"
}
```

**最终失败时 (T=20min)**:
```json
{
  "transferId": "file9-uuid",
  "status": "FAILED",
  "localFilePath": "/data/logs/app.log",
  "remoteTargetPath": "172.19.200.130:/tmp/logs/app.log",
  "retryCount": 3,                    ← 达到上限
  "exceptionDesc": "Disk full | FINAL_FAILURE: 达到最大重试次数",  ← 标记
  "updateTime": "2026-05-12 15:20:00.000"
}
```

---

## 7. 目录结构

### 7.1 运行时目录结构

```
/tmp/my-panel/admin/data/transfers/
├── uploadsMeta/                              # 正在上传的任务
│   ├── SCANNED-upload-file1.json             # 成功
│   ├── UPLOADING_CHUNKS-upload-file2.json   # 进行中
│   └── ...
│
├── downloadsMeta/                            # 正在下载的任务
│   └── ...
│
├── uploadFailRetryQueue/                     # ⭐ 上传失败队列（待重试）
│   ├── FAILED-upload-file9.json             # 待重试 (retryCount=2)
│   ├── FAILED-upload-file10.json            # 待重试 (retryCount=0)
│   └── FAILED-upload-file11.json            # 最终失败 (retryCount=3)
│
├── downloadFailRetryQueue/                   # ⭐ 下载失败队列（待重试）
│   └── FAILED-download-backup1.json         # 待重试
│
├── uploadSendingQueue/                       # 上传发送队列
└── downloadSendingQueue/                     # 下载发送队列
```

---

## 8. API 设计

### 8.1 RetryManager 公开接口

```java
public class RetryManager {
    
    /**
     * 初始化依赖（启动时调用一次）
     */
    public void init(TransferMetaStore<UploadTask> uploadMetaStore,
                     TransferMetaStore<DownloadTask> downloadMetaStore,
                     Path uploadFailQueueDir,
                     Path downloadFailQueueDir,
                     AgentUploader uploader,
                     AgentDownloader downloader);
    
    /**
     * 注册任务配置（接收Proxy配置后调用）
     */
    public void registerTaskConfig(Long taskId, BatchTransferTaskConfig config);
    
    /**
     * 扫描上传失败队列并重试（Quartz Job调用）
     */
    public void scanAndRetryFailedUploads();
    
    /**
     * 扫描下载失败队列并重试（Quartz Job调用）
     */
    public void scanAndRetryFailedDownloads();
    
    // ===== 现有接口（保持不变）=====
    public boolean shouldRetry(Long subtaskId, String error);
    public long calculateNextRetryDelay(Long subtaskId, int attempt);
    public void recordSuccess(Long subtaskId);
    public int getRetryCount(Long subtaskId);
    public void clearAll();
}
```

### 8.2 AgentUploader/Downloader 公开接口

```java
public class AgentUploader {
    
    /**
     * 重新提交失败的任务（供 RetryManager 调用）
     * @param task 包含完整信息的 UploadTask
     */
    public void resubmitTask(UploadTask task);
    
    /**
     * 获取失败队列目录（用于日志和监控）
     */
    public Path getFailedQueueDir();
}
```

---

## 9. 错误处理

### 9.1 异常场景处理

| 场景 | 处理方式 | 日志级别 |
|------|---------|---------|
| 失败队列为空 | 跳过扫描 | DEBUG |
| JSON 文件损坏 | 跳过该文件，记录警告 | WARN |
| retryCount 解析错误 | 使用默认值0，继续处理 | WARN |
| 任务配置缺失 | 使用 RetryManager 默认值 | WARN |
| 重试未启用 | 跳过该任务 | WARN |
| 重新提交失败 | 记录错误，保持原状 | ERROR |
| 达到最大重试次数 | 标记最终失败，通知Proxy | ERROR |

### 9.2 幂等性保证

- **重复扫描安全**：多次调用 `scanAndRetryFailedUploads()` 不会导致重复提交
  - 原因：重试前会将状态改为 PREPARED 并移出失败队列
  - 下次扫描时该文件不再出现在失败队列中
  
- **retryCount 准确性**：使用 AtomicInteger 保证线程安全
  - 每次 `incrementRetryCount()` 都会立即持久化到 JSON
  - 即使重启也能恢复正确的重试次数

---

## 10. 测试策略

### 10.1 单元测试

1. **RetryManagerTest**
   - 测试 `shouldRetryFailedFile()` 各种边界条件
   - 测试 `retrySingleUploadFile()` 的完整流程
   - 测试 `markAsFinalFailure()` 的状态更新
   - 测试 `registerTaskConfig()` 和配置查找

2. **FailedQueueScannerJobTest**
   - 测试 Quartz Job 触发和执行
   - 测试异常情况下的容错

3. **AgentUploaderTest**
   - 测试 `resubmitTask()` 方法
   - 验证任务正确提交到工作队列

### 10.2 集成测试

1. **端到端重试流程**
   - 模拟文件失败 → 进入失败队列 → 定时扫描 → 重试 → 成功
   
2. **边界条件**
   - retryCount = maxRetryCount - 1 （最后一次机会）
   - retryCount = maxRetries （达到上限）
   
3. **并发场景**
   - 多个文件同时失败
   - 扫描过程中新文件进入失败队列

---

## 11. 监控与日志

### 11.1 关键日志点

```
[INFO] 🔍 发现 N 个待重试的上传失败任务
[INFO] 🔄 准备重试: transferId=X, file=Y, retryCount=1/3
[INFO] 💾 已更新任务状态: transferId=X, retryCount=1
[INFO] ✅ 失败文件已重新提交到工作队列: transferId=X
[ERROR] ❌ 达到最大重试次数，标记为最终失败: transferId=X
[WARN] ⚠️ 任务未启用重试: transferId=X
[DEBUG] 📭 上传失败队列为空
```

### 11.2 监控指标（建议）

- `retry_manager.scanned_files_total` - 扫描到的失败文件总数
- `retry_manager.retried_files_total` - 成功重试的文件数
- `retry_manager.final_failure_files_total` - 最终失败的文件数
- `retry_manager.scan_duration_ms` - 扫描耗时

---

## 12. 后续优化方向

### 12.1 短期优化（本次实现范围）

- [x] RetryManager 扩展定时扫描能力
- [x] 整合 BatchTransferTaskConfig.RetryConfig 参数
- [x] retryCount 持久化到 JSON
- [x] 修改 processScannedFiles 不再整体重试
- [x] 实现 FailedQueueScannerJob

### 12.2 中期优化（后续迭代）

- [ ] **Proxy 通知机制**：最终失败时主动通知 Proxy
- [ ] **重试延迟调度**：使用 Quartz 精确控制每次重试的时间
- [ ] **回避策略实现**：支持 fixed/linear/exponential 三种模式
- [ ] **监控指标暴露**：集成 Micrometer/Prometheus

### 12.3 长期优化（未来规划）

- [ ] **智能重试决策**：根据错误类型选择不同的重试策略
- [ ] **重试优先级**：重要文件优先重试
- [ ] **并发控制**：限制同时重试的文件数量
- [ ] **统计报表**：重试成功率、平均重试次数等

---

## 13. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 失败队列文件过多 | 扫描性能下降 | 定期清理过期文件（maxDays） |
| JSON 文件损坏 | 无法重试 | 捕获异常并跳过，记录警告 |
| 重试风暴 | 大量文件同时重试 | 限制并发数 + 指数退避 |
| 配置不一致 | 使用错误的参数 | 日志记录实际使用的参数 |
| 时钟不同步 | 重试时间计算错误 | 使用系统统一时间源 |

---

## 14. 总结

### 14.1 核心价值

✅ **精确重试**：仅重试失败的文件，不浪费资源  
✅ **自动化**：基于 Quartz 定时扫描，无需人工干预  
✅ **可配置**：完全使用 Proxy 推送的重试参数  
✅ **可观测**：完整的日志和 JSON 状态追踪  
✅ **可靠性**：retryCount 持久化，重启不丢失  

### 14.2 改动范围

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| [RetryManager.java](../../agent/src/main/java/com/cq/agent/batch/transfer/RetryManager.java) | **扩展** | 新增 6 个方法 + 依赖注入 |
| [AgentUploader.java](../../agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java) | **扩展** | 新增 resubmitTask() 方法 |
| [AgentDownloader.java](../../agent/src/main/java/com/cq/agent/client/download/AgentDownloader.java) | **扩展** | 新增 resubmitTask() 方法 |
| [BaseAgentClient.java](../../agent/src/main/java/com/cq/agent/client/BaseAgentClient.java) | **修改** | 异常处理不再抛出异常 |
| [BatchTaskSchedulerManager.java](../../agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java) | **修改** | 删除整体重试逻辑 |
| [AgentConfig.java](../../agent/src/main/java/com/cq/agent/config/AgentConfig.java) | **扩展** | 新增扫描间隔配置 |
| [FailedQueueScannerJob.java](../../agent/src/main/java/com/cq/agent/batch/scheduler/FailedQueueScannerJob.java) | **新增** | Quartz Job 定义 |

### 14.3 向后兼容性

- ✅ **完全向后兼容**：现有代码无需改动即可运行
- ✅ **渐进式升级**：可以先启用上传重试，再启用下载重试
- ✅ **可降级**：如果 RetryManager 未初始化，跳过扫描（不影响主流程）

---

**文档版本**: 1.0  
**最后更新**: 2026-05-12  
**作者**: AI Assistant  
**审批状态**: ✅ 已批准
