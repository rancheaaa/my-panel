# Agent Guice 依赖注入重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 引入 Google Guice 7.x 依赖注入框架，重构 Agent 启动前的组件组装过程，使依赖关系显式化、后续扩展更便捷。

**Architecture:** 单 Module + Provider 模式。一个 `AgentModule` 集中管理所有绑定，复杂组件用 `Provider` 封装初始化逻辑。`AgentBootstrap` 负责运行时启动编排。现有组件类零修改。

**Tech Stack:** Google Guice 7.x, Java 21, Netty 4.2.5, Quartz

---

### Task 1: 添加 Guice 依赖到 pom.xml

**Files:**
- Modify: `agent/pom.xml`

- [ ] **Step 1: 在 agent/pom.xml 中添加 Guice 依赖**

在 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>com.google.inject</groupId>
    <artifactId>guice</artifactId>
    <version>7.0.0</version>
</dependency>
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl agent`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add agent/pom.xml
git commit -m "feat(agent): add Google Guice 7.0.0 dependency"
```

---

### Task 2: 创建 AgentModule（Guice Module 核心绑定）

**Files:**
- Create: `agent/src/main/java/com/cq/agent/di/AgentModule.java`

- [ ] **Step 1: 编写 AgentModule 测试**

Create: `agent/src/test/java/com/cq/agent/di/AgentModuleTest.java`

```java
package com.cq.agent.di;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.report.FallbackPersistenceService;
import com.cq.agent.batch.scheduler.FileRetryScheduler;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.client.upload.UploadService;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.executor.CommandExecutor;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentModuleTest {

    private Injector injector;

    @BeforeEach
    void setUp() {
        injector = com.google.inject.Guice.createInjector(new AgentModule());
    }

    @Test
    void shouldCreateAgentConfig() {
        AgentConfig config = injector.getInstance(AgentConfig.class);
        assertNotNull(config);
        assertNotNull(config.getAgentId());
    }

    @Test
    void shouldCreateCommandExecutor() {
        CommandExecutor executor = injector.getInstance(CommandExecutor.class);
        assertNotNull(executor);
    }

    @Test
    void shouldCreateFileService() {
        FileService fileService = injector.getInstance(FileService.class);
        assertNotNull(fileService);
    }

    @Test
    void shouldCreateChunkedTransferService() {
        ChunkedTransferService service = injector.getInstance(ChunkedTransferService.class);
        assertNotNull(service);
    }

    @Test
    void shouldCreateConfigFileManager() {
        ConfigFileManager manager = injector.getInstance(ConfigFileManager.class);
        assertNotNull(manager);
    }

    @Test
    void shouldCreateConfigChangeListener() {
        ConfigChangeListener listener = injector.getInstance(ConfigChangeListener.class);
        assertNotNull(listener);
    }

    @Test
    void shouldCreateUploadServiceAsRetryAware() {
        UploadService uploadService = injector.getInstance(UploadService.class);
        assertNotNull(uploadService);
        assertInstanceOf(RetryAwareUploaderDecorator.class, uploadService);
    }

    @Test
    void shouldCreateBatchTaskScheduler() {
        BatchTaskSchedulerUploaderDecorator scheduler = injector.getInstance(BatchTaskSchedulerUploaderDecorator.class);
        assertNotNull(scheduler);
    }

    @Test
    void shouldCreateFileBatchTracker() {
        FileBatchCompletionTracker tracker = injector.getInstance(FileBatchCompletionTracker.class);
        assertNotNull(tracker);
    }

    @Test
    void shouldCreateProgressReporter() {
        com.cq.agent.batch.report.ProgressReporter reporter = injector.getInstance(com.cq.agent.batch.report.ProgressReporter.class);
        assertNotNull(reporter);
    }

    @Test
    void shouldCreateFallbackPersistenceService() {
        FallbackPersistenceService service = injector.getInstance(FallbackPersistenceService.class);
        assertNotNull(service);
    }

    @Test
    void shouldCreateFileRetryScheduler() {
        FileRetryScheduler scheduler = injector.getInstance(FileRetryScheduler.class);
        assertNotNull(scheduler);
    }

    @Test
    void shouldCreateHttpServer() {
        HttpServer server = injector.getInstance(HttpServer.class);
        assertNotNull(server);
    }

    @Test
    void shouldCreateAgentRegistryService() {
        AgentRegistryService service = injector.getInstance(AgentRegistryService.class);
        assertNotNull(service);
    }

    @Test
    void shouldCreateAgentBootstrap() {
        AgentBootstrap bootstrap = injector.getInstance(AgentBootstrap.class);
        assertNotNull(bootstrap);
    }

    @Test
    void shouldReturnSameSingletonForAgentConfig() {
        AgentConfig config1 = injector.getInstance(AgentConfig.class);
        AgentConfig config2 = injector.getInstance(AgentConfig.class);
        assertSame(config1, config2);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=AgentModuleTest`
