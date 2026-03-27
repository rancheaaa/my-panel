package com.cq.agent.registry;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.AgentRegistryRequest;
import com.cq.agent.dto.AgentRegistryResponse;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.Result;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
    
    private final HttpClient httpClient;
    private final Gson gson;
    private final AgentConfig config;
    private final String registryServerUrl;

    public AgentRegistryClient(AgentConfig config) {
        String registryServerUrlTemp;
        this.config = config;
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getUploadConnectTimeoutSeconds()))
                .build();
        
        registryServerUrlTemp = config.getRegistryServerUrl();
        if (registryServerUrlTemp != null && !registryServerUrlTemp.isEmpty()) {
            if (!registryServerUrlTemp.endsWith("/")) {
                registryServerUrlTemp = registryServerUrlTemp + "/";
            }
        } else {
            logger.warn("Registry server URL is not configured, auto-registration and heartbeat will be disabled");
        }
        this.registryServerUrl = registryServerUrlTemp;
        logger.info("Agent registry client initialized with server URL: {}", this.registryServerUrl);

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
        if (registryServerUrl == null || registryServerUrl.isEmpty()) {
            throw new IllegalStateException("Registry server URL is not configured");
        }
        
        String url = registryServerUrl + REGISTER_ENDPOINT;
        logger.info("Registering agent to server: {}", url);
        
        String requestBody = gson.toJson(request);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(config.getUploadRequestTimeoutSeconds()))
                .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        
        logger.debug("Registry response status: {}, body: {}", response.statusCode(), responseBody);
        
        if (response.statusCode() == 200) {
            Result<AgentRegistryResponse> apiResponse = gson.fromJson(responseBody,
                    new TypeToken<Result<AgentRegistryResponse>>(){}.getType());
            if (apiResponse != null && apiResponse.isSuccess()) {
                logger.info("Agent registered successfully: {}", apiResponse.getData());
                return ApiResponse.success(apiResponse.getData());
            } else {
                logger.warn("Agent registration failed: {}", apiResponse != null ? apiResponse.getMsg() : "Unknown error");
                return ApiResponse.failure("Agent registration failed");
            }
        } else {
            logger.error("Agent registration failed with status code: {}, body: {}", response.statusCode(), responseBody);
            ApiResponse<AgentRegistryResponse> errorResponse = new ApiResponse<>();
            errorResponse.setCode(response.statusCode());
            errorResponse.setMsg("Agent registration failed, HTTP error: " + response.statusCode());
            return errorResponse;
        }
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
    public boolean heartbeat(String agentIp, Integer agentPort) throws IOException, InterruptedException {
        if (registryServerUrl == null || registryServerUrl.isEmpty()) {
            logger.debug("Registry server URL is not configured, skipping heartbeat");
            return false;
        }
        
        String url = registryServerUrl + HEARTBEAT_ENDPOINT + "?agentIp=" + agentIp + "&agentPort=" + agentPort;
        logger.debug("Sending heartbeat to server: {}", url);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(config.getUploadRequestTimeoutSeconds()))
                .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        
        logger.debug("Heartbeat response status: {}, body: {}", response.statusCode(), responseBody);
        
        if (response.statusCode() == 200) {
            ApiResponse<?> apiResponse = gson.fromJson(responseBody, ApiResponse.class);
            boolean success = apiResponse != null && apiResponse.isSuccess();
            if (success) {
                logger.debug("Heartbeat sent successfully");
            } else {
                logger.warn("Heartbeat failed: {}", apiResponse != null ? apiResponse.getMsg() : "Unknown error");
            }
            return success;
        } else {
            logger.error("Heartbeat failed with status code: {}, body: {}", response.statusCode(), responseBody);
            return false;
        }
    }

    /**
     * 检查是否已配置注册服务器
     * 
     * @return 是否已配置
     */
    public boolean isConfigured() {
        return registryServerUrl != null && !registryServerUrl.isEmpty();
    }
}