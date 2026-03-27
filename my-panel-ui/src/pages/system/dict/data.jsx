import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, InputNumber, Radio, message, Popconfirm, Tag, Tooltip, Dropdown, Row, Col } from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ColumnHeightOutlined,
  ArrowLeftOutlined
} from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import { listData, getData, addData, updateData, delData } from '../../../api/dict/data';
import { getType, optionselect } from '../../../api/dict/type';
import './Dict.scss';

const { Option } = Select;

const DictData = () => {
  const { dictType } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tableSize, setTableSize] = useState('large');
  const [typeOptions, setTypeOptions] = useState([]);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    dictName: undefined,
    dictType: dictType,
    status: undefined
  });
  
  const [form] = Form.useForm();
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增字典数据');
  const [modalForm] = Form.useForm();
  const [currentCode, setCurrentCode] = useState(null);

  useEffect(() => {
      // Get all dict types for select
      optionselect().then(res => {
          if (res.code === 200) {
              setTypeOptions(res.data);
          }
      });
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listData(queryParams);
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
    if (dictType) {
        form.setFieldsValue({ dictType: dictType });
        setQueryParams(prev => ({ ...prev, dictType: dictType }));
    }
  }, [dictType]);

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
    form.setFieldsValue({ dictType: dictType }); // Keep current dict type
    setQueryParams({
      ...queryParams,
      dictLabel: undefined,
      status: undefined,
      dictType: dictType,
      pageNum: 1
    });
  };
  
  const onSelectChange = (newSelectedRowKeys) => {
    setSelectedRowKeys(newSelectedRowKeys);
  };

  const rowSelection = {
    selectedRowKeys,
    onChange: onSelectChange,
  };

  // Add Data
  const handleAdd = () => {
    setModalTitle('新增字典数据');
    setCurrentCode(null);
    modalForm.resetFields();
    modalForm.setFieldsValue({ dictType: queryParams.dictType });
    setIsModalOpen(true);
  };

  // Edit Data
  const handleEdit = async (record) => {
    setModalTitle('编辑字典数据');
    setCurrentCode(record.dictCode);
    try {
        const res = await getData(record.dictCode);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取字典数据详情失败');
    }
  };

  // Delete Data
  const handleDelete = async (dictCode) => {
    try {
      await delData(dictCode);
      message.success('删除成功');
      fetchData();
      setSelectedRowKeys([]);
    } catch (error) {
      message.error('删除失败');
    }
  };
  
  const handleBatchDelete = async () => {
      if (!selectedRowKeys.length) return;
      try {
          await delData(selectedRowKeys.join(','));
          message.success('删除成功');
          fetchData();
          setSelectedRowKeys([]);
      } catch (error) {
          message.error('删除失败');
      }
  };

  // Handle Form Submit
  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentCode) {
        await updateData({ ...values, dictCode: currentCode });
        message.success('更新成功');
      } else {
        await addData(values);
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
    { title: '字典编码', dataIndex: 'dictCode', key: 'dictCode', align: 'center', width: 100 },
    { title: '字典标签', dataIndex: 'dictLabel', key: 'dictLabel', align: 'center', width: 150, ellipsis: true },
    { title: '字典键值', dataIndex: 'dictValue', key: 'dictValue', align: 'center', width: 150, ellipsis: true },
    { title: '字典排序', dataIndex: 'dictSort', key: 'dictSort', align: 'center', width: 100 },
    { 
        title: '状态', 
        dataIndex: 'status', 
        key: 'status', 
        align: 'center',
        width: 100,
        render: (text) => (
            <Tag color={text === '0' ? 'success' : 'error'}>
                {text === '0' ? '正常' : '停用'}
            </Tag>
        )
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', align: 'center', width: 200, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 180 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.dictCode)}>
            <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="dict-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item name="dictType" label="字典名称">
                <Select placeholder="请选择字典名称" allowClear>
                  {typeOptions.map(item => (
                    <Option key={item.dictId} value={item.dictType}>{item.dictName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="dictLabel" label="字典标签">
                <Input placeholder="请输入字典标签" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="status" label="状态">
                <Select placeholder="数据状态" allowClear>
                  <Option value="0">正常</Option>
                  <Option value="1">停用</Option>
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
            <Button 
                danger 
                icon={<DeleteOutlined />} 
                disabled={selectedRowKeys.length === 0} 
                onClick={handleBatchDelete}
            >
                批量删除
            </Button>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/system/dict')}>返回</Button>
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
          rowSelection={rowSelection}
          columns={columns}
          dataSource={data}
          rowKey="dictCode"
          loading={loading}
          size={tableSize}
          scroll={{ x: 1140 }}
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
          <Form.Item name="dictType" label="字典类型" rules={[{ required: true, message: '请输入字典类型' }]}>
            <Input disabled />
          </Form.Item>
          <Form.Item name="dictLabel" label="数据标签" rules={[{ required: true, message: '请输入数据标签' }]}>
             <Input placeholder="请输入数据标签" />
          </Form.Item>
          <Form.Item name="dictValue" label="数据键值" rules={[{ required: true, message: '请输入数据键值' }]}>
             <Input placeholder="请输入数据键值" />
          </Form.Item>
          <Form.Item name="dictSort" label="显示排序" rules={[{ required: true, message: '请输入显示排序' }]}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="0">
            <Radio.Group>
                <Radio value="0">正常</Radio>
                <Radio value="1">停用</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="请输入备注" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DictData;
