package com.cq.panel.common.loadbalancer;

/**
 * 健康检查配置
 */
public class HealthCheckConfig {
    
    private final boolean enabled;
    private final String healthCheckPath;
    private final int timeoutMs;
    private final int intervalMs;
    private final int retryCount;
    private final int failureThreshold;
    private final int successThreshold;
    
    /**
     * 私有构造函数
     */
    private HealthCheckConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.healthCheckPath = builder.healthCheckPath;
        this.timeoutMs = builder.timeoutMs;
        this.intervalMs = builder.intervalMs;
        this.retryCount = builder.retryCount;
        this.failureThreshold = builder.failureThreshold;
        this.successThreshold = builder.successThreshold;
    }
    
    /**
     * 获取默认配置
     */
    public static HealthCheckConfig defaultConfig() {
        return builder()
                .enabled(true)
                .healthCheckPath("/health")
                .timeoutMs(5000)
                .intervalMs(30000)
                .retryCount(3)
                .failureThreshold(3)
                .successThreshold(2)
                .build();
    }
    
    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // Getter方法
    public boolean isEnabled() {
        return enabled;
    }
    
    public String getHealthCheckPath() {
        return healthCheckPath;
    }
    
    public int getTimeoutMs() {
        return timeoutMs;
    }
    
    public int getIntervalMs() {
        return intervalMs;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public int getFailureThreshold() {
        return failureThreshold;
    }
    
    public int getSuccessThreshold() {
        return successThreshold;
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private boolean enabled = true;
        private String healthCheckPath = "/health";
        private int timeoutMs = 5000;
        private int intervalMs = 30000;
        private int retryCount = 3;
        private int failureThreshold = 3;
        private int successThreshold = 2;
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder healthCheckPath(String healthCheckPath) {
            this.healthCheckPath = healthCheckPath;
            return this;
        }
        
        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }
        
        public Builder intervalMs(int intervalMs) {
            this.intervalMs = intervalMs;
            return this;
        }
        
        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }
        
        public Builder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }
        
        public Builder successThreshold(int successThreshold) {
            this.successThreshold = successThreshold;
            return this;
        }
        
        public HealthCheckConfig build() {
            return new HealthCheckConfig(this);
        }
    }
}