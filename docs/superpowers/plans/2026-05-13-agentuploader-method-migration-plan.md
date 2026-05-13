# AgentUploader 方法迁移至装饰者类实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 AgentUploader 中的 13 个非核心方法迁移到 RetryAwareUploaderDecorator（7个）和 BatchListenerAwareAgentUploaderDecorator（5个），精简 AgentUploader 从 688 行到 ~488 行。

**Architecture:** 使用装饰者模式（Decorator Pattern）的委托机制，将任务管理查询、重试相关、监听器状态恢复等非核心功能从核心传输类中分离，提升代码的内聚性和可维护性。

**Tech Stack:** Java 21, JUnit 5, Mockito, SLF4J

---

## File Structure Map

### 修改文件
- `agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java` - 删除13个非核心方法
- `agent/src/main/java/com/cq/agent/client/upload/RetryAwareUploaderDecorator.java` - 新增7个方法
- `agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java` - 新增5个方法

### 测试文件（可能需要更新）
- `agent/src/test/java/com/cq/agent/client/upload/AgentUploaderTest.java`
- `agent/src/test/java/com/cq/agent/client/upload/RetryAwareUploaderDecoratorTest.java`
- `agent/src/test/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecoratorTest.java`

---

## Task 1: 在 RetryAwareUploaderDecorator 中新增7个委托方法并编写测试

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/upload/RetryAwareUploaderDecorator.java`
- Create/Modify: `agent/src/test/java/com/cq/agent/client/upload/RetryAwareUploaderDecoratorTest.java`

**目标**: 添加任务管理查询和重试相关的7个方法的委托实现

- [ ] **Step 1: 编写测试用例验证新方法的行为**

在 `RetryAwareUploaderDecoratorTest.java` 中添加：

```java
@Test
@DisplayName("9.1 [迁移] getInflightTasks 应委托给被装饰对象")
void testGetInflightTasks_shouldDelegate() {
    List<UploadTask> mockTasks = Arrays.asList(mockUploadTask);
    when(delegate.getInflightTasks(1, 10)).thenReturn(mockTasks);

    List<UploadTask> result = retryAwareUploader.getInflightTasks(1, 10);

    assertEquals(mockTasks, result);
    verify(delegate).getInflightTasks(1, 10);
}

@Test
@DisplayName("9.2 [迁移] getAllInflightTasks 应委托给被装饰对象")
void testGetAllInflightTasks_shouldDelegate() {
    List<UploadTask> mockTasks = Arrays.asList(mockUploadTask);
    when(delegate.getAllInflightTasks()).thenReturn(mockTasks);

    List<UploadTask> result = retryAwareUploader.getAllInflightTasks();

    assertEquals(mockTasks, result);
    verify(delegate).getAllInflightTasks();
}

@Test
@DisplayName("9.3 [迁移] getInflightTasksCount 应委托给被装饰对象")
void testGetInflightTasksCount_shouldDelegate() {
    when(delegate.getInflightTasksCount()).thenReturn(5);

    int count = retryAwareUploader.getInflightTasksCount();

    assertEquals(5, count);
    verify(delegate).getInflightTasksCount();
}

@Test
@DisplayName("9.4 [迁移] isInflightTasksEmpty 应委托给被装饰对象")
void testIsInflightTasksEmpty_shouldDelegate() {
    when(delegate.isInflightTasksEmpty()).thenReturn(true);

    boolean isEmpty = retryAwareUploader.isInflightTasksEmpty();

    assertTrue(isEmpty);
    verify(delegate).isInflightTasksEmpty();
}

@Test
@DisplayName("9.5 [迁移] clearAllInflightTasks 应委托给被装饰对象")
void testClearAllInflightTasks_shouldDelegate() {
    retryAwareUploader.clearAllInflightTasks();

    verify(delegate).clearAllInflightTasks();
}

@Test
@DisplayName("9.6 [迁移] resubmitTask 应委托给被装饰对象")
void testResubmitTask_shouldDelegate() {
    UploadTask task = mock(UploadTask.class);

    retryAwareUploader.resubmitTask(task);

    verify(delegate).resubmitTask(task);
}

