import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, message, Popconfirm, Tag, Tooltip, DatePicker } from 'antd';
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CloseOutlined, DownloadOutlined, ColumnHeightOutlined, EyeOutlined } from '@ant-design/icons';
import { listJobLog, delJobLog, cleanJobLog, exportJobLog } from '../../../api/monitor/jobLog';
import { getDicts } from '../../../api/dict/data';
import dayjs from 'dayjs';

const { Option } = Select;
const { RangePicker } = DatePicker;

const JobLog = ({ visible, onCancel, jobName: defaultJobName, jobGroup: defaultJobGroup }) => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    jobName: defaultJobName,
    jobGroup: defaultJobGroup,
    status: undefined,
    createTime: undefined
  });

  const [sysJobGroup, setSysJobGroup] = useState([]);
  const [sysCommonStatus, setSysCommonStatus] = useState([]);
  const [form] = Form.useForm();
  
  // Detail Modal
  const [detailOpen, setDetailOpen] = useState(false);
  const [currentLog, setCurrentLog] = useState({});

  useEffect(() => {
    if (visible) {
      getDicts('sys_job_group').then(res => res.code === 200 && setSysJobGroup(res.data));
      getDicts('sys_common_status').then(res => res.code === 200 && setSysCommonStatus(res.data));
      
      // Reset form with default values if provided
      form.setFieldsValue({
        jobName: defaultJobName,
        jobGroup: defaultJobGroup
      });
      
      setQueryParams(prev => ({
        ...prev,
        jobName: defaultJobName,
        jobGroup: defaultJobGroup,
        pageNum: 1
      }));
    }
  }, [visible, defaultJobName, defaultJobGroup]);

  useEffect(() => {
    if (visible) {
      fetchData();
    }
  }, [queryParams, visible]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const { createTime, ...params } = queryParams;
      if (createTime) {
        params['params[beginTime]'] = createTime[0].format('YYYY-MM-DD HH:mm:ss');
        params['params[endTime]'] = createTime[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const res = await listJobLog(params);
      if (res.code === 200) {
        setData(res.data.rows);
        setTotal(res.data.total);
      }
    } catch (error) {
      message.error('获取调度日志失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    form.validateFields().then(values => {
      setQueryParams({
        ...queryParams,
        ...values,
        pageNum: 1
      });
    });
  };

  const handleReset = () => {
    form.resetFields();
    setQueryParams({
      pageNum: 1,
      pageSize: 10,
      jobName: undefined,
      jobGroup: undefined,
      status: undefined,
      createTime: undefined
    });
  };

  const handleDelete = async (ids) => {
    try {
      const res = await delJobLog(ids);
      if (res.code === 200) {
        message.success('删除成功');
        fetchData();
        setSelectedRowKeys([]);
      }
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleClean = async () => {
    try {
      const res = await cleanJobLog();
      if (res.code === 200) {
        message.success('清空成功');
        fetchData();
      }
    } catch (error) {
      message.error('清空失败');
    }
  };

  const handleExport = async () => {
    try {
      const { createTime, ...params } = queryParams;
      if (createTime) {
        params['params[beginTime]'] = createTime[0].format('YYYY-MM-DD HH:mm:ss');
        params['params[endTime]'] = createTime[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const res = await exportJobLog(params);
      // Handle download...
      message.success('导出成功');
    } catch (error) {
      message.error('导出失败');
    }
  };
  
  const showDetail = (record) => {
      setCurrentLog(record);
      setDetailOpen(true);
  };

  const columns = [
    {
      title: '日志编号',
      dataIndex: 'jobLogId',
      key: 'jobLogId',
      width: 100,
    },
    {
      title: '任务名称',
      dataIndex: 'jobName',
      key: 'jobName',
      width: 150,
      ellipsis: true,
    },
    {
      title: '任务分组',
      dataIndex: 'jobGroup',
      key: 'jobGroup',
      width: 100,
      render: (text) => {
        const dict = sysJobGroup.find(d => d.dictValue === text);
        return dict ? <Tag>{dict.dictLabel}</Tag> : text;
      }
    },
    {
      title: '调用目标字符串',
      dataIndex: 'invokeTarget',
      key: 'invokeTarget',
      width: 250,
      ellipsis: true,
    },
    {
      title: '日志信息',
      dataIndex: 'jobMessage',
      key: 'jobMessage',
      width: 200,
      ellipsis: true,
    },
    {
      title: '执行状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => {
         const dict = sysCommonStatus.find(d => d.dictValue === status);
         return dict ? (
             <Tag color={status === '0' ? 'success' : 'error'}>{dict.dictLabel}</Tag>
         ) : status;
      }
    },
    {
      title: '执行时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Tooltip title="详情">
            <Button 
                type="text" 
                icon={<EyeOutlined />} 
                onClick={() => showDetail(record)}
                size="small"
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <Modal
      title="调度日志"
      open={visible}
      onCancel={onCancel}
      width={1200}
      footer={null}
      destroyOnClose
      style={{ top: 20 }}
    >
      <div className="table-search">
        <Form form={form} layout="inline">
          <Form.Item name="jobName" label="任务名称">
            <Input placeholder="请输入任务名称" allowClear style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="jobGroup" label="任务组名">
             <Select placeholder="请选择" allowClear style={{ width: 120 }}>
                {sysJobGroup.map(dict => (
                    <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                ))}
             </Select>
          </Form.Item>
          <Form.Item name="status" label="执行状态">
             <Select placeholder="请选择" allowClear style={{ width: 120 }}>
                {sysCommonStatus.map(dict => (
                    <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                ))}
             </Select>
          </Form.Item>
          <Form.Item name="createTime" label="执行时间">
             <RangePicker style={{ width: 240 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </div>

      <div className="table-toolbar">
        <Space size="middle">
          <Popconfirm
             title="确定删除选中日志吗？"
             onConfirm={() => handleDelete(selectedRowKeys)}
             disabled={selectedRowKeys.length === 0}
          >
             <Button danger icon={<DeleteOutlined />} disabled={selectedRowKeys.length === 0}>删除</Button>
          </Popconfirm>
          <Popconfirm
             title="确定清空所有调度日志吗？"
             onConfirm={handleClean}
          >
             <Button danger icon={<DeleteOutlined />}>清空</Button>
          </Popconfirm>
          <Button icon={<DownloadOutlined />} onClick={handleExport}>导出</Button>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        rowKey="jobLogId"
        loading={loading}
        size="small"
        scroll={{ x: 1060 }}
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys
        }}
        pagination={{
          current: queryParams.pageNum,
          pageSize: queryParams.pageSize,
          total: total,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (page, pageSize) => {
              setQueryParams(prev => ({ ...prev, pageNum: page, pageSize }));
          }
        }}
      />
      
      <Modal
          title="调度日志详情"
          open={detailOpen}
          onCancel={() => setDetailOpen(false)}
          footer={[
              <Button key="close" onClick={() => setDetailOpen(false)}>
                  关闭
              </Button>
          ]}
          width={700}
      >
          <Form labelCol={{ span: 4 }}>
              <Form.Item label="日志序号">{currentLog.jobLogId}</Form.Item>
              <Form.Item label="任务名称">{currentLog.jobName}</Form.Item>
              <Form.Item label="任务分组">{currentLog.jobGroup}</Form.Item>
              <Form.Item label="执行时间">{currentLog.createTime}</Form.Item>
              <Form.Item label="调用方法">{currentLog.invokeTarget}</Form.Item>
              <Form.Item label="日志信息">{currentLog.jobMessage}</Form.Item>
              <Form.Item label="执行状态">
                  {currentLog.status === '0' ? '正常' : '失败'}
              </Form.Item>
              {currentLog.status === '1' && (
                  <Form.Item label="异常信息">
                      <Input.TextArea value={currentLog.exceptionInfo} readOnly rows={4} />
                  </Form.Item>
              )}
          </Form>
      </Modal>
    </Modal>
  );
};

export default JobLog;
