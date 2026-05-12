package com.cq.panel.common.dto.batch;

import java.io.Serial;
import java.io.Serializable;

/**
 * 传输配置 (transferConfig)
 */
public class TransferConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 路由策略: ROUND_ROBIN, RANDOM, REGION_BASED, BROADCAST */
    private String routingStrategy;

    /** 最大带宽限制(KB/s) */
    private Integer maxBandwidthKbS;

    /** 是否保留目录结构 */
    private Boolean preserveDirStructure;

    /** 传输后动作: NONE, DELETE_SOURCE, COMPRESS_TARGET */
    private String postTransferAction;

    public String getRoutingStrategy() {
        return routingStrategy;
    }

    public void setRoutingStrategy(String routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    public Integer getMaxBandwidthKbS() {
        return maxBandwidthKbS;
    }

    public void setMaxBandwidthKbS(Integer maxBandwidthKbS) {
        this.maxBandwidthKbS = maxBandwidthKbS;
    }

    public Boolean isPreserveDirStructure() {
        return preserveDirStructure;
    }

    public void setPreserveDirStructure(Boolean preserveDirStructure) {
        this.preserveDirStructure = preserveDirStructure;
    }

    public String getPostTransferAction() {
        return postTransferAction;
    }

    public void setPostTransferAction(String postTransferAction) {
        this.postTransferAction = postTransferAction;
    }
}
