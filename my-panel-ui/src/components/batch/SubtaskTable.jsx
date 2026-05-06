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
  if (bytes == null || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatSpeedMB = (bytesPerSec) => {
  if (bytesPerSec == null || bytesPerSec <= 0) return '-';
  const mbps = bytesPerSec / (1024 * 1024);
  if (mbps >= 1) return mbps.toFixed(2) + ' MB/s';
  const kbps = bytesPerSec / 1024;
  return kbps.toFixed(2) + ' KB/s';
};

const formatDuration = (ms) => {
  if (ms == null || ms < 0) return '-';
  if (ms < 1000) return ms + 'ms';
  const seconds = ms / 1000;
  if (seconds < 60) return seconds.toFixed(1) + 's';
  const minutes = Math.floor(seconds / 60);
  const remainSec = Math.floor(seconds % 60);
  if (minutes < 60) return `${minutes}m${remainSec}s`;
  const hours = Math.floor(minutes / 60);
  const remainMin = minutes % 60;
  return `${hours}h${remainMin}m`;
};

const formatTime = (t) => {
  if (!t) return '-';
  try { return new Date(t).toLocaleString(); } catch { return t; }
};

const SubtaskTable = ({ subtasks = [], loading = false, onRetry, enrichedInfo }) => {
  const sourceDir = enrichedInfo?.sourceDir || '-';
  const sourceNodeName = enrichedInfo?.sourceNodeName || '-';
  const targetAgentInfoMap = enrichedInfo?.targetAgentInfoMap || {};

  const getTargetDir = (agentId) => {
    if (!agentId || !targetAgentInfoMap[agentId]) return '-';
    return targetAgentInfoMap[agentId].dir || '-';
  };

  const getTargetNodeName = (agentId) => {
    if (!agentId || !targetAgentInfoMap[agentId]) return agentId || '-';
    return targetAgentInfoMap[agentId].nodeName || agentId;
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', width: 140, ellipsis: true },
    { title: '大小', dataIndex: 'fileSizeBytes', key: 'fileSizeBytes', width: 90,
      render: v => v != null ? formatBytes(v) : '-'
    },
    { title: '修改时间', dataIndex: 'fileLastModified', key: 'fileLastModified', width: 160,
      render: v => formatTime(v)
    },
    { title: '发送节点', key: 'sourceNodeName', width: 120, ellipsis: true,
      render: () => <Tooltip title={sourceNodeName}><span>{sourceNodeName}</span></Tooltip>
    },
    { title: '发送目录', key: 'sourceDir', width: 180, ellipsis: true,
      render: () => <Tooltip title={sourceDir}><span style={{ fontFamily: 'monospace', fontSize: 12 }}>{sourceDir}</span></Tooltip>
    },
    { title: '接收节点', key: 'targetNodeName', width: 120, ellipsis: true,
      render: (_, r) => {
        const name = getTargetNodeName(r.targetAgentId);
        return <Tooltip title={name}><span>{name}</span></Tooltip>;
      }
    },
    { title: '接收目录', key: 'targetDir', width: 180, ellipsis: true,
      render: (_, r) => {
        const dir = getTargetDir(r.targetAgentId);
        return <Tooltip title={dir}><span style={{ fontFamily: 'monospace', fontSize: 12 }}>{dir}</span></Tooltip>;
      }
    },
    { title: '状态', dataIndex: 'status', key: 'status', width: 90,
      render: s => <Tag color={subtaskStatusColorMap[s]}>{subtaskStatusLabel[s] || s}</Tag>
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
    { title: '发送速率', dataIndex: 'speedBytesPerSec', key: 'speedBytesPerSec', width: 110,
      render: v => formatSpeedMB(v)
    },
    { title: '耗时', dataIndex: 'durationMs', key: 'durationMs', width: 90,
      render: v => formatDuration(v)
    },
    { title: '已传字节', dataIndex: 'transferredBytes', key: 'transferredBytes', width: 95,
      render: v => v != null ? formatBytes(v) : '-'
    },
    { title: '分块进度', key: 'chunks', width: 100,
      render: (_, r) => (r.transferredChunks ?? 0) + '/' + (r.totalChunks ?? '-')
    },
    { title: 'MD5', dataIndex: 'fileMd5', key: 'fileMd5', width: 120, ellipsis: true,
      render: v => v ? <Tooltip title={v}><span style={{ fontFamily: 'monospace', fontSize: 11 }}>{v.substring(0, 12)}...</span></Tooltip> : '-'
    },
    { title: '传输ID', dataIndex: 'transferId', key: 'transferId', width: 100, ellipsis: true,
      render: v => v || '-'
    },
    { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt', width: 160,
      render: v => formatTime(v)
    },
    { title: '完成时间', dataIndex: 'completedAt', key: 'completedAt', width: 160,
      render: v => formatTime(v)
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
    { title: '上次重试', dataIndex: 'lastRetryAt', key: 'lastRetryAt', width: 160,
      render: v => formatTime(v)
    },
    { title: '下次重试', dataIndex: 'nextRetryAfter', key: 'nextRetryAfter', width: 160,
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
      scroll={{ x: 3000 }}
    />
  );
};

export default SubtaskTable;
