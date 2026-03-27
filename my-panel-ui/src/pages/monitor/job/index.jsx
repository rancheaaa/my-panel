import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, message, Popconfirm, Tag, Tooltip, Switch, Modal, Radio, InputNumber, Row, Col, Descriptions, Dropdown } from 'antd';
import { SearchOutlined, ReloadOutlined, DeleteOutlined, PlusOutlined, EditOutlined, ColumnHeightOutlined, PlayCircleOutlined, EyeOutlined, FileTextOutlined, DownOutlined, UpOutlined } from '@ant-design/icons';
import { listJob, getJob, addJob, updateJob, delJob, changeJobStatus, runJob } from '../../../api/monitor/job';
import JobLog from './JobLog';
import { getDicts } from '../../../api/dict/data';
import dayjs from 'dayjs';

const { Option } = Select;
const { TextArea } = Input;

const Job = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tableSize, setTableSize] = useState('large');
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    jobName: undefined,
    jobGroup: undefined,
    status: undefined
  });

  const [sysJobGroup, setSysJobGroup] = useState([]);
  const [sysJobStatus, setSysJobStatus] = useState([]);

  // Modal State
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [detailOpen, setDetailOpen] = useState(false);
  const [currentJob, setCurrentJob] = useState({});
  
  // Job Log State
  const [logVisible, setLogVisible] = useState(false);
  const [logJobName, setLogJobName] = useState(undefined);
  const [logJobGroup, setLogJobGroup] = useState(undefined);

  const [form] = Form.useForm();
  const [searchForm] = Form.useForm();
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listJob(queryParams);
      if (res.code === 200) {
        setData(res.data.rows);
        setTotal(res.data.total);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    getDicts('sys_job_group').then(res => res.code === 200 && setSysJobGroup(res.data));
    getDicts('sys_job_status').then(res => res.code === 200 && setSysJobStatus(res.data));
  }, [queryParams]);

  const handleSearch = () => {
    searchForm.validateFields().then(values => {
      setQueryParams({
        ...queryParams,
        ...values,
        pageNum: 1
      });
    });
  };

  const handleReset = () => {
    searchForm.resetFields();
    setQueryParams({
      ...queryParams,
      jobName: undefined,
      jobGroup: undefined,
      status: undefined,
      pageNum: 1
    });
  };

  const handleAdd = () => {
    form.resetFields();
    setTitle('添加任务');
    setOpen(true);
    // Set defaults
    form.setFieldsValue({
        misfirePolicy: '1',
        concurrent: '1',
        status: '0'
    });
  };

  const handleUpdate = async (row) => {
    form.resetFields();
    const jobId = row.jobId || selectedRowKeys[0];
    const res = await getJob(jobId);
    if (res.code === 200) {
        form.setFieldsValue(res.data);
        setTitle('修改任务');
        setOpen(true);
    }
  };

  const handleDelete = async (jobId) => {
    try {
      await delJob(jobId);
      message.success('删除成功');
      fetchData();
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleBatchDelete = async () => {
      if (!selectedRowKeys.length) return;
      try {
          await delJob(selectedRowKeys.join(','));
          message.success('删除成功');
          fetchData();
          setSelectedRowKeys([]);
      } catch (error) {
          message.error('删除失败');
      }
  };

  const handleStatusChange = async (row) => {
      const text = row.status === '0' ? '停用' : '启用';
      try {
          await changeJobStatus(row.jobId, row.status === '0' ? '1' : '0');
          message.success(text + '成功');
          fetchData();
      } catch (error) {
          message.error(text + '失败');
      }
  };

  const handleRun = async (row) => {
      try {
          await runJob(row.jobId, row.jobGroup);
          message.success('执行成功');
      } catch (error) {
          message.error('执行失败');
      }
  };

  const handleView = (row) => {
      setCurrentJob(row);
      setDetailOpen(true);
  };

  const handleJobLog = (row) => {
      if (row) {
          setLogJobName(row.jobName);
          setLogJobGroup(row.jobGroup);
      } else {
          setLogJobName(undefined);
          setLogJobGroup(undefined);
      }
      setLogVisible(true);
  };

  const submitForm = async () => {
      try {
          const values = await form.validateFields();
          if (values.jobId) {
              await updateJob(values);
              message.success('修改成功');
          } else {
              await addJob(values);
              message.success('新增成功');
          }
          setOpen(false);
          fetchData();
      } catch (error) {
          console.error(error);
      }
  };

  const columns = [
    { title: '任务编号', dataIndex: 'jobId', key: 'jobId', align: 'center', width: 80 },
    { title: '任务名称', dataIndex: 'jobName', key: 'jobName', align: 'center', width: 150, ellipsis: true },
    { 
        title: '任务组名', 
        dataIndex: 'jobGroup', 
        key: 'jobGroup', 
        align: 'center',
        width: 120,
        render: (text) => {
            const dict = sysJobGroup.find(d => d.dictValue === text);
            return dict ? <Tag>{dict.dictLabel}</Tag> : <Tag>{text}</Tag>;
        }
    },
    { title: '调用目标字符串', dataIndex: 'invokeTarget', key: 'invokeTarget', align: 'center', width: 250, ellipsis: true },
    { title: 'Cron执行表达式', dataIndex: 'cronExpression', key: 'cronExpression', align: 'center', width: 150, ellipsis: true },
    { 
        title: '状态', 
        dataIndex: 'status', 
        key: 'status', 
        align: 'center',
        width: 100,
        render: (text, record) => (
            <Switch 
                checked={text === '0'} 
                onChange={() => handleStatusChange(record)} 
                checkedChildren="正常" 
                unCheckedChildren="暂停" 
            />
        )
    },
    { title: '创建者', dataIndex: 'createBy', key: 'createBy', align: 'center', width: 100, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 160 },
    { title: '更新者', dataIndex: 'updateBy', key: 'updateBy', align: 'center', width: 100, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: 160 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Tooltip title="修改">
            <Button type="text" icon={<EditOutlined />} onClick={() => handleUpdate(record)} />
          </Tooltip>
          <Tooltip title="执行一次">
            <Button type="text" icon={<PlayCircleOutlined />} onClick={() => handleRun(record)} />
          </Tooltip>
          <Tooltip title="详情">
            <Button type="text" icon={<EyeOutlined />} onClick={() => handleView(record)} />
          </Tooltip>
          <Tooltip title="调度日志">
            <Button type="text" icon={<FileTextOutlined />} onClick={() => handleJobLog(record)} />
          </Tooltip>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.jobId)}>
            <Tooltip title="删除">
                 <Button type="text" icon={<DeleteOutlined />} danger />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item name="jobName" label="任务名称">
                <Input placeholder="请输入任务名称" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="jobGroup" label="任务组名">
                 <Select placeholder="请选择" allowClear>
                    {sysJobGroup.map(dict => (
                        <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                    ))}
                 </Select>
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="status" label="任务状态">
                 <Select placeholder="请选择" allowClear>
                    {sysJobStatus.map(dict => (
                        <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                    ))}
                 </Select>
              </Form.Item>
            </Col>
            <Col span={6} style={{ textAlign: 'right' }}>
              <Space>
                <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
                <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
              </Space>
            </Col>
          </Row>
        </Form>
      </Card>

      <Card bordered={false} className="table-card">
        <div className="table-toolbar" style={{ marginBottom: 16 }}>
          <Space size="middle">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
            <Button type="primary" danger icon={<DeleteOutlined />} disabled={selectedRowKeys.length === 0} onClick={handleBatchDelete}>删除</Button>
            <Button icon={<FileTextOutlined />} onClick={() => handleJobLog()}>调度日志</Button>
            <Tooltip title="刷新">
               <Button icon={<ReloadOutlined />} onClick={fetchData} shape="circle" />
            </Tooltip>
            <Tooltip title="密度">
                <Dropdown
                  menu={{
                    items: [
                      { key: 'large', label: '默认' },
                      { key: 'middle', label: '中等' },
                      { key: 'small', label: '紧凑' },
                    ],
                    onClick: ({ key }) => setTableSize(key),
                    selectedKeys: [tableSize],
                  }}
                  trigger={['click']}
                >
                  <Button icon={<ColumnHeightOutlined />} shape="circle" />
                </Dropdown>
            </Tooltip>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={data}
          rowKey="jobId"
          loading={loading}
          size={tableSize}
          scroll={{ x: 1490 }}
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
      </Card>

      <Modal
          title={title}
          open={open}
          onOk={submitForm}
          onCancel={() => setOpen(false)}
          width={700}
      >
          <Form form={form} labelCol={{ span: 6 }} wrapperCol={{ span: 16 }}>
              <Form.Item name="jobId" hidden><Input /></Form.Item>
              <Row>
                  <Col span={12}>
                      <Form.Item name="jobName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
                          <Input placeholder="请输入任务名称" />
                      </Form.Item>
                  </Col>
                  <Col span={12}>
                      <Form.Item name="jobGroup" label="任务分组" rules={[{ required: true, message: '请选择任务分组' }]}>
                         <Select placeholder="请选择">
                            {sysJobGroup.map(dict => (
                                <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                            ))}
                         </Select>
                      </Form.Item>
                  </Col>
              </Row>
              <Form.Item name="invokeTarget" label="调用方法" rules={[{ required: true, message: '请输入调用目标字符串' }]}>
                  <Input placeholder="请输入调用目标字符串" />
              </Form.Item>
              <Form.Item name="cronExpression" label="Cron表达式" rules={[{ required: true, message: '请输入Cron执行表达式' }]}>
                  <Input placeholder="请输入Cron执行表达式" />
              </Form.Item>
              <Form.Item name="misfirePolicy" label="执行策略">
                  <Radio.Group>
                      <Radio.Button value="1">立即执行</Radio.Button>
                      <Radio.Button value="2">执行一次</Radio.Button>
                      <Radio.Button value="3">放弃执行</Radio.Button>
                  </Radio.Group>
              </Form.Item>
              <Row>
                  <Col span={12}>
                      <Form.Item name="concurrent" label="是否并发">
                          <Radio.Group>
                              <Radio value="0">允许</Radio>
                              <Radio value="1">禁止</Radio>
                          </Radio.Group>
                      </Form.Item>
                  </Col>
                  <Col span={12}>
                      <Form.Item name="status" label="状态">
                          <Radio.Group>
                              {sysJobStatus.map(dict => (
                                  <Radio key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Radio>
                              ))}
                          </Radio.Group>
                      </Form.Item>
                  </Col>
              </Row>
          </Form>
      </Modal>
      
      <JobLog 
        visible={logVisible} 
        onCancel={() => setLogVisible(false)}
        jobName={logJobName}
        jobGroup={logJobGroup}
      />

      <Modal
          title="任务详情"
          open={detailOpen}
          onCancel={() => setDetailOpen(false)}
          footer={[<Button key="close" onClick={() => setDetailOpen(false)}>关闭</Button>]}
      >
          <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="任务编号">{currentJob.jobId}</Descriptions.Item>
              <Descriptions.Item label="任务分组">{sysJobGroup.find(d => d.dictValue === currentJob.jobGroup)?.dictLabel}</Descriptions.Item>
              <Descriptions.Item label="任务名称">{currentJob.jobName}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{currentJob.createTime}</Descriptions.Item>
              <Descriptions.Item label="调用目标">{currentJob.invokeTarget}</Descriptions.Item>
              <Descriptions.Item label="执行表达式">{currentJob.cronExpression}</Descriptions.Item>
              <Descriptions.Item label="是否并发">
                  <Tag color={currentJob.concurrent === '0' ? 'blue' : 'red'}>
                      {currentJob.concurrent === '0' ? '允许' : '禁止'}
                  </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="执行策略">
                   {currentJob.misfirePolicy === '1' && <Tag>立即执行</Tag>}
                   {currentJob.misfirePolicy === '2' && <Tag>执行一次</Tag>}
                   {currentJob.misfirePolicy === '3' && <Tag>放弃执行</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                  <Tag color={currentJob.status === '0' ? 'success' : 'error'}>
                      {sysJobStatus.find(d => d.dictValue === currentJob.status)?.dictLabel}
                  </Tag>
              </Descriptions.Item>
          </Descriptions>
      </Modal>
    </div>
  );
};

export default Job;
