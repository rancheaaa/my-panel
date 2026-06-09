package com.cq.panel.admin.server.service.proxy;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Proxy负载均衡配置
 */
@Data
@Configuration
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

    /**
     * 从逗号分隔的URL字符串创建ProxyServerList
     * 支持多地址负载均衡：http://10.0.0.1:9876,http://10.0.0.2:9876
     */
    @Bean
    public ProxyServerList proxyServerList(
            @Value("${app.proxy-urls:${app.proxy-url:http://localhost:9876}}") String proxyUrls) {
        return ProxyServerList.fromUrls(proxyUrls);
    }
}
