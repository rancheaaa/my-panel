package com.cq.agent.registry;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.AgentRegistryRequest;
import com.cq.agent.dto.AgentRegistryResponse;
import com.cq.agent.dto.ApiResponse;
import com.cq.panel.common.constant.AgentNodeStatusConstant;
import com.cq.panel.common.utils.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent注册服务
 * 负责自动注册到服务端和定期发送心跳
 * 
 * @author cq
 */
public class AgentRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistryService.class);
    
    private final AgentConfig config;
    private final AgentRegistryClient registryClient;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean registered;
    private final AtomicBoolean running;
    private int actualPort;
    
    private AgentRegistryResponse registryInfo;

    public AgentRegistryService(AgentConfig config) {
        this.config = config;
        this.registryClient = new AgentRegistryClient(config);
        // 手动创建单线程调度器
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "agent-registry-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        this.registered = new AtomicBoolean(false);
        this.running = new AtomicBoolean(false);
    }

    /**
     * 启动注册服务
     */
    public void start() {
        if (!registryClient.isConfigured()) {
            logger.info("Registry server URL is not configured, skipping auto-registration and heartbeat");
            return;
        }
        
        if (running.compareAndSet(false, true)) {
            logger.info("Starting agent registry service...");
            
            // 立即尝试注册
            if (config.isAutoRegister()) {
                register();
            }
            
            // 启动心跳定时任务
            if (config.isAutoHeartbeat()) {
                int interval = config.getHeartbeatIntervalSeconds();
                logger.info("Starting heartbeat task with interval: {} seconds", interval);
                scheduler.scheduleAtFixedRate(this::heartbeat, interval, interval, TimeUnit.SECONDS);
            }
            
            logger.info("Agent registry service started successfully");
        } else {
            logger.warn("Agent registry service is already running");
        }
    }

    /**
     * 停止注册服务
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping agent registry service...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            try {
                AgentRegistryRequest request = buildDownRegistryRequest();
                logger.info("DeRegistering agent: {}", request);

                registryClient.register(request);
            } catch (IOException | InterruptedException e) {
                //
            }
            registryClient.close();
            logger.info("Agent registry service stopped");
        }
    }

    /**
     * 注册到服务端
     */
    public void register() {
        try {
            AgentRegistryRequest request = buildUpRegistryRequest();
            logger.info("Registering agent: {}", request);
            
            ApiResponse<AgentRegistryResponse> response = registryClient.register(request);
            
            if (response != null && response.isSuccess() && response.getData() != null) {
                this.registryInfo = response.getData();
                this.registered.set(true);
            } else {
                this.registered.set(false);
                String errorMsg = response != null ? response.getMsg() : "Unknown error";
                logger.error("Agent registration failed: {}", errorMsg);
            }
        } catch (Exception e) {
            this.registered.set(false);
            logger.error("Agent registration failed with exception", e);
        }
    }

    /**
     * 发送心跳
     */
    public void heartbeat() {
        if (!isRegistered()) {
            logger.debug("Agent is not registered, skipping heartbeat");
            return;
        }
        
        try {
            String agentIp = config.getAgentIp();
            int agentPort = actualPort > 0 ? actualPort : config.getServerPort();
            
            boolean success = registryClient.heartbeat(agentIp, agentPort);
            
            if (success) {
                logger.debug("Heartbeat sent successfully");
            } else {
                logger.warn("Heartbeat failed, will try to re-register");
            }
        } catch (Exception e) {
            logger.error("Heartbeat failed with exception", e);
        }
    }

    private AgentRegistryRequest buildUpRegistryRequest() {
        return buildUpRegistryRequest(AgentNodeStatusConstant.UP);
    }

    private AgentRegistryRequest buildDownRegistryRequest() {
        return buildUpRegistryRequest(AgentNodeStatusConstant.DOWN);
    }

    /**
     * 构建注册请求
     * 
     * @return 注册请求
     */
    private AgentRegistryRequest buildUpRegistryRequest(Integer nodeStatus) {
        AgentRegistryRequest request = new AgentRegistryRequest();

        // Agent IP (先获取，用于构建节点名称)
        String agentIp = config.getAgentIp();
        if (agentIp == null || agentIp.isEmpty()) {
            try {
                agentIp = IpUtils.getLocalHost();
            } catch (UnknownHostException | SocketException e) {
                agentIp = "127.0.0.1";
            }
        }
        request.setAgentIp(agentIp);
        
        // 节点名称 (格式: user@ip:port)
        String nodeName = config.getNodeName();
        if (nodeName == null || nodeName.isEmpty()) {
            String userName = config.getAppId() != null && !config.getAppId().isEmpty() ? 
                config.getAppId() : System.getProperty("user.name", "unknown");
            int port = actualPort > 0 ? actualPort : config.getServerPort();
            nodeName = userName + "@" + agentIp + ":" + port;
        }
        request.setNodeName(nodeName);
        
        // 操作系统
        String osType = config.getOsType();
        if (osType == null || osType.isEmpty()) {
            osType = detectOSType();
        }
        request.setOsType(osType);
        
        // 应用ID
        if (config.getAppId() == null || config.getAppId().isEmpty()) {
            final String userName = System.getProperty("user.name");
            logger.debug("set appId to {}", userName);
            config.setAppId(userName);
        }
        request.setAppId(config.getAppId());
        
        // Agent端口
        int port = actualPort > 0 ? actualPort : config.getServerPort();
        request.setAgentPort(port);
        
        // 节点状态
        request.setNodeStatus(nodeStatus);
        
        return request;
    }

    /**
     * 检测操作系统类型
     * 
     * @return 操作系统类型
     */
    private String detectOSType() {
        String osName = System.getProperty("os.name", "unknown").toLowerCase();
        
        if (osName.contains("win")) {
            return "Windows";
        } else if (osName.contains("mac")) {
            return "Mac";
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return "Linux";
        } else if (osName.contains("sunos")) {
            return "Solaris";
        } else {
            return "Other";
        }
    }

    /**
     * 检查是否已注册
     * 
     * @return 是否已注册
     */
    public boolean isRegistered() {
        return registered.get();
    }

    /**
     * 获取注册信息
     * 
     * @return 注册信息
     */
    public AgentRegistryResponse getRegistryInfo() {
        return registryInfo;
    }

    /**
     * 检查是否正在运行
     * 
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 设置实际监听的端口号
     * 
     * @param actualPort 实际端口号
     */
    public void setActualPort(int actualPort) {
        this.actualPort = actualPort;
    }
}