# 设计文档：AgentUploader 方法迁移至装饰者类

**日期**: 2026-05-13  
**状态**: 已批准 ✅  
**类型**: 代码重构

---

## 1. 背景与目标

### 1.1 当前问题

`AgentUploader` 类（688行）承担了过多职责，包含**非核心传输功能**：

- **任务管理查询**（5个方法）：分页查询、统计、清空等
- **重试相关**（2个方法）：重新提交任务、获取失败队列路径
- **监听器状态恢复**（5个方法）：填充恢复字段、恢复监听器、进度报告器管理等
- **配置访问**（1个方法）：获取配置对象

这些方法违反了**单一职责原则（SRP）**，导致：
- 类体积过大，难以维护
- 核心传输逻辑与非核心功能混杂
- 测试时需要 mock 不相关的依赖
- 装饰者模式的优势未充分发挥

### 1.2 重构目标

- ✅ **精简 AgentUploader**：仅保留核心传输方法（10个）
- ✅ **职责分离**：将非核心方法迁移到对应的装饰者类
- ✅ **符合装饰者模式**：每个装饰者专注于特定增强功能
- ✅ **向后兼容**：外部调用方式不变（通过装饰者链访问）
- ✅ **代码量优化**：预计减少 AgentUploader ~200 行

---

## 2. 架构设计

### 2.1 当前架构（修改前）

```
AgentUploader (688行) ❌ 职责过载
├── 核心传输 (~320行)
│   ├── processTask() - 核心任务处理
│   ├── uploadFile() - 公开入口
│   ├── uploadChunks() - 分块上传
│   └── ... 其他7个核心方法
│
├── 任务管理查询 (~30行)
│   ├── getInflightTasks(page, size)
│   ├── getAllInflightTasks()
│   ├── getInflightTasksCount()
│   ├── isInflightTasksEmpty()
│   └── clearAllInflightTasks()
│
├── 重试相关 (~25行)
│   ├── resubmitTask(task)
│   └── getFailedQueueDir()
│
├── 监听器状态恢复 (~90行)
│   ├── populateRestoreFields(task, listener)
│   ├── restoreListenerState(listener, task)
│   ├── getOrCreateGlobalProgressReporter()
│   ├── createTemporaryProgressReporter()
│   └── getRegistryServerUrl()
│
└── 配置访问 (~3行)
    └── getAgentConfig()
```

**问题**：
- 单一类承担4种不同职责
- 非核心方法占比 ~47%（200/428行，不含父类和接口实现）
- 违反 SRP 和接口隔离原则（ISP）

### 2.2 目标架构（修改后）✨

```
AgentUploader (精简后 ~480行) ✅ 仅核心传输
├── UploadService 接口实现
│   ├── processTask() - 核心任务处理
│   ├── uploadFile() - 公开入口
│   ├── uploadChunks() - 分块上传
│   ├── readChunk() - 读取文件块
│   ├── initUpload() - 初始化会话
│   ├── getUploadStatus() - 查询状态
│   ├── uploadChunk() - 上传单块
│   ├── mergeChunks() - 合并块
│   ├── verifyRemoteFileExists() - 验证文件
│   ├── updateTaskStatus() - 更新状态
│   └── getAgentConfig() - 配置访问（保留）
│
BatchListenerAwareAgentUploaderDecorator (增强 +90行)
├── 现有功能保留
├── + populateRestoreFields(task, listener)  ← 新增
├── + restoreListenerState(listener, task)   ← 新增
├── + getOrCreateGlobalProgressReporter()    ← 新增
├── + createTemporaryProgressReporter()      ← 新增
└── + getRegistryServerUrl()                 ← 新增

RetryAwareUploaderDecorator (增强 +95行)
├── 现有重试逻辑保留
├── + resubmitTask(task)                     ← 新增
├── + getFailedQueueDir()                    ← 新增
├── + getInflightTasks(page, size)           ← 新增
├── + getAllInflightTasks()                  ← 新增
├── + getInflightTasksCount()                ← 新增
├── + isInflightTasksEmpty()                 ← 新增
└── + clearAllInflightTasks()                ← 新增
```

**优势**：
- AgentUploader 减少 ~200 行（从 688→~488 行）
- 每个类职责单一清晰
- 符合装饰者模式和 SRP
- 易于测试和维护

---

## 3. 详细变更清单

### 3.1 从 AgentUploader 删除的方法（13个）

