package com.cq.panel.admin.server.web.domain.dto.proxy;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * TCP端口探测响应数据
 * 对应 GET /forward/agent/tcp-probe 返回的data字段：
 * {"reachable":true,"host":"10.0.0.1","port":7777,"failureReason":null}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TcpProbeResultData {

    /** 是否可达 */
    private Boolean reachable;

    /** 目标主机IP */
    private String host;

    /** 目标端口 */
    private Integer port;

    /** 失败原因（可达时为null） */
    private String failureReason;
}
