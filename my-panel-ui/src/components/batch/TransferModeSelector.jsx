import React from 'react';
import { Radio, Space, Typography } from 'antd';

const TransferModeSelector = ({ value, onChange }) => {
  return (
    <Space>
      <Radio.Group value={value} onChange={e => onChange(e.target.value)} optionType="button" buttonStyle="solid">
        <Radio.Button value="ONE_TO_ONE">一对一 (1:1)</Radio.Button>
        <Radio.Button value="ONE_TO_MANY">一对多 (1:N)</Radio.Button>
      </Radio.Group>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {value === 'ONE_TO_ONE' ? '每个文件传给一台Agent' : '每个文件传给所有目标Agent'}
      </Typography.Text>
    </Space>
  );
};

export default TransferModeSelector;
