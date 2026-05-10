import React from 'react';
import { Tag } from 'antd';
import { STATUS_COLOR } from '../constants';

const TaskStatusBadge = ({ status }) => {
  const color = STATUS_COLOR[status] || 'default';
  const displayText = status || 'UNKNOWN';

  return (
    <Tag color={color}>{displayText}</Tag>
  );
};

export default React.memo(TaskStatusBadge);
