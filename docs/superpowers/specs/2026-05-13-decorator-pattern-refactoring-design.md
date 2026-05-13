# 装饰者模式重构：AgentUploader/AgentDownloader 监听器管理优化

**日期**: 2026-05-13
**状态**: ✅ 已批准
**方案**: 方案A - 单一统一装饰者

---

## 1. 背景与目标

### 1.1 当前问题

`AgentUploader` 和 `AgentDownloader` 类职责过重，混合了多种功能：

| 职责类别 | 占比 | 问题 |
|---------|------|------|
| **核心传输** | ~40% | 分块上传/下载、HTTP调用、文件IO |
| **监听器管理** | ~25% | 创建、缓存、恢复状态、ProgressReporter集成 |
| **任务队列** | ~20% | BaseAgentClient的队列、持久化、重试 |

**具体问题**：
1. **代码耦合度高**：监听器恢复逻辑与核心传输逻辑混合在 `processTask()` 中
2. **难以测试**：无法单独测试监听器管理逻辑（必须依赖完整传输流程）
3. **难以扩展**：添加新的监听器功能需要修改核心类
4. **职责不清**：一个类承担了太多关注点

### 1.2 重构目标

使用**装饰者模式（Decorator Pattern）**将监听器管理逻辑从核心传输类中分离：

✅ **目标1**：让 `AgentUploader/AgentDownloader` 只保留最核心的传输功能
✅ **目标2**：创建 `BatchListenerAware*Decorator` 装饰者处理所有监听器相关逻辑
✅ **目标3**：保持向后兼容，不影响现有功能
✅ **目标4**：提高可测试性和可维护性

---

## 2. 设计方案

### 2.1 架构图

```
┌─────────────────────────────────────────────┐
│           AgentApplication                   │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ AgentUploader (核心类)               │    │
│  │ ├─ processTask()                    │    │
│  │ ├─ uploadChunks() / downloadChunks()│    │
│  │ ├─ HTTP调用 (init/upload/merge)     │    │
│  │ └─ 文件IO                           │    │
│  └──────────────┬──────────────────────┘    │
│                 │ 装饰                        │
│  ┌──────────────▼──────────────────────┐    │
│  │ BatchListenerAwareAgentUploader      │    │
│  │   Decorator                         │    │
│  │                                     │    │
│  │  职责：                              │    │
│  │  ├─ createListenerInstance()         │    │
│  │  ├─ restoreListenerState()          │    │
│  │  ├─ populateRestoreFields()         │    │
│  │  ├─ setGlobalProgressReporter()     │    │
│  │  ├─ getOrCreateGlobalProgressReporter()│  │
│  │  └─ listenerCache 管理              │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  同样适用于 AgentDownloader:                │
│  ┌─────────────────────────────────────┐    │
│  │ AgentDownloader (核心类)             │    │
│  └──────────────┬──────────────────────┘    │
│                 │ 装饰                        │
│  ┌──────────────▼──────────────────────┐    │
│  │ BatchListenerAwareAgentDownloader    │    │
│  │   Decorator                         │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

### 2.2 新增文件结构

```
agent/src/main/java/com/cq/agent/client/
├── upload/
│   ├── AgentUploader.java                          # ✏️ 精简后的核心类
│   └── BatchListenerAwareAgentUploaderDecorator.java  # 🆕 上传装饰者
│
├── download/
│   ├── AgentDownloader.java                        # ✏️ 精简后的核心类
│   └── BatchListenerAwareAgentDownloaderDecorator.java # 🆕 下载装饰者
│
└── decorator/                                      # 🆕 可选：通用装饰者基类
    └── AbstractBatchListenerAwareDecorator.java    # 🆕 抽象基类（可选）
```

---

## 3. 详细实现设计

### 3.1 核心类精简（AgentUploader）

#### 移除的方法（迁移到装饰者）：

| 方法名 | 功能 | 原始位置 |
|--------|------|---------|
| `restoreListenerState()` | 恢复监听器状态 | :569-610 |
| `populateRestoreFields()` | 填充恢复字段 | :530-565 |
| `setGlobalProgressReporter()` | 设置全局PR | :61-65 |
| `getOrCreateGlobalProgressReporter()` | 获取/创建PR | :613-630 |
| `createTemporaryProgressReporter()` | 创建临时PR | :633-640 |
| `getRegistryServerUrl()` | 获取注册中心URL | :643-650 |
| `globalProgressReporter` 字段 | 全局PR实例 | :42 |

#### 精简后的 AgentUploader：

```java
/**
 * Agent上传客户端 - 核心传输功能（精简版）
 * 
 * 职责：
 * - 文件分块上传
 * - HTTP API调用
 * - 文件IO操作
 * 
 * 注意：监听器管理已移至 BatchListenerAwareAgentUploaderDecorator
 */
