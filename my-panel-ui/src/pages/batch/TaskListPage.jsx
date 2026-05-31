import React, { useState, useEffect } from 'react';
import { Button, Modal, Typography, message } from 'antd';
import { PlusCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import TaskListTab from './components/TaskListTab';
import TaskForm from './components/TaskForm';
import ConnectivityModal from './components/ConnectivityModal';
import { useBatchTasks } from './hooks/useBatchTasks';
import { batchApi } from '../../api/batch';
import { listAgentRegistry } from '../../api/agent';
import './index.scss';

const { Title } = Typography;

const TaskListPage = () => {
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [connModalOpen, setConnModalOpen] = useState(false);
  const [editingTask, setEditingTask] = useState(null);
  const [detailTask, setDetailTask] = useState(null);
  const [connTask, setConnTask] = useState(null);
  const [formLoading, setFormLoading] = useState(false);
  const [agentList, setAgentList] = useState([]);

  const { tasks, loading, pagination, fetchTasks, createTask, updateTask, startTask, pauseTask, resumeTask, stopTask, deleteTask } = useBatchTasks();

  useEffect(() => {
    loadAgentList();
  }, []);

  const loadAgentList = async () => {
    try {
      const res = await listAgentRegistry({ pageNum: 1, pageSize: 1000 });
      if (res.data?.rows) {
        setAgentList(res.data.rows);
      }
    } catch (error) {
      console.error('加载Agent列表失败:', error);
    }
  };

  const handleCreateSubmit = async (values) => {
    setFormLoading(true);
    try {
      const result = await createTask(values);
      if (result) {
        message.success('创建任务成功');
        setCreateModalOpen(false);
      }
    } finally {
      setFormLoading(false);
    }
  };

  const handleEditClick = async (record) => {
    setFormLoading(true);
    try {
      const res = await batchApi.getTaskById(record.id);
      if (res.code === 200 && res.data) {
        setEditingTask(res.data);
        setEditModalOpen(true);
      }
    } catch (error) {
      message.error('获取任务详情失败');
    } finally {
      setFormLoading(false);
    }
  };

  const handleEditSubmit = async (values) => {
    setFormLoading(true);
    try {
      const result = await updateTask(editingTask.id, values);
      if (result.code === 200) {
        message.success('修改任务成功');
        setEditModalOpen(false);
        setEditingTask(null);
      }
    } finally {
      setFormLoading(false);
    }
  };

  const handleViewClick = async (record) => {
    setFormLoading(true);
    try {
      const res = await batchApi.getTaskById(record.id);
      if (res.code === 200 && res.data) {
        setDetailTask(res.data);
        setDetailModalOpen(true);
      }
    } catch (error) {
      message.error('获取任务详情失败');
    } finally {
      setFormLoading(false);
    }
  };

  const handleConnectivityClick = (record) => {
    setConnTask(record);
    setConnModalOpen(true);
  };

  const getTaskInitialValues = (task) => {
    if (!task) return {};
    const targetAgentIds = Array.isArray(task.targetAgentIds)
      ? task.targetAgentIds
      : (() => { try { return JSON.parse(task.targetAgentIds || '[]'); } catch (e) { return []; } })();
    const targetAgentNames = Array.isArray(task.targetAgentNames)
      ? task.targetAgentNames
      : (() => { try { return JSON.parse(task.targetAgentNames || '[]'); } catch (e) { return []; } })();
    const targetDirs = (task.targetDirs || '').split(';').filter(Boolean);

    return {
      id: task.id,
      taskName: task.taskName,
      taskDescription: task.taskDescription,
      sourceAgentId: task.sourceAgentId,
      sourceDir: task.sourceDir,
      targets: targetAgentIds.map((id, idx) => ({
        agentId: id,
        dir: targetDirs[idx] || ''
      })),
      transferMode: task.transferMode || 'ONE_TO_ONE',
      routingStrategy: task.routingStrategy || 'ROUND_ROBIN',
      routingConfig: task.routingConfig || '',
      preserveDirStructure: task.preserveDirStructure === 1,
      retryEnabled: task.retryEnabled === 1,
      retryMaxDays: task.retryMaxDays || 7,
      retryIntervalMin: task.retryIntervalMin || 5,
      maxRetryCount: task.maxRetryCount || 3,
      retryBackoffType: task.retryBackoffType || 'EXPONENTIAL',
      includePatterns: (() => {
        if (Array.isArray(task.includePatterns)) return task.includePatterns;
        try { return JSON.parse(task.includePatterns || '[]'); } catch (e) { return []; }
      })(),
      excludePatterns: (() => {
        if (Array.isArray(task.excludePatterns)) return task.excludePatterns;
        try { return JSON.parse(task.excludePatterns || '[]'); } catch (e) { return []; }
      })(),
      maxScanFiles: task.maxScanFiles || 1000,
      scanCronExpression: task.scanCronExpression,
      scheduledEnabled: task.scheduledEnabled === 1,
      scheduledStartTime: task.scheduledStartTime ? dayjs(task.scheduledStartTime, 'HH:mm:ss') : undefined,
      scheduledEndTime: task.scheduledEndTime ? dayjs(task.scheduledEndTime, 'HH:mm:ss') : undefined,
      taskPriority: task.taskPriority,
      postTransferAction: task.postTransferAction || 'NONE',
      backupDir: task.backupDir,
      backupMode: task.backupMode || 'COPY',
      remark: task.remark
    };
  };

  return (
    <div className="batch-subtask-page">
      <div className="batch-subtask-container">
      <TaskListTab
        tasks={tasks}
        loading={loading}
        pagination={pagination}
        fetchTasks={fetchTasks}
        startTask={startTask}
        pauseTask={pauseTask}
        resumeTask={resumeTask}
        stopTask={stopTask}
        deleteTask={deleteTask}
        onCreateClick={() => setCreateModalOpen(true)}
        onEditClick={handleEditClick}
        onViewClick={handleViewClick}
        onConnectivityClick={handleConnectivityClick}
      />
      </div>
      <Modal
        title="创建传输任务"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        width={1160}
        destroyOnClose
        footer={null}
        styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' } }}
      >
        <TaskForm onSubmit={handleCreateSubmit} mode="create" loading={formLoading} />
      </Modal>
      <Modal
        title="修改传输任务"
        open={editModalOpen}
        onCancel={() => { setEditModalOpen(false); setEditingTask(null); }}
        width={1160}
        destroyOnClose
        footer={null}
        styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' } }}
      >
        <TaskForm
          onSubmit={handleEditSubmit}
          mode="edit"
          initialValues={getTaskInitialValues(editingTask)}
          loading={formLoading}
        />
      </Modal>
      <Modal
        title="传输任务详情"
        open={detailModalOpen}
        onCancel={() => { setDetailModalOpen(false); setDetailTask(null); }}
        width={1160}
        destroyOnClose
        footer={null}
        styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' } }}
      >
        <TaskForm
          mode="detail"
          initialValues={getTaskInitialValues(detailTask)}
        />
      </Modal>
      <ConnectivityModal
        open={connModalOpen}
        onClose={() => { setConnModalOpen(false); setConnTask(null); }}
        task={connTask}
      />
    </div>
  );
};

export default TaskListPage;
