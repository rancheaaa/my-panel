import React from 'react';
import { Card, Table, Tag, Button, Progress, Tooltip } from 'antd';
import { RedoOutlined } from '@ant-design/icons';

const subtaskStatusColorMap = {
  QUEUED: 'default', SENDING: 'processing', COMPLETED: 'success',
  FAILED: 'error', RETRYING: 'warning', CANCELLED: 'default'
};

const SubtaskTable = ({ subtasks = [], loading = false, onRetry }) => {
  const columns = [
    { title: '文件路径', dataIndex: 'filePath', key: 'filePath', ellipsis: true },
    { title: '目标Agent', dataIndex: 'targetAgentId', key: 'targetAgentId', width: 140 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: s => <Tag color={subtaskStatusColorMap[s]}>{s}</Tag>
    },
    { title: '大小', dataIndex: 'fileSizeBytes', key: 'fileSizeBytes', width: 100,
      render: v => v ? `${(v / 1024).toFixed(1)} KB` : '-'
    },
    { title: '进度', key: 'progress', width: 150,
      render: (_, r) => {
        if (r.totalChunks > 0) {
          const pct = Math.round((r.transferredChunks || 0) / r.totalChunks * 100);
          return <Progress percent={pct} size="small" status={r.status === 'FAILED' ? 'exception' : 'active'} />;
        }
        return '-';
      }
    },
    { title: '速率', dataIndex: 'speedBytesPerSec', key: 'speed', width: 100,
      render: v => v ? `${(v / 1024).toFixed(1)} KB/s` : '-'
    },
    { title: '重试', key: 'retry', width: 80,
      render: (_, r) => `${r.retryCount || 0}/${r.proxyRetryCount || 0}`
    },
    { title: '错误', dataIndex: 'errorMessage', key: 'error', ellipsis: true,
      render: v => <Tooltip title={v}>{v || '-'}</Tooltip>
    },
    { title: '操作', key: 'action', width: 60,
      render: (_, r) => r.status === 'FAILED' && onRetry ? (
        <Button type="link" size="small" icon={<RedoOutlined />} onClick={() => onRetry(r.taskId, r.id)} />
      ) : null
    },
  ];

  return (
    <Card title={`子任务明细 (${subtasks.length})`} size="small">
      <Table rowKey="id" columns={columns} dataSource={subtasks} loading={loading}
        pagination={{ pageSize: 20, size: 'small' }} size="small" scroll={{ x: 1000 }} />
    </Card>
  );
};

export default SubtaskTable;
