import React from 'react';
import { Card, Descriptions, Tag, Progress, Row, Col, Statistic } from 'antd';

const statusColorMap = {
  PENDING: 'default', SCANNING: 'processing', TRANSFERRING: 'processing',
  PAUSED: 'warning', POST_PROCESSING: 'processing', COMPLETED: 'success',
  PARTIAL_FAILED: 'error', FAILED: 'error', CANCELLED: 'default', EXPIRED: 'default'
};

const TaskSummaryCard = ({ task }) => {
  if (!task) return null;

  const formatBytes = (bytes) => {
    if (!bytes) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let i = 0;
    let val = bytes;
    while (val >= 1024 && i < units.length - 1) { val /= 1024; i++; }
    return `${val.toFixed(2)} ${units[i]}`;
  };

  return (
    <Card title="任务概览" size="small" style={{ marginBottom: 16 }}>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}><Statistic title="总文件" value={task.totalFiles || 0} /></Col>
        <Col span={6}><Statistic title="已完成" value={task.transferredFiles || 0} valueStyle={{ color: '#52c41a' }} /></Col>
        <Col span={6}><Statistic title="失败" value={task.failedFiles || 0} valueStyle={{ color: task.failedFiles > 0 ? '#ff4d4f' : undefined }} /></Col>
        <Col span={6}><Statistic title="后处理失败" value={task.postProcessFailed || 0} valueStyle={{ color: task.postProcessFailed > 0 ? '#ff4d4f' : undefined }} /></Col>
      </Row>
      <Progress
        percent={Number(task.progressPercent) || 0}
        status={task.failedFiles > 0 ? 'exception' : task.progressPercent >= 100 ? 'success' : 'active'}
        style={{ marginBottom: 16 }}
      />
      <Descriptions bordered column={3} size="small">
        <Descriptions.Item label="任务名称">{task.taskName}</Descriptions.Item>
        <Descriptions.Item label="状态"><Tag color={statusColorMap[task.status]}>{task.statusLabel || task.status}</Tag></Descriptions.Item>
        <Descriptions.Item label="传输模式">{task.transferMode === 'ONE_TO_ONE' ? '一对一' : '一对多'}</Descriptions.Item>
        <Descriptions.Item label="源Agent">{task.sourceAgentId}</Descriptions.Item>
        <Descriptions.Item label="源目录">{task.sourceDir}</Descriptions.Item>
        <Descriptions.Item label="目标目录">{task.targetDirs}</Descriptions.Item>
        <Descriptions.Item label="路由策略">{task.routingStrategy}</Descriptions.Item>
        <Descriptions.Item label="总大小">{formatBytes(task.totalSizeBytes)}</Descriptions.Item>
        <Descriptions.Item label="已传输">{formatBytes(task.transferredSizeBytes)}</Descriptions.Item>
        <Descriptions.Item label="后处理操作">{task.postTransferAction || 'NONE'}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export default TaskSummaryCard;
