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
  ApiOutlined,
  ClusterOutlined,
  DatabaseOutlined,
  SettingOutlined,
  SyncOutlined,
  SaveOutlined
} from '@ant-design/icons';
import { listNodeType, addNodeType, updateNodeType, delNodeType } from '@/api/op/archNodeType';

const { Option } = Select;

const ICON_MAP = {
  ApiOutlined: <ApiOutlined />,
  ClusterOutlined: <ClusterOutlined />,
  DatabaseOutlined: <DatabaseOutlined />,
  SettingOutlined: <SettingOutlined />,
  DeleteOutlined: <DeleteOutlined />,
  SyncOutlined: <SyncOutlined />,
  PlusOutlined: <PlusOutlined />,
  SaveOutlined: <SaveOutlined />,
  ReloadOutlined: <ReloadOutlined />
};

const ICON_OPTIONS = [
  { value: 'ApiOutlined', label: 'API图标' },
  { value: 'ClusterOutlined', label: '集群图标' },
  { value: 'DatabaseOutlined', label: '数据库图标' },
  { value: 'SettingOutlined', label: '设置图标' },
  { value: 'DeleteOutlined', label: '删除图标' },
  { value: 'SyncOutlined', label: '同步图标' },
  { value: 'PlusOutlined', label: '加号图标' },
  { value: 'SaveOutlined', label: '保存图标' },
  { value: 'ReloadOutlined', label: '刷新图标' },
];

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

const ArchNodeType = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    typeName: undefined
  });

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalForm] = Form.useForm();
  const [editingId, setEditingId] = useState(null);
  const [editingRecord, setEditingRecord] = useState(null);

  const safeJsonParse = (value) => {
    if (!value) return null;
    if (typeof value === 'object') return value;
    try {
      return JSON.parse(value);
    } catch {
      return null;
    }
  };

  const extractPrimaryColor = (defaultStyle) => {
    const styleObj = safeJsonParse(defaultStyle);
    return styleObj?.borderColor || styleObj?.backgroundColor || styleObj?.color || '#1890ff';
  };

  const buildDefaultStyle = (baseColor, existingDefaultStyle) => {
    const prev = safeJsonParse(existingDefaultStyle) || {};
    const next = {
      ...prev,
      backgroundColor: baseColor,
      borderColor: baseColor,
    };
    if (next.borderWidth == null) next.borderWidth = 2;
    if (next.borderRadius == null) next.borderRadius = 4;
    if (next.color == null) next.color = '#ffffff';
    return JSON.stringify(next);
  };

  useEffect(() => {
    fetchData();
  }, [queryParams]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listNodeType(queryParams);
      if (res.code === 200) {
        const list = (res.data?.list || []).map((item) => ({
          ...item,
          color: extractPrimaryColor(item.defaultStyle),
        }));
        setData(list);
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
    setQueryParams({ pageNum: 1, pageSize: 10, typeName: undefined });
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
      typeCode: record.typeCode,
      typeName: record.typeName,
      remark: record.remark,
      icon: record.icon || 'ApiOutlined',
      color: extractPrimaryColor(record.defaultStyle),
    });
    setIsModalOpen(true);
  };

  const handleDelete = async (id) => {
    try {
      const res = await delNodeType(id);
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
        typeCode: values.typeCode,
        typeName: values.typeName,
        icon: values.icon,
        remark: values.remark,
        defaultStyle: buildDefaultStyle(values.color, editingRecord?.defaultStyle),
      };
      if (editingId) {
        const res = await updateNodeType({ ...payload, id: editingId });
        if (res.code === 200) {
          message.success('修改成功');
          setIsModalOpen(false);
          fetchData();
        }
      } else {
        const res = await addNodeType(payload);
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
    { title: '类型名称', dataIndex: 'typeName', key: 'typeName' },
    { title: '类型编码', dataIndex: 'typeCode', key: 'typeCode' },
    { 
      title: '图标', 
      dataIndex: 'icon', 
      key: 'icon',
      render: (text) => {
        return (
          <Space>
            {ICON_MAP[text] || <ApiOutlined />}
            <Tag color="blue">{text}</Tag>
          </Space>
        );
      }
    },
    { 
      title: '颜色', 
      dataIndex: 'color', 
      key: 'color',
      render: (text) => (
        <Space>
          <div style={{ width: 20, height: 20, backgroundColor: text, borderRadius: 4, border: '1px solid #ddd' }} />
          <Tag color={text}>{text}</Tag>
        </Space>
      )
    },
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
          <Form.Item name="typeName" label="类型名称">
            <Input placeholder="请输入类型名称" allowClear />
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
            icon={<SettingOutlined />} 
            onClick={handleAdd}
            size="large"
            style={{ borderRadius: '6px', fontWeight: '500' }}
          >
            新增自定义节点类型
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
        title={editingId ? '编辑节点类型' : '新增自定义节点类型'}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        destroyOnClose
        width={600}
        okText="确定"
        cancelText="取消"
      >
        <Form form={modalForm} layout="vertical">
          <Form.Item
            label="节点类型编码"
            name="typeCode"
            rules={[
              { required: true, message: '请输入节点类型标识' },
              { pattern: /^[a-zA-Z0-9_-]+$/, message: '只能包含字母、数字、下划线和连字符' }
            ]}
            tooltip="节点的唯一标识符，用于区分不同类型的节点"
          >
            <Input placeholder="例如：custom-service" />
          </Form.Item>

          <Form.Item
            label="节点类型名称"
            name="typeName"
            rules={[{ required: true, message: '请输入节点类型名称' }]}
            tooltip="节点类型的显示名称"
          >
            <Input placeholder="例如：自定义服务" />
          </Form.Item>

          <Form.Item
            label="节点类型描述"
            name="remark"
            tooltip="节点类型的详细描述"
          >
            <Input.TextArea placeholder="请输入节点类型描述" rows={3} />
          </Form.Item>

          <Form.Item
            label="节点图标"
            name="icon"
            initialValue="ApiOutlined"
            tooltip="选择节点显示的图标"
          >
            <Select>
              {ICON_OPTIONS.map((item) => (
                <Option key={item.value} value={item.value}>
                  <Space>
                    {ICON_MAP[item.value] || <ApiOutlined />}
                    <span>{item.label}</span>
                  </Space>
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            label="节点颜色"
            name="color"
            initialValue="#1890ff"
            tooltip="选择节点的主题颜色"
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

          <div style={{
            padding: '12px',
            backgroundColor: '#f5f5f5',
            borderRadius: '4px',
            fontSize: '13px',
            color: '#595959'
          }}>
            <div style={{ marginBottom: '8px', fontWeight: '500' }}>
              <span style={{ color: '#1890ff' }}>自定义节点说明：</span>
            </div>
            <div style={{ marginBottom: '4px' }}>• 节点类型标识：唯一标识符，用于区分不同类型</div>
            <div style={{ marginBottom: '4px' }}>• 节点类型名称：显示在界面上的名称</div>
            <div style={{ marginBottom: '4px' }}>• 节点类型描述：详细描述节点类型的用途</div>
            <div style={{ marginBottom: '4px' }}>• 节点图标：选择节点显示的图标样式</div>
            <div>• 节点颜色：选择节点的主题颜色</div>
          </div>
        </Form>
      </Modal>
    </div>
  );
};

export default ArchNodeType;
