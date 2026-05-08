import React, { useState, useEffect } from 'react';
import { Card, Table, Row, Col, Button, Tag, Space, Statistic, Tooltip } from 'antd';
import { ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import { getStatisticsList } from '../../../api/batch/monitor';
import { useNavigate } from 'react-router-dom';

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatSpeed = (bytesPerSec) => {
  if (!bytesPerSec || bytesPerSec <= 0) return '-';
  const mbps = bytesPerSec / (1024 * 1024);
  if (mbps >= 1) return mbps.toFixed(2) + ' MB/s';
  return (bytesPerSec / 1024).toFixed(1) + ' KB/s';
};

const formatDuration = (ms) => {
  if (!ms || ms < 0) return '-';
  if (ms < 1000) return ms + 'ms';
  const s = ms / 1000;
  if (s < 60) return s.toFixed(1) + 's';
  const m = Math.floor(s / 60);
  const h = Math.floor(m / 60);
  if (h > 0) return `${h}h${m % 60}m`;
  return `${m}m${Math.floor(s % 60)}s`;
};

const StatisticsPage = () => {
  const navigate = useNavigate();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [queryParams, setQueryParams] = useState({ pageNum: 1, pageSize: 20 });

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getStatisticsList(queryParams);
      if (res.code === 200) {
        setData(res.data?.rows || []);
        setTotal(res.data?.total || 0);
      }
    } catch (e) { /* ignore */ }
    setLoading(false);
  };

  useEffect(() => { fetchData(); }, [queryParams.pageNum, queryParams.pageSize]);

  const columns = [
    {
      title: '任务ID', dataIndex: 'taskId', key: 'taskId', width: 80,
      render: v => <Button type="link" size="small" onClick={() => navigate(`/batch/taskDetail?id=${v}`)}>#{v}</Button>
    },
    {
      title: '子任务统计', key: 'subtaskStats', width: 200,
      render: (_, r) => (
        <Space size="small" wrap>
          <span>总{r.totalSubtasks || 0}</span>
          <Tag color="success">{r.completedCount || 0}完成</Tag>
          {(r.runningCount || 0) > 0 && <Tag color="processing">{r.runningCount}运行</Tag>}
          {(r.failedCount || 0) > 0 && <Tag color="error">{r.failedCount}失败</Tag>}
        </Space>
      )
    },
    {
      title: '进度', key: 'progress', width: 120,
      render: (_, r) => {
        const pct = Number(r.progressPercent) || 0;
        return <span style={{ fontWeight: 600 }}>{pct.toFixed(1)}%</span>;
      }
    },
    {
      title: '传输量', key: 'transferSize', width: 150,
      render: (_, r) => (
        <Tooltip title={`${formatBytes(r.transferredBytes)} / ${formatBytes(r.totalSizeBytes)}`}>
          <span>{formatBytes(r.transferredBytes)} / {formatBytes(r.totalSizeBytes)}</span>
        </Tooltip>
      )
    },
    { title: '平均速率', dataIndex: 'avgSpeedBytesPerSec', key: 'avgSpeed', width: 100, render: v => formatSpeed(v) },
    { title: '峰值速率', dataIndex: 'peakSpeedBytesPerSec', key: 'peakSpeed', width: 100, render: v => formatSpeed(v) },
    { title: '总耗时', dataIndex: 'totalElapsedMs', key: 'elapsed', width: 100, render: v => formatDuration(v) },
    { title: '平均文件耗时', dataIndex: 'avgDurationPerFileMs', key: 'avgDuration', width: 110, render: v => formatDuration(v) },
    {
      title: '重试', key: 'retry', width: 120,
      render: (_, r) => (
        <Space size="small">
          <span>总{r.totalRetryCount || 0}</span>
          {(r.maxSingleFileRetries || 0) > 0 && <Tag color="warning">最大{r.maxSingleFileRetries}</Tag>}
        </Space>
      )
    },
    {
      title: 'Top错误', key: 'topError', width: 180, ellipsis: true,
      render: (_, r) => r.topErrorCode ? (
        <Tooltip title={r.topErrorMessage}>
          <Tag color="error">{r.topErrorCode}</Tag>
        </Tooltip>
      ) : '-'
    },
    {
      title: '后处理', key: 'postProcess', width: 120,
      render: (_, r) => (
        <Space size="small">
          <span>完成{r.postProcessCompleted || 0}</span>
          {(r.postProcessFailed || 0) > 0 && <Tag color="error">失败{r.postProcessFailed}</Tag>}
        </Space>
      )
    },
    { title: '快照时间', dataIndex: 'snapshotTime', key: 'snapshotTime', width: 170 },
  ];

  return (
    <Card>
      <Row justify="space-between" style={{ marginBottom: 16 }}>
        <Col>
          <span style={{ fontSize: 16, fontWeight: 600 }}>传输统计总览</span>
          <span style={{ color: '#999', marginLeft: 8 }}>每个任务一条统计记录，由系统自动刷新</span>
        </Col>
        <Col>
          <Button icon={<ReloadOutlined />} onClick={fetchData} loading={loading}>刷新</Button>
        </Col>
      </Row>
      <Table rowKey="id" columns={columns} dataSource={data} loading={loading} scroll={{ x: 1800 }}
        pagination={{
          current: queryParams.pageNum, pageSize: queryParams.pageSize, total, showTotal: t => `共 ${t} 条`,
          onChange: (p, s) => setQueryParams(prev => ({ ...prev, pageNum: p, pageSize: s }))
        }}
      />
    </Card>
  );
};

export default StatisticsPage;
