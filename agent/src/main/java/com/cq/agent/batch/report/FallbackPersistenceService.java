package com.cq.agent.batch.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 本地持久化补报服务
 * 当Proxy不可用时，将进度事件持久化到本地文件
 * 等待网络恢复后补报
 */
public class FallbackPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(FallbackPersistenceService.class);

    private final String storageDir;
    private final Gson gson;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final Map<Long, ProgressEvent> eventCache = new ConcurrentHashMap<>();

    public FallbackPersistenceService(String storageDir) {
        this.storageDir = storageDir;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        try {
            Files.createDirectories(Paths.get(storageDir));
            loadExistingEvents();
            log.info("✅ 本地持久化服务初始化: dir={}", storageDir);
        } catch (IOException e) {
            log.error("❌ 创建存储目录失败: {}", e.getMessage());
            throw new RuntimeException("Failed to init persistence service", e);
        }
    }

    /**
     * 持久化进度事件
     */
    public void persist(ProgressEvent event) {
        lock.writeLock().lock();
        try {
            String fileName = "event_" + event.getSubtaskId() + "_" + 
                System.currentTimeMillis() + ".json";
            
            Path filePath = Paths.get(storageDir, fileName);
            String json = gson.toJson(event);
            
            Files.writeString(filePath, json, StandardOpenOption.CREATE, 
                StandardOpenOption.TRUNCATE_EXISTING);
                
            eventCache.put(event.getSubtaskId(), event);
            
            log.debug("💾 事件已持久化: subtask={}, file={}", 
                event.getSubtaskId(), fileName);
                
        } catch (IOException e) {
            log.error("❌ 持久化失败: subtask={}, error={}", 
                event.getSubtaskId(), e.getMessage());
            throw new RuntimeException("Persist failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 加载所有未上报事件
     */
    public List<ProgressEvent> loadAll() {
        lock.readLock().lock();
        try {
            List<ProgressEvent> events = new ArrayList<>();
            
            try (var paths = Files.newDirectoryStream(Paths.get(storageDir), "*.json")) {
                for (Path path : paths) {
                    try {
                        String content = Files.readString(path);
                        ProgressEvent event = gson.fromJson(content, ProgressEvent.class);
                        events.add(event);
                    } catch (Exception e) {
                        log.warn("⚠️  加载事件失败: {}, error={}", path.getFileName(), e.getMessage());
                    }
                }
            }
            
            events.sort(Comparator.comparingLong(ProgressEvent::getTimestamp));
            
            return events;
            
        } catch (IOException e) {
            log.error("❌ 加载事件失败: {}", e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 删除已成功上报的事件
     */
    public void deleteReported(List<Long> reportedSubtaskIds) {
        lock.writeLock().lock();
        try {
            for (Long subtaskId : reportedSubtaskIds) {
                eventCache.remove(subtaskId);
                
                // 删除对应的JSON文件
                try (var paths = Files.newDirectoryStream(Paths.get(storageDir))) {
                    List<Path> pathList = new ArrayList<>();
                    for (Path p : paths) {
                        pathList.add(p);
                    }
                    
                    for (Path path : pathList) {
                        if (path.toString().contains("event_" + subtaskId + "_")) {
                            Files.deleteIfExists(path);
                            log.debug("🗑️  已删除已上报事件: subtask={}, file={}", 
                                subtaskId, path.getFileName());
                            break; // 只删除最新的一个
                        }
                    }
                }
            }
            
            log.info("🗑️  已清理{}个已上报事件", reportedSubtaskIds.size());
            
        } catch (Exception e) {
            log.error("❌ 清理失败: {}", e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取待补报的事件数量
     */
    public int getPendingCount() {
        return loadAll().size();
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        log.info("FallbackPersistenceService已关闭");
    }

    // ==================== 内部方法 ====================

    private void loadExistingEvents() throws IOException {
        Path dirPath = Paths.get(storageDir);
        
        if (!Files.exists(dirPath)) {
            return;
        }
        
        try (var paths = Files.newDirectoryStream(dirPath, "*.json")) {
            for (Path path : paths) {
                try {
                    String content = Files.readString(path);
                    ProgressEvent event = gson.fromJson(content, ProgressEvent.class);
                    eventCache.put(event.getSubtaskId(), event);
                } catch (Exception e) {
                    log.warn("⚠️  加载已有事件失败: {}", path.getFileName());
                }
            }
        }
        
        log.info("✅ 加载已有待补报事件: count={}", eventCache.size());
    }
}
