import React from 'react';
import { Tabs } from 'antd';
import TaskListTab from './components/TaskListTab';
import CreateEditTab from './components/CreateEditTab';
import StatisticsTab from './components/StatisticsTab';

const BatchTransferPage = () => {
  const items = [
    {
      key: 'list',
      label: '任务列表',
      children: <TaskListTab />
    },
    {
      key: 'create',
      label: '创建/编辑',
      children: <CreateEditTab />
    },
    {
      key: 'statistics',
      label: '统计面板',
      children: <StatisticsTab />
    }
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Tabs defaultActiveKey="list" items={items} size="large" />
    </div>
  );
};

export default BatchTransferPage;
