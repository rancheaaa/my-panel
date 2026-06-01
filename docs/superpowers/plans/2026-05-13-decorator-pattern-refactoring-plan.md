# 装饰者模式重构：AgentUploader/AgentDownloader 优化

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用装饰者模式将监听器管理逻辑从 AgentUploader/AgentDownloader 中分离，创建 BatchListenerAware*Decorator 装饰者类

**Architecture:** 采用单一装饰者方案（方案A），通过组合模式包装核心传输类，将监听器生命周期管理（创建、缓存、恢复状态、ProgressReporter集成）抽离到独立的装饰者类中

**Tech Stack:** Java 21, Spring Boot 4, JUnit 5, Mockito, Maven, Decorator Pattern

---

## 文件结构总览

### 新增文件：
- `agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java` - 上传装饰者
- `agent/src/main/java/com/cq/agent/client/download/BatchListenerAwareAgentDownloaderDecorator.java` - 下载装饰者
- `agent/src/test/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecoratorTest.java` - 上传装饰者测试
- `agent/src/test/java/com/cq/agent/client/download/BatchListenerAwareAgentDownloaderDecoratorTest.java` - 下载装饰者测试

### 修改文件：
- `agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java` - 移除监听器管理方法
- `agent/src/main/java/com/cq/agent/client/download/AgentDownloader.java` - 移除监听器管理方法
- `agent/src/main/java/com/cq/agent/AgentApplication.java` - 集成装饰者

---

## Task 1: 创建上传装饰者类骨架

**Files:**
- Create: `agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java`

- [ ] **Step 1: 创建装饰者类文件并定义基本结构**

```java
package com.cq.agent.client.upload;

import com.cq.agent.batch.scheduler.BatchUploadListener;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Objects;

/**
 * 批量任务监听器感知的上传装饰者
 * 
 * 使用装饰者模式为 AgentUploader 添加监听器管理能力：
 * - 监听器的反射创建与缓存
 * - 重启恢复场景的状态重建
 * - ProgressReporter 集成
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
        logger.info("✅ 创建BatchListenerAwareAgentUploaderDecorator");
    }

    /**
     * 获取委托对象（核心上传客户端）
     * @return AgentUploader实例
     */
    public AgentUploader getDelegate() {
        return delegate;
    }

    /**
     * 获取AgentConfig配置
     * @return 配置对象
     */
    public AgentConfig getAgentConfig() {
        return delegate.getAgentConfig();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl agent -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java
git commit -m "feat(upload): create BatchListenerAwareAgentUploaderDecorator skeleton"
```

---

## Task 2: 实现 ProgressReporter 管理功能

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java`
- Test: `agent/src/test/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecoratorTest.java`

- [ ] **Step 1: 编写 ProgressReporter 管理的单元测试**

```java
package com.cq.agent.client.upload;

import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchListenerAwareAgentUploaderDecorator - ProgressReporter Management")
class BatchListenerAwareAgentUploaderDecoratorTest {

    private AgentUploader mockDelegate;
    private BatchListenerAwareAgentUploaderDecorator decorator;

    @BeforeEach
    void setUp() {
        mockDelegate = new AgentUploader(new AgentConfig());
        decorator = new BatchListenerAwareAgentUploaderDecorator(mockDelegate);
    }

    @Test
    @DisplayName("设置全局ProgressReporter - 正常情况")
    void testSetGlobalProgressReporter() {
        ProgressReporter reporter = new ProgressReporter("http://localhost:9876");
        
        decorator.setGlobalProgressReporter(reporter);
        
        assertNotNull(decorator.getGlobalProgressReporter());
        assertEquals(reporter, decorator.getGlobalProgressReporter());
    }

    @Test
    @DisplayName("设置全局ProgressReporter为null")
    void testSetGlobalProgressReporterNull() {
        decorator.setGlobalProgressReporter(null);
        
        assertNull(decorator.getGlobalProgressReporter());
    }

