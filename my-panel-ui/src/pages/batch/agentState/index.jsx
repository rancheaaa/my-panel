import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Card, Table, Input, Select, Row, Col, Button, Tag, Space, Tooltip } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { getAgentStates } from '../../../api/batch/monitor';
import { listBatchTasks } from '../../../api/batch/task';

const statusColorMap = {
  PREPARED: 'default', INITIALIZING: 'processing', UPLOADING: 'processing',
  MERGING: 'processing', COMPLETED: 'success', FAILED: 'error', CANCELLED: 'default'
};

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const AgentStatePage = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [queryParams, setQueryParams] = useState({ pageNum: 1, pageSize: 20 });
  const [taskOptions, setTaskOptions] = useState([]);
  const searchTimer = useRef(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getAgentStates(queryParams);
      if (res.code === 200) {
        setData(res.data?.rows || []);
        setTotal(res.data?.total || 0);
      }
    } catch (e) { /* ignore */ }
    setLoading(false);
  };

  useEffect(() => { fetchData(); }, [queryParams.pageNum, queryParams.pageSize]);

  const handleTaskSearch = useCallback((value) => {
    if (searchTimer.current) clearTimeout(searchTimer.current);
    if (!value) { setTaskOptions([]); return; }
    searchTimer.current = setTimeout(async () => {
      try {
        const res = await listBatchTasks({ taskName: value, pageNum: 1, pageSize: 20 });
        if (res.code === 200) {
          const rows = res.data?.rows || [];
          setTaskOptions(rows.map(t => ({ value: t.id, label: `${t.taskName} (#${t.id})` })));
        }
      } catch (e) { /* ignore */ }
    }, 300);
  }, []);

  const columns = [
    { title: 'Agent ID', dataIndex: 'agentId', key: 'agentId', width: 120, ellipsis: true },
    { title: '发送节点', dataIndex: 'senderNodeName', key: 'senderNodeName', width: 130, ellipsis: true, render: v => v || '-' },
    { title: '接收节点', dataIndex: 'receiverNodeName', key: 'receiverNodeName', width: 130, ellipsis: true, render: v => v || '-' },
    { title: '传输ID', dataIndex: 'transferId', key: 'transferId', width: 120, ellipsis: true },
    { title: '任务', dataIndex: 'taskId', key: 'taskId', width: 150, ellipsis: true, render: (_, r) => r.taskName ? `${r.taskName} (#${r.taskId})` : r.taskId || '-' },
    { title: '子任务ID', dataIndex: 'subtaskId', key: 'subtaskId', width: 80 },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', width: 180, ellipsis: true },
    { title: '本地路径', dataIndex: 'filePath', key: 'filePath', width: 200, ellipsis: true },
    { title: '远程路径', dataIndex: 'remoteTargetPath', key: 'remoteTargetPath', width: 200, ellipsis: true },
    { title: '文件大小', dataIndex: 'totalSize', key: 'totalSize', width: 100, render: v => formatBytes(v) },
    { title: '分块大小', dataIndex: 'chunkSize', key: 'chunkSize', width: 100, render: v => formatBytes(v) },
    {
      title: '分块进度', key: 'chunkProgress', width: 140,
      render: (_, r) => {
        if (!r.totalChunks || r.totalChunks === 0) return '-';
        const pct = Math.round((r.transferredChunks / r.totalChunks) * 100);
        return <Tooltip title={`${r.transferredChunks}/${r.totalChunks} 分块`}>
          <span>{pct}%</span>
        </Tooltip>;
      }
    },
    { title: '状态', dataIndex: 'status', key: 'status', width: 110, render: v => <Tag color={statusColorMap[v] || 'default'}>{v}</Tag> },
    { title: '重试次数', dataIndex: 'retryCount', key: 'retryCount', width: 80 },
    { title: '错误描述', dataIndex: 'exceptionDesc', key: 'exceptionDesc', width: 200, ellipsis: true, render: v => v ? <span style={{ color: '#ff4d4f' }}>{v}</span> : '-' },
    { title: '创建时间', dataIndex: 'createTimeStr', key: 'createTimeStr', width: 160, render: v => v || '-' },
    { title: '入队时间', dataIndex: 'enqueuedTime', key: 'enqueuedTime', width: 160, render: v => v || '-' },
    { title: '初始化开始', dataIndex: 'initUploadStartTime', key: 'initUploadStartTime', width: 160, render: v => v || '-' },
    { title: '初始化结束', dataIndex: 'initUploadEndTime', key: 'initUploadEndTime', width: 160, render: v => v || '-' },
    { title: '上传开始', dataIndex: 'uploadChunksStartTime', key: 'uploadChunksStartTime', width: 160, render: v => v || '-' },
    { title: '上传结束', dataIndex: 'uploadChunksEndTime', key: 'uploadChunksEndTime', width: 160, render: v => v || '-' },
    { title: '合并开始', dataIndex: 'mergeChunksStartTime', key: 'mergeChunksStartTime', width: 160, render: v => v || '-' },
    { title: '合并结束', dataIndex: 'mergeChunksEndTime', key: 'mergeChunksEndTime', width: 160, render: v => v || '-' },
    { title: '成功时间', dataIndex: 'uploadSuccessTime', key: 'uploadSuccessTime', width: 160, render: v => v || '-' },
    { title: '更新时间', dataIndex: 'updateTimeStr', key: 'updateTimeStr', width: 160, render: v => v || '-' },
  ];

  return (
    <Card>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col><Input placeholder="Agent ID" allowClear style={{ width: 160 }}
          onChange={e => setQueryParams(p => ({ ...p, agentId: e.target.value || undefined }))} /></Col>
        <Col><Select placeholder="任务ID" allowClear showSearch style={{ width: 200 }}
          filterOption={false} onSearch={handleTaskSearch} options={taskOptions}
          onChange={v => setQueryParams(p => ({ ...p, taskId: v || undefined }))} /></Col>
        <Col><Select placeholder="状态" allowClear style={{ width: 130 }}
          onChange={v => setQueryParams(p => ({ ...p, status: v }))}>
          {Object.keys(statusColorMap).map(s => <Select.Option key={s} value={s}>{s}</Select.Option>)}
        </Select></Col>
        <Col><Button icon={<SearchOutlined />} type="primary" onClick={() => setQueryParams(p => ({ ...p, pageNum: 1 }))}>搜索</Button></Col>
        <Col><Button icon={<ReloadOutlined />} onClick={() => setQueryParams({ pageNum: 1, pageSize: 20 })}>重置</Button></Col>
      </Row>
      <Table rowKey="id" columns={columns} dataSource={data} loading={loading} scroll={{ x: 4100 }}
        pagination={{
          current: queryParams.pageNum, pageSize: queryParams.pageSize, total, showTotal: t => `共 ${t} 条`,
          onChange: (p, s) => setQueryParams(prev => ({ ...prev, pageNum: p, pageSize: s }))
        }}
      />
    </Card>
  );
};

export default AgentStatePage;
