import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Space, Spin, message, Popconfirm, Row, Col, Card, Tabs } from 'antd';
import { PlayCircleOutlined, PauseCircleOutlined, StopOutlined, ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { getBatchTaskDetail, startBatchTask, pauseBatchTask, resumeBatchTask, cancelBatchTask, retrySubtask, listSubtasks } from '../../../api/batch/task';
import TaskSummaryCard from '../../../components/batch/TaskSummaryCard';
import TargetProgressPanel from '../../../components/batch/TargetProgressPanel';
import SubtaskTable from '../../../components/batch/SubtaskTable';

const BatchTaskDetail = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const taskId = searchParams.get('id');
  const [task, setTask] = useState(null);
  const [subtasks, setSubtasks] = useState([]);
  const [loading, setLoading] = useState(false);

  const fetchData = async () => {
    if (!taskId) return;
    setLoading(true);
    try {
      const res = await getBatchTaskDetail(taskId);
      if (res.code === 200) setTask(res.data);
    } catch (e) { message.error('加载失败'); }
    try {
      const res = await listSubtasks(taskId);
      if (res.code === 200) setSubtasks(res.data?.rows || res.data || []);
    } catch (e) { /* ignore */ }
    setLoading(false);
  };

  useEffect(() => { fetchData(); }, [taskId]);

  const handleStart = async () => { await startBatchTask(taskId); message.success('已启动'); fetchData(); };
  const handlePause = async () => { await pauseBatchTask(taskId); message.success('已暂停'); fetchData(); };
  const handleResume = async () => { await resumeBatchTask(taskId); message.success('已恢复'); fetchData(); };
  const handleCancel = async () => { await cancelBatchTask(taskId); message.success('已取消'); fetchData(); };
  const handleRetry = async (tid, sid) => { await retrySubtask(tid, sid); message.success('已重试'); fetchData(); };

  if (loading && !task) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (!task) return <Card><div style={{ textAlign: 'center', padding: 40 }}>任务不存在</div></Card>;

  const status = task.status;
  const canPause = ['TRANSFERRING', 'SCANNING'].includes(status);
  const canCancel = !['COMPLETED', 'CANCELLED', 'EXPIRED'].includes(status);
  const canStart = ['PENDING', 'PAUSED'].includes(status);

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col><Space><Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/batch/taskList')}>返回</Button><Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button></Space></Col>
        <Col>
          <Space>
            {canStart && <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleStart}>启动</Button>}
            {canPause && <Button icon={<PauseCircleOutlined />} onClick={handlePause}>暂停</Button>}
            {status === 'PAUSED' && <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleResume}>恢复</Button>}
            {canCancel && <Popconfirm title="确认取消?" onConfirm={handleCancel}><Button danger icon={<StopOutlined />}>取消</Button></Popconfirm>}
          </Space>
        </Col>
      </Row>
      <TaskSummaryCard task={task} />
      {task.targetProgress && task.targetProgress.length > 0 && (
        <TargetProgressPanel targetProgress={task.targetProgress} />
      )}
      <Tabs defaultActiveKey="subtasks" items={[
        { key: 'subtasks', label: '子任务明细', children: <SubtaskTable subtasks={subtasks} loading={loading} onRetry={handleRetry} /> },
        { key: 'config', label: '配置详情', children: (
          <Card size="small">
            <pre style={{ maxHeight: 400, overflow: 'auto', fontSize: 12, background: '#f5f5f5', padding: 12, borderRadius: 4 }}>
              {JSON.stringify({
                sourceDir: task.sourceDir, includePatterns: task.includePatterns, excludePatterns: task.excludePatterns,
                targetAgents: task.targetAgents, transferMode: task.transferMode, routingStrategy: task.routingStrategy,
                routingConfig: task.routingConfig, maxBandwidthKbS: task.maxBandwidthKbS,
                postTransferAction: task.postTransferAction, backupDir: task.backupDir, backupMode: task.backupMode,
                retryEnabled: task.retryEnabled, retryMaxDays: task.retryMaxDays, retryIntervalMin: task.retryIntervalMin,
                scanFrequencySec: task.scanFrequencySec, maxScanFiles: task.maxScanFiles,
              }, null, 2)}
            </pre>
          </Card>
        )},
      ]} />
    </div>
  );
};

export default BatchTaskDetail;
