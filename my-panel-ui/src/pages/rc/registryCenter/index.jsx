import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Modal, message, Popconfirm, Tooltip, Select, Tag } from 'antd';
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined, ExportOutlined } from '@ant-design/icons';
import { listNode, getNode, addNode, updateNode, delNode, exportNode } from '../../../api/rc/node';
import { listEnv } from '../../../api/rc/env';
import { listProject } from '../../../api/rc/project';

const RegistryCenter = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    envId: undefined,
    projectId: undefined,
    nodeIp: undefined,
    status: undefined
  });

  const [envs, setEnvs] = useState([]);
  const [projects, setProjects] = useState([]);
  const [form] = Form.useForm();
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增节点');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listNode(queryParams);
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

  const fetchEnvs = async () => {
    try {
      const res = await listEnv({ pageSize: 100 });
      if (res.code === 200) {
        setEnvs(res.data.rows);
      }
    } catch (error) {
      console.error(error);
    }
  };

  const fetchProjects = async () => {
    try {
      const res = await listProject({ pageSize: 100 });
      if (res.code === 200) {
        setProjects(res.data.rows);
      }
    } catch (error) {
      console.error(error);
    }
  };

  useEffect(() => {
    fetchEnvs();
    fetchProjects();
  }, []);

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
      pageNum: 1,
      pageSize: 10,
      envId: undefined,
      projectId: undefined,
      nodeIp: undefined,
      status: undefined
    });
  };

  const handleAdd = () => {
    setModalTitle('新增节点');
    setCurrentId(null);
    modalForm.resetFields();
    setIsModalOpen(true);
  };

  const handleEdit = async (record) => {
    setModalTitle('修改节点');
    setCurrentId(record.id);
    try {
        const res = await getNode(record.id);
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
      await delNode(ids);
      message.success('删除成功');
      setSelectedRowKeys([]);
      fetchData();
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleExport = async () => {
    try {
      const response = await exportNode(queryParams);
      const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = `注册中心节点_${new Date().getTime()}.xlsx`;
      link.click();
    } catch (error) {
      message.error('导出失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentId) {
        await updateNode({ ...values, id: currentId });
        message.success('更新成功');
      } else {
        await addNode(values);
        message.success('新增成功');
      }
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error(error.message || '操作失败');
    }
  };

  const columns = [
    { title: '节点ID', dataIndex: 'id', key: 'id', align: 'center', width: 80 },
    { title: '环境', dataIndex: 'envName', key: 'envName', align: 'center', width: 120, ellipsis: true },
    { title: '应用', dataIndex: 'projectName', key: 'projectName', align: 'center', width: 150, ellipsis: true },
    { title: '节点IP', dataIndex: 'nodeIp', key: 'nodeIp', align: 'center', width: 150 },
    { title: '节点端口', dataIndex: 'nodePort', key: 'nodePort', align: 'center', width: 100 },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status', 
      align: 'center',
      width: 100,
      render: (status) => (
        <Tag color={status === '0' ? 'green' : 'red'}>
          {status === '0' ? '在线' : '离线'}
        </Tag>
      )
    },
    { title: '最后刷新时间', dataIndex: 'lastRefreshTime', key: 'lastRefreshTime', align: 'center', width: 180 },
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
          <Form.Item name="envId" label="环境">
            <Select placeholder="请选择环境" style={{ width: 150 }} allowClear>
              {envs.map(env => (
                <Select.Option key={env.id} value={env.id}>{env.envName}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="projectId" label="应用">
            <Select placeholder="请选择应用" style={{ width: 150 }} allowClear>
              {projects.map(p => (
                <Select.Option key={p.id} value={p.id}>{p.projectName}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="nodeIp" label="节点IP">
            <Input placeholder="请输入节点IP" allowClear />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择状态" style={{ width: 120 }} allowClear>
              <Select.Option value="0">在线</Select.Option>
              <Select.Option value="1">离线</Select.Option>
            </Select>
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
          scroll={{ x: 1400 }}
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
        >
          <Form.Item
            name="envId"
            label="环境"
            rules={[{ required: true, message: '请选择环境' }]}
          >
            <Select placeholder="请选择环境">
              {envs.map(env => (
                <Select.Option key={env.id} value={env.id}>{env.envName}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="projectId"
            label="应用"
            rules={[{ required: true, message: '请选择应用' }]}
          >
            <Select placeholder="请选择应用">
              {projects.map(p => (
                <Select.Option key={p.id} value={p.id}>{p.projectName}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="nodeIp"
            label="节点IP"
            rules={[{ required: true, message: '请输入节点IP' }]}
          >
            <Input placeholder="请输入节点IP" />
          </Form.Item>
          <Form.Item
            name="nodePort"
            label="节点端口"
            rules={[{ required: true, message: '请输入节点端口' }]}
          >
            <Input placeholder="请输入节点端口" type="number" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default RegistryCenter;
