package com.cq.agent.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

import com.cq.panel.common.dto.batch.AgentTaskConfig;
import com.cq.panel.common.dto.batch.ScanConfig;
import com.cq.panel.common.dto.batch.TransferConfig;
import com.cq.panel.common.dto.batch.RetryConfig;

/**
 * 配置变更监听器
 * 监听配置变更并触发相应的更新操作
 * 支持所有配置字段的变更检测和热更新
 */
public class ConfigChangeListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeListener.class);

    private final ConfigFileManager configFileManager;
    private final VersionManager versionManager;

    private Consumer<Long> cronChangeHandler;
    private Consumer<PatternChangeContext> patternChangeHandler;
    private Consumer<RetryConfigChangeContext> retryConfigChangeHandler;
    private Consumer<TargetAgentsChangeContext> targetAgentsChangeHandler;
    private Consumer<TransferConfigChangeContext> transferConfigChangeHandler;
    private Consumer<AnyChangeContext> anyChangeHandler;

    public ConfigChangeListener(ConfigFileManager configFileManager, VersionManager versionManager) {
        this.configFileManager = configFileManager;
        this.versionManager = versionManager;
    }

    /**
     * 检测并应用配置变更
     * 
     * @param newConfig 新收到的配置
     * @return true表示有变更，false表示无变更
     */
    public boolean detectAndApplyChange(AgentTaskConfig newConfig) {
        Long taskId = newConfig.getTaskId();
        long newVersion = newConfig.getVersion() != null ? Long.parseLong(newConfig.getVersion()) : 0L;

        if (!versionManager.acceptVersion(taskId, newVersion)) {
            log.debug("版本未变化: taskId={}, version={}", taskId, newVersion);
            configFileManager.saveTaskConfig(newConfig);
            return false;
        }

        AgentTaskConfig oldConfig = configFileManager.loadTaskConfig(taskId);

        if (oldConfig == null) {
            log.info("新任务配置: taskId={}", taskId);
            saveAndNotify(newConfig, "NEW_TASK");
            return true;
        }

        boolean changed = false;
        List<String> changedFields = new ArrayList<>();

        // 1. Cron表达式变更
        String oldCron = getCronExpression(oldConfig);
        String newCron = getCronExpression(newConfig);
        if (hasFieldChanged(oldCron, newCron)) {
            notifyCronChange(taskId);
            changed = true;
            changedFields.add("cronExpression");
        }

        // 2. 文件模式变更 (includePatterns/excludePatterns)
        if (hasListChanged(oldConfig.getIncludePatterns(), newConfig.getIncludePatterns()) ||
                hasListChanged(oldConfig.getExcludePatterns(), newConfig.getExcludePatterns())) {
            notifyPatternChange(taskId, newConfig.getIncludePatterns());
            changed = true;
            changedFields.add("patterns");
        }

        // 3. ScanConfig完整比较 (cronExpression + maxScanFiles)
        if (hasScanConfigChanged(oldConfig, newConfig)) {
            changed = true;
            if (!changedFields.contains("cronExpression")) {
                changedFields.add("scanConfig");
            }
        }

        // 4. RetryConfig完整比较 (enabled, maxDays, intervalMin, maxRetryCount,
        // backoffType)
        if (hasRetryConfigChanged(oldConfig, newConfig)) {
            RetryConfig newRetryConfig = newConfig.getRetryConfig();
            int newMaxRetries = newRetryConfig != null && newRetryConfig.getMaxRetryCount() != null
                    ? newRetryConfig.getMaxRetryCount()
                    : 0;
            notifyRetryConfigChange(taskId, newMaxRetries);
            changed = true;
            changedFields.add("retryConfig");
        }

        // 5. TargetAgents变更 (agentIds, agentNames, targetDirs)
        if (hasTargetAgentsChanged(oldConfig, newConfig)) {
            List<String> newAgentIds = extractAgentIds(newConfig.getTargetAgents());
            notifyTargetAgentsChange(taskId, newAgentIds);
            changed = true;
            changedFields.add("targetAgents");
        }

        // 6. TransferConfig完整比较 (postTransferAction, routingStrategy,
        // preserveDirStructure, maxBandwidthKbS)
        if (hasTransferConfigChanged(oldConfig, newConfig)) {
            notifyTransferConfigChange(taskId, newConfig.getTransferConfig());
            changed = true;
            changedFields.add("transferConfig");
        }

        // 7. 基础字段变更 (sourceDir, backupDir, backupMode, status)
        if (hasBasicFieldsChanged(oldConfig, newConfig)) {
            changed = true;
            changedFields.add("basicFields");
        }

        // 保存配置并通知
        if (changed) {
            log.info("🔄 配置变更检测: taskId={}, changedFields={}", taskId, changedFields);
            saveAndNotify(newConfig, "CONFIG_UPDATED");
        } else {
            log.debug("版本更新但内容相同，仍保存配置: taskId={}", taskId);
            configFileManager.saveTaskConfig(newConfig);
        }

        return true;
    }

    // ==================== 字段比较辅助方法 ====================

    private String getCronExpression(AgentTaskConfig config) {
        if (config != null && config.getScanConfig() != null) {
            return config.getScanConfig().getCronExpression();
        }
        return null;
    }

    private boolean hasScanConfigChanged(AgentTaskConfig oldConfig, AgentTaskConfig newConfig) {
        ScanConfig oldScan = oldConfig.getScanConfig();
        ScanConfig newScan = newConfig.getScanConfig();

        boolean cronChanged = hasFieldChanged(
                oldScan != null ? oldScan.getCronExpression() : null,
                newScan != null ? newScan.getCronExpression() : null);

        boolean maxFilesChanged = !Objects.equals(
                oldScan != null ? oldScan.getMaxScanFiles() : null,
                newScan != null ? newScan.getMaxScanFiles() : null);

        return cronChanged || maxFilesChanged;
    }

    private boolean hasRetryConfigChanged(AgentTaskConfig oldConfig, AgentTaskConfig newConfig) {
        RetryConfig oldRetry = oldConfig.getRetryConfig();
        RetryConfig newRetry = newConfig.getRetryConfig();

        boolean enabledChanged = !Objects.equals(
                oldRetry != null ? oldRetry.isEnabled() : null,
                newRetry != null ? newRetry.isEnabled() : null);

        boolean maxDaysChanged = !Objects.equals(
                oldRetry != null ? oldRetry.getMaxDays() : null,
                newRetry != null ? newRetry.getMaxDays() : null);

        boolean intervalMinChanged = !Objects.equals(
                oldRetry != null ? oldRetry.getIntervalMin() : null,
                newRetry != null ? newRetry.getIntervalMin() : null);

        boolean maxRetriesChanged = !Objects.equals(
                oldRetry != null ? oldRetry.getMaxRetryCount() : null,
                newRetry != null ? newRetry.getMaxRetryCount() : null);

        boolean backoffTypeChanged = hasFieldChanged(
                oldRetry != null ? oldRetry.getBackoffType() : null,
                newRetry != null ? newRetry.getBackoffType() : null);

        return enabledChanged || maxDaysChanged || intervalMinChanged || maxRetriesChanged || backoffTypeChanged;
    }

    private boolean hasTargetAgentsChanged(AgentTaskConfig oldConfig, AgentTaskConfig newConfig) {
        List<String> oldAgentIds = extractAgentIds(oldConfig.getTargetAgents());
        List<String> newAgentIds = extractAgentIds(newConfig.getTargetAgents());

        List<String> oldAgentNames = extractAgentNames(oldConfig.getTargetAgents());
        List<String> newAgentNames = extractAgentNames(newConfig.getTargetAgents());

        List<String> oldTargetDirs = extractTargetDirs(oldConfig.getTargetAgents());
        List<String> newTargetDirs = extractTargetDirs(newConfig.getTargetAgents());

        return hasListChanged(oldAgentIds, newAgentIds) ||
                hasListChanged(oldAgentNames, newAgentNames) ||
                hasListChanged(oldTargetDirs, newTargetDirs);
    }

    private boolean hasTransferConfigChanged(AgentTaskConfig oldConfig, AgentTaskConfig newConfig) {
        TransferConfig oldTransfer = oldConfig.getTransferConfig();
        TransferConfig newTransfer = newConfig.getTransferConfig();

        boolean actionChanged = hasFieldChanged(
                oldTransfer != null ? oldTransfer.getPostTransferAction() : null,
                newTransfer != null ? newTransfer.getPostTransferAction() : null);

        boolean strategyChanged = hasFieldChanged(
                oldTransfer != null ? oldTransfer.getRoutingStrategy() : null,
                newTransfer != null ? newTransfer.getRoutingStrategy() : null);

        boolean preserveDirChanged = !Objects.equals(
                oldTransfer != null ? oldTransfer.isPreserveDirStructure() : null,
                newTransfer != null ? newTransfer.isPreserveDirStructure() : null);

        boolean bandwidthChanged = !Objects.equals(
                oldTransfer != null ? oldTransfer.getMaxBandwidthKbS() : null,
                newTransfer != null ? newTransfer.getMaxBandwidthKbS() : null);

        return actionChanged || strategyChanged || preserveDirChanged || bandwidthChanged;
    }

    private boolean hasBasicFieldsChanged(AgentTaskConfig oldConfig, AgentTaskConfig newConfig) {
        boolean sourceDirChanged = hasFieldChanged(oldConfig.getSourceDir(), newConfig.getSourceDir());
        boolean backupDirChanged = hasFieldChanged(oldConfig.getBackupDir(), newConfig.getBackupDir());
        boolean backupModeChanged = hasFieldChanged(oldConfig.getBackupMode(), newConfig.getBackupMode());
        boolean statusChanged = hasFieldChanged(oldConfig.getStatus(), newConfig.getStatus());

        return sourceDirChanged || backupDirChanged || backupModeChanged || statusChanged;
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

    public void onTransferConfigChange(Consumer<TransferConfigChangeContext> handler) {
        this.transferConfigChangeHandler = handler;
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

    // ==================== 通知方法 ====================

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

    private void notifyTransferConfigChange(Long taskId, TransferConfig transferConfig) {
        log.info("🔧 传输配置变更: taskId={}, postTransferAction={}",
                taskId,
                transferConfig != null ? transferConfig.getPostTransferAction() : null);
        if (transferConfigChangeHandler != null) {
            transferConfigChangeHandler.accept(new TransferConfigChangeContext(taskId, transferConfig));
        }
    }

    private void saveAndNotify(AgentTaskConfig config, String changeType) {
        configFileManager.saveTaskConfig(config);
        if (anyChangeHandler != null) {
            anyChangeHandler.accept(new AnyChangeContext(config.getTaskId(), changeType));
        }
    }

    // ==================== 通用比较方法 ====================

    private boolean hasFieldChanged(String oldVal, String newVal) {
        if (oldVal == null && newVal == null)
            return false;
        if (oldVal == null || newVal == null)
            return true;
        return !oldVal.equals(newVal);
    }

    private boolean hasListChanged(List<String> oldList, List<String> newList) {
        if (oldList == null && newList == null)
            return false;
        if (oldList == null || newList == null)
            return true;
        return !oldList.equals(newList);
    }

    // ==================== TargetAgents提取方法 ====================

    private List<String> extractAgentIds(List<com.cq.panel.common.dto.batch.TargetAgentInfo> targetAgents) {
        List<String> ids = new ArrayList<>();
        if (targetAgents != null) {
            for (com.cq.panel.common.dto.batch.TargetAgentInfo agent : targetAgents) {
                if (agent.getAgentId() != null && !agent.getAgentId().isEmpty()) {
                    ids.add(agent.getAgentId());
                }
            }
        }
        return ids;
    }

    private List<String> extractAgentNames(List<com.cq.panel.common.dto.batch.TargetAgentInfo> targetAgents) {
        List<String> names = new ArrayList<>();
        if (targetAgents != null) {
            for (com.cq.panel.common.dto.batch.TargetAgentInfo agent : targetAgents) {
                if (agent.getAgentName() != null && !agent.getAgentName().isEmpty()) {
                    names.add(agent.getAgentName());
                }
            }
        }
        return names;
    }

    private List<String> extractTargetDirs(List<com.cq.panel.common.dto.batch.TargetAgentInfo> targetAgents) {
        List<String> dirs = new ArrayList<>();
        if (targetAgents != null) {
            for (com.cq.panel.common.dto.batch.TargetAgentInfo agent : targetAgents) {
                if (agent.getTargetDir() != null && !agent.getTargetDir().isEmpty()) {
                    dirs.add(agent.getTargetDir());
                }
            }
        }
        return dirs;
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

    public static class TransferConfigChangeContext {
        public final Long taskId;
        public final TransferConfig transferConfig;

        public TransferConfigChangeContext(Long taskId, TransferConfig transferConfig) {
            this.taskId = taskId;
            this.transferConfig = transferConfig;
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
