import React from 'react';
import { Card, Row, Col, Statistic, Tag, Progress, Tooltip } from 'antd';

const congestionColorMap = { NORMAL: 'green', WARNING: 'orange', CRITICAL: 'red' };

const QueueStatusCard = ({ agentId, status, loading }) => {
  if (!status) return <Card loading={loading}>选择Agent查看队列状态</Card>;

  const utilizationPct = status.sendQueueCapacity > 0
    ? Math.round(status.sendQueueDepth / status.sendQueueCapacity * 100)
    : 0;

  return (
    <Card title={`Agent: ${agentId}`} size="small" style={{ marginBottom: 16 }}
      extra={<Tag color={congestionColorMap[status.congestionLevel] || 'blue'}>{status.congestionLevel || 'UNKNOWN'}</Tag>}>
      <Row gutter={16}>
        <Col span={6}>
          <Statistic title="发送队列" value={status.sendQueueDepth}
            suffix={`/ ${status.sendQueueCapacity || 10000}`} />
          <Progress percent={utilizationPct} size="small"
            status={utilizationPct > 80 ? 'exception' : 'active'} style={{ marginTop: 4 }} />
        </Col>
        <Col span={6}><Statistic title="发送队列峰值" value={status.sendQueuePeakDepth} /></Col>
        <Col span={6}><Statistic title="重试队列" value={status.retryQueueDepth} /></Col>
        <Col span={6}><Statistic title="平均等待" value={status.sendQueueAvgWaitMs || 0}
          suffix="ms" valueStyle={{ color: (status.sendQueueAvgWaitMs || 0) > 60000 ? '#ff4d4f' : undefined }} /></Col>
      </Row>
      <Row gutter={16} style={{ marginTop: 12 }}>
        <Col span={8}><Statistic title="处理速率" value={status.processingRatePerSec || 0} suffix="文件/秒" /></Col>
        <Col span={8}>
          <Statistic title="堵塞" value={status.isCongested ? '是' : '否'}
            valueStyle={{ color: status.isCongested ? '#ff4d4f' : '#52c41a' }} />
        </Col>
        <Col span={8}>
          <Tooltip title={status.congestionReason}>
            <Statistic title="快照时间" value={status.snapshotTime || '-'} />
          </Tooltip>
        </Col>
      </Row>
    </Card>
  );
};

export default QueueStatusCard;
