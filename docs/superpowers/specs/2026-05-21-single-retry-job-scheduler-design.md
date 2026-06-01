# 单任务 Quartz Job 重试调度器设计文档

## 1. 背景与目标

### 1.1 当前问题

当前上传重试机制采用**批量扫描模式**：
- `FileRetryScheduler` 创建单一 Quartz Job（`FailedQueueScannerJob`）
- 定时触发 `scanAndRetryFailedUploads()` 批量扫描失败队列
- 每次扫描遍历所有 `.json` 文件，手动计算退避时间
- **问题**：无法精确控制每个任务的调度时间；手动实现退避策略冗余；效率低下

### 1.2 优化目标

- 每个失败任务封装为**独立的 Quartz Job**
- 自研 **BackoffStrategy** 支持 FIXED / LINEAR / EXPONENTIAL 三种退避策略
- 支持 **AgentTaskConfig.RetryConfig** 中的所有重试参数
- 解决**重复注册问题**（批量扫描时去重）
- 支持**宕机恢复**（重启后继续重试，处理长时间停机场景）
- 所有调度管理能力内聚到 `RetryAwareUploaderDecorator`，**不引入任何新的调度器类**
- **彻底移除 `FileRetryScheduler`、`FailedQueueScannerJob`、`FileRetrySchedulerProvider`**

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                 RetryAwareUploaderDecorator (增强)                   │
│                                                                     │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐  │
│  │ BackoffStrategy │   │ RetryScheduleCalc │   │  JobRegistry     │  │
│  │ (枚举)         │──→│ (工具类)          │──→│ (已注册Job去重)   │  │
│  └──────────────┘    └──────────────────┘    └──────────────────┘  │
│                              ↓                                      │
│                    ┌────────────────────────┐                       │
│                    │   SingleRetryJob       │                       │
│                    │   (每个任务独立执行)     │                       │
│                    └────────────────────────┘                       │
│                              ↑                                      │
│                    ┌────────────────────────┐                       │
│                    │  Scheduler (Quartz)     │                       │
│                    └────────────────────────┘                       │
└─────────────────────────────────────────────────────────────────────┘
```

> **设计原则**：所有调度管理能力（Job 注册/注销/启动恢复/Quartz 生命周期）全部内聚到现有的 `RetryAwareUploaderDecorator` 中。
>
> **被替代的组件**：
> - ~~`FileRetryScheduler`~~ → 功能迁移至 Decorator 内部
> - ~~`FailedQueueScannerJob`~~ → 替换为 `SingleRetryJob`（单任务模式）
> - ~~`FileRetrySchedulerProvider`~~ → 不再需要

---

## 3. 核心组件设计

### 3.1 BackoffStrategy（退避策略枚举）

```java
package com.cq.agent.batch.scheduler;

public enum BackoffStrategy {
    FIXED,      // 固定间隔: intervalMin 分钟
    LINEAR,     // 线性递增: intervalMin * (retryCount + 1) 分钟
    EXPONENTIAL // 指数递增: intervalMin * 2^retryCount 分钟
}
```

### 3.2 RetryScheduleCalculator（调度时间计算器）

职责：根据策略和当前重试次数，计算下次执行的延迟时间（毫秒）。

```java
package com.cq.agent.batch.scheduler;

public class RetryScheduleCalculator {
    
    public static long calculateNextDelayMs(BackoffStrategy strategy, 
                                             int retryCount, 
                                             int intervalMin) {
        long baseMs = intervalMin * 60_000L;
        switch (strategy) {
            case FIXED:
                return baseMs;
            case LINEAR:
                return baseMs * (retryCount + 1);
            case EXPONENTIAL:
                return baseMs * (long) Math.pow(2, retryCount);
            default:
                return baseMs;
        }
    }
    
