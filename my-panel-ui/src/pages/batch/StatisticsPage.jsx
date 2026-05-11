import React from 'react';
import { Typography } from 'antd';
import StatisticsTab from './components/StatisticsTab';

const { Title } = Typography;

const StatisticsPage = () => {
  return (
    <div style={{ padding: '24px' }}>
      <Title level={4} style={{ margin: 0, color: '#1f1f1f', marginBottom: 16 }}>统计面板</Title>
      <StatisticsTab />
    </div>
  );
};

export default StatisticsPage;
