package com.cq.agent.batch.config;

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
 * 配置文件管理器
 * 管理Agent本地的批量传输任务配置文件
 * 支持原子写入、并发安全、损坏恢复
 */
public class ConfigFileManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileManager.class);
    
    private final String configDir;
    private final Gson gson;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final Map<Long, TaskMetaInfo> metaCache = new ConcurrentHashMap<>();
    private final Map<Long, BatchTransferTaskConfig> configCache = new ConcurrentHashMap<>();

    public ConfigFileManager(String configDir) {
        this.configDir = configDir;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        try {
            Files.createDirectories(Paths.get(configDir));
            loadExistingMeta();
        } catch (IOException e) {
            log.error("创建配置目录失败: {}", e.getMessage());
            throw new RuntimeException("Failed to create config dir", e);
        }
    }

    /**
     * 保存任务配置（原子操作）
     */
    public void saveTaskConfig(BatchTransferTaskConfig config) {
        lock.writeLock().lock();
        try {
            String json = gson.toJson(config);
            Path targetFile = Paths.get(configDir, "task_" + config.getTaskId() + ".json");
            Path tempFile = Paths.get(configDir, "task_" + config.getTaskId() + ".tmp");
            
            atomicWrite(tempFile, json);
            
            if (Files.exists(targetFile)) {
                Files.delete(targetFile);
            }
            
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
            
            updateMetaCache(config.getTaskId(), config.getTaskName());
            configCache.put(config.getTaskId(), config);
            
            log.info("✅ 保存配置成功: taskId={}, file={}", 
                config.getTaskId(), targetFile.getFileName());
                
        } catch (Exception e) {
            log.error("❌ 保存配置失败: taskId={}", config.getTaskId(), e);
            throw new RuntimeException("Failed to save config", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 加载任务配置
     */
    public BatchTransferTaskConfig loadTaskConfig(Long taskId) {
        lock.readLock().lock();
        try {
            Path configFile = Paths.get(configDir, "task_" + taskId + ".json");
            
            if (!Files.exists(configFile)) {
                return null;
            }
            
            try {
                String content = readFileSafely(configFile);
                BatchTransferTaskConfig config = gson.fromJson(content, 
                    BatchTransferTaskConfig.class);
                configCache.put(taskId, config);
                return config;
            } catch (Exception e) {
                log.warn("⚠️  配置文件损坏，使用缓存: taskId={}", taskId);
                return configCache.get(taskId);
            }
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 删除任务配置
     */
    public void deleteTaskConfig(Long taskId) {
        lock.writeLock().lock();
        try {
            Path configFile = Paths.get(configDir, "task_" + taskId + ".json");
            
            if (Files.exists(configFile)) {
                Files.delete(configFile);
            }
            
            metaCache.remove(taskId);
            configCache.remove(taskId);
            
            log.info("🗑️  删除配置成功: taskId={}", taskId);
            
        } catch (Exception e) {
            log.error("❌ 删除配置失败: taskId={}", taskId, e);
            throw new RuntimeException("Failed to delete config", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 加载所有任务元数据
     */
    public List<TaskMetaInfo> loadAllTaskMeta() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(metaCache.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 更新任务元数据
     */
    public void updateTasksMeta(TaskMetaInfo meta) {
        lock.writeLock().lock();
        try {
            metaCache.put(meta.getTaskId(), meta);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== 内部方法 ====================

    private void atomicWrite(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardOpenOption.CREATE, 
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
    }

    private String readFileSafely(Path path) throws IOException {
        return new String(Files.readAllBytes(path));
    }

    private void updateMetaCache(Long taskId, String taskName) {
        TaskMetaInfo meta = new TaskMetaInfo(taskId, taskName, System.currentTimeMillis());
        metaCache.put(taskId, meta);
    }

    @SuppressWarnings("unchecked")
    private void loadExistingMeta() throws IOException {
        Path dirPath = Paths.get(configDir);
        
        if (!Files.exists(dirPath)) {
            return;
        }
        
        List<Path> jsonFiles = Files.list(dirPath)
            .filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".tmp"))
            .collect(Collectors.toList());
            
        for (Path file : jsonFiles) {
            try {
                String fileName = file.getFileName().toString();
                Long taskId = Long.parseLong(fileName.replace("task_", "").replace(".json", ""));
                
                String content = readFileSafely(file);
                BatchTransferTaskConfig config = gson.fromJson(content, 
                    BatchTransferTaskConfig.class);
                    
                updateMetaCache(taskId, config.getTaskName());
                configCache.put(taskId, config);
                
            } catch (Exception e) {
                log.warn("加载元数据失败: {}", file.getFileName(), e);
            }
        }
        
        log.info("✅ 加载已有配置: count={}", metaCache.size());
    }
}