    public static long calculateRecoveryDelayMs(BackoffStrategy strategy,
                                                 int retryCount,
                                                 int intervalMin,
                                                 long lastUpdateTimeMs) {
        long elapsed = System.currentTimeMillis() - lastUpdateTimeMs;
        long normalDelay = calculateNextDelayMs(strategy, retryCount, intervalMin);
        
        if (elapsed < normalDelay) {
            return normalDelay - elapsed;
        }
        
        return Math.min(intervalMin * 60_000L, normalDelay);
    }
}
```

**宕机恢复逻辑说明**：

| 场景 | 处理方式 |
|------|---------|
| 停机时间 < 正常延迟 | 补偿剩余时间后执行 |
| 停机时间 >= 正常延迟 | 立即执行（使用基础间隔作为缓冲） |

示例：`intervalMin=2min, LINEAR, retryCount=2`

```
正常流程: 上次更新10:00 → 下次 10:00 + 2*(2+1)min = 10:06

宕机恢复 (Agent在10:05停机, 12:00重启):
  已过期114分钟 >> 正常延迟6分钟
  → 返回 min(2min, 6min) = 2分钟后立即执行
  → 后续按正常退避策略继续
```

### 3.3 SingleRetryJob（单任务重试 Job）

每个失败任务对应一个独立的 Job 实例，仅执行一次。

```java
package com.cq.agent.batch.scheduler;

@DisallowConcurrentExecution
public class SingleRetryJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        UploadTask task = (UploadTask) dataMap.get("task");
        RetryAwareUploaderDecorator decorator = 
            (RetryAwareUploaderDecorator) dataMap.get("decorator");
        
        task.incrementRetryCount();
        
        try {
            UploadListener listener = decorator.createRetryListener(task);
            boolean submitted = decorator.resubmitTask(task, listener);
            
            if (submitted) {
                logger.info("重试提交成功: transferId={}, retryCount={}", 
                           task.getTransferId(), task.getRetryCount());
            }
        } catch (Exception e) {
            logger.error("重试执行异常: transferId={}", task.getTransferId(), e);
        }
    }
}
```

> 注意：`SingleRetryJob` 只负责**提交任务到工作队列**，不直接判断成功/失败。
> 成功/失败的判断和下次调度由 `RetryAwareUploaderDecorator` 的监听器回调处理。

### 3.4 JobRegistry（Job 注册表 - 去重机制）

防止同一任务被重复注册到 Quartz Scheduler。

```java
package com.cq.agent.batch.scheduler;

public class JobRegistry {
    
    private final ConcurrentHashMap<String, String> registeredJobs = new ConcurrentHashMap<>();
    
    public boolean register(String transferId, String jobKey) {
        return registeredJobs.putIfAbsent(transferId, jobKey) == null;
    }
    
    public void unregister(String transferId) {
        registeredJobs.remove(transferId);
    }
    
    public boolean isRegistered(String transferId) {
        return registeredJobs.containsKey(transferId);
    }
    
    public int size() {
        return registeredJobs.size();
    }
    
    public void clear() {
        registeredJobs.clear();
    }
}
```

### 3.5 RetryAwareUploaderDecorator 增强（唯一调度入口）

将原 `FileRetryScheduler` + `FailedQueueScannerJob` 的全部能力内聚到此类中，使其成为**唯一的调度管理入口**。

#### 新增字段

```java
// ===== Quartz 调度器（替代原有 FileRetryScheduler 的全部功能）=====
private Scheduler retryJobScheduler;
private JobRegistry jobRegistry;

// 在构造函数中初始化（替代原 FileRetryScheduler 的初始化逻辑）
this.retryJobScheduler = new StdSchedulerFactory().getScheduler();
this.retryJobScheduler.start();
this.jobRegistry = new JobRegistry();
```

#### 新增方法：registerRetryJob()

注册单个任务的重试 Job（替代原 `scanAndRetryFailedUploads()` 中的逐个重试逻辑）。

```java
/**
 * 注册单个任务的重试 Job（每个任务独立调度）
 */
