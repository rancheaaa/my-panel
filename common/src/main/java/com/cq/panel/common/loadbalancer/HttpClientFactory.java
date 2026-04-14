package com.cq.panel.common.loadbalancer;

/**
 * HTTP客户端工厂
 * 用于创建不同类型的HTTP客户端实现
 */
public class HttpClientFactory {

    /**
     * HTTP客户端类型枚举
     */
    public enum HttpClientType {
        /**
         * Java标准库实现（轻量级）
         */
        SIMPLE,
        
        /**
         * Apache HttpClient实现（功能丰富）
         */
        APACHE
    }

    /**
     * 创建默认的HTTP客户端
     * 
     * @return HTTP客户端实例
     */
    public static HttpClient createDefault() {
        return create(HttpClientType.SIMPLE);
    }

    /**
     * 创建指定类型的HTTP客户端
     * 
     * @param type HTTP客户端类型
     * @return HTTP客户端实例
     */
    public static HttpClient create(HttpClientType type) {
        return create(type, 5000, 10000);
    }

    /**
     * 创建指定类型的HTTP客户端
     * 
     * @param type HTTP客户端类型
     * @param connectTimeout 连接超时时间（毫秒）
     * @param readTimeout 读取超时时间（毫秒）
     * @return HTTP客户端实例
     */
    public static HttpClient create(HttpClientType type, int connectTimeout, int readTimeout) {
        switch (type) {
            case SIMPLE:
                return new SimpleHttpClient(connectTimeout, readTimeout);
            case APACHE:
                return new ApacheHttpClient(connectTimeout, readTimeout);
            default:
                throw new IllegalArgumentException("Unsupported HTTP client type: " + type);
        }
    }

    /**
     * 创建轻量级HTTP客户端（使用Java标准库）
     * 
     * @return HTTP客户端实例
     */
    public static HttpClient createSimple() {
        return new SimpleHttpClient();
    }

    /**
     * 创建功能丰富的HTTP客户端（使用Apache HttpClient）
     * 
     * @return HTTP客户端实例
     */
    public static HttpClient createApache() {
        return new ApacheHttpClient();
    }

    /**
     * 创建快速HTTP客户端（较短的超时时间）
     * 
     * @param type HTTP客户端类型
     * @return HTTP客户端实例
     */
    public static HttpClient createFast(HttpClientType type) {
        return create(type, 2000, 5000);
    }

    /**
     * 创建高可用HTTP客户端（较长的超时时间）
     * 
     * @param type HTTP客户端类型
     * @return HTTP客户端实例
     */
    public static HttpClient createHighAvailability(HttpClientType type) {
        return create(type, 10000, 30000);
    }
}