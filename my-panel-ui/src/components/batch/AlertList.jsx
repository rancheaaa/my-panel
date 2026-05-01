import React from 'react';
import { Card, Table, Tag, Button, Space } from 'antd';

const alertLevelColorMap = {
  INFO: 'blue', WARNING: 'orange', ERROR: 'red', CRITICAL: 'red'
};

const AlertList = ({ alerts = [], loading = false, onResolve }) => {
  const columns = [
    { title: '级别', dataIndex: 'alertLevel', width: 80,
      render: v => <Tag color={alertLevelColorMap[v] || 'blue'}>{v}</Tag>
    },
    { title: '分类', dataIndex: 'alertCategory', width: 160, ellipsis: true },
    { title: '标题', dataIndex: 'alertTitle', ellipsis: true },
    { title: 'Agent', dataIndex: 'agentId', width: 120 },
    { title: '时间', dataIndex: 'createTime', width: 170 },
    { title: '状态', dataIndex: 'isResolved', width: 80,
      render: v => v ? <Tag color="green">已解决</Tag> : <Tag color="red">未解决</Tag>
    },
    { title: '操作', key: 'action', width: 80,
      render: (_, r) => !r.isResolved && onResolve ? (
        <Button type="link" size="small" onClick={() => onResolve(r.id)}>解决</Button>
      ) : null
    },
  ];

  return (
    <Card title={`告警事件 (${alerts.length})`} size="small">
      <Table rowKey="id" columns={columns} dataSource={alerts} loading={loading}
        pagination={{ pageSize: 10, size: 'small' }} size="small" />
    </Card>
  );
};

export default AlertList;
