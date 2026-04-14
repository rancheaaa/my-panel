package com.cq.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proxy.cache")
public record ProxyCacheProperties(
    long maximumSize,
    long expireAfterWriteSeconds
) {
}

