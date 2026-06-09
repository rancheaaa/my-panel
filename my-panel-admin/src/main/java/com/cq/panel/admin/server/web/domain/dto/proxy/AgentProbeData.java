package com.cq.panel.admin.server.web.domain.dto.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Agent连通性探测响应数据
 * 对应Agent端 GET /api/probe?host=&port= 返回的data字段：
 * {"reachable":true,"details":["✅ ..."]}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentProbeData {

    /** 是否可达 */
    private Boolean reachable;

    /** 探测详情列表 */
    private List<String> details;
}
