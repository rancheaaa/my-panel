import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, message, Popconfirm, Tag, Tooltip, Dropdown, Row, Col } from 'antd';
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined, ColumnHeightOutlined, DownOutlined, UpOutlined } from '@ant-design/icons';
import { listConfig, getConfig, addConfig, updateConfig, delConfig, refreshCache } from '../../../api/config';
import { getDicts } from '../../../api/dict/data';

const { Option } = Select;

const Config = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [tableSize, setTableSize] = useState('large');
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    configName: undefined,
    configKey: undefined,
    configType: undefined
  });
  const [sysYesNo, setSysYesNo] = useState([]);

  const [form] = Form.useForm();
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增参数');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listConfig(queryParams);
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
    getDicts('sys_yes_no').then(res => {
      if (res.code === 200) {
        setSysYesNo(res.data);
      }
    });
  }, [queryParams]);

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
      ...queryParams,
      configName: undefined,
      configKey: undefined,
      configType: undefined,
      pageNum: 1
    });
  };

  const handleAdd = () => {
    setModalTitle('新增参数');
    setCurrentId(null);
    modalForm.resetFields();
    setIsModalOpen(true);
  };

  const handleEdit = async (record) => {
    setModalTitle('修改参数');
    setCurrentId(record.configId);
    try {
        const res = await getConfig(record.configId);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取详情失败');
    }
  };

  const handleDelete = async (id) => {
    try {
      await delConfig(id);
      message.success('删除成功');
      fetchData();
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleRefreshCache = async () => {
      try {
          await refreshCache();
          message.success('刷新缓存成功');
      } catch (error) {
          message.error('刷新缓存失败');
      }
  };

  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentId) {
        await updateConfig({ ...values, configId: currentId });
        message.success('更新成功');
      } else {
        await addConfig(values);
        message.success('新增成功');
      }
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('操作失败');
    }
  };

  const columns = [
    { title: '参数主键', dataIndex: 'configId', key: 'configId', align: 'center', width: 100 },
    { title: '参数名称', dataIndex: 'configName', key: 'configName', align: 'center', width: 200, ellipsis: true },
    { title: '参数键名', dataIndex: 'configKey', key: 'configKey', align: 'center', width: 200, ellipsis: true },
    { title: '参数键值', dataIndex: 'configValue', key: 'configValue', align: 'center', width: 200, ellipsis: true },
    { 
        title: '系统内置', 
        dataIndex: 'configType', 
        key: 'configType', 
        align: 'center',
        width: 100,
        render: (text) => (
            <Tag color={text === 'Y' ? 'blue' : 'green'}>
                {text === 'Y' ? '是' : '否'}
            </Tag>
        )
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', align: 'center', width: 200, ellipsis: true },
    { title: '创建者', dataIndex: 'createBy', key: 'createBy', align: 'center', width: 100, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 160 },
    { title: '更新者', dataIndex: 'updateBy', key: 'updateBy', align: 'center', width: 100, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: 160 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.configId)}>
            <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item name="configName" label="参数名称">
                <Input placeholder="请输入参数名称" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="configKey" label="参数键名">
                <Input placeholder="请输入参数键名" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="configType" label="系统内置">
                 <Select placeholder="系统内置" allowClear>
                    {sysYesNo.map(dict => (
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
        <div className="table-toolbar">
          <Space size="middle">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
            <Button danger icon={<ReloadOutlined />} onClick={handleRefreshCache}>刷新缓存</Button>
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
          rowKey="configId"
          loading={loading}
          size={tableSize}
          scroll={{ x: 1500 }}
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
        title={modalTitle}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        destroyOnClose
      >
        <Form form={modalForm} layout="vertical">
          <Form.Item name="configName" label="参数名称" rules={[{ required: true, message: '请输入参数名称' }]}>
            <Input placeholder="请输入参数名称" />
          </Form.Item>
          <Form.Item name="configKey" label="参数键名" rules={[{ required: true, message: '请输入参数键名' }]}>
             <Input placeholder="请输入参数键名" />
          </Form.Item>
          <Form.Item name="configValue" label="参数键值" rules={[{ required: true, message: '请输入参数键值' }]}>
             <Input.TextArea placeholder="请输入参数键值" />
          </Form.Item>
          <Form.Item name="configType" label="系统内置" initialValue="Y">
             <Select>
                {sysYesNo.map(dict => (
                    <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                ))}
             </Select>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="请输入备注" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Config;
