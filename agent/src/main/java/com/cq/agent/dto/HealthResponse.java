package com.cq.agent.dto;

/**
 * Response for health check API.
 */
public class HealthResponse {

    private String status;
    private String os;
    private String osType;
    private long defaultTimeout;
    private long maxTimeout;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getOsType() {
        return osType;
    }

    public void setOsType(String osType) {
        this.osType = osType;
    }

    public long getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(long defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public long getMaxTimeout() {
        return maxTimeout;
    }

    public void setMaxTimeout(long maxTimeout) {
        this.maxTimeout = maxTimeout;
    }
}
