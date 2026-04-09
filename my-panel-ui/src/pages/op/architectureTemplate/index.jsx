import React, { useState, useEffect } from 'react';
import { 
  Table, 
  Card, 
  Button, 
  Space, 
  Form, 
  Input, 
  Modal, 
  message, 
  Popconfirm, 
  Tag
} from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined
} from '@ant-design/icons';
import { listTemplate, addTemplate, updateTemplate, delTemplate } from '@/api/op/archTemplate';

const ArchTemplate = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    templateName: undefined
  });

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalForm] = Form.useForm();
  const [editingId, setEditingId] = useState(null);

  useEffect(() => {
    fetchData();
  }, [queryParams]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listTemplate(queryParams);
      if (res.code === 200) {
        setData(res.data.list);
        setTotal(res.data.total);
      }
    } catch (error) {
      console.error('Fetch data error', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (values) => {
    setQueryParams({ ...queryParams, ...values, pageNum: 1 });
  };

  const resetSearch = () => {
    setQueryParams({ pageNum: 1, pageSize: 10, templateName: undefined });
  };

  const handleAdd = () => {
    setEditingId(null);
    modalForm.resetFields();
    setIsModalOpen(true);
  };

  const handleEdit = (record) => {
    setEditingId(record.id);
    modalForm.setFieldsValue(record);
    setIsModalOpen(true);
  };

  const handleDelete = async (id) => {
    try {
      const res = await delTemplate(id);
      if (res.code === 200) {
        message.success('删除成功');
        fetchData();
      }
    } catch (error) {
      console.error('Delete error', error);
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (editingId) {
        const res = await updateTemplate({ ...values, id: editingId });
        if (res.code === 200) {
          message.success('修改成功');
          setIsModalOpen(false);
          fetchData();
        }
      } else {
        const res = await addTemplate(values);
        if (res.code === 200) {
          message.success('新增成功');
          setIsModalOpen(false);
          fetchData();
        }
      }
    } catch (error) {
      console.error('Modal submit error', error);
    }
  };

  const columns = [
    { title: '模板名称', dataIndex: 'templateName', key: 'templateName' },
    { title: '模板编码', dataIndex: 'templateCode', key: 'templateCode' },
    { title: '版本', dataIndex: 'version', key: 'version', render: (text) => <Tag color="green">{text}</Tag> },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Card bordered={false} style={{ marginBottom: '16px' }}>
        <Form layout="inline" onFinish={handleSearch}>
          <Form.Item name="templateName" label="模板名称">
            <Input placeholder="请输入模板名称" allowClear />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} htmlType="submit">查询</Button>
              <Button icon={<ReloadOutlined />} onClick={resetSearch}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card bordered={false}>
        <div style={{ marginBottom: '16px' }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增模板</Button>
        </div>
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          pagination={{
            total,
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            onChange: (page, pageSize) => setQueryParams({ ...queryParams, pageNum: page, pageSize }),
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
        />
      </Card>

      <Modal
        title={editingId ? '编辑模板' : '新增模板'}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        destroyOnClose
      >
        <Form form={modalForm} layout="vertical">
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
            <Input placeholder="请输入模板名称" />
          </Form.Item>
          <Form.Item name="templateCode" label="模板编码" rules={[{ required: true, message: '请输入模板编码' }]}>
            <Input placeholder="请输入模板编码" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="请输入备注" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ArchTemplate;