#### **类别 1：任务管理查询方法 → RetryAwareUploaderDecorator**

| 方法签名 | 原位置 | 行数 | 删除原因 |
|----------|--------|------|----------|
| `public List<UploadTask> getInflightTasks(int page, int pageSize)` | L493-L505 | 13 | 任务查询属于管理功能 |
| `public List<UploadTask> getAllInflightTasks()` | L507-L509 | 3 | 同上 |
| `public int getInflightTasksCount()` | L511-L513 | 3 | 同上 |
| `public boolean isInflightTasksEmpty()` | L515-L517 | 3 | 同上 |
| `void clearAllInflightTasks()` | L519-L521 | 3 | 同上 |

**迁移策略**：
- 这些方法都依赖 `inflightTasks` 字段（继承自 BaseAgentClient）
- 在 RetryAwareUploaderDecorator 中通过 `delegate.getInflightTasks()` 调用
- 或者直接访问 `delegate.inflightTasks`（如果可见性允许）

#### **类别 2：重试相关方法 → RetryAwareUploaderDecorator**

| 方法签名 | 原位置 | 行数 | 删除原因 |
|----------|--------|------|----------|
| `public void resubmitTask(UploadTask task)` | L566-L585 | 20 | 重试逻辑属于重试装饰者 |
| `public Path getFailedQueueDir()` | L590-L592 | 3 | 失败队列管理属于重试装饰者 |

**迁移策略**：
- `resubmitTask()` 直接操作 `taskQueue`（工作队列）
- 在装饰者中通过 `delegate.resubmitTask()` 或重新实现
- `getFailedQueueDir()` 访问 `failedQueueDir` 字段

#### **类别 3：监听器状态恢复方法 → BatchListenerAwareAgentUploaderDecorator**

| 方法签名 | 原位置 | 行数 | 删除原因 |
|----------|--------|------|----------|
| `private void populateRestoreFields(UploadTask task, UploadListener listener)` | L530-L555 | 26 | 监听器管理属于此装饰者 |
| `private void restoreListenerState(UploadListener listener, UploadTask task)` | L603-L644 | 42 | 同上 |
| `private ProgressReporter getOrCreateGlobalProgressReporter()` | L650-L659 | 10 | 进度报告属于此装饰者 |
| `private ProgressReporter createTemporaryProgressReporter()` | L664-L672 | 9 | 同上 |
| `private String getRegistryServerUrl()` | L677-L687 | 11 | 注册中心URL属于此装饰者 |

**迁移策略**：
- 这些方法都是 private，仅在内部调用
- 迁移后改为 public 或 package-private（供测试）
- 依赖 `globalProgressReporter` 和 `agentConfig` 字段
- 需要在 BatchListenerAwareAgentUploaderDecorator 中注入或访问这些依赖

---

### 3.2 修改的文件

#### **文件 1: AgentUploader.java**

**删除内容**：
- 13 个非核心方法（~200 行）
- 相关的 private 辅助方法

**保留内容**：
- 10 个核心传输方法
- `getAgentConfig()` 接口实现
- 构造函数
- 继承自 BaseAgentClient 的字段和方法

**预期结果**：
- 文件从 688 行减少到 ~488 行
- 减少比例：~29%

#### **文件 2: RetryAwareUploaderDecorator.java**

**新增内容**（+95行）：

```java
// ==================== 任务管理查询方法 ====================

/**
 * 分页查询进行中的上传任务
 */
public List<UploadTask> getInflightTasks(int page, int pageSize) {
    return delegate.getInflightTasks(page, pageSize);
}

/**
 * 获取所有进行中的上传任务
 */
public List<UploadTask> getAllInflightTasks() {
    return delegate.getAllInflightTasks();
}

/**
 * 获取进行中任务数量
 */
public int getInflightTasksCount() {
    return delegate.getInflightTasksCount();
}

/**
 * 检查是否有进行中的任务
 */
public boolean isInflightTasksEmpty() {
    return delegate.isInflightTasksEmpty();
}

/**
 * 清空所有进行中的任务
 */
public void clearAllInflightTasks() {
    delegate.clearAllInflightTasks();
}

// ==================== 重试相关方法 ====================

/**
 * 重新提交失败的上传任务
 */
public void resubmitTask(UploadTask task) {
    delegate.resubmitTask(task);
}

/**
 * 获取失败队列目录路径
 */
public Path getFailedQueueDir() {
    return delegate.getFailedQueueDir();
}
```