public class AgentUploader extends BaseAgentClient<UploadTask, UploadListener> {

    private static final Logger logger = LoggerFactory.getLogger(AgentUploader.class);
    
    private final AgentConfig agentConfig;

    public AgentUploader(AgentConfig agentConfig) {
        super(agentConfig, ...);  // 保持原有构造函数
        this.agentConfig = agentConfig;
    }

    @Override
    protected void processTask(UploadTask task) {
        // 核心传输流程（不包含监听器恢复逻辑）
        // 1. 文件验证
        // 2. 初始化上传
        // 3. 分块上传
        // 4. 合并分块
        // 5. 验证文件
    }

    public boolean uploadFile(String localFilePath, String remoteTargetInfo, UploadListener listener) {
        // 上传入口（不包含 populateRestoreFields）
    }
    
    // 其他核心方法保持不变...
}
```

### 3.2 装饰者实现（BatchListenerAwareAgentUploaderDecorator）

```java
/**
 * 批量任务监听器感知的上传装饰者
 * 
 * 使用装饰者模式为 AgentUploader 添加监听器管理能力：
 * - 监听器的反射创建与缓存
 * - 重启恢复场景的状态重建
 * - ProgressReporter 集成
 * 
 * 设计原则：
 * - 单一职责：只关注监听器生命周期管理
 * - 开闭原则：可以轻松添加新功能而不修改原始类
 * - 依赖倒置：依赖抽象而非具体实现
 */
public class BatchListenerAwareAgentUploaderDecorator {

    private static final Logger logger = LoggerFactory.getLogger(
        BatchListenerAwareAgentUploaderDecorator.class);

    /** 委托对象（核心上传客户端） */
    private final AgentUploader delegate;
    
    /** 全局ProgressReporter实例 */
    private ProgressReporter globalProgressReporter;

    /**
     * 构造函数
     * @param delegate 被装饰的核心AgentUploader实例
     */
    public BatchListenerAwareAgentUploaderDecorator(AgentUploader delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为null");
    }

    // ==================== 公开API ====================

    /**
     * 包装后的上传方法（自动处理监听器相关逻辑）
     * 
     * @param localFilePath 本地文件路径
     * @param remoteTargetInfo 远程目标信息
     * @param listener 上传监听器（可能是BatchUploadListener）
     * @return 是否成功提交到队列
     */
    public boolean uploadFileWithListenerSupport(
            String localFilePath, String remoteTargetInfo, UploadListener listener) {
        
        boolean result = delegate.uploadFile(localFilePath, remoteTargetInfo, listener);
        
        if (result && listener != null) {
            populateRestoreFieldsFromListener(listener);
        }
        
        return result;
    }

    /**
     * 处理带监听器的任务（从processTask中抽离）
     * 
     * @param task 上传任务
     * @param taskKey 任务唯一标识
     */
    public void handleListenerForTask(UploadTask task, String taskKey) {
        if (task == null || taskKey == null) return;
        
        String listenerClassName = task.getListenerClassName();
        if (listenerClassName == null) return;
        
        Map<String, UploadListener> listenerCache = getListenerCache();
        if (listenerCache.containsKey(taskKey)) return;
        
        try {
            UploadListener listener = createListenerInstance(listenerClassName, UploadListener.class);
            if (listener != null) {
                restoreListenerStateIfNeeded(listener, task);
                listenerCache.put(taskKey, listener);
            }
        } catch (Exception e) {
            logger.error("❌ 创建或恢复监听器失败: className={}", listenerClassName, e);
        }
    }

    // ==================== ProgressReporter 管理 ====================

    public void setGlobalProgressReporter(ProgressReporter progressReporter) {
        this.globalProgressReporter = progressReporter;
        logger.info("✅ 已设置全局ProgressReporter: {}", 
            progressReporter != null ? "已配置" : "null");
    }

    private ProgressReporter getOrCreateGlobalProgressReporter() {
        if (globalProgressReporter != null) {
            return globalProgressReporter;
        }
        
        logger.warn("⚠️ globalProgressReporter未设置，将创建临时实例");
        return createTemporaryProgressReporter();
    }

