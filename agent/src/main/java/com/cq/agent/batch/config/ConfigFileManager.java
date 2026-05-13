package com.cq.agent.batch.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.cq.panel.common.dto.batch.AgentTaskConfig;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置文件管理器
 * 管理Agent本地JSON配置文件的读写
 * 符合spec.md设计要求：维护tasks.meta.json元数据索引
 */
public class ConfigFileManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String configDir;
    private final AtomicFileWriter atomicFileWriter;
    private final Map<Long, TaskMetaInfo.TaskMeta> metaCache = new ConcurrentHashMap<>();

    public ConfigFileManager(String configDir) {
        this.configDir = configDir;
        this.atomicFileWriter = new AtomicFileWriter();
        new File(configDir).mkdirs();
        loadExistingMeta();
    }

    /**
     * 保存任务配置到本地JSON文件
     * 同时更新tasks.meta.json
     */
    public void saveTaskConfig(AgentTaskConfig config) {
        String filename = "task_" + config.getTaskId() + ".json";
        File file = new File(configDir, filename);

        try {
            // 使用原子写入：临时文件→校验→重命名
            String jsonContent = GSON.toJson(config);
            atomicFileWriter.writeAtomic(file, jsonContent);
            log.info("✅ 保存配置成功: taskId={}, file={}", config.getTaskId(), filename);

            // 更新meta缓存
            TaskMetaInfo.TaskMeta meta = new TaskMetaInfo.TaskMeta();
            meta.setTaskId(config.getTaskId());
            meta.setTaskName(config.getTaskName());
            meta.setStatus(config.getStatus());
            meta.setVersion(config.getVersion() != null ? Long.parseLong(config.getVersion()) : null);
            meta.setReceivedAt(config.getReceivedAt());
            meta.setPersistedAt(config.getPersistedAt());
            meta.setLastModified(System.currentTimeMillis());
            metaCache.put(config.getTaskId(), meta);

            // 持久化tasks.meta.json
            persistMetaFile();

        } catch (RuntimeException e) {
            log.error("❌ 保存配置失败: taskId={}, error={}", config.getTaskId(), e.getMessage());
            throw e;
        }
    }

    /**
     * 加载指定任务的配置
     */
    public AgentTaskConfig loadTaskConfig(Long taskId) {
        String filename = "task_" + taskId + ".json";
        File file = new File(configDir, filename);

        if (!file.exists()) {
            log.info("配置不存在: taskId={}", taskId);
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, AgentTaskConfig.class);
        } catch (IOException e) {
            log.error("❌ 加载配置失败: taskId={}, error={}", taskId, e.getMessage());
            return null;
        }
    }

    /**
     * 加载所有任务配置
     */
    public List<AgentTaskConfig> loadAllTaskConfigs() {
        List<AgentTaskConfig> configs = new ArrayList<>();
        File dir = new File(configDir);
        File[] files = dir.listFiles((d, name) -> name.startsWith("task_") && name.endsWith(".json"));

        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    AgentTaskConfig config = GSON.fromJson(reader, AgentTaskConfig.class);
                    if (config != null) {
                        configs.add(config);
                    }
                } catch (IOException e) {
                    log.error("❌ 加载配置失败: file={}, error={}", file.getName(), e.getMessage());
                }
            }
        }

        log.info("✅ 加载所有配置: count={}", configs.size());
        return configs;
    }

    /**
     * 删除任务配置
     * 同时更新tasks.meta.json
     */
    public void deleteTaskConfig(Long taskId) {
        String filename = "task_" + taskId + ".json";
        File file = new File(configDir, filename);

        if (file.exists()) {
            file.delete();
            log.info("🗑️  删除配置: taskId={}", taskId);
        }

        metaCache.remove(taskId);
        persistMetaFile();
    }

    /**
     * 更新任务状态
     * 同时更新tasks.meta.json
     */
    public void updateTaskStatus(Long taskId, String status) {
        AgentTaskConfig config = loadTaskConfig(taskId);
        if (config != null) {
            config.setStatus(status);
            saveTaskConfig(config);
            log.info("📝 更新任务状态: taskId={}, status={}", taskId, status);
        }
    }

    /**
     * 加载tasks.meta.json
     */
    public TaskMetaInfo loadMetaInfo() {
        File metaFile = new File(configDir, "tasks.meta.json");
        if (!metaFile.exists()) {
            TaskMetaInfo emptyMeta = new TaskMetaInfo();
            emptyMeta.setTasks(new ArrayList<>());
            return emptyMeta;
        }

        try (FileReader reader = new FileReader(metaFile)) {
            TaskMetaInfo meta = GSON.fromJson(reader, TaskMetaInfo.class);
            if (meta == null || meta.getTasks() == null) {
                meta = new TaskMetaInfo();
                meta.setTasks(new ArrayList<>());
            }
            return meta;
        } catch (IOException e) {
            log.error("❌ 加载meta失败: error={}", e.getMessage());
            TaskMetaInfo emptyMeta = new TaskMetaInfo();
            emptyMeta.setTasks(new ArrayList<>());
            return emptyMeta;
        }
    }

    /**
     * 获取meta缓存
     */
    public Map<Long, TaskMetaInfo.TaskMeta> getMetaCache() {
        return metaCache;
    }

    // ==================== 内部方法 ====================

    private void loadExistingMeta() {
        File metaFile = new File(configDir, "tasks.meta.json");
        if (metaFile.exists()) {
            try (FileReader reader = new FileReader(metaFile)) {
                TaskMetaInfo loaded = GSON.fromJson(reader, TaskMetaInfo.class);
                if (loaded != null && loaded.getTasks() != null) {
                    for (TaskMetaInfo.TaskMeta meta : loaded.getTasks()) {
                        if (meta.getTaskId() != null) {
                            metaCache.put(meta.getTaskId(), meta);
                        }
                    }
                }
                log.info("✅ 加载已有配置: count={}", metaCache.size());
            } catch (IOException e) {
                log.warn("⚠️  加载meta失败: {}", e.getMessage());
            }
        }
    }

    private void persistMetaFile() {
        File metaFile = new File(configDir, "tasks.meta.json");
        try {
            TaskMetaInfo metaInfo = new TaskMetaInfo();
            metaInfo.setTasks(new ArrayList<>(metaCache.values()));
            String jsonContent = GSON.toJson(metaInfo);
            atomicFileWriter.writeAtomic(metaFile, jsonContent);
        } catch (RuntimeException e) {
            log.error("❌ 持久化meta失败: {}", e.getMessage());
        }
    }
}
