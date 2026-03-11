import React, { useState, useEffect } from 'react';
import { Card, Table, Form, Input, Select, Button, DatePicker, Space, Row, Col, message, Modal, Popconfirm, Tag, Descriptions, Tooltip } from 'antd';
import { SearchOutlined, ReloadOutlined, DeleteOutlined, ClearOutlined, DownloadOutlined, EyeOutlined } from '@ant-design/icons';
import { list, delOperlog, cleanOperlog, exportOperlog } from '../../../api/monitor/operlog';
import request from '../../../utils/request';

const { RangePicker } = DatePicker;
const { Option } = Select;

const Operlog = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState([]);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    title: undefined,
    operName: undefined,
    businessType: undefined,
    status: undefined,
  });
  const [dateRange, setDateRange] = useState([]);

  // Dictionaries
  const [sysOperType, setSysOperType] = useState([]);
  const [sysCommonStatus, setSysCommonStatus] = useState([]);

  // Modal
  const [open, setOpen] = useState(false);
  const [currentRecord, setCurrentRecord] = useState({});

  useEffect(() => {
    fetchDicts();
    fetchData();
  }, []);

  const fetchDicts = async () => {
    // Mock fetching dicts
    try {
        const resType = await request({ url: '/system/dict/data/type/sys_oper_type', method: 'get' });
        if (resType.code === 200) setSysOperType(resType.data);
        
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
      title: undefined,
      operName: undefined,
      businessType: undefined,
      status: undefined,
    });
    setDateRange([]);
    fetchData();
  };

  const handleDelete = async (operIds) => {
    try {
      await delOperlog(operIds);
      message.success('删除成功');
      fetchData();
      setSelectedRowKeys([]);
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleClean = async () => {
    try {
      await cleanOperlog();
      message.success('清空成功');
      fetchData();
    } catch (error) {
      message.error('清空失败');
    }
  };

  const handleExport = () => {
    message.success('正在导出数据，请稍候...');
    exportOperlog(queryParams);
  };

  const columns = [
    { title: '日志编号', dataIndex: 'operId', key: 'operId', align: 'center', width: 100 },
    { title: '系统模块', dataIndex: 'title', key: 'title', align: 'center', width: 120, ellipsis: true },
    { 
        title: '操作类型', 
        dataIndex: 'businessType', 
        key: 'businessType', 
        align: 'center',
        width: 100,
        render: (text) => {
            const dict = sysOperType.find(d => d.dictValue == text);
            return dict ? <Tag>{dict.dictLabel}</Tag> : text;
        }
    },
    { title: '请求方式', dataIndex: 'requestMethod', key: 'requestMethod', align: 'center', width: 100 },
    { title: '操作人员', dataIndex: 'operName', key: 'operName', align: 'center', width: 120 },
    { title: '主机', dataIndex: 'operIp', key: 'operIp', align: 'center', width: 130, ellipsis: true },
    { title: '操作地点', dataIndex: 'operLocation', key: 'operLocation', align: 'center', width: 150, ellipsis: true },
    { 
        title: '操作状态', 
        dataIndex: 'status', 
        key: 'status', 
        align: 'center',
        width: 100,
        render: (text) => {
            const dict = sysCommonStatus.find(d => d.dictValue == text);
            return dict ? <Tag color={String(text) === '0' ? 'success' : 'error'}>{dict.dictLabel}</Tag> : text;
        }
    },
    { title: '操作日期', dataIndex: 'operTime', key: 'operTime', align: 'center', width: 180 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button 
            type="link" 
            icon={<EyeOutlined />} 
            onClick={() => {
                setCurrentRecord(record);
                setOpen(true);
            }}
          >
            详细
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card">
        <Form layout="inline">
          <Form.Item label="系统模块">
            <Input 
                placeholder="请输入系统模块" 
                value={queryParams.title}
                onChange={e => setQueryParams({ ...queryParams, title: e.target.value })}
                onPressEnter={handleSearch}
                allowClear
            />
          </Form.Item>
          <Form.Item label="操作人员">
            <Input 
                placeholder="请输入操作人员" 
                value={queryParams.operName}
                onChange={e => setQueryParams({ ...queryParams, operName: e.target.value })}
                onPressEnter={handleSearch}
                allowClear
            />
          </Form.Item>
          <Form.Item label="类型">
            <Select
                placeholder="操作类型"
                style={{ width: 120 }}
                value={queryParams.businessType}
                onChange={val => setQueryParams({ ...queryParams, businessType: val })}
                allowClear
            >
                {sysOperType.map(d => (
                    <Option key={d.dictValue} value={d.dictValue}>{d.dictLabel}</Option>
                ))}
            </Select>
          </Form.Item>
          <Form.Item label="状态">
            <Select
                placeholder="操作状态"
                style={{ width: 120 }}
                value={queryParams.status}
                onChange={val => setQueryParams({ ...queryParams, status: val })}
                allowClear
            >
                {sysCommonStatus.map(d => (
                    <Option key={d.dictValue} value={d.dictValue}>{d.dictLabel}</Option>
                ))}
            </Select>
          </Form.Item>
          <Form.Item label="操作时间">
            <RangePicker 
                value={dateRange} 
                onChange={setDateRange} 
            />
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
        <Row style={{ marginBottom: 16 }}>
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
                    title="是否确认清空所有操作日志数据项？" 
                    onConfirm={handleClean}
                >
                    <Button danger icon={<ClearOutlined />}>清空</Button>
                </Popconfirm>
                <Button icon={<DownloadOutlined />} onClick={handleExport} style={{ color: '#faad14', borderColor: '#faad14' }}>
                    导出
                </Button>
            </Space>
        </Row>

        <Table
          rowKey="operId"
          columns={columns}
          dataSource={data}
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            total: total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => {
                setQueryParams({ ...queryParams, pageNum: page, pageSize: size });
            }
          }}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
          }}
        />
      </Card>

      <Modal
        title="操作日志详情"
        open={open}
        onCancel={() => setOpen(false)}
        footer={[<Button key="close" onClick={() => setOpen(false)}>关闭</Button>]}
        width={700}
      >
        <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="操作模块">{currentRecord.title} / {sysOperType.find(d => d.dictValue == currentRecord.businessType)?.dictLabel}</Descriptions.Item>
            <Descriptions.Item label="请求地址">{currentRecord.operUrl}</Descriptions.Item>
            <Descriptions.Item label="登录信息">{currentRecord.operName} / {currentRecord.operIp} / {currentRecord.operLocation}</Descriptions.Item>
            <Descriptions.Item label="请求方式">{currentRecord.requestMethod}</Descriptions.Item>
            <Descriptions.Item label="操作方法" span={2}>{currentRecord.method}</Descriptions.Item>
            <Descriptions.Item label="请求参数" span={2}>
                <Input.TextArea value={currentRecord.operParam} rows={4} readOnly />
            </Descriptions.Item>
            <Descriptions.Item label="返回参数" span={2}>
                <Input.TextArea value={currentRecord.jsonResult} rows={4} readOnly />
            </Descriptions.Item>
            <Descriptions.Item label="状态">
                <div style={{ display: 'flex', alignItems: 'center' }}>
                    {String(currentRecord.status) === '0' ? <Tag color="success">正常</Tag> : <Tag color="error">失败</Tag>}
                </div>
            </Descriptions.Item>
            <Descriptions.Item label="操作时间">{currentRecord.operTime}</Descriptions.Item>
            {String(currentRecord.status) === '1' && (
                <Descriptions.Item label="异常信息" span={2}>
                    {currentRecord.errorMsg}
                </Descriptions.Item>
            )}
        </Descriptions>
      </Modal>
    </div>
  );
};

export default Operlog;
