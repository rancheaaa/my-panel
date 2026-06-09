package com.cq.panel.admin.server.web.domain.dto.proxy;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Proxy Ping检测响应数据
 * 对应 GET /forward/ping 返回的data字段：{"status":"UP","timestamp":1234567890}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PingResult {

    /** 服务状态：UP/DOWN */
    private String status;

    /** 时间戳（毫秒） */
    private Long timestamp;
}
