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
    <div>
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
