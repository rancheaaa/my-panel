import React, { useState, useEffect } from 'react';
import { Card, Descriptions, Row, Col, Progress, Statistic, Skeleton } from 'antd';
import { getCache } from '../../../api/monitor/cache';
import { PieChartOutlined, DesktopOutlined } from '@ant-design/icons';

const Cache = () => {
  const [cache, setCache] = useState({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await getCache();
        if (res.code === 200) {
          setCache(res.data);
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

  const { info, dbSize, commandStats } = cache;

  return (
    <div className="app-container">
      <Row gutter={[16, 16]}>
        <Col span={24} md={24} lg={8}>
          <Card title="基本信息" bordered={false}>
            <Descriptions column={1} bordered size="small">
               <Descriptions.Item label="Redis版本">{info?.redis_version}</Descriptions.Item>
               <Descriptions.Item label="运行模式">{info?.redis_mode === 'standalone' ? '单机' : '集群'}</Descriptions.Item>
               <Descriptions.Item label="端口">{info?.tcp_port}</Descriptions.Item>
               <Descriptions.Item label="客户端数">{info?.connected_clients}</Descriptions.Item>
               <Descriptions.Item label="运行时间(天)">{info?.uptime_in_days}</Descriptions.Item>
               <Descriptions.Item label="使用内存">{info?.used_memory_human}</Descriptions.Item>
               <Descriptions.Item label="使用CPU">{info?.used_cpu_user_children}</Descriptions.Item>
               <Descriptions.Item label="内存配置">{info?.maxmemory_human}</Descriptions.Item>
               <Descriptions.Item label="AOF是否开启">{info?.aof_enabled === '0' ? '否' : '是'}</Descriptions.Item>
               <Descriptions.Item label="RDB是否成功">{info?.rdb_last_bgsave_status}</Descriptions.Item>
               <Descriptions.Item label="Key数量">{dbSize}</Descriptions.Item>
               <Descriptions.Item label="网络入口/出口">{info?.instantaneous_input_kbps}kps / {info?.instantaneous_output_kbps}kps</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        
        <Col span={24} md={24} lg={16}>
           <Card title="命令统计" bordered={false}>
               {/* 模拟图表，使用Progress条 */}
               <div style={{ height: 400, overflow: 'auto' }}>
                   {commandStats?.map((item) => (
                       <div key={item.name} style={{ marginBottom: 16 }}>
                           <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                               <span>{item.name}</span>
                               <span>{item.value}次</span>
                           </div>
                           <Progress percent={Math.min(item.value, 100)} status="active" strokeColor={
                               item.name === 'get' ? '#1890ff' : 
                               item.name === 'set' ? '#52c41a' : 
                               '#faad14'
                           }/>
                       </div>
                   ))}
               </div>
           </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Cache;
