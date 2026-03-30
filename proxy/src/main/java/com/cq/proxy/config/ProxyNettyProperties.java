package com.cq.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proxy.netty")
public record ProxyNettyProperties(
    int port,
    int bossThreads,
    int workerThreads,
    int soBacklog,
    boolean tcpNodelay,
    boolean soKeepalive,
    int idleSeconds
) {
}

