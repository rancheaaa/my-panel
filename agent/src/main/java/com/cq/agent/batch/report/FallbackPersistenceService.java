package com.cq.agent.batch.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 本地持久化补报服务
 * 当Proxy不可用时，将进度事件持久化到本地文件系统
 * 支持启动时加载待补报事件，网络恢复后自动补报
 */
public class FallbackPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(FallbackPersistenceService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss").create();
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final String PENDING_FILE_PREFIX = "pending-";
    private static final String PENDING_FILE_SUFFIX = ".json";

    // 默认配置
    public static final long DEFAULT_SCAN_INTERVAL_SECONDS = 30;
    public static final int DEFAULT_BATCH_SIZE = 10;
    public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;

    private final Path storageDir;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * -- SETTER --
     *  设置ProgressReporter用于自动补报
     */
    // 自动补报相关
    @Setter
    private ProgressReporter progressReporter;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean autoRetryEnabled = new AtomicBoolean(false);
    private long scanIntervalSeconds = DEFAULT_SCAN_INTERVAL_SECONDS;
    private int batchSize = DEFAULT_BATCH_SIZE;

    public FallbackPersistenceService(String storageDir) {
        this.storageDir = Paths.get(storageDir);
        initializeStorageDir();
        loadExistingEvents();
        log.info("✅ 本地持久化服务初始化: dir={}", storageDir);
    }

    /**
     * 配置自动补报参数
     * @param scanIntervalSeconds 扫描间隔（秒）
     * @param batchSize 每次处理的批次大小
     */
    public void configureAutoRetry(long scanIntervalSeconds, int batchSize) {
        this.scanIntervalSeconds = scanIntervalSeconds;
        this.batchSize = batchSize;
        log.info("⚙️ 自动补报配置: interval={}s, batch={}", scanIntervalSeconds, batchSize);
    }

    /**
     * 启动定时自动补报任务
     */
    public void startAutoRetry() {
        if (progressReporter == null) {
            log.warn("⚠️ 未设置ProgressReporter，无法启动自动补报");
            return;
        }

        if (autoRetryEnabled.compareAndSet(false, true)) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fallback-retry-scheduler");
                t.setDaemon(true);
                return t;
            });

            scheduler.scheduleAtFixedRate(this::scanAndRetry, 
                scanIntervalSeconds, scanIntervalSeconds, TimeUnit.SECONDS);

            log.info("🔄 自动补报已启动: interval={}s", scanIntervalSeconds);
        } else {
            log.warn("⚠️ 自动补报已在运行中");
        }
    }

    /**
     * 停止定时自动补报任务
     */
    public void stopAutoRetry() {
        if (autoRetryEnabled.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            log.info("⏹️ 自动补报已停止");
        }
    }

    /**
     * 手动触发一次补报（立即执行）
     */
    public void retryNow() {
        if (progressReporter == null) {
            log.warn("⚠️ 未设置ProgressReporter，无法执行手动补报");
            return;
        }
        
        log.info("🔧 手动触发补报...");
        scanAndRetry();
    }

    /**
     * 持久化事件到本地
     */
    public void persist(SubTaskEvent event) {
        lock.writeLock().lock();
        try {
            String fileName = PENDING_FILE_PREFIX + event.getSubtaskId() + "-" +
                LocalDateTime.now().format(FILE_DATE_FORMAT) + PENDING_FILE_SUFFIX;
            Path filePath = storageDir.resolve(fileName);

            String json = GSON.toJson(event);
            Files.writeString(filePath, json, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

            log.debug("✅ 事件已持久化: subtaskId={}, file={}", event.getSubtaskId(), fileName);
        } catch (FileAlreadyExistsException e) {
            log.warn("⚠️ 文件已存在，跳过: subtaskId={}", event.getSubtaskId());
        } catch (IOException e) {
            log.error("❌ 持久化失败: subtaskId={}, error={}", event.getSubtaskId(), e.getMessage());
            throw new RuntimeException("Failed to persist event", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 加载所有待补报事件
     */
    public List<SubTaskEvent> loadAll() {
        lock.readLock().lock();
        try {
            if (!Files.exists(storageDir)) {
                return new ArrayList<>();
            }

            List<SubTaskEvent> events = Files.list(storageDir)
                .filter(path -> path.getFileName().toString().startsWith(PENDING_FILE_PREFIX))
                .sorted(Comparator.comparing(Path::getFileName))
                .map(this::loadFromFile)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

            log.debug("✅ 加载待补报事件: count={}", events.size());
            return events;
        } catch (IOException e) {
            log.error("❌ 加载失败: error={}", e.getMessage());
            throw new RuntimeException("Failed to load pending events", e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 删除已上报的事件文件
     */
    public void deleteReported(List<Long> reportedSubtaskIds) {
        lock.writeLock().lock();
        try {
            AtomicInteger deletedCount = new AtomicInteger(0);
            for (Long subtaskId : reportedSubtaskIds) {
                try {
                    Files.list(storageDir)
                        .filter(path -> path.getFileName().toString()
                            .contains(PENDING_FILE_PREFIX + subtaskId))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                                deletedCount.incrementAndGet();
                            } catch (IOException e) {
                                log.warn("⚠️ 删除失败: file={}, error={}", path, e.getMessage());
                            }
                        });
                } catch (IOException e) {
                    log.warn("⚠️ 清理失败: subtaskId={}, error={}", subtaskId, e.getMessage());
                }
            }
            log.info("🗑️ 已清理{}个已上报事件, remaining={}", deletedCount.get(),
                getSafePendingCount() - deletedCount.get());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取待补报事件数量
     */
    public long getPendingCount() {
        return getSafePendingCount();
    }

    public void shutdown() {
        stopAutoRetry();
        log.info("FallbackPersistenceService已关闭");
    }

    // ==================== 内部方法 ====================

    /**
     * 扫描并重试待补报事件
     */
    private void scanAndRetry() {
        try {
            List<SubTaskEvent> pendingEvents = loadAll();

            if (pendingEvents.isEmpty()) {
                log.debug("✅ 无待补报事件");
                return;
            }

            log.info("🔍 发现{}个待补报事件，开始批量处理...", pendingEvents.size());

            List<SubTaskEvent> batch = pendingEvents.stream()
                .limit(batchSize)
                .toList();

            List<Long> successIds = new ArrayList<>();
            int failedCount = 0;

            for (SubTaskEvent event : batch) {
                boolean success = retrySingleEvent(event);
                
                if (success) {
                    successIds.add(event.getSubtaskId());
                } else {
                    failedCount++;
                }
            }

            // 删除成功上报的事件
            if (!successIds.isEmpty()) {
                deleteReported(successIds);
                log.info("✅ 补报完成: 成功={}, 失败={}, 剩余待处理={}", 
                    successIds.size(), failedCount, getSafePendingCount());
            } else if (failedCount > 0) {
                log.warn("❌ 本批次全部失败: count={}", failedCount);
            }

        } catch (Exception e) {
            log.error("❌ 补报扫描异常: error={}", e.getMessage(), e);
        }
    }

    /**
     * 重试单个事件
     */
    private boolean retrySingleEvent(SubTaskEvent event) {
        try {
            log.debug("🔄 尝试补报: subtaskId={}, status={}", event.getSubtaskId(), event.getStatus());
            progressReporter.report(event, true);
            return true;
        } catch (Exception e) {
            log.error("❌ 补报异常: subtaskId={}, error={}", event.getSubtaskId(), e.getMessage());
            return false;
        }
    }

    private void initializeStorageDir() {
        if (!Files.exists(storageDir)) {
            try {
                Files.createDirectories(storageDir);
                log.info("✅ 创建存储目录: {}", storageDir.toAbsolutePath());
            } catch (IOException e) {
                log.error("❌ 创建存储目录失败: error={}", e.getMessage());
                throw new RuntimeException("Failed to create storage directory", e);
            }
        }
    }

    private void loadExistingEvents() {
        try {
            long pendingCount = getSafePendingCount();
            if (pendingCount > 0) {
                log.info("✅ 加载已有待补报事件: count={}", pendingCount);
            }
        } catch (Exception e) {
            log.warn("⚠️ 检查已有事件失败: error={}", e.getMessage());
        }
    }

    private SubTaskEvent loadFromFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            return GSON.fromJson(content, SubTaskEvent.class);
        } catch (Exception e) {
            log.error("❌ 加载文件失败: file={}, error={}", filePath.getFileName(), e.getMessage());
            return null;
        }
    }

    private long countPendingFiles() throws IOException {
        if (!Files.exists(storageDir)) {
            return 0;
        }
        return Files.list(storageDir)
            .filter(path -> path.getFileName().toString().startsWith(PENDING_FILE_PREFIX))
            .count();
    }

    private long getSafePendingCount() {
        try {
            return countPendingFiles();
        } catch (IOException e) {
            log.warn("⚠️ 获取待处理文件数失败: error={}", e.getMessage());
            return 0;
        }
    }
}
