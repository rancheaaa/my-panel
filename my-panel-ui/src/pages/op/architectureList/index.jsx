import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
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
  Tag, 
  Tooltip,
  Row,
  Col
} from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ClusterOutlined,
  SendOutlined,
  EyeOutlined
} from '@ant-design/icons';
import { listDiagram, addDiagram, delDiagram, publishDiagram } from '@/api/op/architecture';

const ArchitectureList = () => {
  const navigate = useNavigate();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    diagramName: undefined
  });

  const [form] = Form.useForm();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalForm] = Form.useForm();

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listDiagram(queryParams);
      if (res.code === 200) {
        setData(res.data.list);
        setTotal(res.data.total);
      }
    } catch (error) {
      console.error(error);
      message.error('加载列表失败');
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
      pageNum: 1,
      pageSize: 10,
      diagramName: undefined
    });
  };

  const handleAdd = () => {
    setIsModalOpen(true);
    modalForm.resetFields();
  };

  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      const res = await addDiagram(values);
      if (res.code === 200) {
        message.success('创建成功');
        setIsModalOpen(false);
        // 创建成功后直接跳转到编辑页面
        navigate(`/op/architectureEdit/${res.data.id}`);
      }
    } catch (error) {
      console.error(error);
      message.error('创建失败');
    }
  };

  const handleDelete = async (id) => {
    try {
      await delDiagram(id);
      message.success('删除成功');
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('删除失败');
    }
  };

  const handlePublish = async (id) => {
    try {
      const res = await publishDiagram(id);
      if (res.code === 200) {
        message.success('发布成功');
        fetchData();
      }
    } catch (error) {
      console.error(error);
      message.error('发布失败');
    }
  };

  const columns = [
    { title: '编号', dataIndex: 'id', key: 'id', width: 80, align: 'center' },
    { title: '架构图名称', dataIndex: 'diagramName', key: 'diagramName', ellipsis: true },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status', 
      width: 100, 
      align: 'center',
      render: (status) => {
        const statusMap = {
          '0': { color: 'default', text: '草稿' },
          '1': { color: 'green', text: '已发布' },
          '2': { color: 'orange', text: '已归档' }
        };
        const config = statusMap[status] || statusMap['0'];
        return <Tag color={config.color}>{config.text}</Tag>;
      }
    },
    { title: '版本', dataIndex: 'version', key: 'version', width: 80, align: 'center' },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, align: 'center' },
    {
      title: '操作',
      key: 'action',
      width: 250,
      align: 'center',
      render: (_, record) => (
        <Space size="middle">
          <Tooltip title="编辑架构图">
            <Button 
              type="text" 
              icon={<EditOutlined />} 
              onClick={() => navigate(`/op/architectureEdit/${record.id}`)}
              style={{ color: '#1890ff' }}
            >
              编辑
            </Button>
          </Tooltip>
          <Tooltip title="发布架构图">
            <Button 
              type="text" 
              icon={<SendOutlined />} 
              onClick={() => handlePublish(record.id)}
              style={{ color: '#52c41a' }}
              disabled={record.status === '1'}
            >
              发布
            </Button>
          </Tooltip>
          <Popconfirm title="确定删除该架构图吗？" onConfirm={() => handleDelete(record.id)}>
            <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="app-container" style={{ padding: '24px' }}>
      <Card bordered={false} style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline">
          <Form.Item name="diagramName" label="架构名称">
            <Input placeholder="请输入架构名称" allowClear onPressEnter={handleSearch} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card bordered={false}>
        <div style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>创建架构图</Button>
        </div>

        <Table
          columns={columns}
          dataSource={data}
          loading={loading}
          rowKey="id"
          pagination={{
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            total: total,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
              setQueryParams({ ...queryParams, pageNum: page, pageSize });
            },
          }}
        />
      </Card>

      <Modal
        title="创建新架构图"
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        okText="确定"
        cancelText="取消"
      >
        <Form form={modalForm} layout="vertical">
          <Form.Item
            name="diagramName"
            label="架构图名称"
            rules={[{ required: true, message: '请输入架构图名称' }]}
          >
            <Input placeholder="请输入架构图名称" />
          </Form.Item>
          <Form.Item
            name="remark"
            label="备注"
          >
            <Input.TextArea placeholder="请输入备注" rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ArchitectureList;
