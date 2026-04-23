import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, message, Popconfirm, Tag, Tooltip, Row, Col, Radio, Dropdown } from 'antd';
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined, ColumnHeightOutlined } from '@ant-design/icons';
import { ResizableTitle } from '../../../components/ResizableTable';
import { listNotice, getNotice, addNotice, updateNotice, delNotice } from '../../../api/notice';
import { getDicts } from '../../../api/dict/data';

const { Option } = Select;

const Notice = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tableSize, setTableSize] = useState('large');
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    noticeTitle: undefined,
    createBy: undefined,
    noticeType: undefined
  });
  const [sysNoticeType, setSysNoticeType] = useState([]);
  const [sysNoticeStatus, setSysNoticeStatus] = useState([]);

  const [form] = Form.useForm();
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增公告');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);

  const buildColumns = (prevColumns = []) => {
    const getWidth = (key, defaultWidth) => {
      const prev = prevColumns.find((c) => c.key === key);
      return prev?.width ?? defaultWidth;
    };

    return [
      { title: '序号', dataIndex: 'noticeId', key: 'noticeId', align: 'center', width: getWidth('noticeId', 80) },
      { title: '公告标题', dataIndex: 'noticeTitle', key: 'noticeTitle', align: 'center', width: getWidth('noticeTitle', 250), ellipsis: true },
      {
        title: '公告类型',
        dataIndex: 'noticeType',
        key: 'noticeType',
        align: 'center',
        width: getWidth('noticeType', 120),
        render: (text) => {
          const dict = sysNoticeType.find((d) => d.dictValue === text);
          return <Tag>{dict ? dict.dictLabel : text}</Tag>;
        }
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        align: 'center',
        width: getWidth('status', 100),
        render: (text) => (
          <Tag color={text === '0' ? 'success' : 'error'}>
            {text === '0' ? '正常' : '关闭'}
          </Tag>
        )
      },
      { title: '创建者', dataIndex: 'createBy', key: 'createBy', align: 'center', width: getWidth('createBy', 100), ellipsis: true },
      { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: getWidth('createTime', 160) },
      { title: '更新者', dataIndex: 'updateBy', key: 'updateBy', align: 'center', width: getWidth('updateBy', 100), ellipsis: true },
      { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: getWidth('updateTime', 160) },
      {
        title: '操作',
        key: 'action',
        align: 'center',
        width: getWidth('action', 250),
        fixed: 'right',
        render: (_, record) => (
          <Space size="middle">
            <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
            <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.noticeId)}>
              <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
            </Popconfirm>
          </Space>
        ),
      },
    ];
  };

  const [columns, setColumns] = useState(() => buildColumns());

  useEffect(() => {
    setColumns((prev) => buildColumns(prev));
  }, [sysNoticeType]);

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
      const res = await listNotice(queryParams);
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
    getDicts('sys_notice_type').then(res => res.code === 200 && setSysNoticeType(res.data));
    getDicts('sys_notice_status').then(res => res.code === 200 && setSysNoticeStatus(res.data));
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
      noticeTitle: undefined,
      createBy: undefined,
      noticeType: undefined,
      pageNum: 1
    });
  };

  const handleAdd = () => {
    setModalTitle('新增公告');
    setCurrentId(null);
    modalForm.resetFields();
    setIsModalOpen(true);
  };

  const handleEdit = async (record) => {
    setModalTitle('修改公告');
    setCurrentId(record.noticeId);
    try {
        const res = await getNotice(record.noticeId);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            setIsModalOpen(true);
        }
    } catch (error) {
        console.error(error);
        message.error('获取详情失败');
    }
  };

  const handleDelete = async (id) => {
    try {
      await delNotice(id);
      message.success('删除成功');
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('删除失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentId) {
        await updateNotice({ ...values, noticeId: currentId });
        message.success('更新成功');
      } else {
        await addNotice(values);
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
    <div className="app-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item name="noticeTitle" label="公告标题">
                <Input placeholder="请输入公告标题" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="createBy" label="操作人员">
                <Input placeholder="请输入操作人员" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="noticeType" label="公告类型">
                <Select placeholder="公告类型" allowClear>
                  {sysNoticeType.map(dict => (
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

      <Card bordered={false} className="table-card">
        <div className="table-toolbar">
          <Space size="middle">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
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
          components={{
            header: {
              cell: ResizableTitle,
            },
          }}
          columns={resizableColumns}
          dataSource={data}
          rowKey="noticeId"
          loading={loading}
          size={tableSize}
          scroll={{ x: 1230 }}
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
      </Card>

      <Modal
        title={modalTitle}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        destroyOnClose
        width={800}
      >
        <Form form={modalForm} layout="vertical">
          <Row gutter={24}>
              <Col span={12}>
                  <Form.Item name="noticeTitle" label="公告标题" rules={[{ required: true, message: '请输入公告标题' }]}>
                    <Input placeholder="请输入公告标题" />
                  </Form.Item>
              </Col>
              <Col span={12}>
                   <Form.Item name="noticeType" label="公告类型" rules={[{ required: true, message: '请选择公告类型' }]}>
                     <Select placeholder="请选择">
                        {sysNoticeType.map(dict => (
                            <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                        ))}
                     </Select>
                  </Form.Item>
              </Col>
              <Col span={24}>
                  <Form.Item name="status" label="状态" initialValue="0">
                     <Radio.Group>
                        {sysNoticeStatus.map(dict => (
                            <Radio key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Radio>
                        ))}
                     </Radio.Group>
                  </Form.Item>
              </Col>
              <Col span={24}>
                  <Form.Item name="noticeContent" label="内容">
                    <Input.TextArea rows={4} placeholder="请输入内容" />
                  </Form.Item>
              </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};

// Export default
export default Notice;