Expected: FAIL (AgentModule class does not exist yet)

- [ ] **Step 3: 创建 AgentModule**

Create: `agent/src/main/java/com/cq/agent/di/AgentModule.java`

```java
package com.cq.agent.di;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.VersionManager;
import com.cq.agent.batch.report.FallbackPersistenceService;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.report.SubTaskEvent;
import com.cq.agent.batch.scheduler.FileRetryScheduler;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.download.AgentDownloader;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.client.upload.UploadService;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.di.provider.*;
import com.cq.agent.executor.CommandExecutor;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public class AgentModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(AgentModule.class);

    @Override
    protected void configure() {
        bind(AgentConfig.class).toInstance(new AgentConfig());
        bind(CommandExecutor.class).in(Singleton.class);
        bind(FileService.class).in(Singleton.class);
        bind(ChunkedTransferService.class).in(Singleton.class);
        bind(VersionManager.class).in(Singleton.class);
        bind(FileScanner.class).in(Singleton.class);
        bind(ConfigFileManager.class).toProvider(ConfigFileManagerProvider.class).in(Singleton.class);
        bind(FileBatchCompletionTracker.class).toProvider(FileBatchCompletionTrackerProvider.class).in(Singleton.class);
        bind(ProgressReporter.class).toProvider(ProgressReporterProvider.class).in(Singleton.class);
        bind(UploadService.class).toProvider(RetryAwareUploaderProvider.class).in(Singleton.class);
        bind(AgentUploader.class).toProvider(AgentUploaderProvider.class).in(Singleton.class);
        bind(AgentDownloader.class).toProvider(AgentDownloaderProvider.class).in(Singleton.class);
        bind(RetryAwareUploaderDecorator.class).toProvider(RetryAwareUploaderProvider.class).in(Singleton.class);
        bind(BatchTaskSchedulerUploaderDecorator.class).toProvider(BatchTaskSchedulerProvider.class).in(Singleton.class);
        bind(ConfigChangeListener.class).toProvider(ConfigChangeListenerProvider.class).in(Singleton.class);
        bind(FallbackPersistenceService.class).toProvider(FallbackPersistenceServiceProvider.class).in(Singleton.class);
        bind(FileRetryScheduler.class).toProvider(FileRetrySchedulerProvider.class).in(Singleton.class);
        bind(HttpServer.class).toProvider(HttpServerProvider.class).in(Singleton.class);
        bind(AgentRegistryService.class).toProvider(AgentRegistryServiceProvider.class).in(Singleton.class);
        bind(AgentBootstrap.class).in(Singleton.class);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=AgentModuleTest`
Expected: FAIL (Provider classes not yet created, but Module compiles)

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/cq/agent/di/AgentModule.java agent/src/test/java/com/cq/agent/di/AgentModuleTest.java
git commit -m "feat(agent): add AgentModule with Guice bindings and tests"
```

---

### Task 3: 创建简单 Provider（ConfigFileManager、FileBatchCompletionTracker、ProgressReporter、AgentRegistryService）

**Files:**
- Create: `agent/src/main/java/com/cq/agent/di/provider/ConfigFileManagerProvider.java`
- Create: `agent/src/main/java/com/cq/agent/di/provider/FileBatchCompletionTrackerProvider.java`
- Create: `agent/src/main/java/com/cq/agent/di/provider/ProgressReporterProvider.java`
- Create: `agent/src/main/java/com/cq/agent/di/provider/AgentRegistryServiceProvider.java`

- [ ] **Step 1: 创建 ConfigFileManagerProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import java.nio.file.Path;

public class ConfigFileManagerProvider implements Provider<ConfigFileManager> {

    private final AgentConfig config;

    @Inject
    public ConfigFileManagerProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public ConfigFileManager get() {
        String batchConfigDir = Path.of(config.getFileBaseDirectory(), "batch-config").toString();
        return new ConfigFileManager(batchConfigDir);
    }
}
```

