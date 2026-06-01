import React from 'react';
import { Card, message, Alert } from 'antd';
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
      const errorMsg = error.message || error.msg || '未知错误';
      if (errorMsg.includes('必填字段未填写')) {
        const fields = errorMsg.replace(/.*必填字段未填写:\s*/, '');
        const fieldList = fields.split('、').filter(Boolean);
        message.error({
          content: '❌ 以下必填字段未填写',
          duration: 5
        });
      } else {
        message.error('❌ 创建任务时发生错误: ' + errorMsg);
      }
    }
  };

  return (
    <Card title="创建/编辑任务">
      <TaskForm onSubmit={handleSubmit} loading={loading} />
    </Card>
  );
};

export default CreateEditTab;
