package com.cq.proxy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PortListener {

    private static final Logger logger = LoggerFactory.getLogger(PortListener.class);

    @EventListener
    public void portReady(WebServerInitializedEvent event) {
        int realPort = event.getWebServer().getPort();
        logger.info("✅ 实际启动端口：{}", realPort);
    }
}