import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Card, Button, Space, Input, Select, Row, Col, message, Popconfirm, Tag, Progress, Tooltip } from 'antd';
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined, PlayCircleOutlined, PauseCircleOutlined, StopOutlined, EyeOutlined } from '@ant-design/icons';
import { listBatchTasks, startBatchTask, pauseBatchTask, resumeBatchTask, cancelBatchTask, deleteBatchTasks, createBatchTask } from '../../../api/batch/task';
import { listAgentRegistry } from '../../../api/agent';
import CreateTaskModal from '../../../components/batch/CreateTaskModal';

const statusColorMap = {
  PENDING: 'default', SCANNING: 'processing', TRANSFERRING: 'processing',
  PAUSED: 'warning', POST_PROCESSING: 'processing', COMPLETED: 'success',
  PARTIAL_FAILED: 'error', FAILED: 'error', CANCELLED: 'default', EXPIRED: 'default'
};

const BatchTaskList = () => {
  const navigate = useNavigate();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [queryParams, setQueryParams] = useState({ pageNum: 1, pageSize: 10, status: undefined, keyword: undefined });
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [agents, setAgents] = useState([]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listBatchTasks(queryParams);
      if (res.code === 200) {
        setData(res.data?.rows || []);
        setTotal(res.data?.total || 0);
      }
    } catch (e) { message.error('查询失败'); }
    setLoading(false);
  };

  const fetchAgents = async () => {
    try {
      const res = await listAgentRegistry({ pageNum: 1, pageSize: 200 });
      if (res.code === 200) setAgents(res.data?.rows || []);
    } catch (e) { /* ignore */ }
  };

  useEffect(() => { fetchData(); }, [queryParams.pageNum, queryParams.pageSize]);
  useEffect(() => { fetchAgents(); }, []);

  const handleStart = async (id) => { await startBatchTask(id); message.success('已启动'); fetchData(); };
  const handlePause = async (id) => { await pauseBatchTask(id); message.success('已暂停'); fetchData(); };
  const handleResume = async (id) => { await resumeBatchTask(id); message.success('已恢复'); fetchData(); };
  const handleCancel = async (id) => { await cancelBatchTask(id); message.success('已取消'); fetchData(); };
  const handleDelete = async (ids) => { await deleteBatchTasks(ids); message.success('已删除'); fetchData(); };

  const handleCreate = async (values) => {
    await createBatchTask(values);
    message.success('创建成功');
    setCreateModalVisible(false);
    fetchData();
  };

  const columns = [
    { title: '任务名称', dataIndex: 'taskName', key: 'taskName', ellipsis: true },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (s, r) => <Tag color={statusColorMap[s]}>{r.statusLabel || s}</Tag> },
    { title: '传输模式', dataIndex: 'transferMode', key: 'transferMode', width: 100, render: v => v === 'ONE_TO_ONE' ? '一对一' : '一对多' },
    { title: '路由策略', dataIndex: 'routingStrategy', key: 'routingStrategy', width: 100 },
    { title: '进度', key: 'progress', width: 180, render: (_, r) => (
      <Tooltip title={`${r.transferredFiles || 0}/${r.totalFiles || 0} 文件`}>
        <Progress percent={Number(r.progressPercent) || 0} size="small" status={r.failedFiles > 0 ? 'exception' : 'active'} />
      </Tooltip>
    )},
    { title: '失败数', dataIndex: 'failedFiles', key: 'failedFiles', width: 80, render: v => v > 0 ? <Tag color="error">{v}</Tag> : v || 0 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
    { title: '操作', key: 'action', width: 240, render: (_, r) => (
      <Space size="small">
        {['PENDING', 'PAUSED'].includes(r.status) && <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleStart(r.id)}>启动</Button>}
        {['TRANSFERRING', 'SCANNING'].includes(r.status) && <Button type="link" size="small" icon={<PauseCircleOutlined />} onClick={() => handlePause(r.id)}>暂停</Button>}
        {r.status === 'PAUSED' && <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleResume(r.id)}>恢复</Button>}
        {!['COMPLETED', 'CANCELLED', 'EXPIRED'].includes(r.status) && <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => handleCancel(r.id)}>取消</Button>}
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/batch/taskDetail?id=${r.id}`)}>详情</Button>
      </Space>
    )},
  ];

  return (
    <Card>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col><Input placeholder="搜索任务名称" prefix={<SearchOutlined />} allowClear onChange={e => setQueryParams(p => ({...p, keyword: e.target.value}))} /></Col>
        <Col><Select placeholder="状态" allowClear style={{ width: 120 }} onChange={v => setQueryParams(p => ({...p, status: v}))}>
          {Object.entries(statusColorMap).map(([k]) => <Select.Option key={k} value={k}>{k}</Select.Option>)}
        </Select></Col>
        <Col><Button icon={<SearchOutlined />} type="primary" onClick={fetchData}>搜索</Button></Col>
        <Col><Button icon={<ReloadOutlined />} onClick={() => setQueryParams({ pageNum: 1, pageSize: 10 })}>重置</Button></Col>
        <Col><Button icon={<PlusOutlined />} type="primary" onClick={() => setCreateModalVisible(true)}>创建任务</Button></Col>
        <Col><Popconfirm title="确认删除?" onConfirm={() => handleDelete(selectedRowKeys.join(','))}><Button icon={<DeleteOutlined />} disabled={!selectedRowKeys.length}>删除</Button></Popconfirm></Col>
      </Row>
      <Table rowKey="id" columns={columns} dataSource={data} loading={loading}
        rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
        pagination={{ current: queryParams.pageNum, pageSize: queryParams.pageSize, total, onChange: (p, s) => setQueryParams(prev => ({...prev, pageNum: p, pageSize: s})) }}
      />
      <CreateTaskModal visible={createModalVisible} onOk={handleCreate}
        onCancel={() => setCreateModalVisible(false)} agents={agents} />
    </Card>
  );
};

export default BatchTaskList;