- [ ] **Step 2: 创建 FileBatchCompletionTrackerProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class FileBatchCompletionTrackerProvider implements Provider<FileBatchCompletionTracker> {

    private final AgentConfig config;

    @Inject
    public FileBatchCompletionTrackerProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public FileBatchCompletionTracker get() {
        return new FileBatchCompletionTracker(config.getFilebatchPendingDir());
    }
}
```

- [ ] **Step 3: 创建 ProgressReporterProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class ProgressReporterProvider implements Provider<ProgressReporter> {

    private final AgentConfig config;

    @Inject
    public ProgressReporterProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public ProgressReporter get() {
        return new ProgressReporter(config);
    }
}
```

- [ ] **Step 4: 创建 AgentRegistryServiceProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.registry.AgentRegistryService;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class AgentRegistryServiceProvider implements Provider<AgentRegistryService> {

    private final AgentConfig config;

    @Inject
    public AgentRegistryServiceProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public AgentRegistryService get() {
        return new AgentRegistryService(config);
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -pl agent`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add agent/src/main/java/com/cq/agent/di/provider/ConfigFileManagerProvider.java agent/src/main/java/com/cq/agent/di/provider/FileBatchCompletionTrackerProvider.java agent/src/main/java/com/cq/agent/di/provider/ProgressReporterProvider.java agent/src/main/java/com/cq/agent/di/provider/AgentRegistryServiceProvider.java
git commit -m "feat(agent): add simple Guice providers for ConfigFileManager, FileBatchCompletionTracker, ProgressReporter, AgentRegistryService"
```

---

### Task 4: 创建上传/下载 Provider（AgentUploader、AgentDownloader、RetryAwareUploader）

**Files:**
- Create: `agent/src/main/java/com/cq/agent/di/provider/AgentUploaderProvider.java`
- Create: `agent/src/main/java/com/cq/agent/di/provider/AgentDownloaderProvider.java`
- Create: `agent/src/main/java/com/cq/agent/di/provider/RetryAwareUploaderProvider.java`

- [ ] **Step 1: 创建 AgentUploaderProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class AgentUploaderProvider implements Provider<AgentUploader> {

    private final AgentConfig config;

    @Inject
    public AgentUploaderProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public AgentUploader get() {
        AgentUploader uploader = new AgentUploader(config);
        uploader.init();
        return uploader;
    }
}
```

- [ ] **Step 2: 创建 AgentDownloaderProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.client.download.AgentDownloader;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class AgentDownloaderProvider implements Provider<AgentDownloader> {

    private final AgentConfig config;

    @Inject
    public AgentDownloaderProvider(AgentConfig config) {
        this.config = config;
    }

    @Override
    public AgentDownloader get() {
        AgentDownloader downloader = new AgentDownloader(config);
        downloader.init();
        return downloader;
    }
}
```

- [ ] **Step 3: 创建 RetryAwareUploaderProvider**

这是最关键的 Provider，封装装饰者链的创建和 setter 注入：

```java
package com.cq.agent.di.provider;

import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.AgentUploader;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.client.upload.UploadService;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryAwareUploaderProvider implements Provider<RetryAwareUploaderDecorator> {

    private static final Logger logger = LoggerFactory.getLogger(RetryAwareUploaderProvider.class);

    private final AgentUploader coreUploader;
    private final AgentConfig config;
    private final FileBatchCompletionTracker fileBatchTracker;
    private final ProgressReporter progressReporter;

    @Inject
    public RetryAwareUploaderProvider(AgentUploader coreUploader,
                                      AgentConfig config,
                                      FileBatchCompletionTracker fileBatchTracker,
                                      ProgressReporter progressReporter) {
        this.coreUploader = coreUploader;
        this.config = config;
        this.fileBatchTracker = fileBatchTracker;
        this.progressReporter = progressReporter;
    }

    @Override
    public RetryAwareUploaderDecorator get() {
        RetryAwareUploaderDecorator retryAwareUploader = new RetryAwareUploaderDecorator(coreUploader);
        retryAwareUploader.initWithConfig(config);
        retryAwareUploader.setFileBatchTracker(fileBatchTracker);
        retryAwareUploader.setGlobalProgressReporter(progressReporter);
        logger.info("Upload/Download services initialized with decorator chain: Core -> RetryAware");
        return retryAwareUploader;
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -pl agent`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/cq/agent/di/provider/AgentUploaderProvider.java agent/src/main/java/com/cq/agent/di/provider/AgentDownloaderProvider.java agent/src/main/java/com/cq/agent/di/provider/RetryAwareUploaderProvider.java
git commit -m "feat(agent): add upload/download Guice providers with decorator chain"
```

---

### Task 5: 创建 BatchTaskScheduler、ConfigChangeListener、FallbackPersistence、FileRetryScheduler、HttpServer Provider

**Files:**
- Create: `agent/src/main/java/com/cq/agent/di/provider/BatchTaskSchedulerProvider.java`
- Create: `agent/src/main/java/com/cq/agent/di/provider/ConfigChangeListenerProvider.java`
- Create: `agent/src/main/java/com/cq/agent/di/provider/FallbackPersistenceServiceProvider.java`
- Create: `agent/src/main/java/com/cq/agent/di/provider/FileRetrySchedulerProvider.java`
- Create: `agent/src/main/java/com/cq/agent/di/provider/HttpServerProvider.java`

- [ ] **Step 1: 创建 BatchTaskSchedulerProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.scanner.FileScanner;
import com.cq.agent.batch.tracker.FileBatchCompletionTracker;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchTaskSchedulerProvider implements Provider<BatchTaskSchedulerUploaderDecorator> {

    private static final Logger logger = LoggerFactory.getLogger(BatchTaskSchedulerProvider.class);

    private final RetryAwareUploaderDecorator retryAwareUploader;
    private final ConfigFileManager configFileManager;
    private final AgentConfig config;
    private final FileScanner fileScanner;
    private final ProgressReporter progressReporter;
    private final FileBatchCompletionTracker fileBatchTracker;

    @Inject
    public BatchTaskSchedulerProvider(RetryAwareUploaderDecorator retryAwareUploader,
                                      ConfigFileManager configFileManager,
                                      AgentConfig config,
                                      FileScanner fileScanner,
                                      ProgressReporter progressReporter,
                                      FileBatchCompletionTracker fileBatchTracker) {
        this.retryAwareUploader = retryAwareUploader;
        this.configFileManager = configFileManager;
        this.config = config;
        this.fileScanner = fileScanner;
        this.progressReporter = progressReporter;
        this.fileBatchTracker = fileBatchTracker;
    }

    @Override
    public BatchTaskSchedulerUploaderDecorator get() {
        try {
            BatchTaskSchedulerUploaderDecorator batchTaskUploader =
                    new BatchTaskSchedulerUploaderDecorator(retryAwareUploader, configFileManager);
            batchTaskUploader.setRetryAwareUploader(retryAwareUploader);
            batchTaskUploader.setAgentConfig(config);
            batchTaskUploader.setFileScanner(fileScanner);
            batchTaskUploader.setProgressReporter(progressReporter);
            batchTaskUploader.setFileBatchTracker(fileBatchTracker);
            logger.info("BatchTaskSchedulerUploaderDecorator initialized successfully");
            return batchTaskUploader;
        } catch (Exception e) {
            logger.error("Failed to initialize BatchTaskSchedulerUploaderDecorator: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to initialize BatchTaskSchedulerUploaderDecorator", e);
        }
    }
}
```

- [ ] **Step 2: 创建 ConfigChangeListenerProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.VersionManager;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigChangeListenerProvider implements Provider<ConfigChangeListener> {

    private static final Logger logger = LoggerFactory.getLogger(ConfigChangeListenerProvider.class);

    private final ConfigFileManager configFileManager;
    private final VersionManager versionManager;
    private final BatchTaskSchedulerUploaderDecorator batchTaskUploader;
    private final RetryAwareUploaderDecorator retryAwareUploader;

    @Inject
    public ConfigChangeListenerProvider(ConfigFileManager configFileManager,
                                        VersionManager versionManager,
                                        BatchTaskSchedulerUploaderDecorator batchTaskUploader,
                                        RetryAwareUploaderDecorator retryAwareUploader) {
        this.configFileManager = configFileManager;
        this.versionManager = versionManager;
        this.batchTaskUploader = batchTaskUploader;
        this.retryAwareUploader = retryAwareUploader;
    }

    @Override
    public ConfigChangeListener get() {
        ConfigChangeListener configChangeListener = new ConfigChangeListener(configFileManager, versionManager);

        configChangeListener.onCronChange(taskId -> {
            AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(taskId);
            if (taskConfig != null) {
                batchTaskUploader.updateTask(taskConfig);
                retryAwareUploader.registerTaskConfig(taskId, taskConfig);
            }
        });

        configChangeListener.onAnyChange(ctx -> {
            if ("NEW_TASK".equals(ctx.field)) {
                AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                if (taskConfig != null && "RUNNING".equals(taskConfig.getStatus())) {
                    batchTaskUploader.startTask(taskConfig);
                    retryAwareUploader.registerTaskConfig(ctx.taskId, taskConfig);
                }
            } else if ("CONFIG_UPDATED".equals(ctx.field)) {
                AgentTaskConfig taskConfig = configFileManager.loadTaskConfig(ctx.taskId);
                if (taskConfig != null) {
                    batchTaskUploader.updateTask(taskConfig);
                    retryAwareUploader.registerTaskConfig(taskId, taskConfig);
                }
            }
        });

        logger.info("ConfigChangeListener initialized with callbacks registered");
        return configChangeListener;
    }
}
```

- [ ] **Step 3: 创建 FallbackPersistenceServiceProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.batch.report.FallbackPersistenceService;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.batch.report.SubTaskEvent;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FallbackPersistenceServiceProvider implements Provider<FallbackPersistenceService> {

    private static final Logger logger = LoggerFactory.getLogger(FallbackPersistenceServiceProvider.class);

    private final AgentConfig config;
    private final ProgressReporter progressReporter;

    @Inject
    public FallbackPersistenceServiceProvider(AgentConfig config, ProgressReporter progressReporter) {
        this.config = config;
        this.progressReporter = progressReporter;
    }

    @Override
    public FallbackPersistenceService get() {
        FallbackPersistenceService fallbackPersistenceService =
                new FallbackPersistenceService(config.getProgressFallbackDir());

        fallbackPersistenceService.setProgressReporter(progressReporter);
        fallbackPersistenceService.configureAutoRetry(30, 10);

        progressReporter.setFallbackHandler(event -> {
            try {
                fallbackPersistenceService.persist(event);
                logger.info("Progress event fallback to local storage: subtaskId={}, status={}",
                        event.getSubtaskId(), event.getStatus());
            } catch (Exception e) {
                logger.error("Local persistence failed: subtaskId={}, error={}", event.getSubtaskId(), e.getMessage());
            }
        });

        fallbackPersistenceService.startAutoRetry();
        logger.info("FallbackPersistenceService integrated and auto-retry started: storageDir={}",
                config.getProgressFallbackDir());

        return fallbackPersistenceService;
    }
}
```

- [ ] **Step 4: 创建 FileRetrySchedulerProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.batch.scheduler.FileRetryScheduler;
import com.cq.agent.client.upload.RetryAwareUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileRetrySchedulerProvider implements Provider<FileRetryScheduler> {

    private static final Logger logger = LoggerFactory.getLogger(FileRetrySchedulerProvider.class);

    private final RetryAwareUploaderDecorator retryAwareUploader;
    private final AgentConfig config;

    @Inject
    public FileRetrySchedulerProvider(RetryAwareUploaderDecorator retryAwareUploader, AgentConfig config) {
        this.retryAwareUploader = retryAwareUploader;
        this.config = config;
    }

    @Override
    public FileRetryScheduler get() {
        try {
            FileRetryScheduler fileRetryScheduler =
                    new FileRetryScheduler(retryAwareUploader, config.getFailedQueueScanIntervalMs());
            logger.info("FileRetryScheduler initialized (independent Quartz instance)");
            return fileRetryScheduler;
        } catch (Exception e) {
            logger.error("Failed to initialize FileRetryScheduler: {}", e.getMessage());
            throw new RuntimeException("Unable to initialize file retry scheduler", e);
        }
    }
}
```

- [ ] **Step 5: 创建 HttpServerProvider**

```java
package com.cq.agent.di.provider;

import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.executor.CommandExecutor;
import com.cq.agent.server.HttpServer;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.inject.Provider;
import com.google.inject.Inject;

public class HttpServerProvider implements Provider<HttpServer> {

    private final AgentConfig config;
    private final CommandExecutor commandExecutor;
    private final FileService fileService;
    private final ChunkedTransferService chunkedTransferService;
    private final ConfigFileManager configFileManager;
    private final ConfigChangeListener configChangeListener;
    private final BatchTaskSchedulerUploaderDecorator batchTaskUploader;

    @Inject
    public HttpServerProvider(AgentConfig config,
                              CommandExecutor commandExecutor,
                              FileService fileService,
                              ChunkedTransferService chunkedTransferService,
                              ConfigFileManager configFileManager,
                              ConfigChangeListener configChangeListener,
                              BatchTaskSchedulerUploaderDecorator batchTaskUploader) {
        this.config = config;
        this.commandExecutor = commandExecutor;
        this.fileService = fileService;
        this.chunkedTransferService = chunkedTransferService;
        this.configFileManager = configFileManager;
        this.configChangeListener = configChangeListener;
        this.batchTaskUploader = batchTaskUploader;
    }

    @Override
    public HttpServer get() {
        return new HttpServer(config, commandExecutor, fileService, chunkedTransferService,
                configFileManager, configChangeListener, batchTaskUploader);
    }
}
```

- [ ] **Step 6: 编译验证**

Run: `mvn clean compile -pl agent`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add agent/src/main/java/com/cq/agent/di/provider/BatchTaskSchedulerProvider.java agent/src/main/java/com/cq/agent/di/provider/ConfigChangeListenerProvider.java agent/src/main/java/com/cq/agent/di/provider/FallbackPersistenceServiceProvider.java agent/src/main/java/com/cq/agent/di/provider/FileRetrySchedulerProvider.java agent/src/main/java/com/cq/agent/di/provider/HttpServerProvider.java
git commit -m "feat(agent): add remaining Guice providers for BatchTaskScheduler, ConfigChangeListener, FallbackPersistence, FileRetryScheduler, HttpServer"
```

---

### Task 6: 创建 AgentBootstrap 启动编排类

**Files:**
- Create: `agent/src/main/java/com/cq/agent/di/AgentBootstrap.java`

- [ ] **Step 1: 编写 AgentBootstrap 测试**

Create: `agent/src/test/java/com/cq/agent/di/AgentBootstrapTest.java`

```java
package com.cq.agent.di;

import com.cq.agent.batch.scheduler.FileRetryScheduler;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentBootstrapTest {

    @Mock private HttpServer server;
    @Mock private AgentConfig config;
    @Mock private BatchTaskSchedulerUploaderDecorator batchTaskUploader;
    @Mock private FileRetryScheduler fileRetryScheduler;
    @Mock private AgentRegistryService registryService;

    @Test
    void shouldCreateAgentBootstrapWithDependencies() {
        AgentBootstrap bootstrap = new AgentBootstrap(server, config, batchTaskUploader,
                fileRetryScheduler, registryService);
        assertNotNull(bootstrap);
    }

    @Test
    void shouldStartAllRunningTasksOnStart() throws Exception {
        AgentBootstrap bootstrap = new AgentBootstrap(server, config, batchTaskUploader,
                fileRetryScheduler, registryService);
        when(server.getActualPort()).thenReturn(7777);
        doNothing().when(server).start();

        Thread testThread = new Thread(() -> {
            try {
                bootstrap.start();
            } catch (Exception e) {
                // expected - awaitTermination will be interrupted in test
            }
        });
        testThread.start();
        Thread.sleep(500);
        testThread.interrupt();
        testThread.join(2000);

        verify(batchTaskUploader).startAllRunningTasks();
    }

    @Test
    void shouldRegisterActualPortAndStartRegistry() throws Exception {
        AgentBootstrap bootstrap = new AgentBootstrap(server, config, batchTaskUploader,
                fileRetryScheduler, registryService);
        when(server.getActualPort()).thenReturn(7777);
        doNothing().when(server).start();

        Thread testThread = new Thread(() -> {
            try {
                bootstrap.start();
            } catch (Exception e) {
                // expected
            }
        });
        testThread.start();
        Thread.sleep(500);
        testThread.interrupt();
        testThread.join(2000);

        verify(registryService).setActualPort(7777);
        verify(registryService).start();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl agent -Dtest=AgentBootstrapTest`
Expected: FAIL (AgentBootstrap class does not exist yet)

- [ ] **Step 3: 创建 AgentBootstrap**

```java
package com.cq.agent.di;

import com.cq.agent.batch.scheduler.FileRetryScheduler;
import com.cq.agent.client.upload.BatchTaskSchedulerUploaderDecorator;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.registry.AgentRegistryService;
import com.cq.agent.server.HttpServer;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public class AgentBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(AgentBootstrap.class);

    private final HttpServer server;
    private final AgentConfig config;
    private final BatchTaskSchedulerUploaderDecorator batchTaskUploader;
    private final FileRetryScheduler fileRetryScheduler;
    private final AgentRegistryService registryService;

    @Inject
    public AgentBootstrap(HttpServer server,
                          AgentConfig config,
                          BatchTaskSchedulerUploaderDecorator batchTaskUploader,
                          FileRetryScheduler fileRetryScheduler,
                          AgentRegistryService registryService) {
        this.server = server;
        this.config = config;
        this.batchTaskUploader = batchTaskUploader;
        this.fileRetryScheduler = fileRetryScheduler;
        this.registryService = registryService;
    }

    public void start() throws InterruptedException {
        batchTaskUploader.startAllRunningTasks();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            registryService.stop();
            batchTaskUploader.shutdown();
            fileRetryScheduler.shutdown();
            server.stop();
        }));

        server.start();
        logger.info("Agent started successfully on port {}", server.getActualPort());

        registryService.setActualPort(server.getActualPort());
        registryService.start();

        printApiEndpoints(config);
        server.awaitTermination();
    }

    private void printApiEndpoints(AgentConfig config) {
        logger.info("");
        logger.info("Command API Endpoints:");
        logger.info("  GET  /api/health   - Health check");
        logger.info("  POST /api/execute  - Execute command (timeout: {}s)", config.getDefaultTimeoutSeconds());
        logger.info("");
        logger.info("File API Endpoints (FTP-like):");
        logger.info("  GET  /api/file/syst     - System info");
        logger.info("  GET  /api/file/feat     - Supported features");
        logger.info("  GET  /api/file/list     - List directory (LIST)");
        logger.info("  GET  /api/file/nlst     - Name list (NLST)");
        logger.info("  GET  /api/file/retr     - Retrieve file (RETR)");
        logger.info("  POST /api/file/stor     - Store file (STOR)");
        logger.info("  POST /api/file/stou     - Store unique (STOU)");
        logger.info("  POST /api/file/appe     - Append to file (APPE)");
        logger.info("  DELETE /api/file/dele   - Delete file (DELE)");
        logger.info("  POST /api/file/mkd      - Make directory (MKD)");
        logger.info("  DELETE /api/file/rmd    - Remove directory (RMD)");
        logger.info("  GET  /api/file/pwd      - Print working directory (PWD)");
        logger.info("  GET  /api/file/size     - Get file size (SIZE)");
        logger.info("  GET  /api/file/mdtm     - Get modification time (MDTM)");
        logger.info("  POST /api/file/mfmt     - Set modification time (MFMT)");
        logger.info("  POST /api/file/rename   - Rename file (RNFR/RNTO)");
        logger.info("  POST /api/file/copy     - Copy file");
        logger.info("  GET  /api/file/stat     - File status (STAT)");
        logger.info("  GET  /api/file/exists   - Check if exists");
        logger.info("  POST /api/file/chmod    - Change permissions (CHMOD)");
        logger.info("  GET  /api/file/checksum - Calculate checksum (MD5/SHA1/SHA256)");
        logger.info("  GET  /api/file/search   - Search files");
        logger.info("  GET  /api/file/disk     - Disk space info");
        logger.info("");
        logger.info("File base directory: {}", Path.of(config.getFileBaseDirectory()).toAbsolutePath().normalize());
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl agent -Dtest=AgentBootstrapTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/cq/agent/di/AgentBootstrap.java agent/src/test/java/com/cq/agent/di/AgentBootstrapTest.java
git commit -m "feat(agent): add AgentBootstrap for runtime startup orchestration"
```

---

### Task 7: 重构 AgentApplication.main() 使用 Guice Injector

**Files:**
- Modify: `agent/src/main/java/com/cq/agent/AgentApplication.java`

- [ ] **Step 1: 重写 AgentApplication**

将整个 `AgentApplication.java` 替换为：

```java
package com.cq.agent;

import com.cq.agent.di.AgentBootstrap;
import com.cq.agent.di.AgentModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentApplication {

    private static final Logger logger = LoggerFactory.getLogger(AgentApplication.class);

    public static void main(String[] args) {
        try {
            Injector injector = Guice.createInjector(new AgentModule());
            AgentBootstrap bootstrap = injector.getInstance(AgentBootstrap.class);
            bootstrap.start();
        } catch (Exception e) {
            logger.error("Failed to start agent", e);
            System.exit(1);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl agent`
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行 AgentModuleTest 验证所有绑定正常**

Run: `mvn test -pl agent -Dtest=AgentModuleTest`
Expected: PASS

- [ ] **Step 4: 运行所有 Agent 测试确保无回归**

Run: `mvn test -pl agent`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/cq/agent/AgentApplication.java
git commit -m "refactor(agent): rewrite AgentApplication.main() to use Guice Injector"
```

---

### Task 8: 更新 AgentApplicationTest 并运行全量测试

**Files:**
- Modify: `agent/src/test/java/com/cq/agent/AgentApplicationTest.java`

- [ ] **Step 1: 更新 AgentApplicationTest**

```java
package com.cq.agent;

import com.cq.agent.di.AgentModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AgentApplicationTest {

    @Test
    public void testAgentApplication() {
        assertTrue(true, "Test framework is working");
    }

    @Test
    public void testGuiceInjectorCreation() {
        Injector injector = Guice.createInjector(new AgentModule());
        assertNotNull(injector, "Guice Injector should be created successfully");
    }

    @Test
    public void testAgentBootstrapCreation() {
        Injector injector = Guice.createInjector(new AgentModule());
        com.cq.agent.di.AgentBootstrap bootstrap = injector.getInstance(com.cq.agent.di.AgentBootstrap.class);
        assertNotNull(bootstrap, "AgentBootstrap should be created via Guice");
    }
}
```

- [ ] **Step 2: 运行全量测试**

Run: `mvn test -pl agent`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add agent/src/test/java/com/cq/agent/AgentApplicationTest.java
git commit -m "test(agent): update AgentApplicationTest with Guice injector tests"
```

---

### Task 9: 最终验证

- [ ] **Step 1: 完整编译 Agent 模块**

Run: `mvn clean compile -pl agent`
Expected: BUILD SUCCESS

- [ ] **Step 2: 运行全量测试**

Run: `mvn test -pl agent`
Expected: All tests PASS

- [ ] **Step 3: 确认所有新增文件已 git add**

```bash
git status
```

确认以下文件已添加：
- `agent/pom.xml`
- `agent/src/main/java/com/cq/agent/di/AgentModule.java`
- `agent/src/main/java/com/cq/agent/di/AgentBootstrap.java`
- `agent/src/main/java/com/cq/agent/di/provider/ConfigFileManagerProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/FileBatchCompletionTrackerProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/ProgressReporterProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/AgentRegistryServiceProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/AgentUploaderProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/AgentDownloaderProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/RetryAwareUploaderProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/BatchTaskSchedulerProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/ConfigChangeListenerProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/FallbackPersistenceServiceProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/FileRetrySchedulerProvider.java`
- `agent/src/main/java/com/cq/agent/di/provider/HttpServerProvider.java`
- `agent/src/main/java/com/cq/agent/AgentApplication.java`
- `agent/src/test/java/com/cq/agent/di/AgentModuleTest.java`
- `agent/src/test/java/com/cq/agent/di/AgentBootstrapTest.java`
- `agent/src/test/java/com/cq/agent/AgentApplicationTest.java`