public void registerRetryJob(UploadTask task, AgentTaskConfig config) {
    String transferId = task.getTransferId();
    
    if (jobRegistry.isRegistered(transferId)) {
        logger.debug("任务已注册重试Job，跳过: transferId={}", transferId);
        return;
    }
    
    if (config == null || config.getRetryConfig() == null || !config.getRetryConfig().isEnabled()) {
        logger.debug("任务未启用重试或配置不存在: taskId={}, transferId={}",
                task.getTaskId(), transferId);
        return;
    }
    
    try {
        RetryConfig retryConfig = config.getRetryConfig();
        BackoffStrategy strategy = BackoffStrategy.valueOf(
                retryConfig.getBackoffType().toUpperCase());
        
        int currentRetries = task.getRetryCount();
        long delayMs = RetryScheduleCalculator.calculateNextDelayMs(
                strategy, currentRetries, retryConfig.getIntervalMin());
        
        String jobKey = "retry-" + transferId;
        JobDetail job = JobBuilder.newJob(SingleRetryJob.class)
                .withIdentity(jobKey, "retry-group")
                .usingJobData("task", task)
                .usingJobData("decorator", this)
                .build();
        
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobKey + "-trigger", "retry-group")
                .startAt(new Date(System.currentTimeMillis() + delayMs))
                .build();
        
        retryJobScheduler.scheduleJob(job, trigger);
        jobRegistry.register(transferId, jobKey);
        
        logger.info("已注册重试Job: transferId={}, strategy={}, delay={}ms, retryCount={}/{}",
                transferId, strategy, delayMs, currentRetries + 1, retryConfig.getMaxRetryCount());
                
    } catch (Exception e) {
        logger.error("注册重试Job失败: transferId={}", transferId, e);
    }
}
```

#### 新增方法：unregisterRetryJob()

取消注册并删除 Job。

```java
/**
 * 取消注册并删除重试 Job
 */
public void unregisterRetryJob(String transferId) {
    String jobKey = null;
    for (Map.Entry<String, String> entry : jobRegistry.registeredJobs.entrySet()) {
        if (entry.getKey().equals(transferId)) {
            jobKey = entry.getValue();
            break;
        }
    }
    if (jobKey == null) {
        return;
    }
    
    try {
        JobKey key = new JobKey(jobKey, "retry-group");
        retryJobScheduler.deleteJob(key);
        jobRegistry.unregister(transferId);
        logger.info("已删除重试Job: transferId={}", transferId);
    } catch (SchedulerException e) {
        logger.error("删除重试Job失败: transferId={}", transferId, e);
    }
}
```

#### 新增方法：recoverFailedTasksOnStartup()

启动时恢复失败队列中的任务（替代原 `FileRetryScheduler` 启动时的恢复功能）。

```java
/**
 * 启动时恢复失败队列中的任务（Agent 重启后调用）
 * 替代原 FileRetryScheduler 的启动恢复逻辑
 */
public void recoverFailedTasksOnStartup() {
    List<UploadTask> failedTasks = recoverFailedTasks(uploadFailQueueDir);
    
    if (failedTasks.isEmpty()) {
        logger.info("无待恢复的失败任务");
        return;
    }
    
    int recovered = 0;
    int skipped = 0;
    
    for (UploadTask task : failedTasks) {
        try {
            if (jobRegistry.isRegistered(task.getTransferId())) {
                skipped++;
                continue;
            }
            
            Long taskId = task.getTaskId();
            AgentTaskConfig config = findTaskConfigForUpload(taskId);
            if (config == null || !config.getRetryConfig().isEnabled()) {
                continue;
            }
            
            registerRetryJob(task, config);
            recovered++;
            
        } catch (Exception e) {
            logger.error("恢复失败任务异常: transferId={}, error={}",
                    task.getTransferId(), e.getMessage());
        }
    }
    
    logger.info("启动恢复完成: 总任务数={}, 已恢复={}, 已跳过(已注册)={}, 当前Job数={}",
            failedTasks.size(), recovered, skipped, jobRegistry.size());
}
```

#### 修改 shutdown 方法

```java
/**
 * 关闭装饰者（包括 Quartz 调度器，替代原 FileRetryScheduler.shutdown()）
 */