    private ProgressReporter createTemporaryProgressReporter() {
        String proxyUrl = getRegistryServerUrl();
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return new ProgressReporter(null);
        }
        return new ProgressReporter(proxyUrl);
    }

    private String getRegistryServerUrl() {
        List<String> urls = delegate.getAgentConfig().getRegistryServerUrls();
        return (urls != null && !urls.isEmpty()) ? urls.get(0) : null;
    }

    // ==================== 监听器状态恢复 ====================

    private void restoreListenerStateIfNeeded(UploadListener listener, UploadTask task) {
        if (!(listener instanceof BatchUploadListener batchListener)) return;
        if (!batchListener.isRestored()) return;  // 有参构造函数创建的跳过
        
        Long subtaskId = task.getSubtaskId();
        if (subtaskId == null) {
            logger.warn("⚠️ subtaskId为null，无法恢复监听器");
            return;
        }
        
        try {
            ProgressReporter reporter = getOrCreateGlobalProgressReporter();
            
            batchListener.restoreState(
                task.getTaskId(),
                subtaskId,
                task.getLocalFilePath(),
                task.getFileName(),
                task.getFileSize(),
                reporter
            );
            
            logger.info("✅ 上传监听器状态已恢复: subtaskId={}, file={}", 
                subtaskId, task.getFileName());
                
        } catch (Exception e) {
            logger.error("❌ 恢复监听器状态失败: {}", e.getMessage());
        }
    }

    private void populateRestoreFieldsFromListener(UploadListener listener) {
        if (!(listener instanceof BatchUploadListener batchListener)) return;
        
        try {
            logger.debug("✅ 从监听器提取恢复字段: taskId={}, subtaskId={}, fileName={}",
                batchListener.getTaskId(), 
                batchListener.getSubtaskId(),
                batchListener.getFileName());
        } catch (Exception e) {
            logger.warn("⚠️ 提取恢复字段失败: {}", e.getMessage());
        }
    }

    // ==================== 委托方法 ====================
    
    public AgentUploader getDelegate() { return delegate; }
    
    @SuppressWarnings("unchecked")
    private Map<String, UploadListener> getListenerCache() {
        try {
            Field field = BaseAgentClient.class.getDeclaredField("listenerCache");
            field.setAccessible(true);
            return (Map<String, UploadListener>) field.get(delegate);
        } catch (Exception e) {
            throw new RuntimeException("无法访问listenerCache", e);
        }
    }
}
```

### 3.3 AgentDownloader 的对称实现

```java
/**
 * 批量任务监听器感知的下载装饰者
 * 
 * 与 BatchListenerAwareAgentUploaderDecorator 对称，
 * 为 AgentDownloader 添加监听器管理能力。
 */
public class BatchListenerAwareAgentDownloaderDecorator {
    
    private final AgentDownloader delegate;
    private ProgressReporter globalProgressReporter;
    
    public BatchListenerAwareAgentDownloaderDecorator(AgentDownloader delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }
    
    // 类似于上传装饰者的实现...
    // （结构相同，只是委托对象类型不同）
}
```

---

## 4. 集成方式

### 4.1 AgentApplication 改动

```java
// ====== 重构前 ======
AgentUploader agentUploader = new AgentUploader(config);
agentUploader.init();
agentUploader.setGlobalProgressReporter(progressReporter);

// ====== 重构后 ======
// 1. 创建核心上传客户端
AgentUploader coreUploader = new AgentUploader(config);
coreUploader.init();

// 2. 用装饰者包装
BatchListenerAwareAgentUploaderDecorator uploader = 
    new BatchListenerAwareAgentUploaderDecorator(coreUploader);
uploader.setGlobalProgressReporter(progressReporter);

// 3. 后续使用uploader（替代原来的agentUploader）
taskSchedulerManager.setAgentUploader(uploader);
retryManager.init(..., uploader, ...);

// 同样处理下载...
AgentDownloader coreDownloader = new AgentDownloader(config);
coreDownloader.init();
BatchListenerAwareAgentDownloaderDecorator downloader = 
    new BatchListenerAwareAgentDownloaderDecorator(coreDownloader);
```

### 4.2 向后兼容性保证

**关键决策**：装饰者是否需要继承原始类？

**推荐方案**：**不继承，使用组合**

理由：
1. ✅ 更符合装饰者模式的标准定义
2. ✅ 避免暴露不必要的方法
3. ✅ 接口更清晰

**如果需要完全兼容**（外部代码直接使用 `agentUploader.uploadFile()`），可以让装饰者实现相同的接口或继承。

---

## 5. 数据流对比

### 5.1 重构前的数据流 ❌

```
AgentUploader.processTask(task)
  │
  ├─ [混合] createListenerInstance()
  ├─ [混合] restoreListenerState()
  ├─ [核心] 文件验证
  ├─ [核心] initUpload()
  ├─ [核心] uploadChunks()
  │     ├─ [核心] 分块读取
  │     ├─ [核心] HTTP POST
  │     └─ [核心] 进度回调 → listener.onProgress()
  ├─ [核心] mergeChunks()
  ├─ [核心] verifyDownload()
  └─ [核心] onListenerSuccess/Error()

问题：监听器管理与核心传输深度耦合
```

### 5.2 重构后的数据流 ✅

```
BatchListenerAwareAgentUploaderDecorator.handleListenerForTask(task)
  │
  ├─ [装饰者] createListenerInstance()
  ├─ [装饰者] restoreListenerStateIfNeeded()
  └─ [装饰者] 缓存到 listenerMap
  
  ↓ 委托给核心