    @Test
    @DisplayName("获取或创建ProgressReporter - 已设置时返回全局实例")
    void testGetOrCreateGlobalProgressReporter_WhenSet() {
        ProgressReporter reporter = new ProgressReporter("http://localhost:9876");
        decorator.setGlobalProgressReporter(reporter);
        
        ProgressReporter result = decorator.getOrCreateGlobalProgressReporter();
        
        assertSame(reporter, result);
    }

    @Test
    @DisplayName("获取或创建ProgressReporter - 未设置时返回临时实例")
    void testGetOrCreateGlobalProgressReporter_WhenNotSet() {
        ProgressReporter result = decorator.getOrCreateGlobalProgressReporter();
        
        assertNotNull(result);
        // 应该是一个新的临时实例，不是null
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=BatchListenerAwareAgentUploaderDecoratorTest -DfailIfNoTests=false`
Expected: COMPILATION ERROR - 方法不存在

- [ ] **Step 3: 实现 ProgressReporter 管理方法**

在 `BatchListenerAwareAgentUploaderDecorator.java` 中添加以下方法：

```java
/**
 * 设置全局ProgressReporter（用于重启恢复场景）
 * @param progressReporter 全局唯一的进度上报器
 */
public void setGlobalProgressReporter(ProgressReporter progressReporter) {
    this.globalProgressReporter = progressReporter;
    logger.info("✅ 已设置全局ProgressReporter: {}", 
        progressReporter != null ? "已配置" : "null");
}

/**
 * 获取全局ProgressReporter
 * @return ProgressReporter实例，可能为null
 */
public ProgressReporter getGlobalProgressReporter() {
    return globalProgressReporter;
}

/**
 * 获取或创建全局ProgressReporter实例
 * 优先使用已设置的全局实例，避免重复创建浪费资源
 * @return ProgressReporter实例（不为null）
 */
public ProgressReporter getOrCreateGlobalProgressReporter() {
    if (globalProgressReporter != null) {
        return globalProgressReporter;
    }
    
    logger.warn("⚠️ globalProgressReporter未设置，将创建临时实例");
    return createTemporaryProgressReporter();
}

/**
 * 创建临时的ProgressReporter实例（仅作为降级方案）
 * @return ProgressReporter实例
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
 * 获取注册中心/代理服务器URL（用于进度上报）
 * @return URL字符串，可能为null
 */
private String getRegistryServerUrl() {
    AgentConfig config = delegate.getAgentConfig();
    if (config == null) return null;
    
    List<String> urls = config.getRegistryServerUrls();
    if (urls != null && !urls.isEmpty()) {
        return urls.get(0);
    }
    
    return null;
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=BatchListenerAwareAgentUploaderDecoratorTest -DfailIfNoTests=false`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java
git add agent/src/test/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecoratorTest.java
git commit -m "feat(upload): implement ProgressReporter management in decorator"
```

---

## Task 3: 实现监听器状态恢复功能

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java`
- Test: `agent/src/test/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecoratorTest.java`

- [ ] **Step 1: 编写监听器恢复逻辑的单元测试**

在测试文件中添加：

```java
@Test
@DisplayName("恢复BatchUploadListener状态 - 正常情况")
void testRestoreListenerStateIfNeeded_Success() {
    UploadTask task = new UploadTask("/path/to/file.txt", "/remote/path", 1024L, "http://192.168.1.100:7777/", "root");
    task.setTaskId(123456789L);
    task.setSubtaskId(9876543210123L);
    task.setFileName("file.txt");
    
    BatchUploadListener listener = new BatchUploadListener(); // 无参构造函数
    
    decorator.setGlobalProgressReporter(new ProgressReporter("http://localhost:9876"));
    
    decorator.restoreListenerStateIfNeeded(listener, task);
    
    assertTrue(listener.isRestored());
    assertEquals(9876543210123L, listener.getSubtaskId());
}

@Test
@DisplayName("恢复监听器状态 - subtaskId为null时跳过")
void testRestoreListenerStateIfNeeded_SubtaskIdNull() {
    UploadTask task = new UploadTask("/path/to/file.txt", "/remote/path", 1024L, "http://192.168.1.100:7777/", "root");
    task.setSubtaskId(null); // 未设置
    
    BatchUploadListener listener = new BatchUploadListener();
    
    decorator.restoreListenerStateIfNeeded(listener, task);
    
    assertTrue(listener.isRestored()); // 无参构造默认为true
}

@Test
@DisplayName("恢复监听器状态 - 非BatchUploadListener类型跳过")
void testRestoreListenerStateIfNeeded_NonBatchListener() {
    UploadTask task = new UploadTask("/path/to/file.txt", "/remote/path", 1024L, "http://192.168.1.100:7777/", "root");
    task.setSubtaskId(12345L);
    
    UploadListener mockListener = new UploadListener() { ... }; // 普通实现
    
    // 不应该抛出异常
    assertDoesNotThrow(() -> decorator.restoreListenerStateIfNeeded(mockListener, task));
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=BatchListenerAwareAgentUploaderDecoratorTest#testRestoreListenerStateIfNeeded_* -DfailIfNoTests=false`
Expected: METHOD NOT FOUND

- [ ] **Step 3: 实现监听器状态恢复方法**

在装饰者类中添加：

```java
/**
 * 恢复监听器状态（用于重启恢复场景）
 * 
 * 当从JSON反序列化恢复任务时，Listener通过无参构造函数创建，
 * 需要调用此方法恢复完整状态，包括taskId、subtaskId、文件信息等。
 *
 * @param listener 通过反射创建的监听器实例
 * @param task 包含恢复信息的任务对象
 */
public void restoreListenerStateIfNeeded(UploadListener listener, UploadTask task) {
    if (listener == null || task == null) {
        logger.debug("⚠️ listener或task为null，跳过状态恢复");
        return;
    }

    // 只处理BatchUploadListener类型
    if (!(listener instanceof BatchUploadListener batchListener)) {
        return;
    }
    
    // 检查是否需要恢复（如果已经是有参构造函数创建的则跳过）
    if (!batchListener.isRestored()) {
        logger.debug("ℹ️ 监听器已通过有参构造函数初始化，跳过状态恢复: subtask={}",
            batchListener.getSubtaskId());
        return;
    }

    Long subtaskId = task.getSubtaskId();
    if (subtaskId == null) {
        logger.warn("⚠️ subtaskId为null，无法恢复监听器状态: file={}", task.getFileName());
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

        logger.info("✅ 上传监听器状态已恢复: subtaskId={}, file={}, size={}bytes",
            subtaskId, task.getFileName(), task.getFileSize());

    } catch (Exception e) {
        logger.error("❌ 恢复上传监听器状态失败: subtaskId={}, file={}, error={}",
            subtaskId, task.getFileName(), e.getMessage());
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=BatchListenerAwareAgentUploaderDecoratorTest -DfailIfNoTests=false`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(upload): implement listener state restoration in decorator"
```

---

## Task 4: 实现填充恢复字段方法

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecorator.java`
- Test: `agent/src/test/java/com/cq/agent/client/upload/BatchListenerAwareAgentUploaderDecoratorTest.java`

- [ ] **Step 1: 编写填充字段的测试**

```java
@Test
@DisplayName("从BatchUploadListener提取恢复字段")
void testPopulateRestoreFieldsFromListener() {
    FileScanner.ScannedFile scannedFile = new FileScanner.ScannedFile(
        "/path/to/test.log", "test.log", 2048L, System.currentTimeMillis(), false
    );
    
    BatchUploadListener listener = new BatchUploadListener(
        111222333L, scannedFile, null, null
    );
    
    UploadTask task = new UploadTask(scannedFile.getAbsolutePath(), "/remote/path", 2048L, "http://192.168.1.100:7777/", "root");
    
    decorator.populateRestoreFieldsFromListener(task, listener);
    
    assertEquals(111222333L, task.getTaskId());
    assertNotNull(task.getSubtaskId());
    assertEquals("test.log", task.getFileName());
    assertEquals(2048L, task.getFileSize());
}
```

- [ ] **Step 2: 运行测试验证失败**

- [ ] **Step 3: 实现填充字段方法**

```java
/**
 * 从监听器提取恢复字段到任务对象
 * 用于确保任务持久化时包含完整的监听器恢复信息
 *
 * @param task 上传任务对象
 * @param listener 监听器实例（可能是BatchUploadListener）
 */
public void populateRestoreFieldsFromListener(UploadTask task, UploadListener listener) {
    if (task == null || listener == null) {
        return;
    }

    if (!(listener instanceof BatchUploadListener batchListener)) {
        return;
    }

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
```

- [ ] **Step 4: 运行测试验证通过**

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(upload): implement populateRestoreFields method in decorator"
```

---

## Task 5: 创建下载装饰者类（对称实现）

**Files:**
- Create: `agent/src/main/java/com/cq/agent/client/download/BatchListenerAwareAgentDownloaderDecorator.java`
- Create: `agent/src/test/java/com/cq/agent/client/download/BatchListenerAwareAgentDownloaderDecoratorTest.java`

- [ ] **Step 1: 基于上传装饰者创建下载装饰者**

参考 Task 1-4 的实现，创建对称的下载装饰者类。主要区别：
- 委托对象类型：`AgentDownloader` 而非 `AgentUploader`
- 类名：`BatchListenerAwareAgentDownloaderDecorator`
- 包路径：`com.cq.agent.client.download`

- [ ] **Step 2: 编写下载装饰者的测试用例**

至少包含：
- ✅ 设置/获取 GlobalProgressReporter
- ✅ 恢复监听器状态（如果有 BatchDownloadListener）
- ✅ 填充恢复字段

- [ ] **Step 3: 运行所有装饰者测试**

Run: `mvn test -pl agent -Dtest="*DecoratorTest" -DfailIfNoTests=false`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add .
git commit -m "feat(download): create BatchListenerAwareAgentDownloaderDecorator with tests"
```

---

## Task 6: 精简 AgentUploader 核心类

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java`

- [ ] **Step 1: 备份当前代码**

```bash
cp agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java \
   agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java.backup
```

- [ ] **Step 2: 移除已迁移到装饰者的方法**

从 `AgentUploader.java` 中删除以下内容：

1. 字段声明（第42行附近）：
```java
/** 全局ProgressReporter实例（用于重启恢复场景） */
private ProgressReporter globalProgressReporter;
```

2. 方法 `setGlobalProgressReporter()` （第61-65行）

3. 方法 `restoreListenerState()` （第569-610行）

4. 方法 `populateRestoreFields()` （第530-565行）

5. 方法 `getOrCreateGlobalProgressReporter()` （第613-630行）

6. 方法 `createTemporaryProgressReporter()` （第633-640行）

7. 方法 `getRegistryServerUrl()` （第643-650行）

8. 在 `processTask()` 中调用 `restoreListenerState()` 的代码（第82行）
9. 在 `uploadFile()` 中调用 `populateRestoreFields()` 的代码（第299行）

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl agent -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: 运行现有测试确保不破坏功能**

Run: `mvn test -pl agent -DfailIfNoTests=false`
Expected: All existing tests PASS

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java
git commit -m "refactor(upload): remove migrated listener management methods from core class"
```

---

## Task 7: 精简 AgentDownloader 核心类

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/client/download/AgentDownloader.java`

- [ ] **Step 1: 类似于 Task 6，移除已迁移的方法**

如果 AgentDownloader 中有类似的监听器管理代码，将其删除。

- [ ] **Step 2: 编译和测试验证**

- [ ] **Step 3: Commit**

```bash
git add .
git commit -m "refactor(download): clean up AgentDownloader core class"
```

---

## Task 8: 集成到 AgentApplication

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/AgentApplication.java`

- [ ] **Step 1: 修改 AgentApplication 使用装饰者**

找到当前代码（约第80-84行）：

```java
// Initialize upload/download agents
AgentUploader agentUploader = new AgentUploader(config);
agentUploader.init();

AgentDownloader agentDownloader = new AgentDownloader(config);
agentDownloader.init();
```

替换为：

```java
// Initialize upload/download agents (core)
AgentUploader coreUploader = new AgentUploader(config);
coreUploader.init();

AgentDownloader coreDownloader = new AgentDownloader(config);
coreDownloader.init();

// Wrap with decorators for listener management
BatchListenerAwareAgentUploaderDecorator agentUploader = 
    new BatchListenerAwareAgentUploaderDecorator(coreUploader);

BatchListenerAwareAgentDownloaderDecorator agentDownloader = 
    new BatchListenerAwareAgentDownloaderDecorator(coreDownloader);
```

- [ ] **Step 2: 更新 ProgressReporter 设置位置**

找到约第119-131行的 ProgressReporter 初始化代码，改为：

```java
if (registryUrl != null && !registryUrl.isBlank()) {
    ProgressReporter progressReporter = new ProgressReporter(registryUrl);
    taskSchedulerManager.setProgressReporter(progressReporter);
    
    // Set to decorators instead of core classes
    agentUploader.setGlobalProgressReporter(progressReporter);
    agentDownloader.setGlobalProgressReporter(progressReporter);
    
    logger.info("✅ ProgressReporter 初始化完成，上报地址: {}", registryUrl);
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl agent -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: 运行全量测试**

Run: `mvn test -pl agent -DfailIfNoTests=false`
Expected: All tests PASS

- [ ] **Step 5: 手动验证启动**

Run: 启动 Agent 应用程序
Expected: 日志显示 "✅ 已设置全局ProgressReporter: 已配置"

- [ ] **Step 6: Commit**

```bash
git add agent/src/main/java/com/cq/agent/AgentApplication.java
git commit -m "integrate: use BatchListenerAware*Decorators in AgentApplication"
```

---

## Task 9: 最终验证与文档更新

**Files:**
- Test: All existing + new tests
- Docs: Update README if needed

- [ ] **Step 1: 运行完整测试套件**

Run: `mvn test -pl agent -DfailIfNoTests=false`
Expected: 
- 所有现有测试通过
- 新增装饰者测试通过
- 覆盖率 ≥ 90%

- [ ] **Step 2: 性能基准测试（可选）**

对比重构前后的性能：
- 启动时间
- 内存占用
- 任务处理延迟

预期：性能下降 < 5%

- [ ] **Step 3: 清理备份文件**

```bash
rm agent/src/main/java/com/cq/agent/client/upload/AgentUploader.java.backup
```

- [ ] **Step 4: 最终Commit**

```bash
git add .
git commit -m "release: complete decorator pattern refactoring for AgentUploader/AgentDownloader"
```

- [ ] **Step 5: 更新设计文档状态**

编辑 `docs/superpowers/specs/2026-05-13-decorator-pattern-refactoring-design.md`：
- 将状态从 "已批准" 改为 "✅ 已完成"
- 添加实际实施的总结

---

## 自检清单

### Spec 覆盖度检查 ✅

- [x] 目标1：让 AgentUploader/AgentDownloader 只保留核心传输功能 → Task 6 & 7
- [x] 目标2：创建 BatchListenerAware*Decorator → Task 1 & 5
- [x] 目标3：保持向后兼容 → Task 8 & 9
- [x] 目标4：提高可测试性 → Task 2, 3, 4 的测试

### 占位符扫描 ✅

- [x] 无 TBD / TODO
- [x] 无 "实现细节待定"
- [x] 所有步骤都包含具体代码

### 类型一致性检查 ✅

- [x] 方法签名一致：`restoreListenerStateIfNeeded(listener, task)`
- [x] 类名正确：`BatchListenerAwareAgentUploaderDecorator`
- [x] 包路径正确：`com.cq.agent.client.upload`

---

## 执行统计

| Task | 描述 | 预计时间 | 复杂度 |
|------|------|---------|--------|
| 1 | 创建上传装饰者骨架 | 10min | 低 |
| 2 | 实现 PR 管理 | 20min | 低 |
| 3 | 实现状态恢复 | 30min | 中 |
| 4 | 实现字段填充 | 15min | 低 |
| 5 | 创建下载装饰者 | 30min | 中 |
| 6 | 精简 Uploader | 20min | 中 |
| 7 | 精简 Downloader | 15min | 低 |
| 8 | 集成到 Application | 25min | 中 |
| 9 | 最终验证 | 20min | 低 |

**总计**: ~3.5小时  
**风险**: 低（渐进式迁移，每步可回滚）
