import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, message, Popconfirm, Tooltip } from 'antd';
import { SearchOutlined, ReloadOutlined, DeleteOutlined, ColumnHeightOutlined } from '@ant-design/icons';
import { list, forceLogout } from '../../../api/monitor/online';
import dayjs from 'dayjs';

const Online = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    ipaddr: undefined,
    userName: undefined
  });

  const [form] = Form.useForm();

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await list(queryParams);
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
      ipaddr: undefined,
      userName: undefined,
      pageNum: 1
    });
  };

  const handleForceLogout = async (tokenId) => {
    try {
      await forceLogout(tokenId);
      message.success('强退成功');
      fetchData();
    } catch (error) {
      message.error('强退失败');
    }
  };

  const columns = [
    { title: '序号', dataIndex: 'index', key: 'index', align: 'center', width: 60, render: (text, record, index) => (queryParams.pageNum - 1) * queryParams.pageSize + index + 1 },
    { title: '会话编号', dataIndex: 'tokenId', key: 'tokenId', align: 'center', width: 250, ellipsis: true },
    { title: '用户名称', dataIndex: 'userName', key: 'userName', align: 'center', width: 120 },
    { title: '主机', dataIndex: 'ipaddr', key: 'ipaddr', align: 'center', width: 120 },
    { title: '登录地点', dataIndex: 'loginLocation', key: 'loginLocation', align: 'center', width: 150, ellipsis: true },
    { title: '浏览器', dataIndex: 'browser', key: 'browser', align: 'center', width: 120, ellipsis: true },
    { title: '操作系统', dataIndex: 'os', key: 'os', align: 'center', width: 120, ellipsis: true },
    { 
        title: '登录时间', 
        dataIndex: 'loginTime', 
        key: 'loginTime', 
        align: 'center', 
        width: 180,
        render: (text) => text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : '-'
    },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Popconfirm title="确定强退该用户吗？" onConfirm={() => handleForceLogout(record.tokenId)}>
            <Button type="text" icon={<DeleteOutlined />} danger>强退</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card">
        <Form form={form} layout="inline">
          <Form.Item name="ipaddr" label="登录地址">
            <Input placeholder="请输入登录地址" allowClear />
          </Form.Item>
          <Form.Item name="userName" label="用户名称">
            <Input placeholder="请输入用户名称" allowClear />
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
             <Tooltip title="刷新">
                <Button icon={<ReloadOutlined />} onClick={fetchData} shape="circle" />
             </Tooltip>
             <Tooltip title="密度">
                <Button icon={<ColumnHeightOutlined />} shape="circle" />
             </Tooltip>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={data}
          rowKey="tokenId"
          loading={loading}
          scroll={{ x: 1220 }}
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
    </div>
  );
};

export default Online;
