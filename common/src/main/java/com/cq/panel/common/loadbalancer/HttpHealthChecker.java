package com.cq.panel.common.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP健康检查器
 * 通过发送HTTP请求检查服务器健康状态
 */
public class HttpHealthChecker implements HealthChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpHealthChecker.class);
    private static final String NAME = "httpHealthChecker";
    
    private final HttpClient httpClient;
    private final String healthCheckPath;
    private final int timeoutMs;
    
    /**
     * 构造函数（支持依赖注入）
     * 
     * @param httpClient HTTP客户端
     * @param healthCheckPath 健康检查路径，默认为"/health"
     * @param timeoutMs 超时时间（毫秒），默认为5000ms
     */
    public HttpHealthChecker(HttpClient httpClient, String healthCheckPath, int timeoutMs) {
        this.httpClient = httpClient != null ? httpClient : new SimpleHttpClient();
        this.healthCheckPath = healthCheckPath != null ? healthCheckPath : "/api/health";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 5000;
    }
    
    /**
     * 构造函数
     * 
     * @param healthCheckPath 健康检查路径，默认为"/health"
     * @param timeoutMs 超时时间（毫秒），默认为5000ms
     */
    public HttpHealthChecker(String healthCheckPath, int timeoutMs) {
        this(null, healthCheckPath, timeoutMs);
    }
    
    /**
     * 默认构造函数
     */
    public HttpHealthChecker() {
        this(null, "/api/health", 5000);
    }
    
    @Override
    public boolean isHealthy(Server server) {
        if (server == null) {
            return false;
        }
        if (!"http".equals(server.getScheme())
                && !"https".equals(server.getScheme())) {
            return false;
        }
        
        try {
            // 构建健康检查URL
            String url = server.getScheme() + "://" + server.getHost() + ":" + server.getPort() + healthCheckPath;

            // 发送健康检查请求
            HttpResponse<String> response = httpClient.get(url, String.class);
            
            // 检查响应状态码
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                logger.debug("http心跳检查完成: {} - {}:{} -{} -{}", server.getId(), server.getHost(), server.getPort(), url, "健康");
                return true;
            } else {
                logger.warn("http心跳检查失败: {} - {}:{} -{} -{} -statusCode:{}", server.getId(), server.getHost(), server.getPort(), url, "不健康", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.warn("健康检查异常: {} - {}", server.getId(), e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    /**
     * 获取健康检查路径
     */
    public String getHealthCheckPath() {
        return healthCheckPath;
    }
    
    /**
     * 获取超时时间
     */
    public int getTimeoutMs() {
        return timeoutMs;
    }
}