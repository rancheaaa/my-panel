package com.cq.agent.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * 配置变更监听器
 * 监听配置变更并触发相应的更新操作
 * 支持Cron、模式、重试配置、目标Agent等维度的变更检测
 */
public class ConfigChangeListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeListener.class);

    private final ConfigFileManager configFileManager;
    private final VersionManager versionManager;
    
    private Consumer<Long> cronChangeHandler;
    private Consumer<PatternChangeContext> patternChangeHandler;
    private Consumer<RetryConfigChangeContext> retryConfigChangeHandler;
    private Consumer<TargetAgentsChangeContext> targetAgentsChangeHandler;
    private Consumer<AnyChangeContext> anyChangeHandler;

    public ConfigChangeListener(ConfigFileManager configFileManager, VersionManager versionManager) {
        this.configFileManager = configFileManager;
        this.versionManager = versionManager;
    }

    /**
     * 检测并应用配置变更
     * @param newConfig 新收到的配置
     * @return true表示有变更，false表示无变更
     */
    public boolean detectAndApplyChange(BatchTransferTaskConfig newConfig) {
        Long taskId = newConfig.getTaskId();
        Long newVersion = newConfig.getVersion();
        
        if (!versionManager.acceptVersion(taskId, newVersion)) {
            log.debug("版本未变化: taskId={}, version={}", taskId, newVersion);
            // 幂等：即使版本相同，也保存配置（确保持久化）
            configFileManager.saveTaskConfig(newConfig);
            return false;
        }
        
        BatchTransferTaskConfig oldConfig = configFileManager.loadTaskConfig(taskId);
        
        if (oldConfig == null) {
            log.info("新任务配置: taskId={}", taskId);
            saveAndNotify(newConfig, "NEW_TASK");
            return true;
        }
        
        boolean changed = false;
        
        if (hasFieldChanged(oldConfig.getCronExpression(), newConfig.getCronExpression())) {
            notifyCronChange(taskId);
            changed = true;
        }
        
        if (hasListChanged(oldConfig.getIncludePatterns(), newConfig.getIncludePatterns()) ||
            hasListChanged(oldConfig.getExcludePatterns(), newConfig.getExcludePatterns())) {
            notifyPatternChange(taskId, newConfig.getIncludePatterns());
            changed = true;
        }
        
        if (oldConfig.getMaxRetries() != newConfig.getMaxRetries()) {
            notifyRetryConfigChange(taskId, newConfig.getMaxRetries());
            changed = true;
        }
        
        if (hasListChanged(oldConfig.getTargetAgentIds(), newConfig.getTargetAgentIds())) {
            notifyTargetAgentsChange(taskId, newConfig.getTargetAgentIds());
            changed = true;
        }
        
        // 无论内容是否变化，version已更新，都要保存配置（热更新场景）
        if (changed) {
            saveAndNotify(newConfig, "CONFIG_UPDATED");
        } else {
            log.debug("版本更新但内容相同，仍保存配置: taskId={}", taskId);
            configFileManager.saveTaskConfig(newConfig);
        }
        
        return true;
    }

    // ==================== 事件注册方法 ====================

    public void onCronChange(Consumer<Long> handler) {
        this.cronChangeHandler = handler;
    }

    public void onPatternChange(Consumer<PatternChangeContext> handler) {
        this.patternChangeHandler = handler;
    }

    public void onRetryConfigChange(Consumer<RetryConfigChangeContext> handler) {
        this.retryConfigChangeHandler = handler;
    }

    public void onTargetAgentsChange(Consumer<TargetAgentsChangeContext> handler) {
        this.targetAgentsChangeHandler = handler;
    }

    public void onAnyChange(Consumer<AnyChangeContext> handler) {
        this.anyChangeHandler = handler;
    }

    /**
     * 关闭监听器
     */
    public void shutdown() {
        log.info("ConfigChangeListener已关闭");
    }

    // ==================== 内部方法 ====================

    private void notifyCronChange(Long taskId) {
        log.info("🔄 Cron变更: taskId={}", taskId);
        if (cronChangeHandler != null) {
            cronChangeHandler.accept(taskId);
        }
    }

    private void notifyPatternChange(Long taskId, List<String> patterns) {
        log.info("📋 模式变更: taskId={}, patterns={}", taskId, patterns);
        if (patternChangeHandler != null) {
            patternChangeHandler.accept(new PatternChangeContext(taskId, patterns));
        }
    }

    private void notifyRetryConfigChange(Long taskId, int maxRetries) {
        log.info("🔁 重试配置变更: taskId={}, maxRetries={}", taskId, maxRetries);
        if (retryConfigChangeHandler != null) {
            retryConfigChangeHandler.accept(new RetryConfigChangeContext(taskId, maxRetries));
        }
    }

    private void notifyTargetAgentsChange(Long taskId, List<String> agents) {
        log.info("🎯 目标Agent变更: taskId={}, agents={}", taskId, agents);
        if (targetAgentsChangeHandler != null) {
            targetAgentsChangeHandler.accept(new TargetAgentsChangeContext(taskId, agents));
        }
    }

    private void saveAndNotify(BatchTransferTaskConfig config, String changeType) {
        configFileManager.saveTaskConfig(config);
        if (anyChangeHandler != null) {
            anyChangeHandler.accept(new AnyChangeContext(config.getTaskId(), changeType));
        }
    }

    private boolean hasFieldChanged(String oldVal, String newVal) {
        if (oldVal == null && newVal == null) return false;
        if (oldVal == null || newVal == null) return true;
        return !oldVal.equals(newVal);
    }

    private boolean hasListChanged(List<String> oldList, List<String> newList) {
        if (oldList == null && newList == null) return false;
        if (oldList == null || newList == null) return true;
        return !oldList.equals(newList);
    }

    // ==================== 变更上下文类 ====================

    public static class PatternChangeContext {
        public final Long taskId;
        public final List<String> patterns;
        
        public PatternChangeContext(Long taskId, List<String> patterns) {
            this.taskId = taskId;
            this.patterns = patterns;
        }
    }

    public static class RetryConfigChangeContext {
        public final Long taskId;
        public final int maxRetries;
        
        public RetryConfigChangeContext(Long taskId, int maxRetries) {
            this.taskId = taskId;
            this.maxRetries = maxRetries;
        }
    }

    public static class TargetAgentsChangeContext {
        public final Long taskId;
        public final List<String> agents;
        
        public TargetAgentsChangeContext(Long taskId, List<String> agents) {
            this.taskId = taskId;
            this.agents = agents;
        }
    }

    public static class AnyChangeContext {
        public final Long taskId;
        public final String field;
        
        public AnyChangeContext(Long taskId, String field) {
            this.taskId = taskId;
            this.field = field;
        }
    }
}
