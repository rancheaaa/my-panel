# 设计文档：优化为纯文件级重试机制

**日期**: 2026-05-13  
**状态**: 已批准 ✅  
**类型**: 架构重构

---

## 1. 背景与目标

### 1.1 当前问题

系统中存在**两层冗余的重试机制**：

1. **任务级重试（要删除）**
   - 组件：`DelayedRetryJob` + `failTask()` + `scheduleDelayedRetry()`
   - 行为：整个任务失败时重新执行所有文件传输
   - 问题：与文件级重试功能重复，增加复杂度

2. **文件级重试（要保留）**
   - 组件：`FailedQueueScannerJob` + `RetryAwareUploaderDecorator`
   - 行为：仅针对失败的文件进行重试
   - 优势：细粒度控制，使用 Proxy 推送的重试策略

### 1.2 重构目标

- ✅ **删除任务级重试**：移除 DelayedRetryJob 及相关调度逻辑
- ✅ **保留文件级重试**：FailedQueueScannerJob 继续工作
- ✅ **Quartz 实例隔离**：文件级重试使用独立的调度器实例
- ✅ **职责清晰**：任务失败仅记录日志，不触发重试

---

## 2. 架构设计

### 2.1 当前架构（修改前）

```
┌─────────────────────────────────┐
│  BatchTaskSchedulerManager      │
│  └─ quartzTaskScheduler         │ ← 唯一的 Quartz 实例
│     ├─ Cron 任务调度             │
│     ├─ FailedQueueScannerJob    │ ← 共享同一个实例 ❌
│     └─ DelayedRetryJob          │ ← 要删除 ❌
└─────────────────────────────────┘
```

**问题**：
- 任务调度和失败队列扫描共用同一个 Quartz 实例
- 两者生命周期耦合，无法独立管理
- DelayedRetryJob 与 FailedQueueScannerJob 功能重叠

### 2.2 目标架构（修改后）

```
┌──────────────────────────────────┐
│  BatchTaskSchedulerManager       │
│  └─ quartzTaskScheduler          │ ← 任务调度专用
│     └─ Cron 任务调度              │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│  FileRetryScheduler (新增)        │ ← 独立的 Quartz 实例 ✨
│  └─ retryScheduler               │
│     └─ FailedQueueScannerJob     │ ← 文件级重试专用
└──────────────────────────────────┘
```

**优势**：
- 职责隔离：任务调度 vs 文件重试
- 故障隔离：一个崩溃不影响另一个
- 独立运维：可单独调整扫描频率、监控健康状态

---

## 3. 详细变更清单

### 3.1 删除的组件

#### 3.1.1 DelayedRetryJob.java（完整删除）

- **路径**: `agent/src/main/java/com/cq/agent/batch/scheduler/DelayedRetryJob.java`
- **行数**: 67 行
- **原因**: 不再需要任务级延迟重试

#### 3.1.2 BatchTaskSchedulerManager 中删除的方法

| 方法名 | 原位置 | 行数 | 删除原因 |
|--------|--------|------|----------|
| `failTask()` | L226-L251 | ~25 行 | 改为 handleTaskFailure() |
| `scheduleDelayedRetry()` | L457-L487 | ~30 行 | 不再调度延迟重试 |
| `createTaskRunnableForRetry()` | L292-L294 | 3 行 | 仅被 DelayedRetryJob 调用 |
| `scheduleFailedQueueScannerJob()` | L201-L230 | ~30 行 | 转移到 FileRetryScheduler |

**总删除代码**: ~88 行

---

### 3.2 新增的组件

#### 3.2.1 FileRetryScheduler.java（新建）

**路径**: `agent/src/main/java/com/cq/agent/batch/scheduler/FileRetryScheduler.java`

