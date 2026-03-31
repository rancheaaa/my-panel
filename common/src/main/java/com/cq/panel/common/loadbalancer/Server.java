package com.cq.panel.common.loadbalancer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务器实体类
 */
public class Server {
    private final String id;
    private final String host;
    private final int port;
    private final String scheme;
    private final String zone;
    private volatile boolean alive;
    private volatile long lastAccessTime;
    private final AtomicInteger concurrentRequests;
    private volatile long responseTime;
    private volatile long lastHealthCheckTime;
    private final AtomicLong consecutiveFailures;
    private final AtomicLong consecutiveSuccesses;

    public Server(String host, int port) {
        this(null, host, port, "http", null);
    }

    public Server(String id, String host, int port, String scheme, String zone) {
        this.id = id != null ? id : host + ":" + port;
        this.host = host;
        this.port = port;
        this.scheme = scheme != null ? scheme : "http";
        this.zone = zone;
        this.alive = true;
        this.lastAccessTime = System.currentTimeMillis();
        this.concurrentRequests = new AtomicInteger(0);
        this.responseTime = 0;
        this.lastHealthCheckTime = 0;
        this.consecutiveFailures = new AtomicLong(0);
        this.consecutiveSuccesses = new AtomicLong(0);
    }

    public String getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getScheme() {
        return scheme;
    }

    public String getZone() {
        return zone;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public int getConcurrentRequests() {
        return concurrentRequests.get();
    }

    public void setConcurrentRequests(int concurrentRequests) {
        this.concurrentRequests.set(concurrentRequests);
    }

    public long getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }

    public long getLastHealthCheckTime() {
        return lastHealthCheckTime;
    }

    public void setLastHealthCheckTime(long lastHealthCheckTime) {
        this.lastHealthCheckTime = lastHealthCheckTime;
    }

    public long getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void setConsecutiveFailures(long consecutiveFailures) {
        this.consecutiveFailures.set(consecutiveFailures);
    }

    public long getConsecutiveSuccesses() {
        return consecutiveSuccesses.get();
    }

    public void setConsecutiveSuccesses(long consecutiveSuccesses) {
        this.consecutiveSuccesses.set(consecutiveSuccesses);
    }

    public String getUrl() {
        return scheme + "://" + host + ":" + port;
    }

    public void incrementConcurrentRequests() {
        this.concurrentRequests.incrementAndGet();
    }

    public void decrementConcurrentRequests() {
        if (this.concurrentRequests.get() > 0) {
            this.concurrentRequests.decrementAndGet();
        }
    }

    /**
     * 记录健康检查成功
     */
    public void recordHealthCheckSuccess() {
        this.consecutiveSuccesses.incrementAndGet();
        this.consecutiveFailures.set(0);
        this.lastHealthCheckTime = System.currentTimeMillis();
    }

    /**
     * 记录健康检查失败
     */
    public void recordHealthCheckFailure() {
        this.consecutiveFailures.incrementAndGet();
        this.consecutiveSuccesses.set(0);
        this.lastHealthCheckTime = System.currentTimeMillis();
    }

    /**
     * 根据健康检查结果更新服务器状态
     * 
     * @param failureThreshold 失败阈值
     * @param successThreshold 成功阈值
     */
    public void updateHealthStatus(int failureThreshold, int successThreshold) {
        if (this.consecutiveFailures.get() >= failureThreshold) {
            this.alive = false;
        } else if (this.consecutiveSuccesses.get() >= successThreshold) {
            this.alive = true;
        }
    }

    /**
     * 检查是否需要健康检查
     * 
     * @param intervalMs 检查间隔（毫秒）
     * @return true表示需要检查
     */
    public boolean needsHealthCheck(long intervalMs) {
        long currentTime = System.currentTimeMillis();
        return (currentTime - this.lastHealthCheckTime) >= intervalMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return port == server.port && Objects.equals(host, server.host) && Objects.equals(scheme, server.scheme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, scheme);
    }

    @Override
    public String toString() {
        return "Server{" +
                "id='" + id + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", scheme='" + scheme + '\'' +
                ", zone='" + zone + '\'' +
                ", alive=" + alive +
                ", concurrentRequests=" + concurrentRequests +
                ", responseTime=" + responseTime +
                ", lastHealthCheckTime=" + lastHealthCheckTime +
                ", consecutiveFailures=" + consecutiveFailures +
                ", consecutiveSuccesses=" + consecutiveSuccesses +
                '}';
    }
}