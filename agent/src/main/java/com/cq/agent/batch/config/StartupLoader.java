package com.cq.agent.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 启动加载器
 * Agent启动时加载本地配置，恢复运行中任务的调度器
 */
public class StartupLoader {

    private static final Logger log = LoggerFactory.getLogger(StartupLoader.class);

    private final ConfigFileManager configFileManager;
    
    private Consumer<Long> schedulerRecreator;

    public StartupLoader(ConfigFileManager configFileManager) {
        this.configFileManager = configFileManager;
    }

    /**
     * 加载所有本地任务配置
     * @return 任务配置列表（可能为空）
     */
    public List<BatchTransferTaskConfig> load() {
        log.info("🚀 开始加载本地配置...");
        
        TaskMetaInfo metaInfo = configFileManager.loadMetaInfo();
        List<TaskMetaInfo.TaskMeta> metaList = metaInfo.getTasks();
        
        if (metaList == null || metaList.isEmpty()) {
            log.info("✅ 首次启动: 空配置，等待Proxy推送");
            return List.of();
        }
        
        List<BatchTransferTaskConfig> loadedTasks = metaList.stream()
            .map(meta -> {
                try {
                    BatchTransferTaskConfig config = configFileManager.loadTaskConfig(meta.getTaskId());
                    if (config != null && "RUNNING".equals(config.getStatus())) {
                        recreateScheduler(meta.getTaskId());
                    }
                    return config;
                } catch (Exception e) {
                    log.warn("⚠️  加载任务失败: taskId={}, error={}", 
                        meta.getTaskId(), e.getMessage());
                    return null;
                }
            })
            .filter(config -> config != null)
            .collect(Collectors.toList());
        
        log.info("✅ 配置加载完成: total={}, success={}", 
            metaList.size(), loadedTasks.size());
            
        return loadedTasks;
    }

    /**
     * 设置调度器重建回调
     */
    public void setSchedulerRecreator(Consumer<Long> recreator) {
        this.schedulerRecreator = recreator;
    }

    /**
     * 重建运行中任务的调度器
     */
    private void recreateScheduler(Long taskId) {
        log.info("🔄 重建调度器: taskId={}", taskId);
        if (schedulerRecreator != null) {
            schedulerRecreator.accept(taskId);
        }
    }
}
