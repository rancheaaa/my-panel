package com.cq.proxy.manager;

import com.cq.panel.common.constant.EnvNameConstant;
import com.cq.panel.common.utils.IpUtils;
import com.cq.proxy.service.RcDictionaryService;
import com.cq.proxy.repository.mapper.RcNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;

import static com.cq.panel.common.constant.ServiceNameConstant.PROXY_SERVICE_NAME;

/**
 * Proxy服务关闭管理器
 * 确保应用退出时能自动将Proxy服务标记为下线状态
 *
 * @author cq
 */
@Component
public class ProxyShutdownManager {

    private static final Logger log = LoggerFactory.getLogger(ProxyShutdownManager.class);

    private final RcDictionaryService dictionaryService;
    private final RcNodeMapper nodeMapper;

    private String localHost;
    private int localPort;

    public ProxyShutdownManager(RcDictionaryService dictionaryService,
                               RcNodeMapper nodeMapper) {
        this.dictionaryService = dictionaryService;
        this.nodeMapper = nodeMapper;
    }

    /**
     * 监听WebServer初始化事件，获取本地主机和端口信息
     */
    @EventListener
    public void onPortReady(WebServerInitializedEvent event) {
        try {
            this.localHost = IpUtils.getLocalHost();
            this.localPort = event.getWebServer().getPort();
            log.info("ProxyShutdownManager初始化完成，服务地址: {}:{}", localHost, localPort);
        } catch (Exception e) {
            log.error("ProxyShutdownManager初始化失败", e);
        }
    }

    /**
     * 应用关闭时执行，将Proxy服务标记为下线
     */
    @PreDestroy
    public void destroy() {
        try {
            log.info("====开始关闭Proxy服务，标记服务为下线状态====");
            
            if (localHost != null && localPort > 0) {
                // 直接通过数据库操作标记为下线
                markProxyOfflineDirectly();
                
                log.info("Proxy服务下线处理完成: {}:{}", localHost, localPort);
            } else {
                log.warn("无法获取Proxy服务地址信息，跳过下线处理");
            }
        } catch (Exception e) {
            log.error("Proxy服务下线处理失败", e);
        }
    }

    /**
     * 直接通过数据库操作将Proxy服务标记为下线
     */
    private void markProxyOfflineDirectly() {
        try {
            // 获取环境ID和项目ID
            String environmentName = EnvNameConstant.DEFAULT_ENV_NAME;
            long envId = dictionaryService.getOrCreateEnvId(environmentName);
            String serviceName = PROXY_SERVICE_NAME;
            long projectId = dictionaryService.getOrCreateProjectId(serviceName);
            
            // 查询当前Proxy服务的注册节点
            var nodes = nodeMapper.selectByEnvProjectIpPort(envId, projectId, localHost, localPort);
            
            if (!nodes.isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                for (var node : nodes) {
                    // 将状态设置为下线（1表示下线，0表示在线）
                    node.setStatus("1");
                    node.setUpdateTime(now);
                    nodeMapper.updateById(node);
                    log.info("标记Proxy服务节点为下线: {}:{} ({})", localHost, localPort, serviceName);
                }
            } else {
                log.warn("未找到Proxy服务注册节点: {}:{} ({})", localHost, localPort, serviceName);
            }
        } catch (Exception e) {
            log.error("直接标记Proxy服务下线失败", e);
            throw e;
        }
    }
}