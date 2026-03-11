import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Modal, message, Popconfirm, Tooltip } from 'antd';
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined, ExportOutlined } from '@ant-design/icons';
import { listEnv, getEnv, addEnv, updateEnv, delEnv, exportEnv } from '../../../api/rc/env';

const EnvManage = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    envName: undefined
  });

  const [form] = Form.useForm();
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增环境');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listEnv(queryParams);
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
      envName: undefined,
      pageNum: 1
    });
  };

  const handleAdd = () => {
    setModalTitle('新增环境');
    setCurrentId(null);
    modalForm.resetFields();
    setIsModalOpen(true);
  };

  const handleEdit = async (record) => {
    setModalTitle('修改环境');
    setCurrentId(record.id);
    try {
        const res = await getEnv(record.id);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取详情失败');
    }
  };

  const handleDelete = async (id) => {
    const ids = id || selectedRowKeys;
    if (!ids || ids.length === 0) {
      message.warning('请选择要删除的数据');
      return;
    }
    try {
      await delEnv(ids);
      message.success('删除成功');
      setSelectedRowKeys([]);
      fetchData();
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleExport = async () => {
    try {
      const response = await exportEnv(queryParams);
      const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = `环境数据_${new Date().getTime()}.xlsx`;
      link.click();
    } catch (error) {
      message.error('导出失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentId) {
        await updateEnv({ ...values, id: currentId });
        message.success('更新成功');
      } else {
        await addEnv(values);
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
    { title: '环境ID', dataIndex: 'id', key: 'id', align: 'center', width: 100 },
    { title: '环境名称', dataIndex: 'envName', key: 'envName', align: 'center', width: 150, ellipsis: true },
    { title: '环境描述', dataIndex: 'envDesc', key: 'envDesc', align: 'center', width: 300, ellipsis: true },
    { title: '更新人', dataIndex: 'updateBy', key: 'updateBy', align: 'center', width: 100, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: 180 },
    { title: '创建人', dataIndex: 'createBy', key: 'createBy', align: 'center', width: 100, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 180 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
            <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline">
          <Form.Item name="envName" label="环境名称">
            <Input placeholder="请输入环境名称" allowClear />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card bordered={false} className="table-card">
        <div className="table-toolbar" style={{ marginBottom: 16 }}>
          <Space size="middle">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
            <Button 
              danger 
              icon={<DeleteOutlined />} 
              disabled={selectedRowKeys.length === 0}
              onClick={() => handleDelete()}
            >
              批量删除
            </Button>
            <Button icon={<ExportOutlined />} onClick={handleExport}>导出</Button>
            <Tooltip title="刷新">
                <Button icon={<ReloadOutlined />} onClick={fetchData} shape="circle" />
            </Tooltip>
          </Space>
        </div>

        <Table
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          columns={columns}
          dataSource={data}
          loading={loading}
          rowKey="id"
          scroll={{ x: 1300 }}
          pagination={{
            total: total,
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            onChange: (page, pageSize) => {
              setQueryParams({ ...queryParams, pageNum: page, pageSize: pageSize });
            },
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
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
        <Form
          form={modalForm}
          layout="vertical"
          initialValues={{ envName: '', envDesc: '' }}
        >
          <Form.Item
            name="envName"
            label="环境名称"
            rules={[{ required: true, message: '请输入环境名称' }]}
          >
            <Input placeholder="请输入环境名称" />
          </Form.Item>
          <Form.Item
            name="envDesc"
            label="环境描述"
          >
            <Input.TextArea placeholder="请输入环境描述" rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default EnvManage;
