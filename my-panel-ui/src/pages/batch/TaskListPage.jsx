import React, { useState } from 'react';
import { Button, Modal, Typography } from 'antd';
import { PlusCircleOutlined } from '@ant-design/icons';
import TaskListTab from './components/TaskListTab';
import TaskForm from './components/TaskForm';
import { useBatchTasks } from './hooks/useBatchTasks';

const { Title } = Typography;

const TaskListPage = () => {
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const { tasks, loading, pagination, fetchTasks, createTask, startTask, pauseTask, resumeTask, stopTask, deleteTask } = useBatchTasks();

  const handleCreateSubmit = async (values) => {
    const result = await createTask(values);
    if (result) {
      setCreateModalOpen(false);
    }
  };

  return (
    <div style={{ padding: '24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0, color: '#1f1f1f' }}>任务列表</Title>
        <Button type="primary" icon={<PlusCircleOutlined />} onClick={() => setCreateModalOpen(true)} style={{ borderRadius: 6 }}>
          新建任务
        </Button>
      </div>
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
      />
      <Modal
        title="创建传输任务"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        width={960}
        destroyOnClose
        footer={null}
        styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' } }}
      >
        <TaskForm onSubmit={handleCreateSubmit} />
      </Modal>
    </div>
  );
};

export default TaskListPage;
