package com.cq.proxy.config;

import com.cq.panel.common.utils.PortUtils;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

@Component
public class RandomPortConfig implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private static final int MIN_PORT = 8000;
    private static final int MAX_PORT = 9000;

    private final ProxyRegistryProperties registryProperties;

    public RandomPortConfig(ProxyRegistryProperties registryProperties) {
        this.registryProperties = registryProperties;
    }

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        factory.setPort(findAvailablePort());
    }

    private int findAvailablePort() {

        for (int port = registryProperties.getPortRangeMin(); port <= registryProperties.getPortRangeMax(); port++) {
            if (PortUtils.isPortAvailable(port)) {
                return port;
            }
        }
        throw new RuntimeException("No available port found in range [" + MIN_PORT + ", " + MAX_PORT + "]");
       }
}