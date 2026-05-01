import React from 'react';
import { Progress, Tooltip, Tag } from 'antd';

const statusColorMap = {
  QUEUED: 'default', SENDING: 'processing', COMPLETED: 'success',
  FAILED: 'error', RETRYING: 'warning', CANCELLED: 'default'
};

const FileProgressBar = ({ subtask }) => {
  if (!subtask) return null;

  const pct = subtask.totalChunks > 0
    ? Math.round((subtask.transferredChunks || 0) / subtask.totalChunks * 100)
    : 0;

  const formatSpeed = (bytesPerSec) => {
    if (!bytesPerSec) return '';
    if (bytesPerSec >= 1024 * 1024) return `${(bytesPerSec / 1024 / 1024).toFixed(1)} MB/s`;
    return `${(bytesPerSec / 1024).toFixed(1)} KB/s`;
  };

  const formatSize = (bytes) => {
    if (!bytes) return '0 B';
    if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
    if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${bytes} B`;
  };

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <Tag color={statusColorMap[subtask.status]} style={{ minWidth: 70, textAlign: 'center' }}>
        {subtask.status}
      </Tag>
      <Tooltip title={`${subtask.filePath} → ${subtask.targetAgentId}`}>
        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 12 }}>
          {subtask.fileName || subtask.filePath}
        </span>
      </Tooltip>
      <Progress percent={pct} size="small" style={{ width: 120 }}
        status={subtask.status === 'FAILED' ? 'exception' : 'active'} />
      <span style={{ fontSize: 11, color: '#888', minWidth: 80, textAlign: 'right' }}>
        {formatSize(subtask.transferredBytes)} / {formatSize(subtask.fileSizeBytes)}
      </span>
      <span style={{ fontSize: 11, color: '#1890ff', minWidth: 80, textAlign: 'right' }}>
        {formatSpeed(subtask.speedBytesPerSec)}
      </span>
    </div>
  );
};

export default FileProgressBar;
