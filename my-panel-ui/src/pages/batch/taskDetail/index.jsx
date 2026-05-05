import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Space, Spin, message, Popconfirm, Row, Col, Card, Tabs, Descriptions, Tag, Statistic, Progress } from 'antd';
import { PlayCircleOutlined, PauseCircleOutlined, StopOutlined, ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { getBatchTaskDetail, startBatchTask, pauseBatchTask, resumeBatchTask, stopBatchTask, retrySubtask, listSubtasks } from '../../../api/batch/task';
import SubtaskTable from '../../../components/batch/SubtaskTable';

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

const parseJsonSafe = (str) => {
  if (!str) return null;
  try { return JSON.parse(str); } catch { return str; }
};

const renderPatterns = (patterns) => {
  if (!patterns) return '-';
  if (Array.isArray(patterns)) return patterns.length > 0 ? patterns.join(', ') : '-';
  if (typeof patterns === 'string') return patterns;
  return '-';
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
  const handleStop = async () => { await stopBatchTask(taskId); message.success('已停止'); fetchData(); };
  const handleRetry = async (tid, sid) => { await retrySubtask(tid, sid); message.success('已重试'); fetchData(); };

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
            {task.canStart && <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleStart}>启动</Button>}
            {task.canPause && <Button icon={<PauseCircleOutlined />} onClick={handlePause}>暂停</Button>}
            {task.canResume && <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleResume}>恢复</Button>}
            {task.canStop && <Popconfirm title="确认停止?" onConfirm={handleStop}><Button danger icon={<StopOutlined />}>停止</Button></Popconfirm>}
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

      <Tabs defaultActiveKey="subtasks" items={[
        { key: 'subtasks', label: `子任务明细 (${totalSubtasks})`, children: <SubtaskTable subtasks={subtasks} loading={loading} onRetry={handleRetry} /> },
        { key: 'config', label: '配置详情（模板）', children: (
          <Card size="small">
            <Descriptions bordered column={3} size="small" title="基本配置">
              <Descriptions.Item label="任务名称">{task.taskName}</Descriptions.Item>
              <Descriptions.Item label="任务描述">{task.taskDescription || '-'}</Descriptions.Item>
              <Descriptions.Item label="运行状态"><Tag color={statusColorMap[task.status]}>{statusLabelMap[task.status] || task.status}</Tag></Descriptions.Item>

              <Descriptions.Item label="源Agent ID">{task.sourceAgentId}</Descriptions.Item>
              <Descriptions.Item label="源目录">{task.sourceDir}</Descriptions.Item>
              <Descriptions.Item label="目标目录">{task.targetDirs || '-'}</Descriptions.Item>

              <Descriptions.Item label="传输模式">{transferModeLabel[task.transferMode] || task.transferMode}</Descriptions.Item>
              <Descriptions.Item label="路由策略">{routingStrategyLabel[task.routingStrategy] || task.routingStrategy}</Descriptions.Item>
              <Descriptions.Item label="保持目录结构">{task.preserveDirStructure === 1 ? '是' : '否'}</Descriptions.Item>

              <Descriptions.Item label="包含模式(Glob)" span={2}>{renderPatterns(parseJsonSafe(task.includePatterns))}</Descriptions.Item>
              <Descriptions.Item label="带宽限制(KB/s)">{task.maxBandwidthKbS || '不限'}</Descriptions.Item>

              <Descriptions.Item label="排除模式(Glob)" span={3}>{renderPatterns(parseJsonSafe(task.excludePatterns))}</Descriptions.Item>

              <Descriptions.Item label="后处理操作">{postTransferActionLabel[task.postTransferAction] || task.postTransferAction || 'NONE'}</Descriptions.Item>
              {(task.postTransferAction === 'BACKUP') && (
                <>
                  <Descriptions.Item label="备份目录">{task.backupDir}</Descriptions.Item>
                  <Descriptions.Item label="备份模式">{backupModeLabel[task.backupMode] || task.backupMode}</Descriptions.Item>
                </>
              )}
              {(!task.postTransferAction || task.postTransferAction !== 'BACKUP') && (
                <>
                  <Descriptions.Item label="备份目录">-</Descriptions.Item>
                  <Descriptions.Item label="备份模式">-</Descriptions.Item>
                </>
              )}

              <Descriptions.Item label="自动重试">{task.retryEnabled === 1 ? '是' : '否'}</Descriptions.Item>
              {task.retryEnabled === 1 ? (
                <>
                  <Descriptions.Item label="重试保留(天)">{task.retryMaxDays}</Descriptions.Item>
                  <Descriptions.Item label="重试间隔(分)">{task.retryIntervalMin}</Descriptions.Item>
                </>
              ) : (
                <>
                  <Descriptions.Item label="重试保留(天)">-</Descriptions.Item>
                  <Descriptions.Item label="重试间隔(分)">-</Descriptions.Item>
                </>
              )}
            </Descriptions>
            {parseJsonSafe(task.routingConfig) && typeof parseJsonSafe(task.routingConfig) === 'object' && Object.keys(parseJsonSafe(task.routingConfig)).length > 0 && (
              <Descriptions bordered column={3} size="small" title="区域路由配置" style={{ marginTop: 12 }}>
                {Object.entries(parseJsonSafe(task.routingConfig)).map(([key, value]) => (
                  <Descriptions.Item key={key} label={key}>{JSON.stringify(value)}</Descriptions.Item>
                ))}
              </Descriptions>
            )}
            <Descriptions bordered column={3} size="small" title="时间信息" style={{ marginTop: 12 }}>
              <Descriptions.Item label="创建时间">{task.createTime}</Descriptions.Item>
              <Descriptions.Item label="创建人">{task.createBy}</Descriptions.Item>
              <Descriptions.Item label="首次启动时间">{task.startedAt || '-'}</Descriptions.Item>
            </Descriptions>
          </Card>
        )},
      ]} />
    </div>
  );
};

export default BatchTaskDetail;
