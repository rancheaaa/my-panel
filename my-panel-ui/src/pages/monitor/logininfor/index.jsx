import React, { useState, useEffect } from 'react';
import { Card, Table, Form, Input, Select, Button, DatePicker, Space, Row, Col, message, Popconfirm, Tag, Tooltip, Dropdown } from 'antd';
import zhCN from 'antd/es/locale/zh_CN';
import { SearchOutlined, ReloadOutlined, DeleteOutlined, ClearOutlined, DownloadOutlined, UnlockOutlined, ColumnHeightOutlined, DownOutlined, UpOutlined } from '@ant-design/icons';
import { ResizableTitle } from '../../../components/ResizableTable';
import { list, delLogininfor, cleanLogininfor, unlockLogininfor, exportLogininfor } from '../../../api/monitor/logininfor';
import request from '../../../utils/request';

const { RangePicker } = DatePicker;
const { Option } = Select;

const Logininfor = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState([]);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [tableSize, setTableSize] = useState('large');
  const [expand, setExpand] = useState(true);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    ipaddr: undefined,
    userName: undefined,
    status: undefined,
  });
  const [dateRange, setDateRange] = useState([]);

  // Dictionaries
  const [sysCommonStatus, setSysCommonStatus] = useState([]);

  // Modal (Optional for details, but helpful)
  // Usually login logs don't have complex details hidden, but we can show full user agent etc.
  
  useEffect(() => {
    fetchDicts();
    fetchData();
  }, []);

  const fetchDicts = async () => {
    try {
        const resStatus = await request({ url: '/system/dict/data/type/sys_common_status', method: 'get' });
        if (resStatus.code === 200) setSysCommonStatus(resStatus.data);
    } catch (e) {
        console.error(e);
    }
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const params = {
        ...queryParams,
        beginTime: dateRange[0] ? dateRange[0].format('YYYY-MM-DD HH:mm:ss') : undefined,
        endTime: dateRange[1] ? dateRange[1].format('YYYY-MM-DD HH:mm:ss') : undefined,
      };
      const res = await list(params);
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

  const handleSearch = () => {
    setQueryParams({ ...queryParams, pageNum: 1 });
    fetchData();
  };

  const handleReset = () => {
    setQueryParams({
      pageNum: 1,
      pageSize: 10,
      ipaddr: undefined,
      userName: undefined,
      status: undefined,
    });
    setDateRange([]);
    fetchData();
  };

  const handleDelete = async (infoIds) => {
    try {
      await delLogininfor(infoIds);
      message.success('删除成功');
      fetchData();
      setSelectedRowKeys([]);
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleClean = async () => {
    try {
      await cleanLogininfor();
      message.success('清空成功');
      fetchData();
    } catch (error) {
      message.error('清空失败');
    }
  };

  const handleUnlock = async (userName) => {
      try {
          await unlockLogininfor(userName);
          message.success('用户' + userName + '解锁成功');
      } catch (error) {
          message.error('解锁失败');
      }
  };

  const handleExport = () => {
    message.success('正在导出数据，请稍候...');
    exportLogininfor(queryParams);
  };

  const buildColumns = (prevColumns = []) => {
    const getWidth = (key, defaultWidth) => {
      const prev = prevColumns.find((c) => c.key === key);
      return prev?.width ?? defaultWidth;
    };

    return [
      { title: '访问编号', dataIndex: 'infoId', key: 'infoId', align: 'center', width: getWidth('infoId', 100) },
      { title: '用户名称', dataIndex: 'userName', key: 'userName', align: 'center', width: getWidth('userName', 120) },
      { title: '登录地址', dataIndex: 'ipaddr', key: 'ipaddr', align: 'center', width: getWidth('ipaddr', 150), ellipsis: true },
      { title: '登录地点', dataIndex: 'loginLocation', key: 'loginLocation', align: 'center', width: getWidth('loginLocation', 150), ellipsis: true },
      { title: '浏览器', dataIndex: 'browser', key: 'browser', align: 'center', width: getWidth('browser', 120), ellipsis: true },
      { title: '操作系统', dataIndex: 'os', key: 'os', align: 'center', width: getWidth('os', 120), ellipsis: true },
      {
        title: '登录状态',
        dataIndex: 'status',
        key: 'status',
        align: 'center',
        width: getWidth('status', 100),
        render: (text) => {
          const dict = sysCommonStatus.find(d => d.dictValue == text);
          return dict ? <Tag color={String(text) === '0' ? '#1890ff' : 'error'}>{dict.dictLabel}</Tag> : text;
        }
      },
      { title: '操作信息', dataIndex: 'msg', key: 'msg', align: 'center', width: getWidth('msg', 150), ellipsis: true },
      { title: '登录日期', dataIndex: 'loginTime', key: 'loginTime', align: 'center', width: getWidth('loginTime', 180) },
      {
        title: '操作',
        key: 'action',
        align: 'center',
        width: getWidth('action', 100),
        fixed: 'right',
        render: (_, record) => (
          <Space size="middle">
            {String(record.status) === '1' && (
              <Button
                type="link"
                icon={<UnlockOutlined />}
                onClick={() => handleUnlock(record.userName)}
              >
                解锁
              </Button>
            )}
          </Space>
        ),
      },
    ];
  };

  const [columns, setColumns] = useState(() => buildColumns());

  useEffect(() => {
    setColumns((prev) => buildColumns(prev));
  }, [sysCommonStatus]);

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

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card">
        <Form layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item label="登录地址">
                <Input 
                    placeholder="请输入登录地址" 
                    value={queryParams.ipaddr}
                    onChange={e => setQueryParams({ ...queryParams, ipaddr: e.target.value })}
                    onPressEnter={handleSearch}
                    allowClear
                />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item label="用户名称">
                <Input 
                    placeholder="请输入用户名称" 
                    value={queryParams.userName}
                    onChange={e => setQueryParams({ ...queryParams, userName: e.target.value })}
                    onPressEnter={handleSearch}
                    allowClear
                />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item label="状态">
                <Select
                    placeholder="登录状态"
                    value={queryParams.status}
                    onChange={val => setQueryParams({ ...queryParams, status: val })}
                    allowClear
                >
                    {sysCommonStatus.map(d => (
                        <Option key={d.dictValue} value={d.dictValue}>{d.dictLabel}</Option>
                    ))}
                </Select>
              </Form.Item>
            </Col>
            {expand && (
              <Col span={6}>
                <Form.Item label="登录时间">
                  <RangePicker 
                      value={dateRange} 
                      onChange={setDateRange} 
                      style={{ width: '100%' }}
                      locale={zhCN}
                      showTime
                      format="YYYY-MM-DD HH:mm:ss"
                  />
                </Form.Item>
              </Col>
            )}
            <Col span={24} style={{ textAlign: 'right', marginTop: '8px' }}>
              <Space>
                <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
                <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
                <Button 
                    type="link" 
                    onClick={() => setExpand(!expand)}
                    icon={expand ? <UpOutlined /> : <DownOutlined />}
                >
                  {expand ? '收起' : '展开'}
                </Button>
              </Space>
            </Col>
          </Row>
        </Form>
      </Card>

      <Card bordered={false} className="table-card">
        <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
            <Space>
                <Popconfirm 
                    title="是否确认删除选中的数据项？" 
                    onConfirm={() => handleDelete(selectedRowKeys.join(','))}
                    disabled={selectedRowKeys.length === 0}
                >
                    <Button type="primary" danger icon={<DeleteOutlined />} disabled={selectedRowKeys.length === 0}>
                        删除
                    </Button>
                </Popconfirm>
                <Popconfirm 
                    title="是否确认清空所有登录日志数据项？" 
                    onConfirm={handleClean}
                >
                    <Button danger icon={<ClearOutlined />}>清空</Button>
                </Popconfirm>
                <Button icon={<DownloadOutlined />} onClick={handleExport} style={{ color: '#faad14', borderColor: '#faad14' }}>
                    导出
                </Button>
            </Space>
            <Space>
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
        </Row>

        <Table
          rowKey="infoId"
          components={{
            header: {
              cell: ResizableTitle,
            },
          }}
          columns={resizableColumns}
          dataSource={data}
          loading={loading}
          size={tableSize}
          scroll={{ x: 1290 }}
          pagination={{
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            total: total,
            showTotal: (total, range) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
                setQueryParams({ ...queryParams, pageNum: page, pageSize });
            },
            position: ['bottomRight'],
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100']
          }}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
          }}
        />
      </Card>
    </div>
  );
};

export default Logininfor;