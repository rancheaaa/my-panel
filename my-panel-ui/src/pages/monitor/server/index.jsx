import React, { useState, useEffect } from 'react';
import { Card, Descriptions, Row, Col, Table, Tag, Badge, Skeleton, Button, Space } from 'antd';
import { getServer } from '../../../api/monitor/server';
import { getConfigKey } from '../../../api/config';
import { HddOutlined, CodeOutlined, DesktopOutlined, DashboardOutlined, FileTextOutlined } from '@ant-design/icons';

const Server = () => {
  const [server, setServer] = useState({});
  const [loading, setLoading] = useState(true);
  const [showSwagger, setShowSwagger] = useState(false);
  const [showActuator, setShowActuator] = useState(false);

  useEffect(() => {
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
      }
    };
    fetchData();
  }, []);

  if (loading) {
      return (
          <div className="app-container">
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
        render: (text) => <span style={{ color: text > 80 ? 'red' : 'green' }}>{text}%</span>
    },
  ];

  return (
    <div className="app-container">
      <Row gutter={[16, 16]}>
        {(showActuator || showSwagger) && (
          <Col span={24}>
              <div style={{ textAlign: 'left', marginBottom: 16 }}>
                  <Space size="middle">
                      {showActuator && (
                        <Button type="primary" onClick={handleOpenActuator} icon={<DashboardOutlined />}>
                            Actuator监控
                        </Button>
                      )}
                      {showSwagger && (
                        <Button type="primary" onClick={handleOpenSwagger} icon={<FileTextOutlined />} style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }}>
                            Swagger文档
                        </Button>
                      )}
                  </Space>
              </div>
          </Col>
        )}
        <Col span={12}>
          <Card title={<Space><DashboardOutlined />CPU</Space>} bordered={false}>
            <Descriptions column={1} bordered size="small">
                <Descriptions.Item label="核心数">{cpu?.cpuNum}</Descriptions.Item>
                <Descriptions.Item label="用户使用率">{cpu?.used}%</Descriptions.Item>
                <Descriptions.Item label="系统使用率">{cpu?.sys}%</Descriptions.Item>
                <Descriptions.Item label="当前空闲率">{cpu?.free}%</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col span={12}>
          <Card title={<Space><DashboardOutlined />内存</Space>} bordered={false}>
            <Descriptions column={1} bordered size="small">
                <Descriptions.Item label="总内存">{mem?.total}GB</Descriptions.Item>
                <Descriptions.Item label="已用内存">{mem?.used}GB</Descriptions.Item>
                <Descriptions.Item label="剩余内存">{mem?.free}GB</Descriptions.Item>
                <Descriptions.Item label="使用率">
                    <span style={{ color: mem?.usage > 80 ? 'red' : 'green' }}>{mem?.usage}%</span>
                </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        
        <Col span={24}>
          <Card title={<Space><DesktopOutlined />服务器信息</Space>} bordered={false}>
            <Descriptions column={2} bordered size="small">
                <Descriptions.Item label="服务器名称">{sys?.computerName}</Descriptions.Item>
                <Descriptions.Item label="操作系统">{sys?.osName}</Descriptions.Item>
                <Descriptions.Item label="服务器IP">{sys?.computerIp}</Descriptions.Item>
                <Descriptions.Item label="系统架构">{sys?.osArch}</Descriptions.Item>
                <Descriptions.Item label="项目路径">{sys?.userDir}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        
        <Col span={24}>
          <Card title={<Space><CodeOutlined />Java虚拟机信息</Space>} bordered={false}>
             <Descriptions column={2} bordered size="small">
                <Descriptions.Item label="Java名称">{jvm?.name}</Descriptions.Item>
                <Descriptions.Item label="Java版本">{jvm?.version}</Descriptions.Item>
                <Descriptions.Item label="启动时间">{jvm?.startTime}</Descriptions.Item>
                <Descriptions.Item label="运行时长">{jvm?.runTime}</Descriptions.Item>
                <Descriptions.Item label="安装路径" span={2}>{jvm?.home}</Descriptions.Item>
                <Descriptions.Item label="项目路径" span={2}>{sys?.userDir}</Descriptions.Item>
                <Descriptions.Item label="运行参数" span={2}>{jvm?.inputArgs}</Descriptions.Item>
             </Descriptions>
          </Card>
        </Col>

        <Col span={24}>
           <Card title={<Space><HddOutlined />磁盘状态</Space>} bordered={false}>
               <Table 
                 columns={diskColumns} 
                 dataSource={sysFiles} 
                 rowKey="dirName" 
                 pagination={false} 
                 size="small"
                 scroll={{ x: 810 }}
               />
           </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Server;
