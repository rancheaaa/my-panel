import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Row, Col, Card, Statistic, Spin, Progress } from 'antd';
import {
  DesktopOutlined,
  ApiOutlined,
  ClusterOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  CodeOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import { getDashboardData } from '../../../api/monitor/dashboard';
import './index.scss';

const ServiceDashboard = () => {
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState(null);
  const [lastUpdate, setLastUpdate] = useState(new Date());

  const fetchData = useCallback(async () => {
    try {
      const res = await getDashboardData();
      if (res.code === 200 && res.data) {
        setData(res.data);
        setLastUpdate(new Date());
      }
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 3000);
    return () => clearInterval(interval);
  }, [fetchData]);

  const getStatusColor = (value) => {
    if (value > 90) return '#ff4d4f';
    if (value > 70) return '#faad14';
    return '#52c41a';
  };

  if (loading) {
    return (
      <div className="dashboard-loading">
        <Spin size="large" />
        <p>加载监控数据中...</p>
      </div>
    );
  }

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <div className="header-title">
          <ClusterOutlined /> 服务监控大屏
        </div>
        <div className="header-info">
          <span>最后更新: {lastUpdate.toLocaleTimeString()}</span>
          <button className="refresh-btn" onClick={fetchData}>
            <ReloadOutlined />
          </button>
        </div>
      </div>

      <div className="dashboard-content">
        <div className="stats-section">
          <div className="stat-box stat-cpu">
            <div className="stat-icon"><DashboardOutlined /></div>
            <div className="stat-info">
              <div className="stat-label">CPU使用率</div>
              <div className="stat-value" style={{ color: getStatusColor(data?.basicStats?.cpuUsage || 0) }}>
                {data?.basicStats?.cpuUsage?.toFixed(1) || 0}%
              </div>
              <div className="stat-sub">核心数: {data?.basicStats?.cpuCore || 0}</div>
            </div>
          </div>

          <div className="stat-box stat-mem">
            <div className="stat-icon"><DatabaseOutlined /></div>
            <div className="stat-info">
              <div className="stat-label">内存使用率</div>
              <div className="stat-value" style={{ color: getStatusColor(data?.basicStats?.memUsage || 0) }}>
                {data?.basicStats?.memUsage?.toFixed(1) || 0}%
              </div>
              <div className="stat-sub">
                {data?.basicStats?.memUsed?.toFixed(1) || 0} / {data?.basicStats?.memTotal?.toFixed(1) || 0} GB
              </div>
            </div>
          </div>

          <div className="stat-box stat-jvm">
            <div className="stat-icon"><CodeOutlined /></div>
            <div className="stat-info">
              <div className="stat-label">JVM使用率</div>
              <div className="stat-value" style={{ color: getStatusColor(data?.basicStats?.jvmUsage || 0) }}>
                {data?.basicStats?.jvmUsage?.toFixed(1) || 0}%
              </div>
              <div className="stat-sub">
                {data?.basicStats?.jvmUsed?.toFixed(0) || 0} / {data?.basicStats?.jvmTotal?.toFixed(0) || 0} MB
              </div>
            </div>
          </div>

          <div className="stat-box stat-server">
            <div className="stat-icon"><DesktopOutlined /></div>
            <div className="stat-info">
              <div className="stat-label">服务器信息</div>
              <div className="stat-value-small">{data?.serviceStatus?.serverName || '-'}</div>
              <div className="stat-sub">{data?.serviceStatus?.serverIp || '-'}</div>
            </div>
          </div>
        </div>

        <div className="info-section">
          <Card className="info-card" title={<><ApiOutlined /> 服务配置信息</>}>
            <div className="info-list">
              <div className="info-row">
                <span className="info-label">服务器名称</span>
                <span className="info-value">{data?.serviceStatus?.serverName || '-'}</span>
              </div>
              <div className="info-row">
                <span className="info-label">服务器IP</span>
                <span className="info-value">{data?.serviceStatus?.serverIp || '-'}</span>
              </div>
              <div className="info-row">
                <span className="info-label">操作系统</span>
                <span className="info-value">{data?.serviceStatus?.osName || '-'}</span>
              </div>
              <div className="info-row">
                <span className="info-label">系统架构</span>
                <span className="info-value">{data?.serviceStatus?.osArch || '-'}</span>
              </div>
              <div className="info-row">
                <span className="info-label">JDK版本</span>
                <span className="info-value">{data?.serviceStatus?.jvmVersion || '-'}</span>
              </div>
              <div className="info-row">
                <span className="info-label">JVM名称</span>
                <span className="info-value">{data?.serviceStatus?.jvmName || '-'}</span>
              </div>
            </div>
          </Card>

          <Card className="disk-card" title={<><DesktopOutlined /> 磁盘状态</>}>
            <div className="disk-list">
              {data?.diskInfo?.map((disk, index) => (
                <div key={index} className="disk-item">
                  <div className="disk-header">
                    <span className="disk-name">{disk.dirName}</span>
                    <span className="disk-size">{disk.used} / {disk.total}</span>
                  </div>
                  <Progress
                    percent={disk.usage}
                    size="small"
                    strokeColor={getStatusColor(disk.usage)}
                    trailColor="rgba(255,255,255,0.1)"
                  />
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default ServiceDashboard;