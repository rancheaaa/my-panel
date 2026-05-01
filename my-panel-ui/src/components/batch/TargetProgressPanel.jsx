import React from 'react';
import { Card, Row, Col, Progress, Tag, Statistic } from 'antd';

const TargetProgressPanel = ({ targetProgress = [] }) => {
  if (!targetProgress || targetProgress.length === 0) return null;

  return (
    <Card title="目标Agent进度" size="small" style={{ marginBottom: 16 }}>
      <Row gutter={[16, 16]}>
        {targetProgress.map((tp, idx) => (
          <Col span={Math.max(6, Math.floor(24 / targetProgress.length))} key={tp.targetAgentId || idx}>
            <Card size="small" hoverable>
              <Statistic title={tp.targetAgentName || tp.targetAgentId}
                value={Number(tp.progressPercent) || 0} suffix="%" />
              <Progress percent={Number(tp.progressPercent) || 0}
                status={tp.failedSubtasks > 0 ? 'exception' : tp.progressPercent >= 100 ? 'success' : 'active'}
                size="small" style={{ marginTop: 8 }} />
              <Row gutter={8} style={{ marginTop: 8, fontSize: 12 }}>
                <Col span={8}><span>总数: {tp.totalSubtasks || 0}</span></Col>
                <Col span={8}><span style={{ color: '#52c41a' }}>完成: {tp.completedSubtasks || 0}</span></Col>
                <Col span={8}>{tp.failedSubtasks > 0 ? <Tag color="error">失败: {tp.failedSubtasks}</Tag> : <span>失败: 0</span>}</Col>
              </Row>
            </Card>
          </Col>
        ))}
      </Row>
    </Card>
  );
};

export default TargetProgressPanel;
