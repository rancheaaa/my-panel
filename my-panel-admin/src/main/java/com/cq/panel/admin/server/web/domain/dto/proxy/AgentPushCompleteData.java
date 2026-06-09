package com.cq.panel.admin.server.web.domain.dto.proxy;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Agent配置推送完成响应数据
 * 对应Agent端 POST /api/config/push/complete 返回的data字段：{"filesCount":5}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentPushCompleteData {

    /** 推送的文件数量 */
    private Integer filesCount;
}
