package com.cq.panel.admin.server.service.proxy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Proxy负载均衡配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.proxy")
public class ProxyLoadBalancerConfig {

    /** 是否启用健康检查 */
    private boolean healthCheckEnabled = true;

    /** 健康检查间隔（毫秒） */
    private long healthCheckInterval = 30000;

    /** 连续失败N次标记宕机 */
    private int healthCheckFailureThreshold = 3;

    /** 连续成功N次标记恢复 */
    private int healthCheckSuccessThreshold = 2;

    /** 请求失败重试次数（不含首次请求） */
    private int retryCount = 2;
}
