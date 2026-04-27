import React, { useState, useEffect, useRef } from 'react';
import { Card, Row, Col, Table, Skeleton, Button, Space, Typography, Progress } from 'antd';
import * as echarts from 'echarts';
import { getServer, getProcessMemoryDistribution } from '../../../api/monitor/server';
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
  const [processMemory, setProcessMemory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showSwagger, setShowSwagger] = useState(false);
  const [showActuator, setShowActuator] = useState(false);
  const memoryPieRef = useRef(null);
  const memoryChartRef = useRef(null);

  const fetchData = async () => {
    try {
      const [serverRes, swaggerRes, actuatorRes, memoryRes] = await Promise.all([
        getServer(),
        getConfigKey('sys.monitor.showSwagger'),
        getConfigKey('sys.monitor.showActuator'),
        getProcessMemoryDistribution()
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
      if (memoryRes.code === 200 && memoryRes.data) {
        setProcessMemory(memoryRes.data);
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

  useEffect(() => {
    if (!memoryPieRef.current || !processMemory?.regions) return;
    if (!memoryChartRef.current) {
      memoryChartRef.current = echarts.init(memoryPieRef.current);
    }
    const chart = memoryChartRef.current;
    const pieData = processMemory.regions.map((r, index) => ({
      name: r.name,
      value: r.bytes
    }));
    const PIE_COLORS = ['#1677ff', '#13c2c2', '#722ed1', '#eb2f96', '#fa8c16', '#2f54eb', '#52c41a', '#f5222d'];
    const option = {
      backgroundColor: 'transparent',
      title: {
        text: `${processMemory.rssMb} MB`,
        subtext: 'RES',
        left: '38%',
        top: '46%',
        textAlign: 'center',
        textStyle: {
          fontSize: 24,
          fontWeight: 'bold',
          color: '#1e293b'
        },
        subtextStyle: {
          fontSize: 13,
          color: '#94a3b8',
          fontWeight: 600
        }
      },
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(17, 24, 39, 0.92)',
        borderColor: 'rgba(59, 130, 246, 0.4)',
        textStyle: { color: '#dbeafe' },
        formatter: (params) => {
          const region = processMemory.regions.find(r => r.name === params.name);
          return `<strong>${params.name}</strong><br/>` +
            `占用: ${region?.mb || 0} MB<br/>` +
            `占比: ${params.percent}%`;
        }
      },
      legend: {
        orient: 'vertical',
        right: '5%',
        top: 'center',
        textStyle: { color: '#4b5563', fontSize: 13 },
        formatter: (name) => {
          const region = processMemory.regions.find(r => r.name === name);
          return `${name}  ${region?.percent || 0}%`;
        }
      },
      series: [
        {
          name: '内存分布',
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['38%', '50%'],
          avoidLabelOverlap: true,
          itemStyle: {
            borderRadius: 8,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            show: false,
            position: 'center'
          },
          emphasis: {
            label: {
              show: true,
              fontSize: 18,
              fontWeight: 'bold',
              color: '#1e293b',
              formatter: () => `${processMemory.rssMb} MB`
            }
          },
          labelLine: { show: false },
          data: pieData.map((item, i) => ({
            ...item,
            itemStyle: { color: PIE_COLORS[i % PIE_COLORS.length] }
          }))
        }
      ]
    };
    chart.setOption(option, true);
    const onResize = () => chart.resize();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [processMemory]);

  useEffect(() => {
    return () => {
      if (memoryChartRef.current) {
        memoryChartRef.current.dispose();
        memoryChartRef.current = null;
      }
    };
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
              <div className="metric-content">
                <div className="metric-list">
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
                </div>
                <div className="metric-ring">
                  <Progress
                    type="circle"
                    percent={parseInt(cpu?.used)}
                    strokeColor="#6366f1"
                    strokeWidth={8}
                    width={120}
                    format={(percent) => `${percent}%`}
                  />
                </div>
              </div>
            </Card>
          </Col>

          <Col xs={24} md={12}>
            <Card
              className="monitor-card memory-card"
              title={
                <div className="card-header-content">
                  <div className="card-icon"><DatabaseOutlined /></div>
                  <span>系统内存使用情况</span>
                </div>
              }
              hoverable
            >
              <div className="metric-content">
                <div className="metric-list">
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
                </div>
                <div className="metric-ring">
                  <Progress
                    type="circle"
                    percent={parseInt(mem?.usage)}
                    strokeColor="#10b981"
                    strokeWidth={8}
                    width={120}
                    format={(percent) => `${percent}%`}
                  />
                </div>
              </div>
            </Card>
          </Col>
        </Row>

        <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
          <Col xs={24} md={12}>
            <Card
              className="monitor-card jvm-memory-card"
              title={
                <div className="card-header-content">
                  <div className="card-icon"><CodeOutlined /></div>
                  <span>JVM 堆内存使用情况</span>
                </div>
              }
              hoverable
            >
              <div className="metric-content">
                <div className="metric-list">
                  <div className="metric-item">
                    <span className="metric-label">堆已提交内存</span>
                    <span className="metric-value">{jvm?.total}MB</span>
                  </div>
                  <div className="metric-item">
                    <span className="metric-label">堆已使用内存</span>
                    <span className="metric-value">{jvm?.used}MB</span>
                  </div>
                  <div className="metric-item">
                    <span className="metric-label">堆空闲内存</span>
                    <span className="metric-value success">{jvm?.free}MB</span>
                  </div>
                  <div className="metric-item">
                    <span className="metric-label">堆内存使用率</span>
                    <span className={`metric-value ${jvm?.usage > 80 ? 'warning' : 'success'}`}>{jvm?.usage}%</span>
                  </div>
                </div>
                <div className="metric-ring">
                  <Progress
                    type="circle"
                    percent={parseInt(jvm?.usage)}
                    strokeColor="#f59e0b"
                    strokeWidth={8}
                    width={120}
                    format={(percent) => `${percent}%`}
                  />
                </div>
              </div>
            </Card>
          </Col>
        </Row>

        <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
          <Col span={24}>
            <Card
              className="monitor-card memory-pie-card"
              title={
                <div className="card-header-content">
                  <div className="card-icon"><DatabaseOutlined /></div>
                  <span>进程内存分布 (RES: {processMemory?.rssMb || '-'} MB)</span>
                </div>
              }
              hoverable
            >
              {processMemory?.regions ? (
                <>
                  <div ref={memoryPieRef} style={{ height: 360 }} />
                  {processMemory.kernelResource && processMemory.kernelResource.totalKernelBytes > 0 && (
                    <div className="kernel-resource-detail">
                      <div className="kernel-detail-title">内核资源明细</div>
                      <div className="kernel-detail-grid">
                        <div className="kernel-detail-item">
                          <span className="kd-label">文件描述符/句柄</span>
                          <span className="kd-value">{processMemory.kernelResource.openFileDescriptors} 个</span>
                          <span className="kd-size">{processMemory.kernelResource.fileDescriptorMb} MB</span>
                        </div>
                        <div className="kernel-detail-item">
                          <span className="kd-label">Socket缓冲区</span>
                          <span className="kd-value">{processMemory.kernelResource.socketCount} 个</span>
                          <span className="kd-size">{processMemory.kernelResource.socketBufferMb} MB</span>
                        </div>
                        <div className="kernel-detail-item">
                          <span className="kd-label">内存映射文件</span>
                          <span className="kd-value">{processMemory.kernelResource.mappedFileCount} 个</span>
                          <span className="kd-size">{processMemory.kernelResource.mappedFileMb} MB</span>
                        </div>
                        <div className="kernel-detail-item">
                          <span className="kd-label">共享内存</span>
                          <span className="kd-value">-</span>
                          <span className="kd-size">{processMemory.kernelResource.sharedMemoryMb} MB</span>
                        </div>
                        <div className="kernel-detail-item">
                          <span className="kd-label">页表占用</span>
                          <span className="kd-value">-</span>
                          <span className="kd-size">{processMemory.kernelResource.pageTableMb} MB</span>
                        </div>
                        <div className="kernel-detail-item kernel-detail-total">
                          <span className="kd-label">内核资源合计</span>
                          <span className="kd-value"></span>
                          <span className="kd-size kd-total-size">{processMemory.kernelResource.totalKernelMb} MB</span>
                        </div>
                      </div>
                    </div>
                  )}
                </>
              ) : (
                <div style={{ height: 380, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>
                  暂无数据
                </div>
              )}
            </Card>
          </Col>
        </Row>

        <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
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
        </Row>

        <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
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
                  <div className="info-label">进程ID</div>
                  <div className="info-value">{jvm?.pid}</div>
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
                  <div className="info-label">jdk安装路径</div>
                  <div className="info-value">{jvm?.home}</div>
                </div>
              </div>
              <div className="info-item-full">
                <div className="info-label">运行参数</div>
                <div className="info-value">{jvm?.inputArgs}</div>
              </div>
            </Card>
          </Col>
        </Row>

        <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
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