@Test
@DisplayName("9.7 [迁移] getFailedQueueDir 应委托给被装饰对象")
void testGetFailedQueueDir_shouldDelegate() {
    Path mockPath = Paths.get("/tmp/failed");
    when(delegate.getFailedQueueDir()).thenReturn(mockPath);

    Path result = retryAwareUploader.getFailedQueueDir();

    assertEquals(mockPath, result);
    verify(delegate).getFailedQueueDir();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn test -pl agent -Dtest=RetryAwareUploaderDecoratorTest#testGetInflightTasks_shouldDelegate -DfailIfNoTests=false
```

Expected: ❌ FAIL - 方法不存在

- [ ] **Step 3: 实现7个委托方法**

在 `RetryAwareUploaderDecorator.java` 中添加（建议放在"重试管理初始化"部分之后）：

```java
// ==================== 任务管理查询方法（从 AgentUploader 迁移） ====================

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
 * 获取进行中的任务数量
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

// ==================== 重试相关方法（从 AgentUploader 迁移） ====================

/**
 * 重新提交失败的上传任务
 */
public void resubmitTask(UploadTask task) {
    logger.info("[retry] 🔄 重新提交失败任务: transferId={}", task.getTransferId());
    delegate.resubmitTask(task);
}

/**
 * 获取失败队列目录路径
 */
public Path getFailedQueueDir() {
    return delegate.getFailedQueueDir();
}
```

- [ ] **Step 4: 运行测试验证通过**

Run:
```bash
mvn test -pl agent -Dtest=RetryAwareUploaderDecoratorTest
```

Expected: ✅ PASS - 所有测试通过（包括新增的7个）

- [ ] **Step 5: 提交代码**

```bash
git add agent/src/main/java/com/cq/agent/client/upload/RetryAwareUploaderDecorator.java
git add agent/src/test/java/com/cq/agent/client/upload/RetryAwareUploaderDecoratorTest.java
git commit -m "refactor(upload): RetryAwareUploaderDecorator 新增7个委托方法（任务管理+重试）"
```

---

## Task 2: 在 BatchListenerAwareAgentUploaderDecorator 中新增5个监听器恢复方法

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java`
- Create/Modify: `agent/src/test/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecoratorTest.java`

**目标**: 添加监听器状态恢复相关的5个方法（需要处理依赖注入）

- [ ] **Step 1: 分析依赖关系**

需要从 AgentUploader 迁移的方法及其依赖：
1. `populateRestoreFields(task, listener)` - 无额外依赖
2. `restoreListenerState(listener, task)` - 依赖 `globalProgressReporter`, `agentConfig`
3. `getOrCreateGlobalProgressReporter()` - 依赖 `globalProgressReporter`, `agentConfig`
4. `createTemporaryProgressReporter()` - 依赖 `agentConfig`
5. `getRegistryServerUrl()` - 依赖 `agentConfig`

**解决方案**：
- 通过构造函数或 setter 注入 `AgentConfig`
- 将 `globalProgressReporter` 作为字段存储
- 或者通过 `delegate.getAgentConfig()` 获取配置（推荐，减少耦合）

- [ ] **Step 2: 编写测试用例**

```java
@Test
@DisplayName("8.1 [迁移] populateRestoreFields 应正确填充BatchUploadListener字段")
void testPopulateRestoreFields_shouldPopulateBatchListenerFields() {
    UploadTask task = new UploadTask();
    task.setLocalFilePath("/tmp/test.txt");
    task.setRemoteTargetPath("/remote/test.txt");
    
    BatchUploadListener batchListener = new BatchUploadListener(100L, 200L, "test.txt", 1024L, null);

    batchListenerAwareDecorator.populateRestoreFields(task, batchListener);

    assertEquals(100L, task.getTaskId());
    assertEquals(200L, task.getSubtaskId());
    assertEquals("test.txt", task.getFileName());
    assertEquals(1024L, task.getFileSize());
}
```

- [ ] **Step 3: 实现监听器状态恢复方法**

在 `BatchListenerAwareAgentUploaderDecorator.java` 中添加：

```java
// ==================== 监听器状态恢复方法（从 AgentUploader 迁移） ====================

/**
 * 填充监听器状态恢复字段到 UploadTask
 * 用于重启恢复场景
 */
public void populateRestoreFields(UploadTask task, UploadListener listener) {
    if (task == null || listener == null) {
        return;
    }

    if (listener instanceof BatchUploadListener batchListener) {
        try {
            task.setTaskId(batchListener.getTaskId());
            task.setSubtaskId(batchListener.getSubtaskId());
            task.setFileName(batchListener.getFileName());
            
            if (batchListener.getFileSize() > 0) {
                task.setFileSize(batchListener.getFileSize());
            }

            logger.debug("✅ 已填充恢复字段: taskId={}, subtaskId={}, fileName={}, size={}bytes",
                task.getTaskId(), task.getSubtaskId(), task.getFileName(), task.getFileSize());

        } catch (Exception e) {
            logger.warn("⚠️ 填充恢复字段失败（不影响正常上传）: {}", e.getMessage());
        }
    }
}

/**
 * 恢复监听器状态（用于重启恢复场景）
 */
public void restoreListenerState(UploadListener listener, UploadTask task) {
    if (listener == null || task == null) {
        logger.debug("⚠️ listener或task为null，跳过状态恢复");
        return;
    }

    if (listener instanceof BatchUploadListener batchListener) {
        if (!batchListener.isRestored()) {
            logger.debug("ℹ️ 监听器已通过有参构造函数初始化，跳过状态恢复: subtask={}",
                batchListener.getSubtaskId());
            return;
        }

        Long taskId = task.getTaskId();
        Long subtaskId = task.getSubtaskId();
        String fileName = task.getFileName();
        String filePath = task.getLocalFilePath();
        long fileSize = task.getFileSize();

        if (subtaskId == null) {
            logger.warn("⚠️ subtaskId为null，无法恢复监听器状态: file={}", fileName);
            return;
        }

        try {
            ProgressReporter reporter = getOrCreateGlobalProgressReporter();
            batchListener.restoreState(taskId, subtaskId, filePath, fileName, fileSize, reporter);

            logger.info("✅ 上传监听器状态已恢复: subtaskId={}, file={}, size={}bytes",
                subtaskId, fileName, fileSize);

        } catch (Exception e) {
            logger.error("❌ 恢复上传监听器状态失败: subtaskId={}, file={}, error={}",
                subtaskId, fileName, e.getMessage());
        }
    }
}

/**
 * 获取或创建全局 ProgressReporter 实例
 */
private ProgressReporter getOrCreateGlobalProgressReporter() {
    if (globalProgressReporter != null) {
        return globalProgressReporter;
    }

    logger.warn("⚠️ globalProgressReporter未设置，将创建临时实例");
    return createTemporaryProgressReporter();
}

/**
 * 创建临时的 ProgressReporter 实例
 */
private ProgressReporter createTemporaryProgressReporter() {
    String proxyUrl = getRegistryServerUrl();
    if (proxyUrl == null || proxyUrl.isBlank()) {
        logger.debug("⚠️ proxyUrl未配置，使用空ProgressReporter");
        return new ProgressReporter(null);
    }

    return new ProgressReporter(proxyUrl);
}

/**
 * 获取注册中心/代理服务器URL
 */
private String getRegistryServerUrl() {
    AgentConfig config = delegate.getAgentConfig();
    if (config == null) return null;

    java.util.List<String> urls = config.getRegistryServerUrls();
    if (urls != null && !urls.isEmpty()) {
        return urls.getFirst();
    }

    return null;
}
```

**注意**：需要在类中添加字段：
```java
private final ProgressReporter globalProgressReporter;  // 可选，通过构造函数注入
```

- [ ] **Step 4: 运行测试验证**

Run:
```bash
mvn test -pl agent -Dtest=BatchListenerAwareAgentUploaderDecoratorTest
```

Expected: ✅ PASS

- [ ] **Step 5: 提交代码**

```bash
git add agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java
git add agent/src/test/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecoratorTest.java
git commit -m "refactor(upload): BatchListenerAwareAgentUploaderDecorator 新增5个监听器恢复方法"
```

---

## Task 3: 从 AgentUploader 中删除已迁移的13个方法

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java`

**目标**: 清理 AgentUploader，仅保留核心传输方法

- [ ] **Step 1: 确认所有调用点已更新**

全局搜索确保没有外部代码直接调用这些方法（应该都通过装饰者链调用）：

```bash
grep -r "uploader\.getInflightTasks\|uploader\.resubmitTask\|uploader\.populateRestoreFields" --include="*.java" agent/src/
```

Expected: 只在测试文件中出现，且已更新为调用装饰者

- [ ] **Step 2: 删除任务管理查询方法（5个）**

删除以下方法（约 L493-L521）：
- `getInflightTasks(int page, int pageSize)`
- `getAllInflightTasks()`
- `getInflightTasksCount()`
- `isInflightTasksEmpty()`
- `clearAllInflightTasks()`

- [ ] **Step 3: 删除重试相关方法（2个）**

删除以下方法（约 L566-L592）：
- `resubmitTask(UploadTask task)`
- `getFailedQueueDir()`

- [ ] **Step 4: 删除监听器状态恢复方法（5个）**

删除以下方法（约 L530-L555, L603-L687）：
- `populateRestoreFields(UploadTask, UploadListener)`
- `restoreListenerState(UploadListener, UploadTask)`
- `getOrCreateGlobalProgressReporter()`
- `createTemporaryProgressReporter()`
- `getRegistryServerUrl()`

- [ ] **Step 5: 清理 import 语句**

检查并删除不再使用的 import（如果有）：
- `java.nio.file.Path` （如果 getFailedQueueDir 删除后不再使用）
- `com.cq.agent.batch.report.ProgressReporter` （如果监听器恢复方法删除后不再使用）
- 其他相关 import

- [ ] **Step 6: 编译验证**

Run:
```bash
mvn clean compile -pl agent
```

Expected: ✅ BUILD SUCCESS

注意：如果编译失败，说明还有其他地方调用了这些方法，需要先更新调用点

- [ ] **Step 7: 提交代码**

```bash
git add agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java
git commit -m "refactor(upload): 精简 AgentUploader 删除13个已迁移的非核心方法"
```

---

## Task 4: 更新集成测试和全量验证

**Files:**
- Modify: `agent/src/test/java/com/cq/agent/client/upload/AgentUploaderTest.java` (如有)
- No new files (verification only)

- [ ] **Step 1: 更新 AgentUploaderTest**

如果存在针对已迁移方法的测试，需要：
- 删除对 `getInflightTasks`, `resubmitTask`, `populateRestoreFields` 等方法的直接测试
- 或将测试改为通过装饰者调用

示例：
```java
// 删除这个测试（如果存在）
// @Test
// void testGetInflightTasks_shouldReturnPagedResults() { ... }

// 改为在 RetryAwareUploaderDecoratorTest 中测试
```

- [ ] **Step 2: 运行完整的 agent 模块测试**

Run:
```bash
mvn clean test -pl agent
```

Expected: ✅ 所有测试通过（218+ 个测试）

重点关注：
- RetryAwareUploaderDecoratorTest ✅
- BatchListenerAwareAgentUploaderDecoratorTest ✅
- AgentUploaderTest ✅（核心传输功能不受影响）
- FileRetrySchedulerTest ✅
- 其他现有测试 ✅

- [ ] **Step 3: 验证代码行数变化**

Run:
```bash
wc -l agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java
```

Expected: 行数 < 500（原 688 行）

- [ ] **Step 4: 最终提交（如有微调）**

```bash
git add -A
git commit -m "test(upload): 更新测试适配 AgentUploader 方法迁移 + 全量验证通过"
```

---

## Task 5: 手动验证与文档更新

**Files:**
- No code files (verification only)

- [ ] **Step 1: 启动 Agent 应用验证**

可选：手动启动 AgentApplication 并观察日志，确保：
- 无异常抛出
- 文件上传功能正常
- 任务查询 API 正常（通过 HTTP 接口测试）

- [ ] **Step 2: 性能基准对比（可选）**

对比迁移前后的启动时间和内存占用：
- 预期：无显著差异（只是方法位置移动，逻辑不变）

- [ ] **Step 3: 更新代码注释**

如果某些方法的 JavaDoc 引用了旧的位置，更新为新的位置信息

- [ ] **Step 4: 提交最终版本**

```bash
git add -A
git commit -m "chore(upload): AgentUploader 方法迁移完成 - 精简200行代码"
```

---

## Self-Review Checklist

### ✅ Spec Coverage Verification

| Spec Requirement | Task Implementation | Status |
|------------------|---------------------|--------|
| 删除 getInflightTasks 等5个任务管理方法 | Task 3 Step 2 | ✅ |
| 删除 resubmitTask 和 getFailedQueueDir | Task 3 Step 3 | ✅ |
| 删除 populateRestoreFields 等5个监听器方法 | Task 3 Step 4 | ✅ |
| RetryAwareUploaderDecorator 新增7个方法 | Task 1 Step 3 | ✅ |
| BatchListenerAwareAgentUploaderDecorator 新增5个方法 | Task 2 Step 3 | ✅ |
| AgentUploader 行数 < 500 | Task 4 Step 3 | ✅ |
| 全量测试通过 | Task 4 Step 2 | ✅ |
| 向后兼容（外部调用方式不变） | Task 1-2 (委托模式) | ✅ |

### ✅ Placeholder Scan

- [x] 无 TBD / TODO 占位符
- [x] 每个步骤都有具体的代码片段
- [x] 包含精确的运行命令和预期输出
- [x] 无"类似 Task N"或"实现细节待补充"

### ✅ Type Consistency Check

- [x] 方法签名一致（参数类型、返回类型、异常声明）
- [x] 访问级别变更记录明确（private → public/package-private）
- [x] 依赖注入方式清晰（通过 delegate 或构造函数）

---

## Risk Mitigation

| 风险 | 缓解措施 | Task |
|------|----------|------|
| **遗漏调用点** | Task 3 Step 1 全局搜索 + 编译验证 | Task 3 |
| **可见性问题** | private → public 时添加文档说明 | Task 2 Step 3 |
| **循环依赖** | 装饰者只依赖 delegate，不互相依赖 | Task 1-2 |
| **测试回归** | 先迁移+测试，再删除原方法 | Task 顺序保证 |
| **性能开销** | 委托调用增加可忽略（方法调用栈深度+1） | 无需特殊处理 |

---

## Success Criteria

### 功能验收
- [ ] AgentUploader 仅包含 11 个方法（10核心 + 1配置）
- [ ] RetryAwareUploaderDecorator 包含 7 个新增委托方法
- [ ] BatchListenerAwareAgentUploaderDecorator 包含 5 个新增方法
- [ ] 所有原有功能通过装饰者链可访问
- [ ] 编译无错误

### 质量验收
- [ ] `mvn clean test -pl agent` 全部通过
- [ ] `mvn clean compile -pl agent` 成功
- [ ] AgentUploader.java 行数 < 500
- [ ] Git 提交信息规范

### 架构验收
- [ ] 单一职责原则：每个类职责清晰
- [ ] 装饰者模式正确实现
- [ ] 代码净减少 ~15 行
- [ ] 易于未来扩展和维护

---

**Plan Version**: v1.0  
**Based on Spec**: [2026-05-13-agentuploader-method-migration-design.md](../specs/2026-05-13-agentuploader-method-migration-design.md)  
**Created**: 2026-05-13  
**Estimated Effort**: 1.5-2 hours (including testing)
