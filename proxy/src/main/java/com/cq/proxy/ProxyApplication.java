package com.cq.proxy;

import com.cq.proxy.netty.ProxyNettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class ProxyApplication {

  private static final Logger log = LoggerFactory.getLogger(ProxyApplication.class);

  public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(ProxyApplication.class, args);
    ProxyNettyServer server = context.getBean(ProxyNettyServer.class);
    server.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.stop();
      } catch (Exception e) {
        log.error("Failed to stop proxy netty server", e);
      }
    }, "proxy-shutdown"));
  }
}

