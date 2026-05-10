import React from 'react';
import { Card } from 'antd';
import TaskForm from './TaskForm';
import { useBatchTasks } from '../hooks/useBatchTasks';

const CreateEditTab = () => {
  const { createTask, loading } = useBatchTasks();

  const handleSubmit = async (values) => {
    await createTask(values);
  };

  return (
    <Card title="创建/编辑任务">
      <TaskForm onSubmit={handleSubmit} loading={loading} />
    </Card>
  );
};

export default CreateEditTab;
