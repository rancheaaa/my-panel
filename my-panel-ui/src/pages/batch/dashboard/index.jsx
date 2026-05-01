import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Statistic, Tag, Spin, Table, Progress } from 'antd';
import { getBatchDashboard } from '../../../api/batch/monitor';
import AlertList from '../../../components/batch/AlertList';

const healthColorMap = { HEALTHY: 'green', WARNING: 'orange', CRITICAL: 'red', OFFLINE: 'default' };
const statusColorMap = {
  PENDING: 'default', SCANNING: 'processing', TRANSFERRING: 'processing',
  PAUSED: 'warning', POST_PROCESSING: 'processing', COMPLETED: 'success',
  PARTIAL_FAILED: 'error', FAILED: 'error', CANCELLED: 'default', EXPIRED: 'default'
};

const BatchDashboard = () => {
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchDashboard = async () => {
    setLoading(true);
    try {
      const res = await getBatchDashboard();
      if (res.code === 200) setDashboard(res.data);
    } catch (e) { /* ignore */ }
    setLoading(false);
  };

  useEffect(() => { fetchDashboard(); }, []);

  const overview = dashboard?.overview;
  const agents = overview?.agents;
  const tasks = overview?.tasks;
  const performance = overview?.performance;
  const healthGrid = dashboard?.agentHealthGrid || [];
  const activeTasks = dashboard?.activeTasksSummary || [];
  const alerts = dashboard?.recentAlerts || [];

  return (
    <Spin spinning={loading}>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={4}><Card><Statistic title="已注册Agent" value={agents?.totalRegistered || 0} /></Card></Col>
        <Col span={4}><Card><Statistic title="在线Agent" value={agents?.online || 0} valueStyle={{ color: '#52c41a' }} /></Card></Col>
        <Col span={4}><Card><Statistic title="离线Agent" value={agents?.offline || 0} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
        <Col span={4}><Card><Statistic title="繁忙Agent" value={agents?.busy || 0} valueStyle={{ color: '#faad14' }} /></Card></Col>
        <Col span={4}><Card><Statistic title="活跃任务" value={tasks?.active || 0} valueStyle={{ color: '#1890ff' }} /></Card></Col>
        <Col span={4}><Card><Statistic title="暂停任务" value={tasks?.paused || 0} /></Card></Col>
      </Row>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}><Card><Statistic title="今日完成" value={tasks?.completedToday || 0} valueStyle={{ color: '#52c41a' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="今日失败" value={tasks?.failedToday || 0} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="全局吞吐量" value={performance?.globalThroughputMBps || 0} suffix="MB/s" /></Card></Col>
        <Col span={6}><Card><Statistic title="今日传输量" value={performance?.todayTransferredGB || 0} suffix="GB" /></Card></Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={14}>
          <Card title="Agent健康网格" size="small">
            <Table rowKey="agentId" size="small" pagination={false} dataSource={healthGrid} columns={[
              { title: 'Agent', dataIndex: 'agentName', key: 'agentName', render: (v, r) => v || r.agentId },
              { title: '状态', dataIndex: 'onlineStatus', width: 70, render: v => <Tag color={v === 'ONLINE' ? 'green' : 'default'}>{v}</Tag> },
              { title: '发送队列', key: 'sendQueue', width: 120, render: (_, r) => r.sendQueueStatus ? (
                <span>{r.sendQueueStatus.depth}/{r.sendQueueStatus.capacity} <Progress percent={Number(r.sendQueueStatus.utilizationPct) || 0} size="small" style={{ width: 60, display: 'inline-flex' }} /></span>
              ) : '-' },
              { title: '重试队列', key: 'retryQueue', width: 80, render: (_, r) => r.retryQueueStatus ? `${r.retryQueueStatus.depth}` : '-' },
              { title: '健康', dataIndex: 'overallHealth', width: 80, render: v => <Tag color={healthColorMap[v] || 'blue'}>{v}</Tag> },
              { title: '任务数', dataIndex: 'activeTaskCount', width: 60 },
            ]} />
          </Card>
        </Col>
        <Col span={10}>
          <Card title="活跃任务" size="small">
            <Table rowKey="taskId" size="small" pagination={false} dataSource={activeTasks} columns={[
              { title: '任务', dataIndex: 'taskName', ellipsis: true },
              { title: '状态', dataIndex: 'status', width: 90, render: s => <Tag color={statusColorMap[s]}>{s}</Tag> },
              { title: '进度', key: 'progress', width: 100, render: (_, r) => <Progress percent={Number(r.progressPercent) || 0} size="small" /> },
            ]} />
          </Card>
        </Col>
      </Row>

      <AlertList alerts={alerts} loading={loading} />
    </Spin>
  );
};

export default BatchDashboard;
