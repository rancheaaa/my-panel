import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Space, Spin, message, Popconfirm, Row, Col, Card, Tabs, Descriptions, Tag, Statistic, Progress, Table } from 'antd';
import { PlayCircleOutlined, PauseCircleOutlined, ArrowLeftOutlined, ReloadOutlined, EditOutlined, InfoCircleOutlined, UnorderedListOutlined, DeleteOutlined, SyncOutlined } from '@ant-design/icons';
import { getBatchTaskDetail, startBatchTask, pauseBatchTask, resumeBatchTask, retrySubtask, listSubtasks, updateBatchTaskConfig, deleteBatchTasks, getTaskStatistics, refreshTaskStatistics } from '../../../api/batch/task';
import { getOperationLogs } from '../../../api/batch/monitor';
import { listAgentRegistry } from '../../../api/agent';
import UpdateTaskConfigModal from '../../../components/batch/UpdateTaskConfigModal';
import TaskConfigDetailModal from '../../../components/batch/TaskConfigDetailModal';
import TargetProgressPanel from '../../../components/batch/TargetProgressPanel';

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

const operationTypeLabel = {
  CREATE: '创建', START: '启动', PAUSE: '暂停', RESUME: '恢复', CANCEL: '取消',
  CONFIG_UPDATE: '配置修改', MANUAL_RETRY: '手动重试', DELETE: '删除'
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
  return `${m}m${Math.floor(s % 60)}s`;
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
  const [statistics, setStatistics] = useState(null);
  const [opLogs, setOpLogs] = useState([]);
  const [opLogsLoading, setOpLogsLoading] = useState(false);

  const fetchAgents = async () => {
    try {
      const res = await listAgentRegistry({ pageNum: 1, pageSize: 200 });
      if (res.code === 200) setAgents(res.data?.rows || []);
    } catch (e) { /* ignore */ }
  };

  const fetchData = useCallback(async () => {
    if (!taskId) return;
    setLoading(true);
    try {
      const res = await getBatchTaskDetail(taskId);
      if (res.code === 200) setTask(res.data);
    } catch (e) { message.error('加载失败'); }
    setLoading(false);
  }, [taskId]);

  const fetchStatistics = useCallback(async () => {
    if (!taskId) return;
    try {
      const res = await getTaskStatistics(taskId);
      if (res.code === 200) setStatistics(res.data);
    } catch (e) { /* ignore */ }
  }, [taskId]);

  const fetchOpLogs = useCallback(async () => {
    if (!taskId) return;
    setOpLogsLoading(true);
    try {
      const res = await getOperationLogs({ taskId, limit: 50 });
      if (res.code === 200) setOpLogs(res.data || []);
    } catch (e) { /* ignore */ }
    setOpLogsLoading(false);
  }, [taskId]);

  useEffect(() => {
    fetchData();
    fetchAgents();
    fetchStatistics();
    fetchOpLogs();
  }, [taskId, fetchData, fetchStatistics, fetchOpLogs]);

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

  const handleDelete = async () => {
    try {
      await deleteBatchTasks(taskId);
      message.success('已删除');
      navigate('/batch/taskList');
    } catch (e) { message.error('删除失败'); }
  };

  const handleRefreshStatistics = async () => {
    try {
      await refreshTaskStatistics(taskId);
      message.success('统计已刷新');
      fetchStatistics();
    } catch (e) { message.error('刷新失败'); }
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
            {task.canDelete && (
              <Popconfirm title="确认删除该任务?" onConfirm={handleDelete}>
                <Button danger icon={<DeleteOutlined />}>删除</Button>
              </Popconfirm>
            )}
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

      {/* 基本信息 + Tabs */}
      <Card size="small">
        <Tabs defaultActiveKey="info" items={[
          {
            key: 'info',
            label: '基本信息',
            children: (
              <Descriptions bordered column={3} size="small">
                <Descriptions.Item label="任务名称">{task.taskName}</Descriptions.Item>
                <Descriptions.Item label="任务描述">{task.taskDescription || '-'}</Descriptions.Item>
                <Descriptions.Item label="运行状态"><Tag color={statusColorMap[task.status]}>{statusLabelMap[task.status] || task.status}</Tag></Descriptions.Item>
                <Descriptions.Item label="源Agent">{task.sourceAgentId}</Descriptions.Item>
                <Descriptions.Item label="源目录" span={2}><span style={{ fontFamily: 'monospace' }}>{task.sourceDir}</span></Descriptions.Item>
                <Descriptions.Item label="传输模式">{transferModeLabel[task.transferMode] || task.transferMode}</Descriptions.Item>
                <Descriptions.Item label="路由策略">{routingStrategyLabel[task.routingStrategy] || task.routingStrategy}</Descriptions.Item>
                <Descriptions.Item label="带宽限制">{task.maxBandwidthKbS ? task.maxBandwidthKbS + ' KB/s' : '不限'}</Descriptions.Item>
                <Descriptions.Item label="创建时间">{task.createTime}</Descriptions.Item>
                <Descriptions.Item label="创建人">{task.createBy}</Descriptions.Item>
                <Descriptions.Item label="首次启动时间">{task.startedAt || '-'}</Descriptions.Item>
              </Descriptions>
            )
          },
          {
            key: 'statistics',
            label: '传输统计',
            children: (
              <div>
                <div style={{ marginBottom: 12 }}>
                  <Button icon={<SyncOutlined />} size="small" onClick={handleRefreshStatistics}>刷新统计</Button>
                </div>
                {statistics ? (
                  <Descriptions bordered column={3} size="small">
                    <Descriptions.Item label="平均速率">{formatSpeed(statistics.avgSpeedBytesPerSec)}</Descriptions.Item>
                    <Descriptions.Item label="峰值速率">{formatSpeed(statistics.peakSpeedBytesPerSec)}</Descriptions.Item>
                    <Descriptions.Item label="进度">{Number(statistics.progressPercent || 0).toFixed(1)}%</Descriptions.Item>
                    <Descriptions.Item label="总耗时">{formatDuration(statistics.totalElapsedMs)}</Descriptions.Item>
                    <Descriptions.Item label="平均单文件耗时">{formatDuration(statistics.avgDurationPerFileMs)}</Descriptions.Item>
                    <Descriptions.Item label="首个文件开始">{statistics.firstFileStartedAt || '-'}</Descriptions.Item>
                    <Descriptions.Item label="总重试次数"><Tag>{statistics.totalRetryCount ?? 0}</Tag></Descriptions.Item>
                    <Descriptions.Item label="最大单文件重试"><Tag>{statistics.maxSingleFileRetries ?? 0}</Tag></Descriptions.Item>
                    <Descriptions.Item label="平均重试次数"><Tag>{statistics.avgRetryCount ?? 0}</Tag></Descriptions.Item>
                    <Descriptions.Item label="Top错误码">{statistics.topErrorCode || '-'}</Descriptions.Item>
                    <Descriptions.Item label="Top错误信息" span={2}>
                      {statistics.topErrorMessage ? <span style={{ color: '#ff4d4f' }}>{statistics.topErrorMessage}</span> : '-'}
                    </Descriptions.Item>
                    {statistics.errorTypeDistribution && (
                      <Descriptions.Item label="错误分布" span={3}>
                        <Space wrap>
                          {Object.entries(JSON.parse(statistics.errorTypeDistribution)).map(([code, count]) => (
                            <Tag key={code} color="error">{code}: {count}</Tag>
                          ))}
                        </Space>
                      </Descriptions.Item>
                    )}
                    <Descriptions.Item label="后处理完成">{statistics.postProcessCompleted ?? 0}</Descriptions.Item>
                    <Descriptions.Item label="后处理失败">{statistics.postProcessFailed ?? 0}</Descriptions.Item>
                    <Descriptions.Item label="最后活动时间">{statistics.lastActivityAt || '-'}</Descriptions.Item>
                  </Descriptions>
                ) : (
                  <div style={{ color: '#999', textAlign: 'center', padding: 24 }}>暂无统计数据</div>
                )}
              </div>
            )
          },
          {
            key: 'opLogs',
            label: '操作日志',
            children: (
              <Table rowKey="id" size="small" loading={opLogsLoading} dataSource={opLogs}
                pagination={{ pageSize: 10, size: 'small', showTotal: t => `共 ${t} 条` }}
                columns={[
                  { title: '操作类型', dataIndex: 'operation_type', width: 100, render: v => <Tag>{operationTypeLabel[v] || v}</Tag> },
                  { title: '操作人', dataIndex: 'operator_name', width: 100 },
                  { title: '操作时间', dataIndex: 'operation_time', width: 170 },
                  { title: '旧值', dataIndex: 'old_config', ellipsis: true, render: v => v || '-' },
                  { title: '新值', dataIndex: 'new_config', ellipsis: true, render: v => v || '-' },
                  { title: '备注', dataIndex: 'remark', ellipsis: true, render: v => v || '-' },
                ]}
              />
            )
          },
        ]} />
      </Card>

      {/* 目标Agent进度面板 */}
      {task.targetAgents && task.subtaskSummary && (
        <TargetProgressPanel targetProgress={(() => {
          try {
            const agentIds = JSON.parse(task.targetAgents);
            return agentIds.map((agentId, idx) => {
              const dirs = task.targetDirs ? task.targetDirs.split(';') : [];
              return {
                targetAgentId: agentId,
                targetAgentName: agentId,
                progressPercent: Number(summary.progressPercent) || 0,
                totalSubtasks: summary.totalSubtasks || 0,
                completedSubtasks: summary.completedCount || 0,
                failedSubtasks: summary.failedCount || 0,
              };
            });
          } catch { return []; }
        })()} />
      )}

      <UpdateTaskConfigModal visible={updateModalVisible} initialValues={task}
        onOk={handleUpdateConfig} onCancel={() => setUpdateModalVisible(false)} />
      <TaskConfigDetailModal visible={configModalVisible} task={task} agents={agents}
        onCancel={() => setConfigModalVisible(false)} />
    </div>
  );
};

export default BatchTaskDetail;
