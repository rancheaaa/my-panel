package com.cq.panel.admin.server.config;

import com.cq.panel.admin.server.service.proxy.ProxyServerList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author cq 2026/6/9 16:02
 * @since 1.0.0
 */
@Configuration
public class ProxyLoadBalancerConfig {

    @Bean
    public ProxyServerList proxyServerList(@Value("${app.proxy-urls::http://localhost:9876}") String proxyUrls) {
        return ProxyServerList.fromUrls(proxyUrls);
    }
}
