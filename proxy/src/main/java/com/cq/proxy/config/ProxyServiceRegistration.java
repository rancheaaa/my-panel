package com.cq.proxy.config;

import com.cq.proxy.api.dto.ServiceRegisterRequest;
import com.cq.proxy.service.RegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProxyServiceRegistration implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ProxyServiceRegistration.class);

  private final RegistryService registryService;
  private final Environment environment;

  private String localHost;
  private int localPort;
  private String serviceName = "proxy-service";
  private String environmentName = "default";

  public ProxyServiceRegistration(RegistryService registryService, Environment environment) {
    this.registryService = registryService;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      this.localHost = getLocalHost();
      this.localPort = getLocalPort();

      ServiceRegisterRequest request = new ServiceRegisterRequest(
          serviceName,
          environmentName,
          localHost,
          localPort
      );

      registryService.register(request);
      log.info("Proxy服务注册成功: {}:{} ({})", localHost, localPort, serviceName);
    } catch (Exception e) {
      log.error("Proxy服务注册失败", e);
    }
  }

  @Scheduled(fixedDelayString = "${proxy.registry.heartbeat-interval-seconds:30}000")
  public void heartbeat() {
    try {
      ServiceRegisterRequest request = new ServiceRegisterRequest(
          serviceName,
          environmentName,
          localHost,
          localPort
      );

      registryService.register(request);
      log.debug("Proxy服务心跳更新: {}:{}", localHost, localPort);
    } catch (Exception e) {
      log.error("Proxy服务心跳更新失败", e);
    }
  }

  private String getLocalHost() {
    String host = environment.getProperty("server.address", "0.0.0.0");
    if ("0.0.0.0".equals(host)) {
      try {
        host = java.net.InetAddress.getLocalHost().getHostAddress();
      } catch (Exception e) {
        log.warn("无法获取本地IP地址，使用默认值", e);
        host = "127.0.0.1";
      }
    }
    return host;
  }

  private int getLocalPort() {
    return environment.getProperty("server.port", Integer.class, 9876);
  }
}