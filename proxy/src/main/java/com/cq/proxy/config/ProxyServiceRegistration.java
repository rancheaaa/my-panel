package com.cq.proxy.config;

import com.cq.panel.common.constant.EnvNameConstant;
import com.cq.proxy.dto.ServiceRegisterRequest;
import com.cq.proxy.service.RegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.net.SocketException;

import static com.cq.panel.common.constant.ServiceNameConstant.PROXY_SERVICE_NAME;

@Component
public class ProxyServiceRegistration implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ProxyServiceRegistration.class);

  private final RegistryService registryService;
  private final Environment environment;
  private final ProxyRegistryProperties registryProperties;

  private String localHost;
  private int localPort;
  private final String serviceName = PROXY_SERVICE_NAME;
  private final String environmentName = EnvNameConstant.DEFAULT_ENV_NAME;

  public ProxyServiceRegistration(RegistryService registryService, Environment environment, ProxyRegistryProperties registryProperties) {
    this.registryService = registryService;
    this.environment = environment;
    this.registryProperties = registryProperties;
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
          localPort,
          registryProperties.zone()
      );

      registryService.register(request);
      log.info("Proxy服务注册成功: {}:{} ({})", localHost, localPort, serviceName);
    } catch (Exception e) {
      log.error("Proxy服务注册失败", e);
    }
  }

  @Scheduled(fixedDelayString = "${proxy.registry.heartbeat-interval-seconds:10}000")
  public void heartbeat() {
    try {
      ServiceRegisterRequest request = new ServiceRegisterRequest(
          serviceName,
          environmentName,
          localHost,
          localPort,
          registryProperties.zone()

      );

      registryService.register(request);
      log.debug("Proxy服务心跳更新: {}:{}", localHost, localPort);
    } catch (Exception e) {
      log.error("Proxy服务心跳更新失败", e);
    }
  }

  private String getLocalHost() {
    // 首先检查是否有配置的server.address
    String configuredHost = environment.getProperty("server.address");
    if (configuredHost != null && !configuredHost.isBlank() && !"0.0.0.0".equals(configuredHost)) {
      return configuredHost;
    }
    
    // 使用Spring Boot的工具类获取本机IP
    try {
      // 方法1: 尝试获取非回环地址
      java.net.InetAddress address = getPreferredInetAddress();
      if (address != null && !address.isLoopbackAddress()) {
        return address.getHostAddress();
      }
      
      // 方法2: 获取所有网络接口的IP地址
      java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        java.net.NetworkInterface networkInterface = interfaces.nextElement();
        if (networkInterface.isLoopback() || !networkInterface.isUp()) {
          continue;
        }
        
        java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
          java.net.InetAddress addr = addresses.nextElement();
          if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
            return addr.getHostAddress();
          }
        }
      }
      
      // 方法3: 回退到传统方式
      return java.net.InetAddress.getLocalHost().getHostAddress();
      
    } catch (Exception e) {
      log.warn("无法获取本地IP地址，使用默认值127.0.0.1", e);
      return "127.0.0.1";
    }
  }
  
  /**
   * 获取首选网络地址，参考Spring Cloud的实现
   */
  private java.net.InetAddress getPreferredInetAddress() {
    try {
      // 优先获取非回环的IPv4地址
      java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        java.net.NetworkInterface networkInterface = interfaces.nextElement();
        if (shouldIgnoreInterface(networkInterface)) {
          continue;
        }
        
        java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
          java.net.InetAddress address = addresses.nextElement();
          if (isPreferredAddress(address)) {
            return address;
          }
        }
      }
    } catch (Exception e) {
      log.debug("获取首选网络地址失败", e);
    }
    return null;
  }
  
  /**
   * 判断是否应该忽略该网络接口
   */
  private boolean shouldIgnoreInterface(java.net.NetworkInterface networkInterface) {
      try {
          return networkInterface.isLoopback() ||
                 !networkInterface.isUp() ||
                 networkInterface.isVirtual() ||
                 networkInterface.isPointToPoint();
      } catch (SocketException e) {
          return true;
      }
  }
  
  /**
   * 判断是否为优选地址
   */
  private boolean isPreferredAddress(java.net.InetAddress address) {
    return !address.isLoopbackAddress() && 
           address instanceof java.net.Inet4Address &&
           !address.isLinkLocalAddress() &&
           !address.isMulticastAddress();
  }

  private int getLocalPort() {
    return environment.getProperty("server.port", Integer.class, 9876);
  }
}