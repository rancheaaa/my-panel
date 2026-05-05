import React from 'react';
import { Table, Tag, Button, Progress, Tooltip, Space } from 'antd';
import { RedoOutlined } from '@ant-design/icons';

const subtaskStatusColorMap = {
  QUEUED: 'default', SENDING: 'processing', COMPLETED: 'success',
  FAILED: 'error', RETRYING: 'warning', CANCELLED: 'default'
};

const subtaskStatusLabel = {
  QUEUED: '排队中', SENDING: '传输中', COMPLETED: '已完成',
  FAILED: '失败', RETRYING: '重试中', CANCELLED: '已取消'
};

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatDuration = (ms) => {
  if (!ms) return '-';
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m${seconds % 60}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h${minutes % 60}m`;
};

const formatTime = (t) => {
  if (!t) return '-';
  try { return new Date(t).toLocaleString(); } catch { return t; }
};

const SubtaskTable = ({ subtasks = [], loading = false, onRetry }) => {
  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', width: 140, ellipsis: true },
    { title: '相对路径', dataIndex: 'filePath', key: 'filePath', ellipsis: true,
      render: v => <Tooltip title={v}><span style={{ fontFamily: 'monospace', fontSize: 12 }}>{v}</span></Tooltip>
    },
    { title: '大小', dataIndex: 'fileSizeBytes', key: 'fileSizeBytes', width: 90,
      render: v => v != null ? formatBytes(v) : '-'
    },
    { title: 'MD5', dataIndex: 'fileMd5', key: 'fileMd5', width: 120, ellipsis: true,
      render: v => v ? <Tooltip title={v}><span style={{ fontFamily: 'monospace', fontSize: 11 }}>{v.substring(0, 12)}...</span></Tooltip> : '-'
    },
    { title: '修改时间', dataIndex: 'fileLastModified', key: 'fileLastModified', width: 150,
      render: v => formatTime(v)
    },
    { title: '目标Agent', dataIndex: 'targetAgentId', key: 'targetAgentId', width: 160, ellipsis: true },
    { title: '状态', dataIndex: 'status', key: 'status', width: 90,
      render: s => <Tag color={subtaskStatusColorMap[s]}>{subtaskStatusLabel[s] || s}</Tag>
    },
    { title: '传输ID', dataIndex: 'transferId', key: 'transferId', width: 100, ellipsis: true,
      render: v => v || '-'
    },
    { title: '传输进度', key: 'progress', width: 150,
      render: (_, r) => {
        if (r.status === 'COMPLETED') return <Progress percent={100} size="small" status="success" />;
        if (r.totalChunks > 0 && r.totalChunks !== null) {
          const pct = Math.round((r.transferredChunks || 0) / r.totalChunks * 100);
          return <Progress percent={pct} size="small" status={r.status === 'FAILED' ? 'exception' : 'active'} />;
        }
        if (r.fileSizeBytes > 0 && r.transferredBytes > 0) {
          const pct = Math.round(r.transferredBytes / r.fileSizeBytes * 100);
          return <Progress percent={pct} size="small" status="active" />;
        }
        return <span style={{ color: '#999' }}>-</span>;
      }
    },
    { title: '已传字节', dataIndex: 'transferredBytes', key: 'transferredBytes', width: 95,
      render: v => v != null ? formatBytes(v) : '-'
    },
    { title: '分块进度', key: 'chunks', width: 100,
      render: (_, r) => (r.transferredChunks ?? 0) + '/' + (r.totalChunks ?? '-')
    },
    { title: '速率', dataIndex: 'speedBytesPerSec', key: 'speedBytesPerSec', width: 95,
      render: v => v != null && v > 0 ? formatBytes(v) + '/s' : '-'
    },
    { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt', width: 150,
      render: v => formatTime(v)
    },
    { title: '完成时间', dataIndex: 'completedAt', key: 'completedAt', width: 150,
      render: v => formatTime(v)
    },
    { title: '耗时', dataIndex: 'durationMs', key: 'durationMs', width: 80,
      render: v => formatDuration(v)
    },
    { title: '错误码', dataIndex: 'errorCode', key: 'errorCode', width: 100, ellipsis: true,
      render: v => v || '-'
    },
    { title: '错误信息', dataIndex: 'errorMessage', key: 'errorMessage', ellipsis: true,
      render: v => v ? <Tooltip title={v}><span style={{ maxWidth: 200 }}>{v}</span></Tooltip> : '-'
    },
    { title: '重试次数', key: 'retry', width: 85,
      render: (_, r) => (
        <Space size={2}>
          <Tooltip title="客户端重试"><Tag>{r.retryCount ?? 0}</Tag></Tooltip>
          <Tooltip title="Proxy端重试"><Tag>{r.proxyRetryCount ?? 0}</Tag></Tooltip>
        </Space>
      )
    },
    { title: '上次重试', dataIndex: 'lastRetryAt', key: 'lastRetryAt', width: 150,
      render: v => formatTime(v)
    },
    { title: '下次重试', dataIndex: 'nextRetryAfter', key: 'nextRetryAfter', width: 150,
      render: v => formatTime(v)
    },
    { title: '操作', key: 'action', width: 70, fixed: 'right',
      render: (_, r) => r.status === 'FAILED' && onRetry ? (
        <Button type="link" size="small" icon={<RedoOutlined />} onClick={() => onRetry(r.taskId, r.id)}>重试</Button>
      ) : null
    },
  ];

  return (
    <Table
      rowKey="id"
      columns={columns}
      dataSource={subtasks}
      loading={loading}
      pagination={{ pageSize: 20, size: 'small', showTotal: t => `共 ${t} 条` }}
      size="small"
      scroll={{ x: 2600 }}
    />
  );
};

export default SubtaskTable;
