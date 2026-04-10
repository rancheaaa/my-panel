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
  SaveOutlined,
  HddOutlined,
  CloudServerOutlined,
  AppstoreOutlined,
  MessageOutlined,
  CodeOutlined,
  LineChartOutlined,
  DashboardOutlined,
  SafetyCertificateOutlined,
  ControlOutlined,
  FolderOpenOutlined,
  ApartmentOutlined,
  GlobalOutlined,
  DesktopOutlined,
  SecurityScanOutlined,
  NodeIndexOutlined,
  DeploymentUnitOutlined
} from '@ant-design/icons';
import { listNodeType, addNodeType, updateNodeType, delNodeType } from '@/api/op/archNodeType.js';
import { getDicts } from '@/api/dict/data';
import BrandIcon, { BRAND_ICON_OPTIONS } from '@/components/BrandIcon';
import NodeShape, { COMMON_SHAPES } from '@/components/NodeShape';

const { Option, OptGroup } = Select;

const ICON_MAP = {
  ApiOutlined: <ApiOutlined />,
  ClusterOutlined: <ClusterOutlined />,
  DatabaseOutlined: <DatabaseOutlined />,
  SettingOutlined: <SettingOutlined />,
  DeleteOutlined: <DeleteOutlined />,
  SyncOutlined: <SyncOutlined />,
  PlusOutlined: <PlusOutlined />,
  SaveOutlined: <SaveOutlined />,
  ReloadOutlined: <ReloadOutlined />,
  HddOutlined: <HddOutlined />,
  CloudServerOutlined: <CloudServerOutlined />,
  AppstoreOutlined: <AppstoreOutlined />,
  MessageOutlined: <MessageOutlined />,
  CodeOutlined: <CodeOutlined />,
  LineChartOutlined: <LineChartOutlined />,
  DashboardOutlined: <DashboardOutlined />,
  SafetyCertificateOutlined: <SafetyCertificateOutlined />,
  ControlOutlined: <ControlOutlined />,
  FolderOpenOutlined: <FolderOpenOutlined />,
  ApartmentOutlined: <ApartmentOutlined />,
  GlobalOutlined: <GlobalOutlined />,
  DesktopOutlined: <DesktopOutlined />,
  SecurityScanOutlined: <SecurityScanOutlined />,
  NodeIndexOutlined: <NodeIndexOutlined />,
  DeploymentUnitOutlined: <DeploymentUnitOutlined />
};

