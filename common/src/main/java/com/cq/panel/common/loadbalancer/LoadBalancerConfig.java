package com.cq.panel.common.loadbalancer;

/**
 * 负载均衡配置类
 */
public class LoadBalancerConfig {
    
    private LoadBalancerAlgorithm algorithm;
    private String localZone;
    private long pingInterval;
    private int maxRetries;
    private boolean enableHealthCheck;
    private int connectTimeout;
    private int readTimeout;
    private String healthCheckPath;
    private int healthCheckTimeout;
    private int healthCheckInterval;
    private int healthCheckRetryCount;
    private int healthCheckFailureThreshold;
    private int healthCheckSuccessThreshold;
    private HealthCheckerFactory.HealthCheckerType healthCheckerType;
    
    // 新增健康检查配置字段（从HealthCheckConfig迁移）
    private boolean healthCheckEnabled;

    public LoadBalancerConfig() {
        this.algorithm = LoadBalancerAlgorithm.ROUND_ROBIN;
        this.localZone = "default";
        this.pingInterval = 30000; // 30秒
        this.maxRetries = 3;
        this.enableHealthCheck = true;
        this.connectTimeout = 5000; // 5秒
        this.readTimeout = 10000;   // 10秒
        this.healthCheckPath = "/api/health";
        this.healthCheckTimeout = 5000; // 5秒
        this.healthCheckInterval = 10000; // 10秒
        this.healthCheckRetryCount = 3;
        this.healthCheckFailureThreshold = 3;
        this.healthCheckSuccessThreshold = 2;
        this.healthCheckerType = HealthCheckerFactory.HealthCheckerType.TCP; // 默认使用TCP健康检查器
        
        // 初始化新增的健康检查配置字段
        this.healthCheckEnabled = true;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    public LoadBalancerAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(LoadBalancerAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public String getLocalZone() {
        return localZone;
    }

    public void setLocalZone(String localZone) {
        this.localZone = localZone;
    }

    public long getPingInterval() {
        return pingInterval;
    }

    public void setPingInterval(long pingInterval) {
        this.pingInterval = pingInterval;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean isEnableHealthCheck() {
        return enableHealthCheck;
    }

    public void setEnableHealthCheck(boolean enableHealthCheck) {
        this.enableHealthCheck = enableHealthCheck;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getHealthCheckPath() {
        return healthCheckPath;
    }

    public void setHealthCheckPath(String healthCheckPath) {
        this.healthCheckPath = healthCheckPath;
    }

    public int getHealthCheckTimeout() {
        return healthCheckTimeout;
    }

    public void setHealthCheckTimeout(int healthCheckTimeout) {
        this.healthCheckTimeout = healthCheckTimeout;
    }

    public int getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public void setHealthCheckInterval(int healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }

    public int getHealthCheckRetryCount() {
        return healthCheckRetryCount;
    }

    public void setHealthCheckRetryCount(int healthCheckRetryCount) {
        this.healthCheckRetryCount = healthCheckRetryCount;
    }

    public int getHealthCheckFailureThreshold() {
        return healthCheckFailureThreshold;
    }

    public void setHealthCheckFailureThreshold(int healthCheckFailureThreshold) {
        this.healthCheckFailureThreshold = healthCheckFailureThreshold;
    }

    public int getHealthCheckSuccessThreshold() {
        return healthCheckSuccessThreshold;
    }

    public void setHealthCheckSuccessThreshold(int healthCheckSuccessThreshold) {
        this.healthCheckSuccessThreshold = healthCheckSuccessThreshold;
    }

    /**
     * 获取健康检查器类型
     * 
     * @return 健康检查器类型
     */
    public HealthCheckerFactory.HealthCheckerType getHealthCheckerType() {
        return healthCheckerType;
    }

    /**
     * 设置健康检查器类型
     * 
     * @param healthCheckerType 健康检查器类型
     */
    public void setHealthCheckerType(HealthCheckerFactory.HealthCheckerType healthCheckerType) {
        this.healthCheckerType = healthCheckerType;
    }

    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    /**
     * 创建默认配置
     */
    public static LoadBalancerConfig defaultConfig() {
        return new LoadBalancerConfig();
    }

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器类
     */
    public static class Builder {
        private LoadBalancerAlgorithm algorithm = LoadBalancerAlgorithm.ROUND_ROBIN;
        private String localZone = "default";
        private long pingInterval = 30000;
        private int maxRetries = 3;
        private boolean enableHealthCheck = true;
        private int connectTimeout = 5000;
        private int readTimeout = 10000;
        private String healthCheckPath = "/health";
        private int healthCheckTimeout = 5000;
        private int healthCheckInterval = 30000;
        private int healthCheckRetryCount = 3;
        private int healthCheckFailureThreshold = 3;
        private int healthCheckSuccessThreshold = 2;
        private HealthCheckerFactory.HealthCheckerType healthCheckerType = HealthCheckerFactory.HealthCheckerType.TCP;
        
        // 新增健康检查配置字段
        private boolean healthCheckEnabled = true;

        public Builder algorithm(LoadBalancerAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder localZone(String localZone) {
            this.localZone = localZone;
            return this;
        }

        public Builder pingInterval(long pingInterval) {
            this.pingInterval = pingInterval;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder enableHealthCheck(boolean enableHealthCheck) {
            this.enableHealthCheck = enableHealthCheck;
            return this;
        }

        public Builder connectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder healthCheckPath(String healthCheckPath) {
            this.healthCheckPath = healthCheckPath;
            return this;
        }

        public Builder healthCheckTimeout(int healthCheckTimeout) {
            this.healthCheckTimeout = healthCheckTimeout;
            return this;
        }

        public Builder healthCheckInterval(int healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
            return this;
        }

        public Builder healthCheckRetryCount(int healthCheckRetryCount) {
            this.healthCheckRetryCount = healthCheckRetryCount;
            return this;
        }

        public Builder healthCheckFailureThreshold(int healthCheckFailureThreshold) {
            this.healthCheckFailureThreshold = healthCheckFailureThreshold;
            return this;
        }

        public Builder healthCheckSuccessThreshold(int healthCheckSuccessThreshold) {
            this.healthCheckSuccessThreshold = healthCheckSuccessThreshold;
            return this;
        }

        public Builder healthCheckerType(HealthCheckerFactory.HealthCheckerType healthCheckerType) {
            this.healthCheckerType = healthCheckerType;
            return this;
        }

        // 新增健康检查配置的builder方法
        public Builder healthCheckEnabled(boolean healthCheckEnabled) {
            this.healthCheckEnabled = healthCheckEnabled;
            return this;
        }

        public LoadBalancerConfig build() {
            LoadBalancerConfig config = new LoadBalancerConfig();
            config.setAlgorithm(this.algorithm);
            config.setLocalZone(this.localZone);
            config.setPingInterval(this.pingInterval);
            config.setMaxRetries(this.maxRetries);
            config.setEnableHealthCheck(this.enableHealthCheck);
            config.setConnectTimeout(this.connectTimeout);
            config.setReadTimeout(this.readTimeout);
            config.setHealthCheckPath(this.healthCheckPath);
            config.setHealthCheckTimeout(this.healthCheckTimeout);
            config.setHealthCheckInterval(this.healthCheckInterval);
            config.setHealthCheckRetryCount(this.healthCheckRetryCount);
            config.setHealthCheckFailureThreshold(this.healthCheckFailureThreshold);
            config.setHealthCheckSuccessThreshold(this.healthCheckSuccessThreshold);
            config.setHealthCheckerType(this.healthCheckerType);
            
            // 设置新增的健康检查配置字段
            config.setHealthCheckEnabled(this.healthCheckEnabled);
            
            return config;
        }
    }
}