import React from 'react';
import { Card, Row, Col, Statistic } from 'antd';
import { 
  FileOutlined, 
  PlayCircleOutlined, 
  PauseCircleOutlined,
  StopOutlined 
} from '@ant-design/icons';
import { useBatchTasks } from '../hooks/useBatchTasks';
import { usePolling } from '../hooks/usePolling';
import * as batchApi from '../../../api/batch';

const StatisticsTab = () => {
  const { data: statistics, stop, start } = usePolling(
    () => batchApi.getStatistics().then(res => res.data),
    5000
  );

  return (
    <Row gutter={[16, 16]}>
      <Col span={6}>
        <Card>
          <Statistic
            title="总任务数"
            value={statistics?.total || 0}
            prefix={<FileOutlined />}
          />
        </Card>
      </Col>
      <Col span={6}>
        <Card>
          <Statistic
            title="运行中"
            value={statistics?.running || 0}
            prefix={<PlayCircleOutlined style={{ color: '#52c41a' }} />}
            valueStyle={{ color: '#52c41a' }}
          />
        </Card>
      </Col>
      <Col span={6}>
        <Card>
          <Statistic
            title="已暂停"
            value={statistics?.paused || 0}
            prefix={<PauseCircleOutlined style={{ color: '#faad14' }} />}
            valueStyle={{ color: '#faad14' }}
          />
        </Card>
      </Col>
      <Col span={6}>
        <Card>
          <Statistic
            title="已停止"
            value={statistics?.stopped || 0}
            prefix={<StopOutlined style={{ color: '#ff4d4f' }} />}
            valueStyle={{ color: '#ff4d4f' }}
          />
        </Card>
      </Col>
    </Row>
  );
};

export default StatisticsTab;
