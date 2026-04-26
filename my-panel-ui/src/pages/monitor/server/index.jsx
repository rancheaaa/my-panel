import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Table, Skeleton, Button, Space, Typography, Progress } from 'antd';
import { getServer } from '../../../api/monitor/server';
import { getConfigKey } from '../../../api/config';
import {
  HddOutlined,
  CodeOutlined,
  DesktopOutlined,
  DashboardOutlined,
  FileTextOutlined,
  ExperimentOutlined,
  DatabaseOutlined,
  InfoCircleOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import './Server.scss';

const { Title, Paragraph } = Typography;

const Server = () => {
  const [server, setServer] = useState({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showSwagger, setShowSwagger] = useState(false);
  const [showActuator, setShowActuator] = useState(false);

  const fetchData = async () => {
    try {
      const [serverRes, swaggerRes, actuatorRes] = await Promise.all([
        getServer(),
        getConfigKey('sys.monitor.showSwagger'),
        getConfigKey('sys.monitor.showActuator')
      ]);

      if (serverRes.code === 200) {
        setServer(serverRes.data);
      }
      if (swaggerRes.code === 200) {
        setShowSwagger(swaggerRes.data?.configValue === 'true');
      }
      if (actuatorRes.code === 200) {
        setShowActuator(actuatorRes.data?.configValue === 'true');
      }
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchData();
  };

  if (loading) {
      return (
          <div className="server-container">
              <Skeleton active />
          </div>
      );
  }

  const { cpu, mem, jvm, sys, sysFiles } = server;

  const handleOpenActuator = () => {
    window.open('/admin/server', '_blank');
  };

  const handleOpenSwagger = () => {
    window.open('/swagger-ui/index.html', '_blank');
  };

  const diskColumns = [
    { title: '盘符路径', dataIndex: 'dirName', key: 'dirName', width: 150, ellipsis: true },
    { title: '文件系统', dataIndex: 'sysTypeName', key: 'sysTypeName', width: 120 },
    { title: '盘符类型', dataIndex: 'typeName', key: 'typeName', width: 120 },
    { title: '总大小', dataIndex: 'total', key: 'total', width: 100 },
    { title: '可用大小', dataIndex: 'free', key: 'free', width: 100 },
    { title: '已用大小', dataIndex: 'used', key: 'used', width: 100 },
    {
        title: '已用百分比',
        dataIndex: 'usage',
        key: 'usage',
        width: 120,
        render: (text) => (
            <span className={`status-badge ${text > 80 ? 'high' : text > 60 ? 'medium' : 'low'}`}>
                {text}%
            </span>
        )
    },
  ];

  return (
    <div className="server-container">
      <div className="section-header">
        <div className="section-title">
          <Title level={2}>服务器监控</Title>
          <Paragraph>实时监控系统资源使用情况和运行状态</Paragraph>
        </div>
        <div className="section-actions">
          <Button
            icon={<ReloadOutlined spin={refreshing} />}
            onClick={handleRefresh}
            loading={refreshing}
          >
            刷新数据
          </Button>
        </div>
      </div>

      <div className="stats-grid">
        <div className="action-buttons">
          {(showActuator || showSwagger) && (
              <Space size="middle" wrap>
                  {showActuator && (
                    <Button
                      type="primary"
                      onClick={handleOpenActuator}
                      icon={<DashboardOutlined />}
                      style={{ background: '#6366f1', borderColor: '#6366f1' }}
                    >
                        Actuator监控
                    </Button>
                  )}
                  {showSwagger && (
                    <Button
                      type="primary"
                      onClick={handleOpenSwagger}
                      icon={<FileTextOutlined />}
                      style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }}
                    >
                        Swagger文档
                    </Button>
                  )}
              </Space>
          )}
        </div>

        <Row gutter={[12, 12]}>
          <Col xs={24} md={12}>
            <Card
              className="monitor-card cpu-card"
              title={
                <div className="card-header-content">
                  <div className="card-icon"><ExperimentOutlined /></div>
                  <span>CPU 使用情况</span>
                </div>
              }
              hoverable
            >
              <div className="metric-item">
                <span className="metric-label">核心数</span>
                <span className="metric-value">{cpu?.cpuNum}</span>
              </div>
              <div className="metric-item">
                <span className="metric-label">用户使用率</span>
                <span className={`metric-value ${cpu?.used > 80 ? 'warning' : 'success'}`}>{cpu?.used}%</span>
              </div>
              <div className="metric-item">
                <span className="metric-label">系统使用率</span>
                <span className={`metric-value ${cpu?.sys > 80 ? 'warning' : 'success'}`}>{cpu?.sys}%</span>
              </div>
              <div className="metric-item">
                <span className="metric-label">当前空闲率</span>
                <span className="metric-value success">{cpu?.free}%</span>
              </div>
              <div className="progress-section">
                <Progress
                  percent={parseInt(cpu?.used)}
                  strokeColor="#6366f1"
                  strokeWidth={10}
                  className="progress-bar"
                />
              </div>
            </Card>
          </Col>

          <Col xs={24} md={12}>
            <Card
              className="monitor-card memory-card"
              title={
                <div className="card-header-content">
                  <div className="card-icon"><DatabaseOutlined /></div>
                  <span>内存使用情况</span>
                </div>
              }
              hoverable
            >
              <div className="metric-item">
                <span className="metric-label">总内存</span>
                <span className="metric-value">{mem?.total}GB</span>
              </div>
              <div className="metric-item">
                <span className="metric-label">已用内存</span>
                <span className="metric-value">{mem?.used}GB</span>
              </div>
              <div className="metric-item">
                <span className="metric-label">剩余内存</span>
                <span className="metric-value success">{mem?.free}GB</span>
              </div>
              <div className="metric-item">
                <span className="metric-label">使用率</span>
                <span className={`metric-value ${mem?.usage > 80 ? 'warning' : 'success'}`}>{mem?.usage}%</span>
              </div>
              <div className="progress-section">
                <Progress
                  percent={parseInt(mem?.usage)}
                  strokeColor="#10b981"
                  strokeWidth={10}
                  className="progress-bar"
                />
              </div>
            </Card>
          </Col>

          <Col span={24}>
            <Card
              className="monitor-card info-card"
              title={
                <div className="card-header-content">
                  <div className="card-icon"><DesktopOutlined /></div>
                  <span>服务器信息</span>
                </div>
              }
              hoverable
            >
              <div className="info-grid">
                <div className="info-item">
                  <div className="info-label">服务器名称</div>
                  <div className="info-value">{sys?.computerName}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">操作系统</div>
                  <div className="info-value">{sys?.osName}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">服务器IP</div>
                  <div className="info-value">{sys?.computerIp}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">系统架构</div>
                  <div className="info-value">{sys?.osArch}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">项目路径</div>
                  <div className="info-value">{sys?.userDir}</div>
                </div>
              </div>
            </Card>
          </Col>

          <Col span={24}>
            <Card
              className="monitor-card jvm-card"
              title={
                <div className="card-header-content">
                  <div className="card-icon"><CodeOutlined /></div>
                  <span>Java虚拟机信息</span>
                </div>
              }
              hoverable
            >
              <div className="info-grid">
                <div className="info-item">
                  <div className="info-label">Java名称</div>
                  <div className="info-value">{jvm?.name}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">Java版本</div>
                  <div className="info-value">{jvm?.version}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">启动时间</div>
                  <div className="info-value">{jvm?.startTime}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">运行时长</div>
                  <div className="info-value">{jvm?.runTime}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">安装路径</div>
                  <div className="info-value">{jvm?.home}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">项目路径</div>
                  <div className="info-value">{sys?.userDir}</div>
                </div>
                <div className="info-item">
                  <div className="info-label">运行参数</div>
                  <div className="info-value">{jvm?.inputArgs}</div>
                </div>
              </div>
            </Card>
          </Col>

          <Col span={24}>
             <Card
               className="monitor-card disk-card"
               title={
                 <div className="card-header-content">
                   <div className="card-icon"><HddOutlined /></div>
                   <span>磁盘状态</span>
                 </div>
               }
               hoverable
             >
               <div className="table-container">
                 <Table
                   columns={diskColumns}
                   dataSource={sysFiles}
                   rowKey="dirName"
                   pagination={false}
                   size="middle"
                   scroll={{ x: 810 }}
                 />
               </div>
             </Card>
          </Col>
        </Row>
      </div>
    </div>
  );
};

export default Server;