const ICON_OPTIONS = [
  { value: 'ApiOutlined', label: 'API图标' },
  { value: 'ClusterOutlined', label: '集群图标' },
  { value: 'DatabaseOutlined', label: '数据库图标' },
  { value: 'SettingOutlined', label: '设置图标' },
  { value: 'HddOutlined', label: '服务器图标' },
  { value: 'CloudServerOutlined', label: '云服务器图标' },
  { value: 'AppstoreOutlined', label: '容器/应用图标' },
  { value: 'MessageOutlined', label: '消息/队列图标' },
  { value: 'CodeOutlined', label: '代码/服务图标' },
  { value: 'LineChartOutlined', label: '图表/监控图标' },
  { value: 'DashboardOutlined', label: '仪表盘图标' },
  { value: 'SafetyCertificateOutlined', label: '安全/防火墙图标' },
  { value: 'ControlOutlined', label: '控制/均衡图标' },
  { value: 'FolderOpenOutlined', label: '文件夹/存储图标' },
  { value: 'ApartmentOutlined', label: '结构/逻辑图标' },
  { value: 'GlobalOutlined', label: '全球/网络图标' },
  { value: 'DesktopOutlined', label: '桌面/终端图标' },
  { value: 'SecurityScanOutlined', label: '扫描/安全图标' },
  { value: 'NodeIndexOutlined', label: '节点/索引图标' },
  { value: 'DeploymentUnitOutlined', label: '部署/单元图标' },
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

const SHAPE_OPTIONS = COMMON_SHAPES;

// 形状预览组件 (已迁移至 NodeShape)
const ShapePreview = ({ shape, color = '#1890ff' }) => {
  return <NodeShape shape={shape} color={color} size={24} strokeWidth={3} isPreview />;
};

const ArchNodeType = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [searchForm] = Form.useForm();
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    typeName: undefined,
    category: undefined
  });

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalForm] = Form.useForm();
  const [editingId, setEditingId] = useState(null);
  const [editingRecord, setEditingRecord] = useState(null);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      const res = await getDicts('arch_node_category');
      if (res.code === 200) {
        setCategoryOptions(res.data || []);
      }
    } catch (error) {
      console.error('Fetch categories error', error);
    }
  };

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

  const extractShape = (defaultStyle) => {
    const styleObj = safeJsonParse(defaultStyle);
    return styleObj?.shape || 'rectangle';
  };

  const buildDefaultStyle = (baseColor, shape, existingDefaultStyle) => {
    const prev = safeJsonParse(existingDefaultStyle) || {};
    const next = {
      ...prev,
      backgroundColor: baseColor,
      borderColor: baseColor,
      shape: shape,
    };
    if (next.borderWidth == null) next.borderWidth = 2;
    if (next.color == null) next.color = '#ffffff';

    switch (shape) {
      case 'circle':
        next.borderRadius = '50%';
        next.width = 80;
        next.height = 80;
        break;
      case 'square':
        next.borderRadius = 4;
        next.width = 80;
        next.height = 80;
        break;
      case 'ellipse':
        next.borderRadius = '50%';
        next.width = 100;
        next.height = 60;
        break;
      case 'diamond':
        next.transform = 'rotate(45deg)';
        next.width = 80;
        next.height = 80;
        break;
      case 'rounded-rectangle':
        next.borderRadius = 12;
        next.width = 120;
        next.height = 50;
        break;
      case 'parallelogram':
      case 'trapezoid':
      case 'triangle':
      case 'hexagon':
      case 'pentagon':
      case 'octagon':
        next.width = 100;
        next.height = 60;
        break;
      case 'cylinder':
        next.width = 80;
        next.height = 100;
        break;
      case 'cloud':
        next.width = 120;
        next.height = 80;
        break;
      case 'actor':
        next.width = 60;
        next.height = 100;
        break;
      case 'logic-and':
      case 'logic-or':
      case 'logic-not':
        next.width = 100;
        next.height = 60;
        break;
      case 'rectangle':
      default:
        next.borderRadius = 4;
        next.width = 120;
        next.height = 50;
        break;
    }
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
          shape: extractShape(item.defaultStyle),
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
    searchForm.resetFields();
    setQueryParams({ pageNum: 1, pageSize: 10, typeName: undefined, category: undefined });
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
      category: record.category,
      remark: record.remark,
      icon: record.icon || 'ApiOutlined',
      color: extractPrimaryColor(record.defaultStyle),
      shape: extractShape(record.defaultStyle),
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
        category: values.category,
        icon: values.icon,
        remark: values.remark,
        defaultStyle: buildDefaultStyle(values.color, values.shape, editingRecord?.defaultStyle),
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
      title: '分类', 
      dataIndex: 'category', 
      key: 'category',
      render: (text) => {
        const option = categoryOptions.find(opt => opt.dictValue === text);
        return <Tag color="cyan">{option ? option.dictLabel : text || '未分类'}</Tag>;
      }
    },
    { 
      title: '图标', 
      dataIndex: 'icon', 
      key: 'icon',
      render: (text) => {
        return (
          <Space>
            {ICON_MAP[text] || <BrandIcon type={text} size="16px" /> || <ApiOutlined />}
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
      title: '形状', 
      dataIndex: 'shape', 
      key: 'shape',
      render: (text, record) => {
        const shapeOption = SHAPE_OPTIONS.find(option => option.value === text);
        return (
          <Space>
            <ShapePreview shape={text} color={record.color} />
            <Tag color="geekblue">{shapeOption ? shapeOption.label : text}</Tag>
          </Space>
        );
      }
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
          <Form.Item name="typeName" label="类型名称">
            <Input placeholder="请输入类型名称" allowClear />
          </Form.Item>
          <Form.Item name="category" label="节点分类">
            <Select placeholder="请选择节点分类" allowClear style={{ width: 150 }}>
              {categoryOptions.map(opt => (
                <Option key={opt.dictValue} value={opt.dictValue}>{opt.dictLabel}</Option>
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
            label="节点分类"
            name="category"
            rules={[{ required: true, message: '请选择节点分类' }]}
            tooltip="节点所属的分类"
          >
            <Select placeholder="请选择节点分类">
              {categoryOptions.map(opt => (
                <Option key={opt.dictValue} value={opt.dictValue}>{opt.dictLabel}</Option>
              ))}
            </Select>
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
            <Select showSearch>
              <OptGroup label="官方品牌图标">
                {BRAND_ICON_OPTIONS.map((item) => (
                  <Option key={item.value} value={item.value}>
                    <Space>
                      <BrandIcon type={item.value} size="16px" />
                      <span>{item.label}</span>
                    </Space>
                  </Option>
                ))}
              </OptGroup>
              <OptGroup label="通用功能图标">
                {ICON_OPTIONS.map((item) => (
                  <Option key={item.value} value={item.value}>
                    <Space>
                      {ICON_MAP[item.value]}
                      <span>{item.label}</span>
                    </Space>
                  </Option>
                ))}
              </OptGroup>
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

          <Form.Item
            label="节点形状"
            name="shape"
            initialValue="rectangle"
            tooltip="选择节点的形状"
          >
            <Select>
              {SHAPE_OPTIONS.map((item) => (
                <Option key={item.value} value={item.value}>
                  <Space>
                    <ShapePreview shape={item.value} color={modalForm.getFieldValue('color')} />
                    <span>{item.label}</span>
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
            <div style={{ marginBottom: '4px' }}>• 节点颜色：选择节点的主题颜色</div>
            <div>• 节点形状：选择节点的显示形状</div>
          </div>
        </Form>
      </Modal>
    </div>
  );
};

export default ArchNodeType;
