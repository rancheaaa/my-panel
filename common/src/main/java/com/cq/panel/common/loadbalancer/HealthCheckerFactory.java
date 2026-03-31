package com.cq.panel.common.loadbalancer;

/**
 * 健康检查器工厂
 * 用于根据配置创建合适的健康检查器
 */
public class HealthCheckerFactory {
    
    /**
     * 默认健康检查器类型
     */
    public enum HealthCheckerType {
        /**
         * TCP端口探测检查器
         */
        TCP,
        
        /**
         * HTTP健康检查器
         */
        HTTP
    }
    
    /**
     * 创建默认健康检查器（TCP端口探测）
     * 
     * @return 默认健康检查器
     */
    public static HealthChecker createDefault() {
        return new TcpHealthChecker();
    }
    
    /**
     * 根据类型创建健康检查器
     * 
     * @param type 健康检查器类型
     * @param timeoutMs 超时时间（毫秒）
     * @param healthCheckPath 健康检查路径（仅HTTP检查器需要）
     * @return 健康检查器实例
     */
    public static HealthChecker create(HealthCheckerType type, int timeoutMs, String healthCheckPath) {
        if (type == null) {
            return createDefault();
        }
        
        switch (type) {
            case TCP:
                return new TcpHealthChecker(timeoutMs);
            case HTTP:
                return new HttpHealthChecker(healthCheckPath, timeoutMs);
            default:
                return createDefault();
        }
    }
    
    /**
     * 根据配置创建健康检查器
     * 
     * @param config 健康检查配置
     * @param type 健康检查器类型
     * @return 健康检查器实例
     */
    public static HealthChecker create(HealthCheckConfig config, HealthCheckerType type) {
        if (config == null) {
            return createDefault();
        }
        
        return create(type, config.getTimeoutMs(), config.getHealthCheckPath());
    }
    
    /**
     * 根据配置创建健康检查器（使用默认类型）
     * 
     * @param config 健康检查配置
     * @return 健康检查器实例
     */
    public static HealthChecker create(HealthCheckConfig config) {
        return create(config, HealthCheckerType.TCP);
    }
}