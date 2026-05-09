import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Space, Spin, message, Card, Row, Col, Statistic, Tag } from 'antd';
import { ArrowLeftOutlined, ReloadOutlined, CheckCircleOutlined, CloseCircleOutlined, SyncOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { listSubtasks, retrySubtask, getBatchTaskDetail } from '../../../api/batch/task';
import SubtaskTable from '../../../components/batch/SubtaskTable';

const taskStatusLabel = { DRAFT: '草稿', RUNNING: '运行中', PAUSED: '已暂停', STOPPED: '已停止' };
const taskStatusColor = { DRAFT: 'default', RUNNING: 'processing', PAUSED: 'warning', STOPPED: 'error' };

const SubtaskDetail = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [taskId] = useState(searchParams.get('taskId'));
  const [task, setTask] = useState(null);
  const [subtasks, setSubtasks] = useState([]);
  const [enrichedInfo, setEnrichedInfo] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchData = useCallback(async () => {
    if (!taskId) return;
    setLoading(true);
    try {
      const [taskRes, subtaskRes] = await Promise.all([
        getBatchTaskDetail(taskId),
        listSubtasks(taskId)
      ]);
      if (taskRes.code === 200) setTask(taskRes.data);
      if (subtaskRes.code === 200) {
        const data = subtaskRes.data;
        if (data && data.subtasks) {
          setSubtasks(data.subtasks);
          setEnrichedInfo({
            sourceDir: data.sourceDir,
            sourceNodeName: data.sourceNodeName,
            targetAgentInfoMap: data.targetAgentInfoMap || {}
          });
        } else {
          setSubtasks(data?.rows || data || []);
          setEnrichedInfo(null);
        }
      }
    } catch {
      message.error('加载失败');
    }
    setLoading(false);
  }, [taskId]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleRetry = async (tid, sid) => {
    try {
      await retrySubtask(tid, sid);
      message.success('已重试');
      fetchData();
    } catch {
      message.error('重试失败');
    }
  };

  if (loading && !task) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (!taskId) return <Card><div style={{ textAlign: 'center', padding: 40 }}>任务ID缺失</div></Card>;

  const completed = subtasks.filter(s => s.status === 'COMPLETED').length;
  const failed = subtasks.filter(s => s.status === 'FAILED').length;
  const sending = subtasks.filter(s => s.status === 'SENDING').length;
  const queued = subtasks.filter(s => s.status === 'QUEUED').length;

  return (
    <div style={{ padding: 0 }}>
      {/* Header */}
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Space size={12}>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>返回</Button>
            <span style={{ fontSize: 17, fontWeight: 600 }}>{task?.taskName || `任务 #${taskId}`}</span>
            {task?.status && <Tag color={taskStatusColor[task.status]}>{taskStatusLabel[task.status] || task.status}</Tag>}
          </Space>
        </Col>
        <Col>
          <Button icon={<ReloadOutlined />} onClick={fetchData} loading={loading}>刷新</Button>
        </Col>
      </Row>

      {/* Stats */}
      <Row gutter={12} style={{ marginBottom: 16 }}>
        <Col flex="1">
          <Card size="small" bordered={false} style={{ background: '#f6ffed' }}>
            <Statistic title="已完成" value={completed} prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a', fontSize: 22 }} />
          </Card>
        </Col>
        <Col flex="1">
          <Card size="small" bordered={false} style={{ background: '#fff7e6' }}>
            <Statistic title="传输中" value={sending} prefix={<SyncOutlined spin style={{ color: '#fa8c16' }} />}
              valueStyle={{ color: '#fa8c16', fontSize: 22 }} />
          </Card>
        </Col>
        <Col flex="1">
          <Card size="small" bordered={false} style={{ background: '#f0f0f0' }}>
            <Statistic title="排队中" value={queued} prefix={<ClockCircleOutlined style={{ color: '#8c8c8c' }} />}
              valueStyle={{ color: '#8c8c8c', fontSize: 22 }} />
          </Card>
        </Col>
        <Col flex="1">
          <Card size="small" bordered={false} style={{ background: '#fff2f0' }}>
            <Statistic title="失败" value={failed} prefix={<CloseCircleOutlined style={{ color: '#ff4d4f' }} />}
              valueStyle={{ color: '#ff4d4f', fontSize: 22 }} />
          </Card>
        </Col>
        <Col flex="1">
          <Card size="small" bordered={false} style={{ background: '#f0f5ff' }}>
            <Statistic title="总任务" value={subtasks.length} valueStyle={{ fontSize: 22 }} />
          </Card>
        </Col>
      </Row>

      {/* Table */}
      <Card size="small" bordered={false} bodyStyle={{ padding: 0 }}>
        <SubtaskTable
          subtasks={subtasks}
          loading={loading}
          onRetry={handleRetry}
          enrichedInfo={enrichedInfo}
        />
      </Card>
    </div>
  );
};

export default SubtaskDetail;
