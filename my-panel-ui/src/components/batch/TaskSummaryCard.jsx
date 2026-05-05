import React from 'react';
import { Card, Descriptions, Tag, Progress, Row, Col, Statistic } from 'antd';

const statusColorMap = {
  PENDING: 'default', SCANNING: 'processing', TRANSFERRING: 'processing',
  PAUSED: 'warning', POST_PROCESSING: 'processing', COMPLETED: 'success',
  PARTIAL_FAILED: 'error', FAILED: 'error', CANCELLED: 'default', EXPIRED: 'default'
};

const transferModeLabel = { ONE_TO_ONE: '一对一 (1:1)', ONE_TO_MANY: '一对多 (1:N)' };
const routingStrategyLabel = {
  BROADCAST: '广播', SINGLE: '单机粘性', ROUND_ROBIN: '轮询',
  REGION_BASED: '区域路由', RANDOM: '随机'
};
const postTransferActionLabel = { NONE: '无操作', DELETE: '删除源文件', BACKUP: '备份' };
const backupModeLabel = { COPY: '复制', MOVE: '移动' };

const parseJsonSafe = (str) => {
  if (!str) return null;
  try { return JSON.parse(str); } catch { return str; }
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

  const includePatterns = parseJsonSafe(task.includePatterns);
  const excludePatterns = parseJsonSafe(task.excludePatterns);
  const routingConfig = parseJsonSafe(task.routingConfig);
  const targetAgents = parseJsonSafe(task.targetAgents);

  const renderPatterns = (patterns) => {
    if (!patterns) return '-';
    if (Array.isArray(patterns)) return patterns.length > 0 ? patterns.join(', ') : '-';
    if (typeof patterns === 'string') return patterns;
    return '-';
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
        <Descriptions.Item label="传输模式">{transferModeLabel[task.transferMode] || task.transferMode}</Descriptions.Item>

        <Descriptions.Item label="源Agent">{task.sourceAgentId}</Descriptions.Item>
        <Descriptions.Item label="源目录">{task.sourceDir}</Descriptions.Item>
        <Descriptions.Item label="目标目录">{task.targetDirs}</Descriptions.Item>

        <Descriptions.Item label="路由策略">{routingStrategyLabel[task.routingStrategy] || task.routingStrategy}</Descriptions.Item>
        <Descriptions.Item label="总大小">{formatBytes(task.totalSizeBytes)}</Descriptions.Item>
        <Descriptions.Item label="已传输">{formatBytes(task.transferredSizeBytes)}</Descriptions.Item>

        <Descriptions.Item label="包含模式(Glob)" span={2}>{renderPatterns(includePatterns)}</Descriptions.Item>
        <Descriptions.Item label="保持目录结构">{task.preserveDirStructure === 1 ? '是' : '否'}</Descriptions.Item>

        <Descriptions.Item label="排除模式(Glob)" span={2}>{renderPatterns(excludePatterns)}</Descriptions.Item>
        <Descriptions.Item label="带宽限制(KB/s)">{task.maxBandwidthKbS || '不限'}</Descriptions.Item>

        <Descriptions.Item label="后处理操作">{postTransferActionLabel[task.postTransferAction] || task.postTransferAction || 'NONE'}</Descriptions.Item>
        {(task.postTransferAction === 'BACKUP') && (
          <>
            <Descriptions.Item label="备份目录">{task.backupDir}</Descriptions.Item>
            <Descriptions.Item label="备份模式">{backupModeLabel[task.backupMode] || task.backupMode}</Descriptions.Item>
          </>
        )}
        {!task.postTransferAction && (
          <>
            <Descriptions.Item label="备份目录">-</Descriptions.Item>
            <Descriptions.Item label="备份模式">-</Descriptions.Item>
          </>
        )}

        <Descriptions.Item label="自动重试">{task.retryEnabled === 1 ? '是' : '否'}</Descriptions.Item>
        {task.retryEnabled === 1 && (
          <>
            <Descriptions.Item label="重试保留(天)">{task.retryMaxDays}</Descriptions.Item>
            <Descriptions.Item label="重试间隔(分)">{task.retryIntervalMin}</Descriptions.Item>
          </>
        )}
        {task.retryEnabled !== 1 && (
          <>
            <Descriptions.Item label="重试保留(天)">-</Descriptions.Item>
            <Descriptions.Item label="重试间隔(分)">-</Descriptions.Item>
          </>
        )}
      </Descriptions>
      {routingConfig && typeof routingConfig === 'object' && Object.keys(routingConfig).length > 0 && (
        <Descriptions bordered column={3} size="small" title="区域路由配置" style={{ marginTop: 12 }}>
          {Object.entries(routingConfig).map(([key, value]) => (
            <Descriptions.Item key={key} label={key}>{JSON.stringify(value)}</Descriptions.Item>
          ))}
        </Descriptions>
      )}
      {targetAgents && Array.isArray(targetAgents) && targetAgents.length > 0 && (
        <Descriptions bordered column={3} size="small" title="目标节点列表" style={{ marginTop: 12 }}>
          {targetAgents.map((agentId, idx) => (
            <Descriptions.Item key={idx} label={`目标${idx + 1}`}>{agentId}</Descriptions.Item>
          ))}
        </Descriptions>
      )}
    </Card>
  );
};

export default TaskSummaryCard;
