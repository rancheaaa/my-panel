import React from 'react';
import { Form, Input, Select, Button } from 'antd';

const TaskForm = ({ onSubmit, initialValues = {}, loading = false }) => {
  const [form] = Form.useForm();

  const onFinish = (values) => {
    onSubmit(values);
  };

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={initialValues}
      onFinish={onFinish}
    >
      <Form.Item
        label="任务名称"
        name="taskName"
        rules={[{ required: true, message: '请输入任务名称' }]}
      >
        <Input placeholder="请输入任务名称" />
      </Form.Item>

      <Form.Item
        label="源Agent ID"
        name="sourceAgentId"
        rules={[{ required: true, message: '请输入源Agent ID' }]}
      >
        <Input placeholder="请输入源Agent ID" />
      </Form.Item>

      <Form.Item
        label="源目录"
        name="sourceDir"
        rules={[{ required: true, message: '请输入源目录' }]}
      >
        <Input placeholder="/path/to/source" />
      </Form.Item>

      <Form.Item
        label="目标目录"
        name="targetDirs"
        rules={[{ required: true, message: '请输入目标目录' }]}
      >
        <Input placeholder="/path/to/target" />
      </Form.Item>

      <Form.Item
        label="包含模式"
        name="includePatterns"
      >
        <Select
          mode="tags"
          placeholder="输入文件匹配模式"
        />
      </Form.Item>

      <Form.Item
        label="排除模式"
        name="excludePatterns"
      >
        <Select
          mode="tags"
          placeholder="输入排除模式"
        />
      </Form.Item>

      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          提交
        </Button>
      </Form.Item>
    </Form>
  );
};

export default TaskForm;
