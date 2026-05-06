import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Space, Spin, message, Popconfirm, Row, Col, Card, Tabs, Descriptions, Tag, Statistic, Progress } from 'antd';
import { PlayCircleOutlined, PauseCircleOutlined, ArrowLeftOutlined, ReloadOutlined, EditOutlined, InfoCircleOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { getBatchTaskDetail, startBatchTask, pauseBatchTask, resumeBatchTask, retrySubtask, listSubtasks, updateBatchTaskConfig } from '../../../api/batch/task';
import { listAgentRegistry } from '../../../api/agent';
import UpdateTaskConfigModal from '../../../components/batch/UpdateTaskConfigModal';
import TaskConfigDetailModal from '../../../components/batch/TaskConfigDetailModal';

const transferModeLabel = { ONE_TO_ONE: '一对一 (1:1)', ONE_TO_MANY: '一对多 (1:N)' };
const routingStrategyLabel = {
  BROADCAST: '广播', SINGLE: '单机粘性', ROUND_ROBIN: '轮询',
  REGION_BASED: '区域路由', RANDOM: '随机'
};
const postTransferActionLabel = { NONE: '无操作', DELETE: '删除源文件', BACKUP: '备份' };
const backupModeLabel = { COPY: '复制', MOVE: '移动' };

const statusColorMap = {
  DRAFT: 'default', RUNNING: 'processing', PAUSED: 'warning', STOPPED: 'default'
};

const statusLabelMap = {
  DRAFT: '草稿', RUNNING: '运行中', PAUSED: '已暂停', STOPPED: '已停止'
};

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const BatchTaskDetail = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [taskId] = useState(searchParams.get('id'));
  const [task, setTask] = useState(null);
  const [loading, setLoading] = useState(false);
  const [updateModalVisible, setUpdateModalVisible] = useState(false);
  const [configModalVisible, setConfigModalVisible] = useState(false);
  const [agents, setAgents] = useState([]);

  const fetchAgents = async () => {
    try {
      const res = await listAgentRegistry({ pageNum: 1, pageSize: 200 });
      if (res.code === 200) setAgents(res.data?.rows || []);
    } catch (e) { /* ignore */ }
  };

  const fetchData = async () => {
    if (!taskId) return;
    setLoading(true);
    try {
      const res = await getBatchTaskDetail(taskId);
      if (res.code === 200) setTask(res.data);
    } catch (e) { message.error('加载失败'); }
    setLoading(false);
  };

  useEffect(() => {
    fetchData();
    fetchAgents();
  }, [taskId]);

  const handleStart = async () => { await startBatchTask(taskId); message.success('已启动'); fetchData(); };
  const handlePause = async () => { await pauseBatchTask(taskId); message.success('已暂停'); fetchData(); };
  const handleResume = async () => { await resumeBatchTask(taskId); message.success('已恢复'); fetchData(); };

  const handleUpdateConfig = async (values) => {
    try {
      await updateBatchTaskConfig(taskId, values);
      message.success('修改配置成功');
      setUpdateModalVisible(false);
      fetchData();
    } catch (e) { message.error('修改配置失败'); }
  };

  if (loading && !task) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (!task) return <Card><div style={{ textAlign: 'center', padding: 40 }}>任务不存在</div></Card>;

  const summary = task.subtaskSummary || {};
  const totalSubtasks = summary.totalSubtasks || 0;
  const completedCount = summary.completedCount || 0;
  const failedCount = summary.failedCount || 0;
  const runningCount = summary.runningCount || 0;
  const queuedCount = summary.queuedCount || 0;
  const retryingCount = summary.retryingCount || 0;
  const progressPercent = Number(summary.progressPercent) || 0;

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col><Space><Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/batch/taskList')}>返回</Button><Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button></Space></Col>
        <Col>
          <Space>
            <Button icon={<InfoCircleOutlined />} onClick={() => setConfigModalVisible(true)}>配置详情</Button>
            <Button icon={<UnorderedListOutlined />} onClick={() => navigate(`/batch/subtaskDetail?taskId=${taskId}`)}>子任务明细</Button>
            {task.canConfig && <Button icon={<EditOutlined />} onClick={() => setUpdateModalVisible(true)}>修改配置</Button>}
            {task.status === 'DRAFT' && <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleStart}>启动</Button>}
            {task.canPause && <Button icon={<PauseCircleOutlined />} onClick={handlePause}>暂停</Button>}
            {task.canResume && <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleResume}>恢复</Button>}
          </Space>
        </Col>
      </Row>

      {/* 子任务统计卡片 */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={4}>
            <Statistic title="总子任务" value={totalSubtasks} />
          </Col>
          <Col span={4}>
            <Statistic title="已完成" value={completedCount} valueStyle={{ color: '#3f8600' }} />
          </Col>
          <Col span={4}>
            <Statistic title="运行中" value={runningCount} valueStyle={{ color: '#1890ff' }} />
          </Col>
          <Col span={4}>
            <Statistic title="排队中" value={queuedCount} />
          </Col>
          <Col span={4}>
            <Statistic title="失败" value={failedCount} valueStyle={{ color: failedCount > 0 ? '#cf1322' : undefined }} />
          </Col>
          <Col span={4}>
            <div style={{ paddingTop: 8 }}>
              <Progress
                percent={progressPercent}
                status={failedCount > 0 ? 'exception' : (runningCount > 0 ? 'active' : 'normal')}
                format={() => `${progressPercent.toFixed(1)}%`}
              />
              <div style={{ textAlign: 'center', fontSize: 12, color: '#999' }}>{formatBytes(summary.transferredSizeBytes || 0)} / {formatBytes(summary.totalSizeBytes || 0)}</div>
            </div>
          </Col>
        </Row>
      </Card>

      <Card size="small" title="基本信息">
        <Descriptions bordered column={3} size="small">
          <Descriptions.Item label="任务名称">{task.taskName}</Descriptions.Item>
          <Descriptions.Item label="任务描述">{task.taskDescription || '-'}</Descriptions.Item>
          <Descriptions.Item label="运行状态"><Tag color={statusColorMap[task.status]}>{statusLabelMap[task.status] || task.status}</Tag></Descriptions.Item>
          <Descriptions.Item label="创建时间">{task.createTime}</Descriptions.Item>
          <Descriptions.Item label="创建人">{task.createBy}</Descriptions.Item>
          <Descriptions.Item label="首次启动时间">{task.startedAt || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <UpdateTaskConfigModal visible={updateModalVisible} initialValues={task}
        onOk={handleUpdateConfig} onCancel={() => setUpdateModalVisible(false)} />
      <TaskConfigDetailModal visible={configModalVisible} task={task} agents={agents}
        onCancel={() => setConfigModalVisible(false)} />
    </div>
  );
};

export default BatchTaskDetail;