AgentUploader.processTask(task)
  │
  ├─ [核心] 文件验证
  ├─ [核心] initUpload()
  ├─ [核心] uploadChunks()
  │     ├─ [核心] 分块读取
  │     ├─ [核心] HTTP POST
  │     └─ [核心] 进度回调 → listener.onProgress()
  ├─ [核心] mergeChunks()
  ├─ [核心] verifyDownload()
  └─ [核心] onListenerSuccess/Error()

优势：职责清晰分离，独立可测试
```

---

## 6. 测试策略

### 6.1 单元测试分层

#### 测试1：核心传输层（无需监听器）

```java
@Test
void testCoreUploadWithoutListener() {
    AgentUploader uploader = new AgentUploader(mockConfig);
    
    // 直接测试核心传输功能
    assertTrue(uploader.uploadFile(localPath, targetInfo, null));
    
    // 验证分块上传逻辑
    verify(mockHttpClient).post(anyString(), any());
}
```

#### 测试2：装饰者层（使用Mock）

```java
@Test
void testListenerRestoreLogic() {
    AgentUploader mockUploader = mock(AgentUploader.class);
    BatchListenerAwareAgentUploaderDecorator decorator = 
        new BatchListenerAwareAgentUploaderDecorator(mockUploader);
    
    decorator.setGlobalProgressReporter(mockReporter);
    
    UploadTask task = createMockTask();  // 包含恢复字段
    BatchUploadListener listener = new BatchUploadListener();  // 无参构造
    
    decorator.restoreListenerStateIfNeeded(listener, task);
    
    assertTrue(listener.isRestored());
    assertNotNull(listener.getSubtaskId());
}
```

#### 测试3：集成测试（完整流程）

```java
@Test
void testFullUploadWithBatchListener() {
    AgentUploader core = new AgentUploader(realConfig);
    BatchListenerAwareAgentUploaderDecorator decorated = 
        new BatchListenerAwareAgentUploaderDecorator(core);
    decorated.setGlobalProgressReporter(new ProgressReporter(registryUrl));
    
    BatchUploadListener listener = new BatchUploadListener(
        taskId, scannedFile, config, decorated.getGlobalProgressReporter()
    );
    
    assertTrue(decorated.uploadFileWithListenerSupport(path, target, listener));
    
    // 验证恢复字段已填充
    UploadTask savedTask = loadTaskFromStore(transferId);
    assertNotNull(savedTask.getSubtaskId());
    assertEquals(scannedFile.getFileName(), savedTask.getFileName());
}
```

### 6.2 测试覆盖率要求

- 核心传输层：≥ 90%
- 装饰者层：≥ 95%（逻辑相对简单）
- 集成测试：关键路径100%

---

## 7. 迁移计划

### 7.1 Phase 1：创建装饰者类（无破坏性）

1. 创建 `BatchListenerAwareAgentUploaderDecorator.java`
2. 创建 `BatchListenerAwareAgentDownloaderDecorator.java`
3. 实现所有监听器管理方法
4. **不修改原始类**
5. 编写单元测试

### 7.2 Phase 2：集成装饰者（渐进式）

1. 在 `AgentApplication` 中同时创建核心类和装饰者
2. 将装饰者注入到 `BatchTaskSchedulerManager` 和 `RetryManager`
3. 验证现有功能不受影响
4. 运行完整测试套件

### 7.3 Phase 3：清理原始类（可选）

1. 从 `AgentUploader/AgentDownloader` 中删除已迁移的方法
2. 更新注释说明职责边界
3. 最终编译和测试

---

## 8. 风险与缓解措施

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 反射访问私有字段 | 性能/安全 | 使用标准getter或包级可见性 |
| 向后兼容性 | 外部代码破坏 | 提供适配器或保持接口一致 |
| 测试覆盖不足 | 回归缺陷 | 分层测试 + 高覆盖率要求 |
| 过度工程 | 维护成本高 | 严格遵循最小范围原则 |

---

## 9. 成功标准

✅ **功能完整性**：所有现有功能正常工作  
✅ **代码质量**：核心类减少25%+代码量  
✅ **可测试性**：可以独立测试监听器管理逻辑  
✅ **性能**：无明显性能下降（<5%）  
✅ **可维护性**：新增监听器功能无需修改核心类  

---

## 10. 总结

本设计方案通过**装饰者模式**优雅地解决了 `AgentUploader/AgentDownloader` 职责过重的问题：

- **核心收益**：职责分离、易测试、易扩展
- **实施风险**：低（渐进式迁移，不影响现有功能）
- **未来扩展**：可轻松添加日志装饰者、监控装饰者等

下一步：调用 `writing-plans` skill 创建详细实施计划。
