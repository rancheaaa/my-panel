import React from 'react';
import { Table, Button, Space, Popconfirm } from 'antd';
import TaskStatusBadge from './TaskStatusBadge';
import { useBatchTasks } from '../hooks/useBatchTasks';

const TaskListTab = () => {
  const {
    tasks,
    loading,
    pagination,
    fetchTasks,
    startTask,
    pauseTask,
    resumeTask,
    stopTask,
    deleteTask
  } = useBatchTasks();

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80
    },
    {
      title: '任务名称',
      dataIndex: 'taskName',
      key: 'taskName'
    },
    {
      title: '源Agent',
      dataIndex: 'sourceAgentId',
      key: 'sourceAgentId',
      width: 120
    },
    {
      title: '目标数',
      dataIndex: 'targetAgentIds',
      key: 'targetAgentIds',
      width: 80,
      render: (ids) => ids?.length || 0
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status) => <TaskStatusBadge status={status} />
    },
    {
      title: 'Cron表达式',
      dataIndex: 'cronExpression',
      key: 'cronExpression',
      width: 150
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {record.status === 'READY' && (
            <Button type="link" size="small" onClick={() => startTask(record.id)}>
              启动
            </Button>
          )}
          {record.status === 'RUNNING' && (
            <Button type="link" size="small" onClick={() => pauseTask(record.id)}>
              暂停
            </Button>
          )}
          {record.status === 'PAUSED' && (
            <Button type="link" size="small" onClick={() => resumeTask(record.id)}>
              恢复
            </Button>
          )}
          {(record.status === 'RUNNING' || record.status === 'PAUSED') && (
            <Popconfirm title="确定停止该任务？" onConfirm={() => stopTask(record.id)}>
              <Button type="link" danger size="small">
                停止
              </Button>
            </Popconfirm>
          )}
          <Popconfirm title="确定删除该任务？" onConfirm={() => deleteTask([record.id])}>
            <Button type="link" danger size="small">
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <Table
      columns={columns}
      dataSource={tasks}
      rowKey="id"
      loading={loading}
      pagination={{
        ...pagination,
        showSizeChanger: true,
        showTotal: (total) => `共 ${total} 条`
      }}
      onChange={(pag) => fetchTasks({ page: pag.current, size: pag.pageSize })}
      scroll={{ x: 1200 }}
    />
  );
};

export default TaskListTab;
