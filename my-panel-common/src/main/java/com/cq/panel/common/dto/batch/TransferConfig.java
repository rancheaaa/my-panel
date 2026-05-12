package com.cq.panel.common.dto.batch;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serial;
import java.io.Serializable;

/**
 * 传输配置 (transferConfig)
 * 
 * 细分为4个子类别：
 * 1. 基础设置 - 传输模式、目录结构
 * 2. 路由控制 - 策略选择与详细配置
 * 3. 性能控制 - 带宽限制
 * 4. 传输后操作 - 删除/备份策略
 */
@Data
@NoArgsConstructor
public class TransferConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 1. 基础设置 ====================

    /**
     * 传输模式:
     * - ONE_TO_ONE: 一对一（每个源文件只传到一个目标）
     * - ONE_TO_MANY: 一对多（每个源文件传到所有目标）
     */
    private String transferMode;

    /**
     * 是否保留原始目录结构: true-保留, false-不保留(扁平化)
     */
    private boolean preserveDirStructure;

    // ==================== 2. 路由控制 ====================

    /**
     * 路由策略:
     * - BROADCAST: 广播（发送到所有目标）
     * - ROUND_ROBIN: 轮询（循环分配到不同目标）
     * - RANDOM: 随机（随机选择目标）
     * - REGION_BASED: 区域优先（根据区域标签路由）
     */
    private String routingStrategy;

    /**
     * 路由策略详细配置 (JSON格式)
     * 
     * 示例：
     * - REGION_BASED: {"regionMapping": {"region-a": ["agent-001","agent-002"],
     * "region-b": ["agent-003"]}}
     * - ROUND_ROBIN: {"startIndex": 0}
     * - 自定义权重: {"weights": {"agent-001": 3, "agent-002": 7}}
     */
    private String routingConfig;

    // ==================== 3. 性能控制 ====================

    /** 最大带宽限制(KB/s), 0或null表示不限速 */
    private Integer maxBandwidthKbS;

    // ==================== 4. 传输后操作 ====================

    /**
     * 传输成功后的操作:
     * - NONE: 不做任何处理（默认）
     * - DELETE: 删除源文件
     * - BACKUP: 备份源文件到backupDir
     */
    private String postTransferAction;

    /** 备份目录绝对路径 (postTransferAction=BACKUP时必填) */
    private String backupDir;

    /**
     * 备份模式 (postTransferAction=BACKUP时有效):
     * - COPY: 复制（保留原文件）
     * - MOVE: 移动（删除原文件）
     */
    private String backupMode;
}
