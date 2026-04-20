import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Table, Card, Button, Space, Form, Input, Modal, message, Popconfirm, Tooltip, Select, Tag, Row, Col, DatePicker, Descriptions } from 'antd';
import { SearchOutlined, ReloadOutlined, DeleteOutlined, HistoryOutlined, EyeOutlined, UpOutlined, DownOutlined, DownloadOutlined } from '@ant-design/icons';
import { ResizableTitle } from '../../../components/ResizableTable';
import {
  listCommandHistory,
  getCommandHistory,
  delCommandHistory,
  exportCommandHistory
} from '../../../api/agent';

const { RangePicker } = DatePicker;
const { Option } = Select;

const CommandHistory = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [tableSize, setTableSize] = useState('large');

  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    agentId: undefined,
    agentName: undefined,
    agentIp: undefined,
    commandStatus: undefined
  });

  const [expand, setExpand] = useState(true);

  const [form] = Form.useForm();
  const [searchParams] = useSearchParams();

  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [detailData, setDetailData] = useState(null);

  useEffect(() => {
    const agentIdParam = searchParams.get('agentId');
    if (agentIdParam) {
      setQueryParams(prev => ({
        ...prev,
        agentId: agentIdParam
      }));
      form.setFieldsValue({ agentId: agentIdParam });
    }
  }, [searchParams, form]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listCommandHistory(queryParams);
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
      const params = { ...values };
      if (params.dateRange && params.dateRange.length === 2) {
        params.beginTime = params.dateRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = params.dateRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      delete params.dateRange;
      setQueryParams({
        ...queryParams,
        ...params,
        pageNum: 1
      });
    });
  };

  const handleReset = () => {
    form.resetFields();
    setQueryParams({
      pageNum: 1,
      pageSize: 10,
      agentId: undefined,
      agentName: undefined,
      agentIp: undefined,
      commandStatus: undefined
    });
  };

  const handleDelete = async (id) => {
    const ids = id || selectedRowKeys;
    if (!ids || ids.length === 0) {
      message.warning('请选择要删除的数据');
      return;
    }
    try {
      await delCommandHistory(ids);
      message.success('删除成功');
      setSelectedRowKeys([]);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('删除失败');
    }
  };

  const handleExport = async () => {
    try {
      const response = await exportCommandHistory(queryParams);
      const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = `Agent命令历史_${new Date().getTime()}.xlsx`;
      link.click();
      message.success('导出成功');
    } catch (error) {
      console.error(error);
      message.error('导出失败');
    }
  };

  const handleViewDetail = async (record) => {
    try {
      const res = await getCommandHistory(record.id);
      if (res.code === 200) {
        setDetailData(res.data);
        setIsDetailModalOpen(true);
      }
    } catch (error) {
      console.error(error);
      message.error('获取详情失败');
    }
  };

  const statusMap = {
    0: { text: '成功', color: 'green' },
    1: { text: '失败', color: 'red' },
    2: { text: '超时', color: 'orange' },
    3: { text: '未知', color: 'default' }
  };

  const [columns, setColumns] = useState([
    {
      title: '记录ID',
      dataIndex: 'id',
      key: 'id',
      align: 'center',
      width: 80,
      ellipsis: true
    },
    {
      title: 'Agent ID',
      dataIndex: 'agentId',
      key: 'agentId',
      align: 'center',
      width: 200,
      ellipsis: true
    },
    {
      title: 'Agent节点名称',
      dataIndex: 'agentName',
      key: 'agentName',
      align: 'center',
      width: 150,
      ellipsis: true
    },
    {
      title: 'Agent IP端口',
      key: 'agentAddr',
      align: 'center',
      width: 180,
      render: (_, record) => `${record.agentIp}:${record.agentPort}`
    },
    {
      title: '命令内容',
      dataIndex: 'command',
      key: 'command',
      width: 300,
      ellipsis: true,
      render: (text) => (
        <Tooltip title={text}>
          <span style={{ cursor: 'pointer' }}>{text}</span>
        </Tooltip>
      )
    },
    {
      title: '超时(秒)',
      dataIndex: 'commandTimeout',
      key: 'commandTimeout',
      align: 'center',
      width: 90,
      render: (timeout) => timeout !== null ? `${timeout}` : '-'
    },
    {
      title: '执行状态',
      dataIndex: 'commandStatus',
      key: 'commandStatus',
      align: 'center',
      width: 100,
      render: (status) => {
        const info = statusMap[status] || statusMap[3];
        return <Tag color={info.color}>{info.text}</Tag>;
      }
    },
    {
      title: '退出码',
      dataIndex: 'exitCode',
      key: 'exitCode',
      align: 'center',
      width: 80,
      render: (code) => code !== null ? code : '-'
    },
    {
      title: '耗时(ms)',
      dataIndex: 'executeTime',
      key: 'executeTime',
      align: 'center',
      width: 100,
      render: (time) => time !== null ? `${time}` : '-'
    },
    {
      title: '操作用户',
      dataIndex: 'userName',
      key: 'userName',
      align: 'center',
      width: 100
    },
    {
      title: '提交时间',
      dataIndex: 'submitTime',
      key: 'submitTime',
      align: 'center',
      width: 170
    },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Tooltip title="查看详情">
            <Button type="text" icon={<EyeOutlined />} onClick={() => handleViewDetail(record)} style={{ color: '#1890ff' }} />
          </Tooltip>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
            <Button type="text" icon={<DeleteOutlined />} danger />
          </Popconfirm>
        </Space>
      )
    }
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

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }}>
          <Row gutter={[24, 16]}>
            <Col span={8}>
              <Form.Item name="agentId" label="Agent ID">
                <Input placeholder="请输入Agent节点ID" allowClear />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="agentName" label="节点名称">
                <Input placeholder="请输入Agent节点名称" allowClear />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="agentIp" label="Agent IP">
                <Input placeholder="请输入Agent IP" allowClear />
              </Form.Item>
            </Col>

            {expand && (
              <>
                  <Col span={8}>
                      <Form.Item name="commandStatus" label="执行状态">
                          <Select placeholder="请选择状态" allowClear>
                              <Option value={0}>成功</Option>
                              <Option value={1}>失败</Option>
                              <Option value={2}>超时</Option>
                              <Option value={3}>未知</Option>
                          </Select>
                      </Form.Item>
                  </Col>
                <Col span={8}>
                  <Form.Item name="dateRange" label="时间范围">
                    <RangePicker showTime format="YYYY-MM-DD HH:mm:ss" style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </>
            )}

            <Col span={24} style={{ textAlign: 'right', marginTop: '8px' }}>
              <Space size="small">
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
        <div className="table-toolbar" style={{ marginBottom: 16 }}>
          <Space size="middle">
            <Button
              danger
              icon={<DeleteOutlined />}
              disabled={selectedRowKeys.length === 0}
              onClick={() => handleDelete()}
            >
              批量删除
            </Button>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={handleExport}
            >
              导出
            </Button>
            <Tooltip title="刷新">
              <Button icon={<ReloadOutlined />} onClick={fetchData} shape="circle" />
            </Tooltip>
          </Space>
        </div>

        <Table
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          components={{
            header: {
              cell: ResizableTitle,
            },
          }}
          columns={resizableColumns}
          dataSource={data}
          loading={loading}
          rowKey="id"
          size={tableSize}
          scroll={{ x: 1400 }}
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
        />
      </Card>

      <Modal
        title="命令执行详情"
        open={isDetailModalOpen}
        onCancel={() => setIsDetailModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsDetailModalOpen(false)}>关闭</Button>
        ]}
        width={800}
      >
        {detailData && (
          <>
            <Descriptions bordered column={2} size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="记录ID">{detailData.id}</Descriptions.Item>
              <Descriptions.Item label="执行状态">
                <Tag color={(statusMap[detailData.commandStatus] || statusMap[3]).color}>
                  {(statusMap[detailData.commandStatus] || statusMap[3]).text}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="命令超时时间">{detailData.commandTimeout !== null ? `${detailData.commandTimeout} 秒` : '-'}</Descriptions.Item>
              <Descriptions.Item label="Agent Id">{detailData.agentId || '-'}</Descriptions.Item>
              <Descriptions.Item label="Agent节点名称">{detailData.agentName || '-'}</Descriptions.Item>
              <Descriptions.Item label="Agent IP端口">{detailData.agentIp}:{detailData.agentPort}</Descriptions.Item>
              <Descriptions.Item label="退出码">{detailData.exitCode !== null ? detailData.exitCode : '-'}</Descriptions.Item>
              <Descriptions.Item label="执行耗时">{detailData.executeTime !== null ? `${detailData.executeTime} ms` : '-'}</Descriptions.Item>
              <Descriptions.Item label="操作用户">{detailData.userName || '-'}</Descriptions.Item>
              <Descriptions.Item label="提交时间">{detailData.submitTime}</Descriptions.Item>
              <Descriptions.Item label="开始时间" span={2}>{detailData.startTime || '-'}</Descriptions.Item>
              <Descriptions.Item label="完成时间" span={2}>{detailData.endTime || '-'}</Descriptions.Item>
            </Descriptions>

            <div style={{ marginBottom: 8 }}>
              <strong>执行的命令：</strong>
            </div>
            <pre style={{
              background: '#f5f5f5',
              padding: 12,
              borderRadius: 4,
              marginBottom: 16,
              fontSize: 13,
              overflow: 'auto',
              maxHeight: 100,
              border: '1px solid #d9d9d9'
            }}>{detailData.command || ''}</pre>

            {detailData.output && (
              <>
                <div style={{ marginBottom: 8 }}>
                  <strong>标准输出：</strong>
                </div>
                <pre style={{
                  background: '#f0fff0',
                  padding: 12,
                  borderRadius: 4,
                  marginBottom: 16,
                  fontSize: 13,
                  overflow: 'auto',
                  maxHeight: 300,
                  border: '1px solid #b7eb8f'
                }}>{detailData.output}</pre>
              </>
            )}

            {detailData.error && (
              <>
                <div style={{ marginBottom: 8 }}>
                  <strong>错误输出：</strong>
                </div>
                <pre style={{
                  background: '#fff0f0',
                  padding: 12,
                  borderRadius: 4,
                  marginBottom: 16,
                  fontSize: 13,
                  overflow: 'auto',
                  maxHeight: 200,
                  border: '1px solid #ffa39e'
                }}>{detailData.error}</pre>
              </>
            )}
          </>
        )}
      </Modal>
    </div>
  );
};

export default CommandHistory;
