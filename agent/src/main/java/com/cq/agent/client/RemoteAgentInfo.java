package com.cq.agent.client;

import java.util.Objects;

/**
 *
 * @author cq 2026/3/26 8:54
 * @since 1.0.0
 */
public class RemoteAgentInfo {

    private final String ip;
    private final int port;
    private final String username;
    private final String destFilePath;

    public RemoteAgentInfo(String ip, int port, String username, String destFilePath) {
        this.ip = ip;
        this.port = port;
        this.username = username;
        this.destFilePath = destFilePath;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getDestFilePath() {
        return destFilePath;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RemoteAgentInfo that = (RemoteAgentInfo) o;
        return port == that.port && Objects.equals(ip, that.ip) && Objects.equals(username, that.username) && Objects.equals(destFilePath, that.destFilePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port, username, destFilePath);
    }

    @Override
    public String toString() {
        return "RemoteAgentInfo{" +
                "ip='" + ip + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", destFilePath='" + destFilePath + '\'' +
                '}';
    }
}
