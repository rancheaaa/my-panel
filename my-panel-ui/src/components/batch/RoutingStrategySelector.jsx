import React from 'react';
import { Radio, Space, Typography } from 'antd';

const strategyLabels = {
  BROADCAST: { label: '广播', desc: '所有目标Agent' },
  SINGLE: { label: '单机粘性', desc: '相同文件始终路由到同一台' },
  ROUND_ROBIN: { label: '轮询', desc: '按顺序均匀分配' },
  REGION_BASED: { label: '区域路由', desc: '按文件模式匹配区域' },
  RANDOM: { label: '随机', desc: '随机选择目标Agent' }
};

const RoutingStrategySelector = ({ value, onChange }) => {
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Radio.Group value={value} onChange={e => onChange(e.target.value)} optionType="button" buttonStyle="solid">
        {Object.entries(strategyLabels).map(([key, { label }]) => (
          <Radio.Button key={key} value={key}>{label}</Radio.Button>
        ))}
      </Radio.Group>
      {strategyLabels[value] && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>{strategyLabels[value].desc}</Typography.Text>
      )}
    </Space>
  );
};

export default RoutingStrategySelector;
