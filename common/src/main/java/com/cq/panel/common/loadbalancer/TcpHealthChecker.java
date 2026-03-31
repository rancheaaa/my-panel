package com.cq.panel.common.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * TCP端口探测健康检查器
 * 通过TCP连接检查服务器端口是否可达
 */
public class TcpHealthChecker implements HealthChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpHealthChecker.class);
    private static final String NAME = "tcpHealthChecker";
    
    private final int timeoutMs;
    
    /**
     * 构造函数
     * 
     * @param timeoutMs 连接超时时间（毫秒），默认为3000ms
     */
    public TcpHealthChecker(int timeoutMs) {
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000;
    }
    
    /**
     * 默认构造函数
     */
    public TcpHealthChecker() {
        this(3000);
    }
    
    @Override
    public boolean isHealthy(Server server) {
        if (server == null || !server.isAlive()) {
            return false;
        }
        
        // 跳过非TCP协议的服务器（如Unix Domain Socket等）
        if (!isTcpProtocol(server.getScheme())) {
            logger.debug("跳过非TCP协议的服务器健康检查: {}", server);
            return false;
        }
        
        String host = server.getHost();
        int port = server.getPort();
        
        if (port <= 0 || port > 65535) {
            logger.warn("服务器端口无效，跳过健康检查: {}:{}", host, port);
            return false;
        }
        
        try (Socket socket = new Socket()) {
            // 设置连接超时
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            
            // 如果连接成功，立即关闭连接（我们只需要验证连接性）
            boolean isHealthy = socket.isConnected();
            
            if (isHealthy) {
                logger.debug("TCP健康检查成功: {}:{}", host, port);
            }
            
            return isHealthy;
            
        } catch (SocketTimeoutException e) {
            logger.debug("TCP健康检查超时: {}:{}, 超时时间: {}ms", host, port, timeoutMs);
            return false;
        } catch (IOException e) {
            logger.debug("TCP健康检查失败: {}:{}, 错误: {}", host, port, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("TCP健康检查异常: {}:{}, 异常: {}", host, port, e.getMessage());
            return false;
        }
    }
    
    /**
     * 判断是否为TCP协议
     * 
     * @param scheme 协议方案
     * @return true表示是TCP协议
     */
    boolean isTcpProtocol(String scheme) {
        if (scheme == null || scheme.trim().isEmpty()) {
            return false;
        }
        
        String lowerScheme = scheme.toLowerCase();
        return lowerScheme.startsWith("http") || 
               lowerScheme.startsWith("https") ||
               lowerScheme.equals("tcp") ||
               lowerScheme.equals("socket");
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    /**
     * 获取超时时间
     * 
     * @return 超时时间（毫秒）
     */
    public int getTimeoutMs() {
        return timeoutMs;
    }
    
    @Override
    public String toString() {
        return "TcpHealthChecker{name=" + NAME + ", timeoutMs=" + timeoutMs + "}";
    }
}