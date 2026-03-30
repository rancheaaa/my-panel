package com.cq.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proxy.registry")
public record ProxyRegistryProperties(
    long heartbeatTimeoutSeconds,
    long offlineScanIntervalSeconds
) {
}