public void shutdown() {
    try {
        if (retryJobScheduler != null && !retryJobScheduler.isShutdown()) {
            retryJobScheduler.shutdown(true);
        }
        if (jobRegistry != null) {
            jobRegistry.clear();
        }
    } catch (SchedulerException e) {
        logger.error("关闭Quartz调度器异常", e);
    }
}
```

#### 废弃的方法

以下方法将被标记为 `@Deprecated` 并最终删除：

| 原方法 | 替代方案 |
|--------|---------|
| `scanAndRetryFailedUploads()` | 由 `registerRetryJob()` 按需触发，无需定时扫描 |
| `shouldRetryFailedFile()` | 逻辑内聚到监听器回调中 |
| `retrySingleUploadFile()` | 由 `SingleRetryJob.execute()` 替代 |
| `calculateNextRetryTimeMs()` | 由 `RetryScheduleCalculator.calculateNextDelayMs()` 替代 |
| `reportNextRetryTime()` | 不再需要（Quartz 直接管理调度时间） |

---

## 4. 流程设计

### 4.1 任务失败 → 注册重试 Job

```
AgentUploader.processTask() 执行失败
    ↓
updateTaskStatus(FAILED)
    ↓
metaStore.moveToFailedQueue(transferId, failQueueDir)  // 写入 JSON
    ↓
listener.onError(message)
    ↓
BatchUploadListener.onError() 回调
    ↓
retryDecorator.registerRetryJob(task, config)  // 调用 Decorator 方法
    ↓
jobRegistry.isRegistered(transferId)?
    ├── 已注册 → 跳过（日志 debug）
    └── 未注册 → 创建 SingleRetryJob + Trigger
                  ├── 计算 delayMs (BackoffStrategy)
                  ├── quartzScheduler.scheduleJob()
                  └── jobRegistry.register()
```

### 4.2 SingleRetryJob 执行流程

```
SingleRetryJob.execute() 触发
    ↓
incrementRetryCount()  // retryCount++
    ↓
createRetryListener(task)
    ↓
resubmitTask(task, listener)  // 提交到工作队列
    ↓
┌──────────────────────────────────────┐
│ 监听器回调（异步）                     │
│                                      │
│ onComplete() → 成功                  │
│   → deleteFailedJsonFile()          │
│   → retryDecorator.unregisterRetryJob() │
│                                      │
│ onError() → 失败                     │
│   ├── retryCount < maxRetryCount     │
│   │   → saveTask() 更新状态          │
│   │   → retryDecorator.registerRetryJob() │ ← 注册下一次
│   │                                  │
│   └── retryCount >= maxRetryCount    │
│       → markAsFinalFailure()        │
│       → renameSourceFileToFailed()  │
│       → retryDecorator.unregisterRetryJob() │
└──────────────────────────────────────┘
```

### 4.3 Agent 重启恢复流程

```
AgentApplication.main() 启动
    ↓
初始化 RetryAwareUploaderDecorator（内部创建 Scheduler + JobRegistry）
    ↓
retryDecorator.recoverFailedTasksOnStartup()  // 替代原 FileRetryScheduler 启动
    ↓
遍历 fail queue 中所有 .json 文件
    ↓
对每个文件:
    1. 解析 UploadTask
    2. jobRegistry.isRegistered()? → 跳过
    3. findTaskConfigForUpload(taskId) → 获取配置
    4. registerRetryJob() → 注册 Job（内部自动计算恢复延迟）
    ↓
