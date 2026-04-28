package com.cq.proxy.config;

import com.cq.panel.common.constant.EnvNameConstant;
import com.cq.panel.common.utils.IpUtils;
import com.cq.proxy.dto.ServiceRegisterRequest;
import com.cq.proxy.service.RegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.cq.panel.common.constant.ServiceNameConstant.PROXY_SERVICE_NAME;

@Component
public class ProxyServiceRegistration {

    private static final Logger log = LoggerFactory.getLogger(ProxyServiceRegistration.class);

    private final RegistryService registryService;
    private final ProxyRegistryProperties registryProperties;

    private String localHost;
    private int localPort;
    private final String serviceName = PROXY_SERVICE_NAME;
    private final String environmentName = EnvNameConstant.DEFAULT_ENV_NAME;
    private volatile boolean firstRegistrationSuccessFlag = false;
    private volatile ServiceRegisterRequest serviceRegisterRequest;

    public ProxyServiceRegistration(RegistryService registryService, ProxyRegistryProperties registryProperties) {
    this.registryService = registryService;
    this.registryProperties = registryProperties;
  }

  @EventListener
  public void onPortReady(WebServerInitializedEvent event) {
    try {
      this.localHost = IpUtils.getLocalHost();
      // 直接从事件中获取实际运行的端口
      this.localPort = event.getWebServer().getPort();

            ServiceRegisterRequest request = new ServiceRegisterRequest(
                    serviceName,
                    environmentName,
                    localHost,
                    localPort,
                    registryProperties.getZone()
            );
            serviceRegisterRequest = request;
            registryService.register(request);
            firstRegistrationSuccessFlag = true;
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
                    registryProperties.getZone()

            );

            registryService.heartbeat(request);
            if (!firstRegistrationSuccessFlag && serviceRegisterRequest != null) {
                registryService.register(serviceRegisterRequest);
                firstRegistrationSuccessFlag = true;
            }
            log.debug("Proxy服务心跳更新: {}:{}", localHost, localPort);
        } catch (Exception e) {
            log.error("Proxy服务心跳更新失败", e);
        }
    }
}