**注意**：
- 所有方法都是委托调用（delegation pattern）
- 保持与原方法相同的签名和行为
- 可在未来添加额外逻辑（如日志、监控、权限检查）

#### **文件 3: BatchListenerAwareAgentUploaderDecorator.java**

**新增内容**（+90行）：

```java
// ==================== 监听器状态恢复方法 ====================

/**
 * 填充监听器状态恢复字段到 UploadTask
 * 用于重启恢复场景
 */
public void populateRestoreFields(UploadTask task, UploadListener listener) {
    // 实现逻辑从 AgentUploader 迁移
}

/**
 * 恢复监听器状态（用于重启恢复场景）
 */
public void restoreListenerState(UploadListener listener, UploadTask task) {
    // 实现逻辑从 AgentUploader 迁移
}

/**
 * 获取或创建全局 ProgressReporter 实例
 */
private ProgressReporter getOrCreateGlobalProgressReporter() {
    // 实现逻辑从 AgentUploader 迁移
}

/**
 * 创建临时的 ProgressReporter 实例
 */
private ProgressReporter createTemporaryProgressReporter() {
    // 实现逻辑从 AgentUploader 迁移
}

/**
 * 获取注册中心/代理服务器URL
 */
private String getRegistryServerUrl() {
    // 实现逻辑从 AgentUploader 迁移
}
```

**依赖处理**：
- `agentConfig`: 通过构造函数注入或从 delegate.getAgentConfig() 获取
- `globalProgressReporter`: 作为字段存储在装饰者中
- `registryServerUrls`: 通过 agentConfig 访问

---

## 4. 数据流与调用链

### 4.1 修改前的调用方式

```java
// 外部代码直接调用 AgentUploader 的所有方法
AgentUploader uploader = new AgentUploader(config);

uploader.uploadFile(localPath, remotePath, listener);  // 核心方法 ✅
uploader.getInflightTasks(1, 10);                       // 非核心方法 ❌
uploader.resubmitTask(failedTask);                      // 非核心方法 ❌
uploader.populateRestoreFields(task, listener);         // 非核心方法 ❌（但这是private）
```

### 4.2 修改后的调用方式

```java
// 外部代码通过装饰者链的最外层访问所有功能
RetryAwareUploaderDecorator retryAwareUploader = 
    new RetryAwareUploaderDecorator(
        new BatchListenerAwareAgentUploaderDecorator(
            new AgentUploader(config)
        ),
        maxRetries,
        initialDelayMs,
        maxDelayMs
    );

retryAwareUploader.uploadFile(localPath, remotePath, listener);     // 核心 ✅
retryAwareUploader.getInflightTasks(1, 10);                        // 管理 ✅（委托给RetryAware）
retryAwareUploader.resubmitTask(failedTask);                        // 重试 ✅（委托给RetryAware）
retryAwareUploader.populateRestoreFields(task, listener);          // 恢复 ✅（委托给BatchListener）
```

**关键点**：
- 外部调用方式不变（仍然通过同一个引用调用）
- 内部委托链自动路由到正确的实现
- 符合迪米特法则（LoD）：调用者无需知道具体是哪个类实现

---

## 5. 影响范围评估

### 5.1 代码变更统计

| 类别 | 数量 | 说明 |
|------|------|------|
| **修改文件** | 3 | AgentUploader, RetryAwareUploaderDecorator, BatchListenerAwareAgentUploaderDecorator |
| **删除代码** | ~200 行 | AgentUploader 中的13个非核心方法 |
| **新增代码** | ~185 行 | 两个装饰者中的新方法 |
| **净变化** | **-15 行** | 代码更精简 ✅ |

### 5.2 功能影响矩阵

| 功能模块 | 影响程度 | 说明 |
|----------|----------|------|
| **核心文件传输** | 🟢 无影响 | processTask/uploadFile等方法不动 |
| **任务查询API** | 🟡 中 | 内部实现迁移，外部调用不变 |
| **失败重试机制** | 🟡 中 | resubmitTask迁移到RetryAware |
| **监听器状态恢复** | 🟡 中 | 恢复方法迁移到BatchListener |
| **进度上报** | 🟢 无影响 | ProgressReporter逻辑跟随迁移 |
| **单元测试** | 🔴 高 | 需要更新测试用例的调用目标 |

### 5.3 测试影响

#### 需要更新的测试类：

