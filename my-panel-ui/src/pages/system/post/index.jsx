import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, InputNumber, Radio, message, Popconfirm, Tag, Tooltip, Dropdown, Row, Col, Spin, Pagination } from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ColumnHeightOutlined,
  HolderOutlined
} from '@ant-design/icons';
import { ResizableTitle } from '../../../components/ResizableTable';
import { DndContext, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import { restrictToVerticalAxis } from '@dnd-kit/modifiers';
import {
  arrayMove,
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { listPost, getPost, addPost, updatePost, delPost, sortPost } from '../../../api/post';
import { getDicts } from '../../../api/dict/data';
import './Post.scss';

const SortableRow = (props) => {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: props['data-row-key'],
  });
  const style = {
    ...props.style,
    transform: CSS.Translate.toString(transform),
    transition,
    cursor: 'move',
    ...(isDragging ? { position: 'relative', zIndex: 9999 } : {}),
  };
  return <tr {...props} ref={setNodeRef} style={style} {...attributes} {...listeners} />;
};

const { Option } = Select;

const Post = () => {
  const [data, setData] = useState([]);
  const [originalData, setOriginalData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tableSize, setTableSize] = useState('large');
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
  const [dragLoading, setDragLoading] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8,
      },
    })
  );

  // Resizable Columns State
  const [columns, setColumns] = useState([
    {
      title: '排序',
      key: 'drag',
      width: 60,
      align: 'center',
      render: () => <HolderOutlined style={{ cursor: 'move', color: '#999' }} />,
    },
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
      width: 200,
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

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listPost(queryParams);
      if (res.code === 200) {
        setData(res.data.rows);
        setOriginalData(res.data.rows);
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
  const handleAdd = async () => {
    setModalTitle('新增岗位');
    setCurrentId(null);
    modalForm.resetFields();
    
    // Calculate auto sort order
    let maxSort = 0;
    try {
      const res = await listPost({ pageNum: 1, pageSize: 1000 });
      if (res.code === 200) {
        const allPosts = res.data.rows || [];
        if (allPosts.length > 0) {
          maxSort = Math.max(...allPosts.map(item => item.postSort || 0));
        }
      }
    } catch (e) {
      console.error('Calculate sort order failed:', e);
    }

    modalForm.setFieldsValue({ 
      postSort: maxSort + 1
    });
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

  // Handle drag end
  const onDragEnd = async ({ active, over }) => {
    if (active.id !== over?.id) {
      const oldIndex = data.findIndex((i) => i.postId === active.id);
      const newIndex = data.findIndex((i) => i.postId === over.id);
      const newData = arrayMove(data, oldIndex, newIndex);
      
      // Immediately update the UI to show the new order
      setData(newData);

      setDragLoading(true);
      
      try {
        // Calculate sort values based on current page data
        const sortData = newData.map((item, index) => ({
          postId: item.postId,
          postSort: index + 1
        }));

        const sortRes = await sortPost(sortData);
        if (sortRes.code === 200) {
          message.success('排序更新成功');
          await fetchData();
        } else {
          message.error(sortRes.msg || '排序更新失败');
          // Revert to original data on error
          setData(originalData);
        }
      } catch (error) {
        console.error(error);
        message.error('排序更新失败，请重试');
        // Revert to original data on error
        setData(originalData);
      } finally {
        setDragLoading(false);
      }
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

  const resizableColumns = columns.map((col, index) => ({
    ...col,
    onHeaderCell: (column) => ({
      width: column.width,
      onResize: handleResize(index),
    }),
  }));

  return (
    <div className="post-page-container">
      {dragLoading && (
        <div className="drag-loading-overlay">
          <div className="drag-loading-content">
            <Spin size="large" />
            <span className="drag-loading-text">正在保存排序...</span>
          </div>
        </div>
      )}
      <Card bordered={false} className="search-card" style={{ marginBottom: 16, flexShrink: 0 }}>
        <Form form={form} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item name="postCode" label="岗位编码">
                <Input placeholder="请输入岗位编码" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="postName" label="岗位名称">
                <Input placeholder="请输入岗位名称" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择状态" allowClear>
                  {sysNormalDisable.map(dict => (
                    <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                  ))}
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

      <Card bordered={false} className="table-card" style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minHeight: 0 }}>
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

        <div className="post-table-container">
          <DndContext sensors={sensors} modifiers={[restrictToVerticalAxis]} onDragEnd={onDragEnd}>
            <SortableContext items={data.map(item => item.postId)} strategy={verticalListSortingStrategy}>
              <Table
                rowSelection={rowSelection}
                components={{
                  header: {
                    cell: ResizableTitle,
                  },
                  body: {
                    row: SortableRow,
                  },
                }}
                columns={resizableColumns}
                dataSource={data}
                rowKey="postId"
                loading={loading}
                size={tableSize}
                scroll={{ x: 'max-content', y: 'calc(100vh - 550px)' }}
                pagination={false}
              />
            </SortableContext>
          </DndContext>
          <div className="fixed-pagination-bar">
            <Pagination
              current={queryParams.pageNum}
              pageSize={queryParams.pageSize}
              total={total}
              showTotal={(t) => `共 ${t} 条`}
              onChange={(pageNum, pageSize) => setQueryParams({ ...queryParams, pageNum, pageSize })}
              showSizeChanger
              pageSizeOptions={['10', '20', '50', '100']}
              showQuickJumper
              size="default"
            />
          </div>
        </div>
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
          <Form.Item name="postSort" label="岗位排序">
            <InputNumber min={0} style={{ width: '100%' }} disabled placeholder="拖拽排序自动计算" />
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