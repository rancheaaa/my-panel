import React from 'react';
import { Card, Alert, Space } from 'antd';

const MitigationSuggestion = ({ congestionLevel, congestionReason, sendQueueDepth, processingRatePerSec, avgWaitMs }) => {
  if (!congestionLevel || congestionLevel === 'NORMAL') return null;

  const suggestions = [];
  if (sendQueueDepth > 1000) suggestions.push('降低扫描频率至当前值×2，减少入队速率');
  if (processingRatePerSec < 10) suggestions.push('减小maxScanFiles至当前值÷2，降低单次扫描量');
  if (avgWaitMs > 150000) suggestions.push('临时降低带宽限制至当前50%，释放网络资源');
  if (congestionLevel === 'CRITICAL') suggestions.push('考虑暂停非紧急任务释放资源');

  if (suggestions.length === 0) return null;

  return (
    <Card size="small" style={{ marginBottom: 16 }}>
      <Alert
        type={congestionLevel === 'CRITICAL' ? 'error' : 'warning'}
        message={`堵塞缓解建议 (${congestionLevel})`}
        description={
          <Space direction="vertical" style={{ width: '100%' }}>
            {congestionReason && <div>堵塞原因: {congestionReason}</div>}
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              {suggestions.map((s, i) => <li key={i}>{s}</li>)}
            </ul>
          </Space>
        }
        showIcon
      />
    </Card>
  );
};

export default MitigationSuggestion;
