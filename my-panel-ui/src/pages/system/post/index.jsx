import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, InputNumber, Radio, message, Popconfirm, Tag, Tooltip } from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ColumnHeightOutlined
} from '@ant-design/icons';
import { listPost, getPost, addPost, updatePost, delPost } from '../../../api/post';
import { getDicts } from '../../../api/dict/data';
import './Post.scss';

const { Option } = Select;

const Post = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    postCode: undefined,
    postName: undefined,
    status: undefined
  });
  
  const [form] = Form.useForm();
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增岗位');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);
  const [sysNormalDisable, setSysNormalDisable] = useState([]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listPost(queryParams);
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
    getDicts('sys_normal_disable').then(res => {
      if (res.code === 200) {
        setSysNormalDisable(res.data);
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
      postCode: undefined,
      postName: undefined,
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

  // Add Post
  const handleAdd = () => {
    setModalTitle('新增岗位');
    setCurrentId(null);
    modalForm.resetFields();
    setIsModalOpen(true);
  };

  // Edit Post
  const handleEdit = async (record) => {
    setModalTitle('编辑岗位');
    setCurrentId(record.postId);
    try {
        const res = await getPost(record.postId);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取岗位详情失败');
    }
  };

  // Delete Post
  const handleDelete = async (id) => {
    try {
      await delPost(id);
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
          await delPost(selectedRowKeys.join(','));
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
      if (currentId) {
        await updatePost({ ...values, postId: currentId });
        message.success('更新成功');
      } else {
        await addPost(values);
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
    { title: '岗位编号', dataIndex: 'postId', key: 'postId', align: 'center', width: 100 },
    { title: '岗位编码', dataIndex: 'postCode', key: 'postCode', align: 'center', width: 150, ellipsis: true },
    { title: '岗位名称', dataIndex: 'postName', key: 'postName', align: 'center', width: 150, ellipsis: true },
    { title: '岗位排序', dataIndex: 'postSort', key: 'postSort', align: 'center', width: 100 },
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
    { title: '创建者', dataIndex: 'createBy', key: 'createBy', align: 'center', width: 100, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 160 },
    { title: '更新者', dataIndex: 'updateBy', key: 'updateBy', align: 'center', width: 100, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: 160 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.postId)}>
            <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="post-container">
      <Card bordered={false} className="search-card">
        <Form form={form} layout="inline">
          <Form.Item name="postCode" label="岗位编码">
            <Input placeholder="请输入岗位编码" allowClear />
          </Form.Item>
          <Form.Item name="postName" label="岗位名称">
            <Input placeholder="请输入岗位名称" allowClear />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择状态" allowClear style={{ width: 120 }}>
              {sysNormalDisable.map(dict => (
                <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
              ))}
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
             <Tooltip title="刷新">
                <Button icon={<ReloadOutlined />} onClick={fetchData} shape="circle" />
             </Tooltip>
             <Tooltip title="密度">
                <Button icon={<ColumnHeightOutlined />} shape="circle" />
             </Tooltip>
          </Space>
        </div>

        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={data}
          rowKey="postId"
          loading={loading}
          scroll={{ x: 1000 }}
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
          <Form.Item name="postName" label="岗位名称" rules={[{ required: true, message: '请输入岗位名称' }]}>
            <Input placeholder="请输入岗位名称" />
          </Form.Item>
          <Form.Item name="postCode" label="岗位编码" rules={[{ required: true, message: '请输入岗位编码' }]}>
             <Input placeholder="请输入岗位编码" />
          </Form.Item>
          <Form.Item name="postSort" label="岗位排序" rules={[{ required: true, message: '请输入岗位排序' }]}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="岗位状态" initialValue="0">
            <Radio.Group>
              {sysNormalDisable.map(dict => (
                <Radio key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Radio>
              ))}
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

export default Post;
