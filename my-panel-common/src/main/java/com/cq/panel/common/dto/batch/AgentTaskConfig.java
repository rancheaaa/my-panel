package com.cq.panel.common.dto.batch;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量传输任务配置 (task_{taskId}.json)
 * Admin和Agent共用的统一配置Bean，确保字段数量和格式与spec.md设计一致
 *
 * @see <a href="spec.md">spec.md - 单个任务配置文件格式</a>
 */
public class AgentTaskConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long taskId;

    /** 任务名称 */
    private String taskName;

    /** 状态: READY, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED */
    private String status;

    /** 配置版本号 (yyyyMMddHHmmss格式的时间戳) */
    private String version;

    /** Agent接收时间 (ISO-8601) */
    private String receivedAt;

    /** 持久化完成时间 (ISO-8601) */
    private String persistedAt;

    /** 源Agent ID */
    private String sourceAgentId;

    /** 源Agent名称 (ip:port格式) */
    private String sourceAgentName;

    /** 源目录 */
    private String sourceDir;

    /** 目标Agent列表 */
    private List<TargetAgentInfo> targetAgents;

    /** 文件包含模式 */
    private List<String> includePatterns;

    /** 文件排除模式 */
    private List<String> excludePatterns;

    /** 扫描配置 */
    private ScanConfig scanConfig;

    /** 传输配置 */
    private TransferConfig transferConfig;

    /** 重试配置 */
    private RetryConfig retryConfig;

    /** 任务启动时间 (ISO-8601) */
    private String startedAt;

    /** 备份目录绝对路径 (postTransferAction=BACKUP时必填) */
    private String backupDir;

    /** 备份模式: COPY-复制, MOVE-移动 */
    private String backupMode;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getPersistedAt() {
        return persistedAt;
    }

    public void setPersistedAt(String persistedAt) {
        this.persistedAt = persistedAt;
    }

    public String getSourceAgentId() {
        return sourceAgentId;
    }

    public void setSourceAgentId(String sourceAgentId) {
        this.sourceAgentId = sourceAgentId;
    }

    public String getSourceAgentName() {
        return sourceAgentName;
    }

    public void setSourceAgentName(String sourceAgentName) {
        this.sourceAgentName = sourceAgentName;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    public List<TargetAgentInfo> getTargetAgents() {
        return targetAgents;
    }

    public void setTargetAgents(List<TargetAgentInfo> targetAgents) {
        this.targetAgents = targetAgents;
    }

    public List<String> getIncludePatterns() {
        return includePatterns;
    }

    public void setIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public ScanConfig getScanConfig() {
        return scanConfig;
    }

    public void setScanConfig(ScanConfig scanConfig) {
        this.scanConfig = scanConfig;
    }

    public TransferConfig getTransferConfig() {
        return transferConfig;
    }

    public void setTransferConfig(TransferConfig transferConfig) {
        this.transferConfig = transferConfig;
    }

    public RetryConfig getRetryConfig() {
        return retryConfig;
    }

    public void setRetryConfig(RetryConfig retryConfig) {
        this.retryConfig = retryConfig;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getBackupDir() {
        return backupDir;
    }

    public void setBackupDir(String backupDir) {
        this.backupDir = backupDir;
    }

    public String getBackupMode() {
        return backupMode;
    }

    public void setBackupMode(String backupMode) {
        this.backupMode = backupMode;
    }
}
