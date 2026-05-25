import React, { useState, useEffect } from 'react';
import { Table, Input, Select, Card, Row, Col, Space, Tag, Tooltip, Progress, Typography, Badge, Button, Pagination, Dropdown, Form } from 'antd';
import {
  SearchOutlined,
  ReloadOutlined,
  FileTextOutlined,
  CloudServerOutlined,
  FolderOpenOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  SyncOutlined,
  WarningOutlined,
  KeyOutlined,
  FieldTimeOutlined,
  DashboardOutlined,
  CodeOutlined,
  BugOutlined,
  UserOutlined,
  ColumnHeightOutlined,
  UpOutlined,
  DownOutlined
} from '@ant-design/icons';
import { useSubtasks } from './hooks/useSubtasks';
import './index.scss';

const { Search } = Input;
const { Text } = Typography;

const statusConfig = {
  QUEUED: { color: '#d9d9d9', bg: '#f5f5f5', text: '排队中', icon: <ClockCircleOutlined /> },
  SENDING: { color: '#1890ff', bg: '#e6f7ff', text: '传输中', icon: <LoadingOutlined spin /> },
  COMPLETED: { color: '#52c41a', bg: '#f6ffed', text: '已完成', icon: <CheckCircleOutlined /> },
  FAILED: { color: '#ff4d4f', bg: '#fff2f0', text: '失败', icon: <CloseCircleOutlined /> },
  RETRYING: { color: '#faad14', bg: '#fffbe6', text: '重试中', icon: <SyncOutlined spin /> }
};

function formatBytes(bytes) {
  if (!bytes || bytes < 0) return '-';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  while (bytes >= 1024 && i < units.length - 1) {
    bytes /= 1024;
    i++;
  }
  return `${bytes.toFixed(i === 0 ? 0 : 2)} ${units[i]}`;
}

function formatSpeed(speed) {
  if (!speed || speed <= 0) return '-';
  return formatBytes(speed) + '/s';
}

function formatDuration(ms) {
  if (!ms || ms < 0) return '-';
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const min = Math.floor(ms / 60000);
  const sec = Math.round((ms % 60000) / 1000);
  return `${min}m${sec}s`;
}

function getProgressPercent(record) {
  if (!record.fileSizeBytes || record.fileSizeBytes <= 0) {
    if (record.status === 'COMPLETED') return 100;
    if (record.status === 'SENDING' || record.status === 'RETRYING') return 50;
    return 0;
  }
  return Math.min(100, Math.round((record.transferredBytes || 0) / record.fileSizeBytes * 100));
}

function calculateDuration(record, now) {
  if (record.durationMs && record.durationMs > 0) {
    return record.durationMs;
  }
  if (record.startedAt && (record.status === 'SENDING' || record.status === 'RETRYING')) {
    const start = new Date(record.startedAt).getTime();
    return now - start;
  }
  return null;
}

