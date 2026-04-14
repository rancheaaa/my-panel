package com.cq.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proxy.registry")
@Data
public class ProxyRegistryProperties {
    private long heartbeatTimeoutSeconds;
    private long offlineScanIntervalSeconds;
    private String zone;
    private int portRangeMin;
    private int portRangeMax;
}