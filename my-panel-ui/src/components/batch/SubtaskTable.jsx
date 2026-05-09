import React, { useState } from 'react';
import { Table, Tag, Button, Progress, Tooltip, Descriptions, Space } from 'antd';
import { RedoOutlined, InfoCircleOutlined } from '@ant-design/icons';

const statusColorMap = {
  QUEUED: 'default', SENDING: 'processing', COMPLETED: 'success',
  FAILED: 'error', RETRYING: 'warning', CANCELLED: 'default'
};

const statusLabel = {
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

const formatSpeed = (bytesPerSec) => {
  if (bytesPerSec == null || bytesPerSec <= 0) return '-';
  const mbps = bytesPerSec / (1024 * 1024);
  if (mbps >= 1) return mbps.toFixed(2) + ' MB/s';
  const kbps = bytesPerSec / 1024;
  return kbps.toFixed(1) + ' KB/s';
};

const formatDuration = (ms) => {
  if (ms == null || ms < 0) return '-';
  if (ms < 1000) return ms + 'ms';
  const s = ms / 1000;
  if (s < 60) return s.toFixed(1) + 's';
  const m = Math.floor(s / 60);
  const rs = Math.floor(s % 60);
  if (m < 60) return `${m}m${rs}s`;
  const h = Math.floor(m / 60);
  return `${h}h${m % 60}m`;
};

const formatTime = (t) => {
  if (!t) return '-';
  try { return new Date(t).toLocaleString(); } catch { return t; }
};

const SubtaskTable = ({ subtasks = [], loading = false, onRetry, enrichedInfo }) => {
  const [expandedKeys, setExpandedKeys] = useState([]);

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

  const renderProgress = (_, r) => {
    if (r.status === 'COMPLETED') return <Progress percent={100} size="small" status="success" />;
    const totalChunks = r.totalChunks || 0;
    const transferredChunks = r.transferredChunks || 0;
    if (totalChunks > 0) {
      const pct = Math.round(transferredChunks / totalChunks * 100);
      return <Progress percent={pct} size="small" status={r.status === 'FAILED' ? 'exception' : 'active'} />;
    }
    if (r.fileSizeBytes > 0 && r.transferredBytes > 0) {
      const pct = Math.round(r.transferredBytes / r.fileSizeBytes * 100);
      return <Progress percent={pct} size="small" status="active" />;
    }
    return <span style={{ color: '#bbb' }}>-</span>;
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60, align: 'center' },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', width: 200, ellipsis: true },
    { title: '大小', dataIndex: 'fileSizeBytes', key: 'size', width: 90, align: 'right',
      render: v => formatBytes(v) },
    { title: '状态', dataIndex: 'status', key: 'status', width: 90, align: 'center',
      render: s => <Tag color={statusColorMap[s]}>{statusLabel[s] || s}</Tag> },
    { title: '传输进度', key: 'progress', width: 150, render: renderProgress },
    { title: '发送节点', key: 'srcNode', width: 120, ellipsis: true,
      render: (_, r) => <Tooltip title={r.sourceAgentName || sourceNodeName}><span>{r.sourceAgentName || sourceNodeName}</span></Tooltip> },
    { title: '源路径', key: 'srcPath', width: 250, ellipsis: true,
      render: (_, r) => <Tooltip title={r.sourcePath}><span style={{ fontFamily: 'monospace', fontSize: 12 }}>{r.sourcePath || '-'}</span></Tooltip> },
    { title: '接收节点', key: 'tgtNode', width: 120, ellipsis: true,
      render: (_, r) => { const n = r.targetAgentName || getTargetNodeName(r.targetAgentId); return <Tooltip title={n}><span>{n}</span></Tooltip>; } },
    { title: '目标路径', key: 'tgtPath', width: 250, ellipsis: true,
      render: (_, r) => <Tooltip title={r.targetPath}><span style={{ fontFamily: 'monospace', fontSize: 12 }}>{r.targetPath || '-'}</span></Tooltip> },
    { title: '操作', key: 'action', width: 120, fixed: 'right', align: 'center',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<InfoCircleOutlined />}
            onClick={() => setExpandedKeys(prev => prev.includes(r.id) ? prev.filter(k => k !== r.id) : [...prev, r.id])}>
            {expandedKeys.includes(r.id) ? '收起' : '详情'}
          </Button>
          {r.status === 'FAILED' && onRetry && (
            <Button type="link" size="small" danger icon={<RedoOutlined />}
              onClick={() => onRetry(r.taskId, r.id)}>重试</Button>
          )}
        </Space>
      )
    },
  ];

  const expandedRowRender = (r) => {
    const totalChunks = r.totalChunks || 0;
    const transferredChunks = r.transferredChunks || 0;

    return (
      <Descriptions size="small" bordered column={3} style={{ margin: '4px 0' }}
        labelStyle={{ width: 120, background: '#fafafa' }} contentStyle={{ minWidth: 120 }}>
        <Descriptions.Item label="文件名">{r.fileName || '-'}</Descriptions.Item>
        <Descriptions.Item label="大小">{formatBytes(r.fileSizeBytes)}</Descriptions.Item>
        <Descriptions.Item label="修改时间">{formatTime(r.fileLastModified)}</Descriptions.Item>

        <Descriptions.Item label="MD5" span={2}>
          {r.fileMd5 ? <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{r.fileMd5}</span> : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="传输ID">{r.transferId || '-'}</Descriptions.Item>

        <Descriptions.Item label="发送速率">{formatSpeed(r.speedBytesPerSec)}</Descriptions.Item>
        <Descriptions.Item label="耗时">{formatDuration(r.durationMs)}</Descriptions.Item>
        <Descriptions.Item label="已传字节">{formatBytes(r.transferredBytes)}</Descriptions.Item>

        <Descriptions.Item label="分块进度">{transferredChunks + ' / ' + (totalChunks || '-')}</Descriptions.Item>
        <Descriptions.Item label="开始时间">{formatTime(r.startedAt)}</Descriptions.Item>
        <Descriptions.Item label="完成时间">{formatTime(r.completedAt)}</Descriptions.Item>

        <Descriptions.Item label="客户端重试"><Tag>{r.retryCount ?? 0}</Tag></Descriptions.Item>
        <Descriptions.Item label="Proxy重试"><Tag>{r.proxyRetryCount ?? 0}</Tag></Descriptions.Item>
        <Descriptions.Item label="上次重试">{formatTime(r.lastRetryAt)}</Descriptions.Item>

        <Descriptions.Item label="下次重试">{formatTime(r.nextRetryAfter)}</Descriptions.Item>
        <Descriptions.Item label="错误码">{r.errorCode || '-'}</Descriptions.Item>
        <Descriptions.Item label="错误信息">
          {r.errorMessage ? <Tooltip title={r.errorMessage}><span style={{ color: '#ff4d4f' }}>{r.errorMessage}</span></Tooltip> : '-'}
        </Descriptions.Item>
      </Descriptions>
    );
  };

  return (
    <Table
      rowKey="id"
      columns={columns}
      dataSource={subtasks}
      loading={loading}
      pagination={{ pageSize: 20, size: 'small', showTotal: t => `共 ${t} 条` }}
      size="small"
      scroll={{ x: 1400 }}
      expandable={{
        expandedRowRender,
        expandedRowKeys: expandedKeys,
        rowExpandable: () => true,
        showExpandColumn: false,
      }}
    />
  );
};

export default SubtaskTable;