const SubtaskListPage = () => {
  const [taskIdFilter, setTaskIdFilter] = useState(null);
  const [statusFilter, setStatusFilter] = useState(null);
  const [sourceFilePath, setSourceFilePath] = useState('');
  const [targetFilePath, setTargetFilePath] = useState('');
  const [targetAgentId, setTargetAgentId] = useState('');
  const [sourceAgentId, setSourceAgentId] = useState('');
  const [sourceAgentName, setSourceAgentName] = useState('');
  const [targetAgentName, setTargetAgentName] = useState('');
  const [fileName, setFileName] = useState('');
  const [scanBatchId, setScanBatchId] = useState(null);
  const [fileBatchId, setFileBatchId] = useState(null);
  const [now, setNow] = useState(() => Date.now());
  const [tableSize, setTableSize] = useState('small');
  const [expand, setExpand] = useState(false);

  const { subtasks, loading, pagination, fetchSubtasks } = useSubtasks();

  useEffect(() => {
    const interval = setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  const handleSearch = () => {
    fetchSubtasks({
      taskId: taskIdFilter || undefined,
      status: statusFilter || undefined,
      sourceFilePath: sourceFilePath || undefined,
      targetFilePath: targetFilePath || undefined,
      targetAgentId: targetAgentId || undefined,
      sourceAgentId: sourceAgentId || undefined,
      sourceAgentName: sourceAgentName || undefined,
      targetAgentName: targetAgentName || undefined,
      fileName: fileName || undefined,
      scanBatchId: scanBatchId || undefined,
      fileBatchId: fileBatchId || undefined
    });
  };

  const handleReset = () => {
    setTaskIdFilter(null);
    setStatusFilter(null);
    setSourceFilePath('');
    setTargetFilePath('');
    setTargetAgentId('');
    setSourceAgentId('');
    setSourceAgentName('');
    setTargetAgentName('');
    setFileName('');
    setScanBatchId(null);
    setFileBatchId(null);
    fetchSubtasks();
  };

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 160,
      align: 'center',
      render: (id) => <Text type="secondary" style={{ fontSize: 11 }}>{id}</Text>
    },
    {
      title: '任务ID',
      dataIndex: 'taskId',
      key: 'taskId',
      width: 160,
      align: 'center',
      render: (id) => (
        <Tag color="blue" style={{ borderRadius: 4, fontSize: 11 }}>{id}</Tag>
      )
    },
    {
      title: '扫描批次ID',
      dataIndex: 'scanBatchId',
      key: 'scanBatchId',
      width: 160,
      align: 'center',
      ellipsis: true,
      render: (id) => id ? (
        <Tooltip title={id}>
          <Text code style={{ fontSize: 11 }}>{id}</Text>
        </Tooltip>
      ) : '-'
    },
    {
      title: '文件批次ID',
      dataIndex: 'fileBatchId',
      key: 'fileBatchId',
      width: 160,
      align: 'center',
      ellipsis: true,
      render: (id) => id ? (
        <Tooltip title={id}>
          <Text code style={{ fontSize: 11 }}>{id}</Text>
        </Tooltip>
      ) : '-'
    },
    {
      title: '文件名',
      dataIndex: 'fileName',
      key: 'fileName',
      width: 160,
      ellipsis: true,
      render: (name) => (
        <Tooltip title={name}>
          <Space size={4}>
            <FileTextOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
            <Text style={{ fontSize: 12.5 }}>{name}</Text>
          </Space>
        </Tooltip>
      )
    },
    {
      title: '源路径',
      dataIndex: 'sourcePath',
      key: 'sourcePath',
      width: 220,
      ellipsis: true,
      render: (path) => path ? (
        <Tooltip title={path}>
          <Text style={{ fontSize: 12 }} type="secondary">{path}</Text>
        </Tooltip>
      ) : '-'
    },
    {
      title: '目标路径',
      dataIndex: 'targetPath',
      key: 'targetPath',
      width: 200,
      ellipsis: true,
      render: (dir) => dir ? (
        <Tooltip title={dir}>
          <Space size={4}>
            <FolderOpenOutlined style={{ color: '#52c41a', fontSize: 11 }} />
            <Text style={{ fontSize: 12 }}>{dir}</Text>
          </Space>
        </Tooltip>
      ) : '-'
    },
    {
      title: '源节点',
      dataIndex: 'sourceAgentName',
      key: 'sourceAgentName',
      width: 200,
      ellipsis: true,
      render: (name, record) => (
        <Tooltip title={`${record.sourceAgentId}`}>
          <Space size={3} wrap>
            <UserOutlined style={{ color: '#1890ff', fontSize: 11 }} />
            <Text style={{ fontSize: 11.5 }}>{name || record.sourceAgentId}</Text>
          </Space>
        </Tooltip>
      )
    },
    {
      title: '目标节点',
      dataIndex: 'targetAgentName',
      key: 'targetAgentName',
      width: 200,
      ellipsis: true,
      render: (name, record) => (
        <Tooltip title={`${record.targetAgentId}`}>
          <Space size={3} wrap>
            <CloudServerOutlined style={{ color: '#52c41a', fontSize: 11 }} />
            <Text style={{ fontSize: 11.5 }}>{name || record.targetAgentId}</Text>
          </Space>
        </Tooltip>
      )
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 85,
      align: 'center',
      filters: Object.entries(statusConfig).map(([key, val]) => ({ text: val.text, value: key })),
      onFilter: (value, record) => record.status === value,
      render: (status) => {
        const config = statusConfig[status] || statusConfig.QUEUED;
        return (
          <Tag 
            color={config.color} 
            style={{ borderRadius: 4, fontSize: 11, padding: '1px 7px' }}
            icon={config.icon}
          >
            {config.text}
          </Tag>
        );
      }
    },
    {
      title: '进度/大小',
      key: 'progressSize',
      width: 180,
      render: (_, record) => {
        const percent = getProgressPercent(record);
        const transferred = formatBytes(record.transferredBytes);
        const total = formatBytes(record.fileSizeBytes);
        
        return (
          <div>
            <Progress 
              percent={percent} 
              size="small" 
              status={record.status === 'FAILED' ? 'exception' : record.status === 'COMPLETED' ? 'success' : 'active'}
              strokeColor={statusConfig[record.status]?.color}
              style={{ marginBottom: 3 }}
            />
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <Text type="secondary" style={{ fontSize: 10 }}>{transferred} / {total}</Text>
              <Text type="secondary" style={{ fontSize: 10 }}>{formatSpeed(record.speedBytesPerSec)}</Text>
            </div>
          </div>
        );
      }
    },
    {
      title: '传输会话ID',
      dataIndex: 'transferId',
      key: 'transferId',
      width: 120,
      ellipsis: true,
      render: (tid) => tid ? (
        <Tooltip title={`会话ID: ${tid}\n用于断点续传`}>
          <Space size={3}>
            <KeyOutlined style={{ color: '#8c8c8c', fontSize: 10 }} />
            <Text code style={{ fontSize: 10, maxWidth: 90 }}>{tid.substring(0, 12)}...</Text>
          </Space>
        </Tooltip>
      ) : '-'
    },
    {
      title: '时间信息',
      key: 'timeInfo',
      width: 190,
      render: (_, record) => {
        const duration = calculateDuration(record, now);
        return (
          <div style={{ lineHeight: '18px' }}>
            <div><Text type="secondary" style={{ fontSize: 10 }}>开始:</Text> {record.startedAt?.substring(0, 19) || '-'}</div>
            <div><Text type="secondary" style={{ fontSize: 10 }}>完成:</Text> {record.completedAt?.substring(0, 19) || '-'}</div>
            <div><Text type="secondary" style={{ fontSize: 10 }}>耗时:</Text> 
              <Text style={{ fontSize: 10, color: duration > 300000 ? '#ff4d4f' : undefined }}>
                {formatDuration(duration)}
              </Text>
            </div>
            <div><Text type="secondary" style={{ fontSize: 10 }}>修改:</Text> {record.fileLastModified?.substring(0, 19) || '-'}</div>
          </div>
        );
      }
    },
    {
      title: '重试信息',
      key: 'retryInfo',
      width: 150,
      render: (_, record) => (
        <div style={{ lineHeight: '20px' }}>
          <div>
            <Text type="secondary" style={{ fontSize: 10 }}>次数: </Text>
            {record.retryCount > 0 ? (
              <Badge count={record.retryCount} style={{ backgroundColor: '#faad14', fontSize: 9 }} />
            ) : <Text type="secondary" style={{ fontSize: 10 }}>0</Text>}
          </div>
          <div>
            {record.lastRetryAt ? (
              <Tooltip title={`最后重试: ${record.lastRetryAt}`}>
                <Text type="warning" style={{ fontSize: 10 }}>
                  <FieldTimeOutlined style={{ marginRight: 2, fontSize: 9 }} />
                  最后: {record.lastRetryAt?.substring(11, 19)}
                </Text>
              </Tooltip>
            ) : <span style={{ color: '#bfbfbf', fontSize: 10 }}>最后: -</span>}
          </div>
          <div>
            {record.nextRetryAfter ? (
              <Tooltip title={`下次可重试: ${record.nextRetryAfter}`}>
                <Text type="success" style={{ fontSize: 10 }}>
                  <FieldTimeOutlined style={{ marginRight: 2, fontSize: 9 }} />
                  下次: {record.nextRetryAfter?.substring(11, 19)}
                </Text>
              </Tooltip>
            ) : <span style={{ color: '#bfbfbf', fontSize: 10 }}>下次: -</span>}
          </div>
        </div>
      )
    },
    {
      title: '错误信息',
      key: 'errorInfo',
      width: 180,
      render: (_, record) => {
        if (!record.errorCode && !record.errorMessage && !record.errorStackTrace) return '-';
        return (
          <div>
            {record.errorCode && (
              <Tooltip title={`错误码: ${record.errorCode}`}>
                <Tag color="red" style={{ borderRadius: 3, fontSize: 10, padding: '0 4px', marginBottom: 2 }}>
                  <CodeOutlined /> {record.errorCode}
                </Tag>
              </Tooltip>
            )}
            {record.errorMessage && (
              <Tooltip title={record.errorMessage}>
                <Text type="danger" style={{ fontSize: 11, display: 'block' }}>
                  <WarningOutlined style={{ marginRight: 3, fontSize: 10 }} />
                  {record.errorMessage.length > 25 ? record.errorMessage.substring(0, 25) + '...' : record.errorMessage}
                </Text>
              </Tooltip>
            )}
            {record.errorStackTrace && (
              <Tooltip title={record.errorStackTrace}>
                <Text type="secondary" style={{ fontSize: 10, display: 'block' }}>
                  <BugOutlined style={{ marginRight: 3 }} /> 堆栈...
                </Text>
              </Tooltip>
            )}
          </div>
        );
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 155,
      sorter: (a, b) => new Date(a.createTime) - new Date(b.createTime),
      render: (time) => time ? <Text style={{ fontSize: 11 }}>{time}</Text> : '-'
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 155,
      render: (time) => time ? <Text style={{ fontSize: 11 }}>{time}</Text> : '-'
    }
  ];

  return (
    <div className="batch-subtask-page">
      <div className="batch-subtask-container">
        <Card size="small" className="search-card" bordered={false} style={{ marginBottom: 16, minHeight: expand ? 220 : 120, transition: 'min-height 0.3s ease' }}>
          <Row gutter={[24, 16]}>
            <Col span={6}>
              <Input
                placeholder="任务ID(精确)"
                allowClear
                size="middle"
                prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                value={taskIdFilter}
                onChange={(e) => setTaskIdFilter(e.target.value)}
                onPressEnter={handleSearch}
                style={{ borderRadius: 8 }}
              />
            </Col>
            <Col span={6}>
              <Select
                placeholder="状态(精确)"
                allowClear
                size="middle"
                value={statusFilter}
                onChange={(val) => setStatusFilter(val)}
                style={{ width: '100%', borderRadius: 8 }}
                options={[
                  { value: 'QUEUED', label: '排队中' },
                  { value: 'SENDING', label: '传输中' },
                  { value: 'COMPLETED', label: '已完成' },
                  { value: 'FAILED', label: '失败' },
                  { value: 'RETRYING', label: '重试中' }
                ]}
              />
            </Col>
            <Col span={6}>
              <Search
                placeholder="源节点名称(模糊)"
                allowClear
                size="middle"
                value={sourceAgentName}
                onChange={(e) => setSourceAgentName(e.target.value)}
                onSearch={handleSearch}
                style={{ borderRadius: 8 }}
              />
            </Col>
            <Col span={6}>
              <Search
                placeholder="目标节点名称(模糊)"
                allowClear
                size="middle"
                value={targetAgentName}
                onChange={(e) => setTargetAgentName(e.target.value)}
                onSearch={handleSearch}
                style={{ borderRadius: 8 }}
              />
            </Col>
            {expand && (
              <>
                <Col span={6}>
                  <Search
                    placeholder="文件名(模糊)"
                    allowClear
                    size="middle"
                    value={fileName}
                    onChange={(e) => setFileName(e.target.value)}
                    onSearch={handleSearch}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
                <Col span={6}>
                  <Search
                    placeholder="源文件路径(模糊)"
                    allowClear
                    size="middle"
                    value={sourceFilePath}
                    onChange={(e) => setSourceFilePath(e.target.value)}
                    onSearch={handleSearch}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
                <Col span={6}>
                  <Search
                    placeholder="目标文件路径(模糊)"
                    allowClear
                    size="middle"
                    value={targetFilePath}
                    onChange={(e) => setTargetFilePath(e.target.value)}
                    onSearch={handleSearch}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
                <Col span={6}>
                  <Search
                    placeholder="源Agent ID(模糊)"
                    allowClear
                    size="middle"
                    value={sourceAgentId}
                    onChange={(e) => setSourceAgentId(e.target.value)}
                    onSearch={handleSearch}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
                <Col span={6}>
                  <Search
                    placeholder="目标Agent ID(模糊)"
                    allowClear
                    size="middle"
                    value={targetAgentId}
                    onChange={(e) => setTargetAgentId(e.target.value)}
                    onSearch={handleSearch}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
                <Col span={6}>
                  <Input
                    placeholder="扫描批次ID(精确)"
                    allowClear
                    size="middle"
                    prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                    value={scanBatchId}
                    onChange={(e) => setScanBatchId(e.target.value)}
                    onPressEnter={handleSearch}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
                <Col span={6}>
                  <Input
                    placeholder="文件批次ID(精确)"
                    allowClear
                    size="middle"
                    prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                    value={fileBatchId}
                    onChange={(e) => setFileBatchId(e.target.value)}
                    onPressEnter={handleSearch}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
              </>
            )}
            <Col span={24} style={{ textAlign: 'right', marginTop: expand ? 0 : 0 }}>
              <Space>
                <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch} style={{ borderRadius: 8 }}>搜索</Button>
                <Button icon={<ReloadOutlined />} onClick={handleReset} style={{ borderRadius: 8 }}>重置</Button>
                <Button
                  type="link"
                  onClick={() => setExpand(!expand)}
                  icon={expand ? <UpOutlined /> : <DownOutlined />}
                  style={{ padding: '0 4px' }}
                >
                  {expand ? '收起' : '展开'}
                </Button>
              </Space>
            </Col>
          </Row>
        </Card>

        <Card size="small" className="table-card" bordered={false}>
          <div className="subtask-toolbar">
            <Space size="large">
              <Tooltip title="刷新">
                <Button icon={<ReloadOutlined />} onClick={() => fetchSubtasks()} shape="circle" />
              </Tooltip>
            </Space>
            <div style={{ flex: 1 }}></div>
            <Space size="large">
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

          <div className="subtask-table-container">
            <Table
              columns={columns}
              dataSource={subtasks}
              rowKey="id"
              loading={loading}
              size={tableSize}
              scroll={{ x: 2130, y: 'calc(100vh - 500px)' }}
              pagination={false}
              rowClassName={(record) => record.status === 'FAILED' ? 'row-error' : ''}
            />
          </div>

          <div className="fixed-pagination-bar">
            <Pagination
              current={pagination.current}
              pageSize={pagination.pageSize}
              total={pagination.total}
              showTotal={(t) => `共 ${t} 条`}
              onChange={(page, pageSize) => fetchSubtasks({ page, size: pageSize })}
              showSizeChanger
              pageSizeOptions={['10', '20', '50', '100']}
              showQuickJumper
              size="default"
            />
          </div>
        </Card>
      </div>
    </div>
  );
};

export default SubtaskListPage;
