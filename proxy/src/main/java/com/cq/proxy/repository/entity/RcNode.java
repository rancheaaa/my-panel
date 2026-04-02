package com.cq.proxy.repository.entity;

import java.time.LocalDateTime;

public class RcNode {

  private Long id;

  private Long envId;
  private Long projectId;
  private String nodeIp;
  private Integer nodePort;
  private String status;
  private String zone;
  private LocalDateTime lastRefreshTime;
  private String createBy;
  private LocalDateTime createTime;
  private String updateBy;
  private LocalDateTime updateTime;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getEnvId() {
    return envId;
  }

  public void setEnvId(Long envId) {
    this.envId = envId;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public String getNodeIp() {
    return nodeIp;
  }

  public void setNodeIp(String nodeIp) {
    this.nodeIp = nodeIp;
  }

  public Integer getNodePort() {
    return nodePort;
  }

  public void setNodePort(Integer nodePort) {
    this.nodePort = nodePort;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public LocalDateTime getLastRefreshTime() {
    return lastRefreshTime;
  }

  public void setLastRefreshTime(LocalDateTime lastRefreshTime) {
    this.lastRefreshTime = lastRefreshTime;
  }

  public String getCreateBy() {
    return createBy;
  }

  public void setCreateBy(String createBy) {
    this.createBy = createBy;
  }

  public LocalDateTime getCreateTime() {
    return createTime;
  }

  public void setCreateTime(LocalDateTime createTime) {
    this.createTime = createTime;
  }

  public String getUpdateBy() {
    return updateBy;
  }

  public void setUpdateBy(String updateBy) {
    this.updateBy = updateBy;
  }

  public LocalDateTime getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(LocalDateTime updateTime) {
    this.updateTime = updateTime;
  }
}