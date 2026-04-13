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
  Tag,
  Select
} from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  TagOutlined
} from '@ant-design/icons';
import { listTag, addTag, updateTag, delTag } from '@/api/op/archTag.js';

const { Option } = Select;

const COLOR_OPTIONS = [
  { value: '#1890ff', label: '蓝色' },
  { value: '#52c41a', label: '绿色' },
  { value: '#fa8c16', label: '橙色' },
  { value: '#722ed1', label: '紫色' },
  { value: '#eb2f96', label: '粉色' },
  { value: '#13c2c2', label: '青色' },
  { value: '#f5222d', label: '红色' },
  { value: '#faad14', label: '黄色' },
];

const TAG_TYPE_OPTIONS = [
  { value: 'custom', label: '自定义' },
  { value: 'system', label: '系统内置' },
];

const ArchTag = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [searchForm] = Form.useForm();
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    tagName: undefined,
    tagType: undefined
  });

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalForm] = Form.useForm();
  const [editingId, setEditingId] = useState(null);
  const [editingRecord, setEditingRecord] = useState(null);

  useEffect(() => {
    fetchData();
  }, [queryParams]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listTag(queryParams);
      if (res.code === 200) {
        setData(res.data?.list || []);
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
    searchForm.resetFields();
    setQueryParams({ pageNum: 1, pageSize: 10, tagName: undefined, tagType: undefined });
  };

  const handleAdd = () => {
    setEditingId(null);
    setEditingRecord(null);
    modalForm.resetFields();
    setIsModalOpen(true);
  };

  const handleEdit = (record) => {
    setEditingId(record.id);
    setEditingRecord(record);
    modalForm.setFieldsValue({
      tagName: record.tagName,
      tagColor: record.tagColor,
      tagType: record.tagType,
      tagDefaultValue: record.tagDefaultValue,
      remark: record.remark
    });
    setIsModalOpen(true);
  };

  const handleDelete = async (id) => {
    try {
      const res = await delTag(id);
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
      const payload = {
        tagName: values.tagName,
        tagColor: values.tagColor,
        tagType: values.tagType,
        tagDefaultValue: values.tagDefaultValue,
        remark: values.remark
      };
      if (editingId) {
        const res = await updateTag({ ...payload, id: editingId });
        if (res.code === 200) {
          message.success('修改成功');
          setIsModalOpen(false);
          fetchData();
        }
      } else {
        const res = await addTag(payload);
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
    {
      title: '标签名称', 
      dataIndex: 'tagName', 
      key: 'tagName',
      render: (text, record) => (
        <Space>
          <Tag color={record.tagColor} style={{ fontSize: '14px', padding: '4px 12px' }}>{text}</Tag>
        </Space>
      )
    },
    {
      title: '标签颜色', 
      dataIndex: 'tagColor', 
      key: 'tagColor',
      render: (text) => (
        <Space>
          <div style={{ width: 20, height: 20, backgroundColor: text, borderRadius: 4, border: '1px solid #ddd' }} />
          <Tag color={text}>{text}</Tag>
        </Space>
      )
    },
    {
      title: '标签类型', 
      dataIndex: 'tagType', 
      key: 'tagType',
      render: (text) => {
        const typeOption = TAG_TYPE_OPTIONS.find(opt => opt.value === text);
        return <Tag color={text === 'system' ? 'blue' : 'green'}>{typeOption ? typeOption.label : text}</Tag>;
      }
    },
    {
      title: '默认值', 
      dataIndex: 'tagDefaultValue', 
      key: 'tagDefaultValue',
      render: (text) => text || '-' 
    },

    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '创建时间', 
      dataIndex: 'createTime', 
      key: 'createTime',
      render: (text) => text ? new Date(text).toLocaleString() : '-'
    },
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
        <Form form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="tagName" label="标签名称">
            <Input placeholder="请输入标签名称" allowClear />
          </Form.Item>
          <Form.Item name="tagType" label="标签类型">
            <Select placeholder="请选择标签类型" allowClear style={{ width: 150 }}>
              {TAG_TYPE_OPTIONS.map(opt => (
                <Option key={opt.value} value={opt.value}>{opt.label}</Option>
              ))}
            </Select>
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
          <Button 
            type="primary" 
            icon={<TagOutlined />} 
            onClick={handleAdd}
            size="large"
            style={{ borderRadius: '6px', fontWeight: '500' }}
          >
            新增标签
          </Button>
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
        title={editingId ? '编辑标签' : '新增标签'}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        destroyOnClose
        width={400}
        okText="确定"
        cancelText="取消"
      >
        <Form form={modalForm} layout="vertical">
          <Form.Item
            label="标签名称"
            name="tagName"
            rules={[
              { required: true, message: '请输入标签名称' },
              { max: 100, message: '标签名称不能超过100个字符' }
            ]}
          >
            <Input placeholder="请输入标签名称" />
          </Form.Item>

          <Form.Item
            label="标签颜色"
            name="tagColor"
            initialValue="#1890ff"
            rules={[
              { required: true, message: '请选择标签颜色' }
            ]}
          >
            <Select>
              {COLOR_OPTIONS.map((item) => (
                <Option key={item.value} value={item.value}>
                  <Space>
                    <div style={{ width: 14, height: 14, backgroundColor: item.value, borderRadius: 3, border: '1px solid #ddd' }} />
                    <span>{item.label}</span>
                    <span style={{ color: '#8c8c8c' }}>{item.value}</span>
                  </Space>
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            label="标签类型"
            name="tagType"
            initialValue="custom"
            rules={[
              { required: true, message: '请选择标签类型' }
            ]}
          >
            <Select>
              {TAG_TYPE_OPTIONS.map(opt => (
                <Option key={opt.value} value={opt.value}>{opt.label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            label="默认值"
            name="tagDefaultValue"
            rules={[
              { max: 255, message: '默认值不能超过255个字符' }
            ]}
          >
            <Input placeholder="请输入标签默认值" />
          </Form.Item>

          <Form.Item
            label="备注"
            name="remark"
          >
            <Input.TextArea placeholder="请输入标签备注" rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ArchTag;