import React from 'react';
import { Card, message } from 'antd';
import TaskForm from './TaskForm';
import { useBatchTasks } from '../hooks/useBatchTasks';

const CreateEditTab = () => {
  const { createTask, loading } = useBatchTasks();

  const handleSubmit = async (values) => {
    try {
      console.log('提交任务数据:', values);
      const result = await createTask(values);
      if (result) {
        message.success('✅ 任务创建成功！');
      } else {
        message.error('❌ 任务创建失败，请检查输入数据');
      }
    } catch (error) {
      console.error('创建任务异常:', error);
      message.error('❌ 创建任务时发生错误: ' + (error.message || '未知错误'));
    }
  };

  return (
    <Card title="创建/编辑任务">
      <TaskForm onSubmit={handleSubmit} loading={loading} />
    </Card>
  );
};

export default CreateEditTab;
