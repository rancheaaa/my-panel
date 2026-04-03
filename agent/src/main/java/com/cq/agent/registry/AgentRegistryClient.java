package com.cq.agent.registry;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.*;
import com.cq.panel.common.loadbalancer.HttpResponse;
import com.cq.panel.common.loadbalancer.LoadBalancerAlgorithm;
import com.cq.panel.common.loadbalancer.LoadBalancerClient;
import com.cq.panel.common.loadbalancer.LoadBalancerManager;
import com.cq.panel.common.loadbalancer.ServerList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Agent注册客户端
 * 负责与管理平台服务端通信
 * 
 * @author cq
 */
public class AgentRegistryClient {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistryClient.class);
    
    private static final String REGISTER_ENDPOINT = "agent/registry/register";
    private static final String HEARTBEAT_ENDPOINT = "agent/registry/heartbeat";
    private static final String SERVICE_NAME = "registry-service";
    
    private final Gson gson;
    private final LoadBalancerClient loadBalancerClient;
    private final ServerList serverList;


    public AgentRegistryClient(AgentConfig config) {
        this.gson = new Gson();

        LoadBalancerManager loadBalancerManager = LoadBalancerManager.createDefault();
        
        List<String> registryServerUrls = config.getRegistryServerUrls();
        ServerList serverList = createServerList(config);
        this.serverList = serverList;
        if (registryServerUrls == null || registryServerUrls.isEmpty()) {
            logger.warn("Registry server URL is not configured, auto-registration and heartbeat will be disabled");
            this.loadBalancerClient = null;
        } else {
            this.loadBalancerClient = loadBalancerManager.getClient(SERVICE_NAME, serverList, LoadBalancerAlgorithm.ROUND_ROBIN);
            logger.info("Agent registry client initialized with {} server URL(s): {}", registryServerUrls.size(), registryServerUrls);
        }

    }

    /**
     * 创建服务器列表
     * 
     * @param config Agent配置
     * @return ServerList实例
     */
    private ServerList createServerList(AgentConfig config) {
        return new DynamicProxyServerList(config);
    }

    /**
     * 注册Agent到服务端
     * 
     * @param request 注册请求
     * @return 注册响应
     * @throws IOException IO异常
     * @throws InterruptedException 中断异常
     */
    public ApiResponse<AgentRegistryResponse> register(AgentRegistryRequest request) throws IOException, InterruptedException {
        if (loadBalancerClient == null) {
            throw new IllegalStateException("Registry server URL is not configured");
        }
        
        logger.info("Registering agent to registry service");
        
        try {
            String requestBody = gson.toJson(request);
            HttpResponse<String> response = loadBalancerClient.post(SERVICE_NAME, "/" + REGISTER_ENDPOINT, requestBody, String.class);
            String responseBodyStr = response.getBody();
            
            logger.debug("Registry response status: {}, body: {}", response.getStatusCode(), responseBodyStr);
            
            if (response.getStatusCode() == 200) {
                final Type type = new TypeToken<ProxyApiResponse<AgentRegistryResponse>>() {
                }.getType();
                ProxyApiResponse<AgentRegistryResponse> apiResponse = gson.fromJson(responseBodyStr, type);
                if (apiResponse != null && apiResponse.isSuccess()) {
                    logger.info("Agent registered successfully: {}", apiResponse.getData());
                    return ApiResponse.success(apiResponse.getData());
                } else {
                    logger.warn("Agent registration failed: {}", apiResponse != null ? apiResponse.getMessage() : "Unknown error");
                    return ApiResponse.failure("Agent registration failed");
                }
            } else {
                logger.error("Agent registration failed with status code: {}, body: {}", response.getStatusCode(), responseBodyStr);
            }
        } catch (Exception e) {
            logger.error("Agent registration failed with exception: {}", e.getMessage(), e);
        }
        
        ApiResponse<AgentRegistryResponse> errorResponse = new ApiResponse<>();
        errorResponse.setCode(500);
        errorResponse.setMsg("Agent registration failed");
        return errorResponse;
    }

    /**
     * 发送心跳到服务端
     * 
     * @param agentIp Agent IP
     * @param agentPort Agent端口
     * @return 是否成功
     * @throws IOException IO异常
     * @throws InterruptedException 中断异常
     */
    public boolean heartbeat(String agentIp, int agentPort) throws IOException, InterruptedException {
        if (loadBalancerClient == null) {
            logger.debug("Registry server URL is not configured, skipping heartbeat");
            return false;
        }
        
        logger.debug("Sending heartbeat to registry service");
        
        try {
            // 刷新服务器列表
            this.serverList.refresh();

            String path = "/" + HEARTBEAT_ENDPOINT + "?agentIp=" + agentIp + "&agentPort=" + agentPort;
            HttpResponse<String> response = loadBalancerClient.post(SERVICE_NAME, path, null, String.class);
            String responseBody = response.getBody();
            
            logger.debug("Heartbeat response status: {}, body: {}", response.getStatusCode(), responseBody);
            
            if (response.getStatusCode() == 200) {
                ProxyApiResponse<Object> apiResponse = gson.fromJson(responseBody,
                        new TypeToken<ProxyApiResponse<Object>>(){}.getType());
                boolean success = apiResponse != null && apiResponse.isSuccess();
                if (success) {
                    logger.debug("Heartbeat sent successfully");
                } else {
                    logger.warn("Heartbeat failed: {}", apiResponse != null ? apiResponse.getMessage() : "Unknown error");
                }
                return success;
            } else {
                logger.error("Heartbeat failed with status code: {}, body: {}", response.getStatusCode(), responseBody);
            }
        } catch (Exception e) {
            logger.error("Heartbeat failed with exception: {}", e.getMessage());
        }
        
        return false;
    }

    /**
     * 检查是否已配置注册服务器
     * 
     * @return 是否已配置
     */
    public boolean isConfigured() {
        return loadBalancerClient != null;
    }

    /**
     * 关闭客户端，释放资源
     */
    public void close() {
        if (loadBalancerClient != null) {
            loadBalancerClient.close();
            logger.info("Agent registry client closed");
        }
    }
}