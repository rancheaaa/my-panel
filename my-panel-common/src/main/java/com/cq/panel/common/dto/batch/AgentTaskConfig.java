package com.cq.panel.common.dto.batch;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量传输任务配置 (task_{taskId}.json)
 * 
 * Admin和Agent共用的统一配置Bean，确保字段数量和格式与batch_transfer_task表一致
 *
 * @see <a href="spec.md">spec.md - 单个任务配置文件格式</a>
 */
@Data
@NoArgsConstructor
public class AgentTaskConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 1. 基础信息 ====================

    /** 任务ID */
    private Long taskId;

    /** 任务名称 */
    private String taskName;

    /** 任务描述 */
    private String taskDescription;

    /** 状态: READY, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED */
    private String status;

    /** 配置版本号 (yyyyMMddHHmmss格式的时间戳) */
    private String version;

    // ==================== 2. 时间戳 ====================

    /** Agent接收时间 (ISO-8601) */
    private String receivedAt;

    /** 持久化完成时间 (ISO-8601) */
    private String persistedAt;

    /** 任务启动时间 (ISO-8601) */
    private String startedAt;

    // ==================== 3. 源信息 ====================

    /** 源Agent ID */
    private String sourceAgentId;

    /** 源Agent名称 (ip:port格式) */
    private String sourceAgentName;

    /** 源目录绝对路径 */
    private String sourceDir;

    // ==================== 4. 目标信息 ====================

    /** 目标Agent列表 (包含agentId, agentName, targetDir) */
    private List<TargetAgentInfo> targetAgents;

    // ==================== 5. 文件过滤 ====================

    /** 文件包含模式 (通配符, 如["*.log","*.txt"]) */
    private List<String> includePatterns;

    /** 文件排除模式 (通配符, 如["debug*","temp*"]) */
    private List<String> excludePatterns;

    // ==================== 6. 嵌套配置对象 ====================

    /**
     * 扫描调度配置
     * 
     * @see ScanConfig
     */
    private ScanConfig scanConfig;

    /**
     * 传输配置（路由、带宽、传输后操作等）
     * 
     * @see TransferConfig
     */
    private TransferConfig transferConfig;

    /**
     * 重试配置（重试次数、间隔、退避策略等）
     * 
     * @see RetryConfig
     */
    private RetryConfig retryConfig;
}