恢复完成，日志输出统计信息
```

---

## 5. 与现有代码的集成点

### 5.1 需要修改的文件

| 文件 | 修改内容 |
|------|---------|
| `RetryAwareUploaderDecorator.java` | 新增 `retryJobScheduler`、`jobRegistry` 字段；新增 `registerRetryJob()`、`unregisterRetryJob()`、`recoverFailedTasksOnStartup()` 方法；新增 `shutdown()` 方法；**废弃旧的重试扫描方法** |
| `BatchUploadListener.java` | 在 `onError()` 回调中调用 `retryDecorator.registerRetryJob()`；在 `onComplete()` 中调用 `retryDecorator.unregisterRetryJob()` |

### 5.2 需要新增的文件

| 文件 | 说明 |
|------|------|
| `BackoffStrategy.java` | 退避策略枚举 |
| `RetryScheduleCalculator.java` | 调度时间计算工具类 |
| `SingleRetryJob.java` | 单任务重试 Quartz Job |
| `JobRegistry.java` | Job 注册表（内存去重） |

### 5.3 需要删除的文件

| 文件 | 原因 |
|------|------|
| `FileRetryScheduler.java` | 全部功能迁移至 `RetryAwareUploaderDecorator` |
| `FailedQueueScannerJob.java` | 被 `SingleRetryJob` 替代（单任务模式） |
| `FileRetrySchedulerProvider.java` | 依赖 `FileRetryScheduler`，不再需要 |

### 5.4 需要修改的 DI 注入点

| 文件 | 修改内容 |
|------|---------|
| Guice Module | 移除 `FileRetrySchedulerProvider` 绑定；确保 `RetryAwareUploaderDecorator` 构造时初始化 Scheduler |
| `AgentApplication` 或启动配置 | 移除对 `FileRetryScheduler` 的引用；改为调用 `retryDecorator.recoverFailedTasksOnStartup()` |

---

## 6. 关键设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 调度管理归属 | **唯一入口** `RetryAwareUploaderDecorator` | 不引入任何新类，彻底消除 FileRetryScheduler 及其 Provider |
| Job 并发控制 | `@DisallowConcurrentExecution` | 同一任务不允许并发执行多次 |
| Job 存储 | RAMJobStore（保持现状） | Agent 是单节点部署，无需集群持久化 |
| 去重机制 | 内存 ConcurrentHashMap | 简单高效，重启后自动从 fail queue 重建 |
| 宕机恢复策略 | 重置为基础间隔立即执行 | 避免大量积压任务同时爆发 |
| fail queue JSON | 保留 | 作为持久化备份，支持重启恢复 |
| 旧代码处理 | **直接删除**而非标记 @Deprecated | FileRetryScheduler / FailedQueueScannerJob / FileRetrySchedulerProvider 彻底移除，避免遗留混淆 |

---

## 7. 测试计划

### 7.1 单元测试

| 测试用例 | 验证内容 |
|---------|---------|
| `RetryScheduleCalculatorTest` | FIXED/LINEAR/EXPONENTIAL 三种策略的延迟计算正确性；宕机恢复延迟计算 |
| `JobRegistryTest` | 并发注册去重、注销、查询、clear 功能 |
| `RetryAwareUploaderDecoratorTest` | registerRetryJob / unregisterRetryJob / recoverFailedTasksOnStartup / shutdown |

### 7.2 集成测试

| 测试场景 | 验证内容 |
|---------|---------|
| 正常重试流程 | 任务失败 → Job 注册 → 触发 → 重试 → 成功 → 清理 |
| 达到上限流程 | 多次失败 → 达到 maxRetryCount → FINAL_FAILURE |
| 宕机恢复 | 任务进行中停机 → 重启 → 自动恢复重试 |
| 重复注册保护 | 同一任务多次失败只注册一个 Job |
| 多任务并发 | 多个不同任务同时失败，各自独立调度互不影响 |
| 旧代码清理验证 | 确认无任何代码引用 FileRetryScheduler / FailedQueueScannerJob / FileRetrySchedulerProvider |

---

## 8. 时间线估算

1. 新增基础组件（BackoffStrategy、RetryScheduleCalculator、JobRegistry、SingleRetryJob）
2. 增强 RetryAwareUploaderDecorator（注入全部调度能力）
3. 修改 BatchUploadListener 回调
4. 删除 FileRetryScheduler / FailedQueueScannerJob / FileRetrySchedulerProvider
5. 修改 DI 配置（移除旧绑定）
6. 编写单元测试
7. 集成测试验证