```java
/**
 * 文件级重试调度器
 * 
 * 核心职责：
 * - 使用独立的 Quartz 实例管理文件失败队列扫描
 * - 与 BatchTaskSchedulerManager 的任务调度完全隔离
 * - 生命周期独立，可单独启停
 */
public class FileRetryScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(FileRetryScheduler.class);
    
    private final Scheduler retryScheduler;  // 独立的 Quartz 实例
    private final RetryAwareUploaderDecorator retryAwareUploader;
    
    /**
     * 构造函数
     * @param retryAwareUploader 重试感知的上传装饰者
     * @param scanIntervalMs 失败队列扫描间隔（毫秒）
     */
    public FileRetryScheduler(RetryAwareUploaderDecorator retryAwareUploader, 
                             long scanIntervalMs) throws SchedulerException {
        this.retryAwareUploader = retryAwareUploader;
        
        // 创建独立的 Quartz 调度器实例
        this.retryScheduler = new StdSchedulerFactory().getScheduler();
        this.retryScheduler.start();
        
        // 注册 FailedQueueScannerJob
        scheduleFailedQueueScannerJob(scanIntervalMs);
        
        log.info("✅ FileRetryScheduler初始化完成, 扫描间隔: {}ms", scanIntervalMs);
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        try {
            if (retryScheduler != null && !retryScheduler.isShutdown()) {
                retryScheduler.shutdown(true);
                log.info("⏹️ FileRetryScheduler已关闭");
            }
        } catch (SchedulerException e) {
            log.error("❌ 关闭FileRetryScheduler异常: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 注册并调度 FailedQueueScannerJob
     */
    private void scheduleFailedQueueScannerJob(long scanIntervalMs) {
        try {
            JobDetail failedQueueScannerJob = JobBuilder.newJob(FailedQueueScannerJob.class)
                    .withIdentity("failedQueueScanner", "retry-group")
                    .build();

            // 将 RetryAwareUploaderDecorator 放入 JobDataMap
            failedQueueScannerJob.getJobDataMap().put("retryAwareUploader", retryAwareUploader);

            Trigger failedQueueScannerTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("failedQueueScannerTrigger", "retry-group")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(scanIntervalMs)
                            .repeatForever())
                    .build();

            retryScheduler.scheduleJob(failedQueueScannerJob, failedQueueScannerTrigger);

            log.info("✅ FailedQueueScannerJob已注册, 扫描间隔: {}ms ({}分钟)",
                    scanIntervalMs, scanIntervalMs / 60000);
        } catch (Exception e) {
            log.warn("⚠️ 注册FailedQueueScannerJob失败: {}", e.getMessage());
        }
    }
}
```

**核心特性**:
- ✅ 独立的 `StdSchedulerFactory` 实例
- ✅ 生命周期独立管理（构造时启动，shutdown() 时关闭）
- ✅ 单一职责：只负责文件级失败队列扫描
- ✅ 接收 `RetryAwareUploaderDecorator` 和扫描间隔作为参数

---

### 3.3 修改的组件

#### 3.3.1 BatchTaskSchedulerManager.java

**变更 1: 新增 handleTaskFailure() 方法**

```java
/**
 * 处理任务执行失败（仅记录日志，不触发重试）
 * 
 * 设计原则：
 * - 文件传输失败由 FileRetryScheduler + FailedQueueScannerJob 处理
 * - 任务级别失败（配置错误、目录不存在等）直接标记为最终失败
 * - 不再进行任务级别的延迟重试
 *
 * @param taskId 任务ID
 * @param error 错误信息
 */
private void handleTaskFailure(Long taskId, String error) {
    log.error("❌ 任务执行失败（最终失败）: taskId={}, error={}", taskId, error);
    
    // TODO: 可选 - 未来可在此处添加：
    // 1. 更新任务状态到配置文件（如将 status 改为 FAILED）
    // 2. 发送失败通知到监控系统
    // 3. 记录失败事件到审计日志
}
```

**变更 2: 修改 createTaskRunnable() 的 catch 块**