| 测试类 | 影响程度 | 更新内容 |
|--------|----------|----------|
| **AgentUploaderTest** | 🔴 高 | 删除对迁移方法的测试，改为测试装饰者 |
| **RetryAwareUploaderDecoratorTest** | 🟡 中 | 新增7个方法的测试 |
| **BatchListenerAwareAgentUploaderDecoratorTest** | 🟡 中 | 新增5个方法的测试 |
| **集成测试** | 🟢 低 | 如果通过装饰者链调用则无需改动 |

---

## 6. 实施步骤概要

### 阶段 1: 准备工作
1. 备份当前代码（git commit）
2. 运行现有测试套件确保基线正常
3. 分析所有调用点，确保无遗漏

### 阶段 2: 迁移到 RetryAwareUploaderDecorator
1. 添加7个任务管理和重试方法的委托实现
2. 编写单元测试验证行为一致
3. 提交代码

### 阶段 3: 迁移到 BatchListenerAwareAgentUploaderDecorator
1. 添加5个监听器状态恢复方法
2. 处理依赖注入（agentConfig, globalProgressReporter）
3. 将 private 改为 public/package-private
4. 编写单元测试
5. 提交代码

### 阶段 4: 清理 AgentUploader
1. 删除13个已迁移的方法
2. 删除不再使用的 import
3. 编译验证
4. 运行全量测试
5. 提交最终版本

### 阶段 5: 验证与文档
1. 手动验证功能正常（启动Agent应用）
2. 更新代码注释和文档（如有）
3. 性能测试（确保无回归）

---

## 7. 风险点与缓解措施

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| **循环依赖** | 高 | 确保装饰者不相互依赖，只依赖被装饰对象 |
| **可见性问题** | 中 | private方法迁移后需调整访问修饰符 |
| **性能开销** | 低 | 委托调用增加的方法调用栈深度可忽略 |
| **测试回归** | 高 | 先迁移并测试，再删除原方法 |
| **遗漏调用点** | 中 | 全局搜索所有方法引用，逐一确认 |

---

## 8. 成功标准

### 功能验收
- [ ] AgentUploader 仅包含10个核心传输方法 + getAgentConfig()
- [ ] RetryAwareUploaderDecorator 包含7个新增方法（委托调用）
- [ ] BatchListenerAwareAgentUploaderDecorator 包含5个新增方法
- [ ] 所有原有功能通过装饰者链可正常访问
- [ ] 编译无错误、无警告（除已知警告）

### 质量验收
- [ ] `mvn clean test -pl agent` 全部通过
- [ ] `mvn clean compile -pl agent` 成功
- [ ] 代码符合项目规范
- [ ] Git 提交信息规范

### 架构验收
- [ ] 每个类遵循单一职责原则
- [ ] 装饰者模式正确实现
- [ ] 向后兼容（外部调用方式不变）
- [ ] AgentUploader 行数 < 500 行

---

## 附录 A: 方法迁移对照表

| 原方法名 | 原位置 | 目标装饰者 | 访问级别变更 |
|----------|--------|-----------|-------------|
| `getInflightTasks(page, size)` | AgentUploader | RetryAwareUploaderDecorator | public → public (委托) |
| `getAllInflightTasks()` | AgentUploader | RetryAwareUploaderDecorator | public → public (委托) |
| `getInflightTasksCount()` | AgentUploader | RetryAwareUploaderDecorator | public → public (委托) |
| `isInflightTasksEmpty()` | AgentUploader | RetryAwareUploaderDecorator | public → public (委托) |
| `clearAllInflightTasks()` | AgentUploader | RetryAwareUploaderDecorator | package → public (委托) |
| `resubmitTask(task)` | AgentUploader | RetryAwareUploaderDecorator | public → public (委托) |
| `getFailedQueueDir()` | AgentUploader | RetryAwareUploaderDecorator | public → public (委托) |
| `populateRestoreFields(task, listener)` | AgentUploader | BatchListenerAware... | **private → public** |
| `restoreListenerState(listener, task)` | AgentUploader | BatchListenerAware... | **private → public** |
| `getOrCreateGlobalProgressReporter()` | AgentUploader | BatchListenerAware... | **private → package-private** |
| `createTemporaryProgressReporter()` | AgentUploader | BatchListenerAware... | **private → private** |
| `getRegistryServerUrl()` | AgentUploader | BatchListenerAware... | **private → private** |

---

**文档版本**: v1.0  
**最后更新**: 2026-05-13  
**批准人**: 用户（通过对话确认）
