import React from 'react';
import { Card, Col, Row, Typography, Progress } from 'antd';
import {
  CodeOutlined,
  ApiOutlined,
  TeamOutlined,
  RocketOutlined,
  BarChartOutlined,
  SettingOutlined,
  SafetyOutlined,
  ThunderboltOutlined,
  DatabaseOutlined,
  HddOutlined,
  WifiOutlined,
  ExperimentOutlined
} from '@ant-design/icons';
import './Home.scss';

const { Title, Paragraph } = Typography;

const Index = () => {
  const stats = [
    {
      title: '总用户数',
      value: 12847,
      icon: <TeamOutlined />,
      color: '#6366f1',
      trend: '+12%'
    },
    {
      title: '今日访问',
      value: 2847,
      icon: <BarChartOutlined />,
      color: '#10b981',
      trend: '+8%'
    },
    {
      title: '系统负载',
      value: 67,
      icon: <ThunderboltOutlined />,
      color: '#f59e0b',
      suffix: '%'
    },
    {
      title: '活跃项目',
      value: 23,
      icon: <CodeOutlined />,
      color: '#ef4444',
      trend: '+3'
    }
  ];

  const features = [
    {
      icon: <RocketOutlined />,
      title: '快速开发',
      description: '基于现代前端技术栈，提供极致的开发体验和性能优化',
      color: '#6366f1'
    },
    {
      icon: <SafetyOutlined />,
      title: '安全可靠',
      description: '完整的权限管理系统，确保数据安全和用户隐私保护',
      color: '#10b981'
    },
    {
      icon: <ApiOutlined />,
      title: 'API 集成',
      description: '丰富的API接口，支持第三方服务无缝集成',
      color: '#f59e0b'
    },
    {
      icon: <SettingOutlined />,
      title: '灵活配置',
      description: '高度可配置的系统，支持个性化定制和扩展',
      color: '#ef4444'
    }
  ];

  return (
    <div className="home-container">
      {/* Stats Section */}
      <div className="stats-section">
        <div className="section-header">
          <Title level={2}>数据概览</Title>
          <Paragraph>实时监控系统运行状态和关键指标</Paragraph>
        </div>
        <Row gutter={[24, 24]}>
          {stats.map((stat, index) => (
            <Col xs={24} sm={12} lg={6} key={index}>
              <Card className="stat-card" hoverable>
                <div className="stat-content">
                  <div className="stat-icon" style={{ background: stat.color }}>
                    {stat.icon}
                  </div>
                  <div className="stat-info">
                    <div className="stat-value" style={{ color: stat.color }}>
                      {stat.value.toLocaleString()}{stat.suffix}
                    </div>
                    <div className="stat-title">{stat.title}</div>
                    {stat.trend && (
                      <div className="stat-trend" style={{ color: stat.color }}>
                        {stat.trend}
                      </div>
                    )}
                  </div>
                </div>
              </Card>
            </Col>
          ))}
        </Row>
      </div>

      {/* Features Section */}
      <div className="features-section">
        <div className="section-header">
          <Title level={2}>核心特性</Title>
          <Paragraph>强大的功能特性，满足各种业务场景需求</Paragraph>
        </div>
        <Row gutter={[32, 32]}>
          {features.map((feature, index) => (
            <Col xs={24} md={12} key={index}>
              <Card className="feature-card" hoverable>
                <div className="feature-content">
                  <div className="feature-icon" style={{ color: feature.color }}>
                    {feature.icon}
                  </div>
                  <div className="feature-info">
                    <Title level={4}>{feature.title}</Title>
                    <Paragraph>{feature.description}</Paragraph>
                  </div>
                </div>
              </Card>
            </Col>
          ))}
        </Row>
      </div>

      {/* System Status Section */}
      <div className="system-section">
        <div className="section-header">
          <Title level={2}>系统状态</Title>
          <Paragraph>实时监控服务器性能和资源使用情况</Paragraph>
        </div>
        <Row gutter={[24, 24]}>
          <Col xs={24} md={12} lg={6}>
            <Card className="system-card cpu-card" hoverable>
              <div className="system-content">
                <div className="system-icon">
                  <ExperimentOutlined />
                </div>
                <div className="system-info">
                  <div className="system-label">CPU 使用率</div>
                  <div className="system-value">45%</div>
                </div>
              </div>
              <Progress
                percent={45}
                strokeColor="#6366f1"
                strokeWidth={8}
                className="system-progress"
              />
            </Card>
          </Col>
          <Col xs={24} md={12} lg={6}>
            <Card className="system-card memory-card" hoverable>
              <div className="system-content">
                <div className="system-icon">
                  <DatabaseOutlined />
                </div>
                <div className="system-info">
                  <div className="system-label">内存使用率</div>
                  <div className="system-value">67%</div>
                </div>
              </div>
              <Progress
                percent={67}
                strokeColor="#10b981"
                strokeWidth={8}
                className="system-progress"
              />
            </Card>
          </Col>
          <Col xs={24} md={12} lg={6}>
            <Card className="system-card disk-card" hoverable>
              <div className="system-content">
                <div className="system-icon">
                  <HddOutlined />
                </div>
                <div className="system-info">
                  <div className="system-label">磁盘使用率</div>
                  <div className="system-value">78%</div>
                </div>
              </div>
              <Progress
                percent={78}
                strokeColor="#f59e0b"
                strokeWidth={8}
                className="system-progress"
              />
            </Card>
          </Col>
          <Col xs={24} md={12} lg={6}>
            <Card className="system-card network-card" hoverable>
              <div className="system-content">
                <div className="system-icon">
                  <WifiOutlined />
                </div>
                <div className="system-info">
                  <div className="system-label">网络负载</div>
                  <div className="system-value">23%</div>
                </div>
              </div>
              <Progress
                percent={23}
                strokeColor="#ef4444"
                strokeWidth={8}
                className="system-progress"
              />
            </Card>
          </Col>
        </Row>
      </div>
    </div>
  );
};

export default Index;