位置: [BatchTaskSchedulerManager.java#L282-L285](file:///e:/java-project2/my-panel/agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java#L282-L285)

```java
// 修改前:
} catch (Exception e) {
    log.error("❌ 任务执行异常: taskId={}, error={}", config.getTaskId(), e.getMessage(), e);
    failTask(config.getTaskId(), e.getMessage());  // 触发任务级重试 ❌
}

// 修改后:
} catch (Exception e) {
    log.error("❌ 任务执行异常: taskId={}, error={}", config.getTaskId(), e.getMessage(), e);
    handleTaskFailure(config.getTaskId(), e.getMessage());  // 仅记录日志 ✅
}
```

**变更 3: 删除方法列表**

以下方法从 `BatchTaskSchedulerManager` 中完全移除：

1. `public boolean failTask(Long taskId, String error)` - L226-L251
2. `private void scheduleDelayedRetry(Long taskId, long delayMs)` - L457-L487
3. `public Runnable createTaskRunnableForRetry(AgentTaskConfig config)` - L292-L294
4. `public void scheduleFailedQueueScannerJob(JobDetail jobDetail, Trigger trigger)` - L201-L230

**变更 4: shutdown() 方法简化**

```java
// 修改前:
public void shutdown() {
    quartzTaskScheduler.shutdown();
    if (retryAwareUploader != null) {
        retryAwareUploader.clearAll();
    }
    log.info("⏹️ 所有任务已停止");
}

// 修改后:
public void shutdown() {
    quartzTaskScheduler.shutdown();
    log.info("⏹️ BatchTaskSchedulerManager已关闭（FileRetryScheduler需单独关闭）");
}
```

**注意**: `retryAwareUploader.clearAll()` 的调用移至 `FileRetryScheduler.shutdown()` 或 `AgentApplication` 的 shutdown hook 中。

---

#### 3.3.2 AgentApplication.java

**变更: 使用 FileRetryScheduler 替代直接调度**

位置: [AgentApplication.java](file:///e:/java-project2/my-panel/agent/src/main/java/com/cq/agent/AgentApplication.java)

```java
// ========== 修改前 ==========
// 在 initializeUploadDownloadServices() 或 main() 中：

// 1. 设置装饰者到任务调度器
taskSchedulerManager.setRetryAwareUploader(retryAwareUploader);
taskSchedulerManager.setAgentUploader(retryAwareUploader);

// 2. 注册 FailedQueueScannerJob 到 BatchTaskSchedulerManager
try {
    JobDetail failedQueueScannerJob = JobBuilder.newJob(FailedQueueScannerJob.class)
            .withIdentity("failedQueueScanner", "retry-group")
            .build();
    failedQueueScannerJob.getJobDataMap().put("retryAwareUploader", retryAwareUploader);
    
    Trigger failedQueueScannerTrigger = TriggerBuilder.newTrigger()
            .withIdentity("failedQueueScannerTrigger", "retry-group")
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMilliseconds(config.getFailedQueueScanIntervalMs())
                    .repeatForever())
            .build();
    
    taskSchedulerManager.scheduleFailedQueueScannerJob(failedQueueScannerJob, failedQueueScannerTrigger);
} catch (Exception e) {
    logger.warn("⚠️ 注册FailedQueueScannerJob失败: {}", e.getMessage());
}

// 3. Shutdown hook
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    registryService.stop();
    taskSchedulerManager.shutdown();  // 同时关闭任务调度和文件重试
    server.stop();
}));

// ========== 修改后 ==========

// 1. 设置装饰者到任务调度器（保留，用于文件上传）
taskSchedulerManager.setRetryAwareUploader(retryAwareUploader);
taskSchedulerManager.setAgentUploader(retryAwareUploader);

// 2. 创建独立的文件重试调度器
FileRetryScheduler fileRetryScheduler = new FileRetryScheduler(
    retryAwareUploader,
    config.getFailedQueueScanIntervalMs()
);

// 3. Shutdown hook（分别关闭两个调度器）
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    logger.info("Shutdown signal received");
    registryService.stop();
    taskSchedulerManager.shutdown();      // 关闭任务调度器
    fileRetryScheduler.shutdown();        // 关闭文件重试调度器
    server.stop();
}));
```

---

## 4. 数据流设计

### 4.1 修改前的混合重试流程

```
场景 A: 整个任务执行失败（配置错误、目录不存在等）
  ↓
createTaskRunnable() 捕获异常
  ↓
failTask(taskId, error)
  ↓
retryAwareUploader.shouldRetry(taskId, error)?
  ├── true → scheduleDelayedRetry() → Quartz调度 → DelayedRetryJob.execute()
  │         ↓
  │         createTaskRunnableForRetry(config).run()
  │         ↓
  │         重新执行整个任务（扫描+传输所有文件）
  │         ↓
  │         如果再次失败 → 递归调用 failTask() → 直到超过最大重试次数
  │
  └── false → 记录最终失败日志 → return false

场景 B: 单个文件传输失败（网络波动等临时错误）
  ↓
processScannedFiles() 捕获单个文件异常
  ↓
failedFiles.add(fileName)
  ↓
文件自动移入失败队列（RocksDB/TransferMetaStore）
  ↓
FailedQueueScannerJob 定时触发（共享Quartz实例）
  ↓
RetryAwareUploaderDecorator.scanAndRetryFailedUploads()
  ↓
shouldRetryFailedFile(task)? → retrySingleUploadFile(task)
  ↓
仅重试该失败文件 ✅
```

**问题**:
- 场景 A 的任务级重试会重新传输所有文件（包括成功的），浪费资源
- 场景 A 和 B 共用同一个 Quartz 实例，生命周期耦合
- DelayedRetryJob 与 FailedQueueScannerJob 功能部分重叠

### 4.2 修改后的纯文件级重试流程

```
场景 A: 整个任务执行失败（配置错误、目录不存在等严重错误）
  ↓
createTaskRunnable() 捕获异常
  ↓
handleTaskFailure(taskId, error)
  ↓
仅记录错误日志 ❌（不触发任何重试）
  ↓
标记任务为最终失败（可选：更新配置文件状态）
  ↓
等待人工介入或下次定时任务修正配置后重新执行

场景 B: 单个文件传输失败（网络波动等临时错误）
  ↓
processScannedFiles() 捕获单个文件异常
  ↓
failedFiles.add(fileName)
  ↓
文件自动移入失败队列（RocksDB/TransferMetaStore）
  ↓
FailedQueueScannerJob 定时触发（独立Quartz实例 ✨）
  ↓
RetryAwareUploaderDecorator.scanAndRetryFailedUploads()
  ↓
shouldRetryFailedFile(task)?
  ├── 读取 AgentTaskConfig.retryConfig （Proxy推送的策略参数）
  │   ├── maxRetryCount: 最大重试次数（默认10次）
  │   ├── intervalMin: 首次重试间隔（默认30分钟）
  │   ├── maxDelayHours: 最大退避时间（默认2小时）
  │   └── backoffType: 退避策略（EXPONENTIAL指数退避）
  │
  ├── 允许重试 → retrySingleUploadFile(task)
  │   ↓
  │   更新任务状态为 PREPARED
  │   ↑
  │   delegate.resubmitTask(task) 重新提交到工作队列
  │   ↓
  │   仅重试该失败文件 ✅
  │
  └── 超过最大重试次数 → markAsFinalFailure(task)
      ↓
      保留在失败队列，记录最终失败原因
```

**优势**:
- 场景 A 快速失败，不浪费资源重试
- 场景 B 细粒度重试，只处理失败的文件
- 两个调度器完全隔离，互不影响
- 重试策略参数来源于 Proxy 配置，支持热更新

---

## 5. 配置来源说明

### 5.1 文件级重试策略参数

根据需求，文件级重试的策略参数来源于 **Proxy 推送并保存在本地的任务配置**：

**配置示例（AgentTaskConfig.yaml）**:
```yaml
taskId: 1001
status: RUNNING
sourceDir: /data/files
targetAgents:
  - agentId: agent-001
    ip: 192.168.1.100
    port: 9000

retryConfig:
  enabled: true              # 是否启用重试
  maxRetryCount: 10          # 最大重试次数
  intervalMin: 30           # 首次重试间隔（分钟）
  maxDelayHours: 2          # 最大退避时间（小时）
  backoffType: EXPONENTIAL  # 退避策略：EXPONENTIAL（指数）/ LINEAR（线性）/ FIXED（固定）
```

### 5.2 配置流转路径

```
┌─────────────┐    推送配置     ┌──────────────────┐
│   Proxy     │ ─────────────→ │ ConfigChangeListener │
│  (配置中心)  │               │  (接收并解析配置)    │
└─────────────┘               └────────┬─────────┘
                                       │
                              保存到本地磁盘
                                       ↓
                              ┌──────────────────┐
                              │ ConfigFileManager  │
                              │  (持久化到文件系统)  │
                              └────────┬─────────┘
                                       │
                              加载最新配置
                                       ↓
                              ┌──────────────────────────────┐
                              │ RetryAwareUploaderDecorator   │
                              │  .registerTaskConfig(taskId,  │
                              │    config)                    │
                              │  (注册到内存中的taskConfigMap) │
                              └────────┬─────────────────────┘
                                       │
                              查询重试策略
                                       ↓
                              ┌──────────────────────────────┐
                              │ FailedQueueScannerJob         │
                              │  (定时扫描失败队列)             │
                              │    ↓                           │
                              │  scanAndRetryFailedUploads()  │
                              │    ↓                           │
                              │  shouldRetryFailedFile(task)  │
                              │    ↓                           │
                              │  读取 taskConfigMap 获取策略    │
                              │  (maxRetryCount, interval...)  │
                              └──────────────────────────────┘
```

**关键点**:
1. **Proxy 是唯一配置源**: 所有重试策略参数由 Proxy 推送，不在本地硬编码
2. **支持热更新**: ConfigChangeListener 监听配置变更，实时更新内存中的策略
3. **持久化保障**: 配置保存到本地磁盘，Agent重启后可恢复
4. **降级处理**: 如果未找到任务配置，使用默认值（构造函数中设置的 maxRetries=10 等）

---

## 6. 影响范围评估

### 6.1 代码变更统计

| 类别 | 数量 | 说明 |
|------|------|------|
| **删除文件** | 1 个 | DelayedRetryJob.java (67 行) |
| **新增文件** | 1 个 | FileRetryScheduler.java (~90 行) |
| **修改文件** | 2 个 | BatchTaskSchedulerManager.java, AgentApplication.java |
| **删除代码** | ~88 行 | failTask / scheduleDelayedRetry / createTaskRunnableForRetry 等 |
| **新增代码** | ~100 行 | handleTaskFailure + FileRetryScheduler |
| **净变化** | **+12 行** | 新增文档注释和日志，实际逻辑更精简 |

### 6.2 功能影响矩阵

| 功能模块 | 影响程度 | 说明 |
|----------|----------|------|
| **Cron 任务调度** | 🟢 无影响 | 仍在 BatchTaskSchedulerManager 中运行 |
| **文件 P2P 传输** | 🟢 无影响 | 上传逻辑不变，仍使用 RetryAwareUploaderDecorator |
| **文件级重试** | 🟡 增强 | 独立 Quartz 实例，故障隔离，稳定性提升 |
| **任务级重试** | 🔴 已删除 | 改为直接标记失败（handleTaskFailure） |
| **进度上报** | 🟢 无影响 | ProgressReporter 工作机制不变 |
| **配置热更新** | 🟢 无影响 | ConfigChangeListener 仍然正常工作 |

### 6.3 测试影响评估

#### 需要修改的测试类：

| 测试类 | 影响程度 | 需要的操作 |
|--------|----------|------------|
| **BatchTaskSchedulerManagerIntegrationTest** | 🔴 高 | - 删除 `testFailTask_shouldCheckRetry`<br>- 删除 `testFailTask_shouldRescheduleWhenRetryAllowed`<br>- 删除 `testFailTask_shouldMarkFinalFailure`<br>- 新增 `testHandleTaskFailure_shouldLogError` |
| **P2PFileTransferTddTest** | 🔴 高 | - 删除 `testFailTask_shouldScheduleDelayedRetryWithQuartz`<br>- 可能需要调整其他相关测试 |
| **BatchTaskExecutionTddTest** | 🟡 中 | 验证任务失败时不再调用 failTask，改为调用 handleTaskFailure |
| **AgentApplicationIntegrationTest** | 🟢 低 | 验证启动时创建 FileRetryScheduler 实例 |

#### 可保持不变的测试类：

| 测试类 | 原因 |
|--------|------|
| FailedQueueScannerJob 相关测试 | 功能不变，只是运行在独立 Quartz 实例上 |
| RetryAwareUploaderDecorator 测试 | 内部逻辑不变 |
| FileScanner / ConfigFileManager 测试 | 与重试机制无关 |
| Upload/Download 相关测试 | 传输逻辑不受影响 |

---

## 7. 实施计划概要

### 7.1 实施阶段

**阶段 1: 创建 FileRetryScheduler 类**
- 新建 FileRetryScheduler.java
- 实现独立的 Quartz 实例管理
- 实现 FailedQueueScannerJob 注册逻辑
- 编写单元测试

**阶段 2: 重构 BatchTaskSchedulerManager**
- 新增 handleTaskFailure() 方法
- 修改 createTaskRunnable() 的 catch 块
- 删除 failTask() / scheduleDelayedRetry() / createTaskRunnableForRetry()
- 删除 scheduleFailedQueueScannerJob()
- 简化 shutdown() 方法
- 更新相关测试

**阶段 3: 更新 AgentApplication**
- 引入 FileRetryScheduler
- 修改初始化流程
- 更新 Shutdown hook
- 集成测试验证

**阶段 4: 清理和优化**
- 删除 DelayedRetryJob.java
- 更新代码注释和文档
- 运行完整测试套件
- 性能测试（验证独立性）

### 7.2 风险点和缓解措施

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| **现有测试失败** | 高 | 先更新测试，再修改实现；保持向后兼容的过渡期 |
| **遗漏调用点** | 中 | 全局搜索 `failTask` 和 `DelayedRetryJob` 的所有引用 |
| **Quartz 实例资源泄漏** | 中 | 确保 FileRetryScheduler.shutdown() 被正确调用 |
| **配置兼容性** | 低 | 保持 retryConfig 结构不变，确保旧配置仍可加载 |

---

## 8. 验收标准

### 8.1 功能验收

- [ ] **删除任务级重试**: 调用 `failTask()` 不再触发 DelayedRetryJob
- [ ] **文件级重试正常**: FailedQueueScannerJob 仍在独立 Quartz 实例上定时运行
- [ ] **任务失败处理**: 任务执行异常时仅记录日志，不重试
- [ ] **Quartz 隔离验证**: 两个调度器进程 ID 不同，互不影响
- [ ] **配置来源正确**: 文件级重试使用 Proxy 推送的 retryConfig 参数

### 8.2 质量验收

- [ ] **编译通过**: `mvn clean compile -pl agent` 成功
- [ ] **单元测试**: 所有原有测试通过（除了明确删除的测试）
- [ ] **新增测试**: FileRetryScheduler 的单元测试覆盖率 > 80%
- [ ] **集成测试**: AgentApplication 启动/关闭正常，无资源泄漏
- [ ] **代码质量**: 无警告，符合项目编码规范

### 8.3 性能验收

- [ ] **启动时间**: 新增 FileRetryScheduler 后启动时间增加 < 100ms
- [ ] **内存占用**: 新增一个 Quartz 实例，内存增加 < 10MB
- [ ] **CPU 开销**: FailedQueueScannerJob 扫描开销与之前持平

---

## 9. 总结

### 9.1 核心价值

1. **架构清晰性**: 任务调度与文件重试解耦，职责单一
2. **故障隔离**: 两个 Quartz 实例独立运行，提高可用性
3. **可维护性**: 删除 ~88 行复杂逻辑，新增清晰的模块化代码
4. **可扩展性**: 未来可为 FileRetryScheduler 添加更多 Job 类型（如清理 Job、统计 Job）

### 9.2 设计决策记录

| 决策点 | 选择 | 原因 |
|--------|------|------|
| 是否保留 failTask()? | 完全删除 | 避免命名混淆，handleTaskFailure 更准确 |
| FileRetryScheduler 位置 | 放在 batch.scheduler 包 | 与 FailedQueueScannerJob 同包，内聚性高 |
| Quartz 实例数量 | 2 个（任务+重试） | 平衡隔离性和资源开销 |
| 失败处理方式 | 仅记录日志 | 快速失败原则，避免无效重试浪费资源 |

---

## 附录 A: 关键代码片段索引

- **DelayedRetryJob.java** (待删除): [link](agent/src/main/java/com/cq/agent/batch/scheduler/DelayedRetryJob.java)
- **BatchTaskSchedulerManager.failTask()** (待删除): [L226-L251](agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java#L226-L251)
- **BatchTaskSchedulerManager.scheduleDelayedRetry()** (待删除): [L457-L487](agent/src/main/java/com/cq/agent/batch/scheduler/BatchTaskSchedulerManager.java#L457-L487)
- **FailedQueueScannerJob.java** (保留): [link](agent/src/main/java/com/cq/agent/batch/scheduler/FailedQueueScannerJob.java)
- **RetryAwareUploaderDecorator.scanAndRetryFailedUploads()** (保留): [L111-L151](agent/src/main/java/com/cq/agent/client/upload/RetryAwareUploaderDecorator.java#L111-L151)

---

**文档版本**: v1.0  
**最后更新**: 2026-05-13  
**批准人**: 用户（通过对话确认）
