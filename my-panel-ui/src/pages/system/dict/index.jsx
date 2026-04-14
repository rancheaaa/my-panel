import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, Radio, message, Popconfirm, Tag, Tooltip, Dropdown, Row, Col } from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ColumnHeightOutlined,
  UnorderedListOutlined,
  SyncOutlined
} from '@ant-design/icons';
import { ResizableTitle } from '../../../components/ResizableTable';
import { useNavigate } from 'react-router-dom';
import { listType, getType, addType, updateType, delType, refreshCache } from '../../../api/dict/type';
import './Dict.scss';

const { Option } = Select;

const Dict = () => {
  const navigate = useNavigate();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tableSize, setTableSize] = useState('large');
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    dictName: undefined,
    dictType: undefined,
    status: undefined
  });
  
  const [form] = Form.useForm();
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增字典类型');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);

  // Resizable Columns State
  const [columns, setColumns] = useState([
    { title: '字典编号', dataIndex: 'dictId', key: 'dictId', align: 'center', width: 100 },
    { title: '字典名称', dataIndex: 'dictName', key: 'dictName', align: 'center', width: 200, ellipsis: true },
    { 
        title: '字典类型', 
        dataIndex: 'dictType', 
        key: 'dictType', 
        align: 'center',
        width: 200,
        ellipsis: true,
        render: (text) => (
            <a onClick={() => navigate(`/system/dict-data/${text}`)}>{text}</a>
        )
    },
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
    { title: '创建者', dataIndex: 'createBy', key: 'createBy', align: 'center', width: 100, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 180 },
    { title: '更新者', dataIndex: 'updateBy', key: 'updateBy', align: 'center', width: 100, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: 180 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
          <Button type="text" icon={<UnorderedListOutlined />} onClick={() => navigate(`/system/dict-data/${record.dictType}`)} style={{ color: '#1890ff' }}>列表</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.dictId)}>
            <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]);

  const handleResize = (index) => (e, { size }) => {
    setColumns((prevColumns) => {
      const nextColumns = [...prevColumns];
      nextColumns[index] = {
        ...nextColumns[index],
        width: size.width,
      };
      return nextColumns;
    });
  };

  const resizableColumns = columns.map((col, index) => ({
    ...col,
    onHeaderCell: (column) => ({
      width: column.width,
      onResize: handleResize(index),
    }),
  }));

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listType(queryParams);
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
      dictName: undefined,
      dictType: undefined,
      status: undefined,
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

  // Add Type
  const handleAdd = () => {
    setModalTitle('新增字典类型');
    setCurrentId(null);
    modalForm.resetFields();
    setIsModalOpen(true);
  };

  // Edit Type
  const handleEdit = async (record) => {
    setModalTitle('编辑字典类型');
    setCurrentId(record.dictId);
    try {
        const res = await getType(record.dictId);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取字典详情失败');
    }
  };

  // Delete Type
  const handleDelete = async (id) => {
    try {
      await delType(id);
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
          await delType(selectedRowKeys.join(','));
          message.success('删除成功');
          fetchData();
          setSelectedRowKeys([]);
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

  // Handle Form Submit
  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentId) {
        await updateType({ ...values, dictId: currentId });
        message.success('更新成功');
      } else {
        await addType(values);
        message.success('新增成功');
      }
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('操作失败');
    }
  };

  return (
    <div className="dict-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item name="dictName" label="字典名称">
                <Input placeholder="请输入字典名称" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="dictType" label="字典类型">
                <Input placeholder="请输入字典类型" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择状态" allowClear style={{ width: '100%' }}>
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
            <Button 
                danger 
                icon={<SyncOutlined />} 
                onClick={handleRefreshCache}
            >
                刷新缓存
            </Button>
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
          components={{
            header: {
              cell: ResizableTitle,
            },
          }}
          columns={resizableColumns}
          dataSource={data}
          rowKey="dictId"
          loading={loading}
          size={tableSize}
          scroll={{ x: 1180 }}
          pagination={{
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            total: total,
            showTotal: (total, range) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
                setQueryParams(prev => ({ ...prev, pageNum: page, pageSize }));
            },
            position: ['bottomRight'],
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100']
          }}
        />

        <Modal
          title={modalTitle}
          open={isModalOpen}
          onOk={handleModalOk}
          onCancel={() => setIsModalOpen(false)}
          destroyOnClose
        >
          <Form form={modalForm} layout="vertical">
            <Form.Item name="dictName" label="字典名称" rules={[{ required: true, message: '请输入字典名称' }]}>
              <Input placeholder="请输入字典名称" />
            </Form.Item>
            <Form.Item name="dictType" label="字典类型" rules={[{ required: true, message: '请输入字典类型' }]}>
               <Input placeholder="请输入字典类型" />
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
      </Card>
    </div>
  );
};

export default Dict;