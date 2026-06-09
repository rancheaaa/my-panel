package com.cq.panel.admin.server.web.domain.dto.proxy;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Agent配置推送会话初始化响应数据
 * 对应Agent端 POST /api/config/push/init 返回的data字段：{"sessionId":"xxx"}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentPushSessionData {

    /** 会话ID */
    private String sessionId;
}
