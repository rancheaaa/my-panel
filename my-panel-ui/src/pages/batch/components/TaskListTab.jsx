import React, { useState } from 'react';
import { Table, Button, Space, Popconfirm, Tag, Input, Tooltip, Card, Row, Col, Badge, Dropdown } from 'antd';
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  CaretRightOutlined,
  StopOutlined,
  DeleteOutlined,
  SearchOutlined,
  ReloadOutlined,
  PlusCircleOutlined,
  EyeOutlined,
  CloudServerOutlined,
  ClusterOutlined,
  FilterOutlined,
  ClockCircleOutlined,
  SyncOutlined,
  SwapOutlined,
  SendOutlined,
  QuestionCircleOutlined
} from '@ant-design/icons';
import TaskStatusBadge from './TaskStatusBadge';

const { Search } = Input;

const statusMap = {
  READY: { color: '#d9d9d9', bg: '#f5f5f5', text: '就绪' },
  RUNNING: { color: '#52c41a', bg: '#f6ffed', text: '运行中' },
  PAUSED: { color: '#faad14', bg: '#fffbe6', text: '已暂停' },
  STOPPED: { color: '#ff4d4f', bg: '#fff2f0', text: '已停止' },
  COMPLETED: { color: '#1890ff', bg: '#e6f7ff', text: '已完成' },
  ERROR: { color: '#ff4d4f', bg: '#fff1f0', text: '异常' }
};

const transferModeMap = {
  ONE_TO_ONE: { color: 'blue', icon: <SwapOutlined />, text: '一对一' },
  ONE_TO_MANY: { color: 'purple', icon: <ClusterOutlined />, text: '一对多' }
};

const routingMap = {
  ROUND_ROBIN: { color: 'cyan', text: '轮询' },
  RANDOM: { color: 'geekblue', text: '随机' },
  REGION_BASED: { color: 'purple', text: '区域' },
  BROADCAST: { color: 'orange', text: '广播' }
};

const cronDescMap = {
  '0 */1 * * * ?': '每隔 1 分钟',
  '0 */5 * * * ?': '每隔 5 分钟',
  '0 */10 * * * ?': '每隔 10 分钟',
  '0 */30 * * * ?': '每隔 30 分钟',
  '0 0 */1 * * ?': '每隔 1 小时',
  '0 0 0 * * ?': '每天 00:00 (午夜)',
  '0 0 2 * * ?': '每天 02:00 (凌晨)',
  '0 0 12 * * ?': '每天 12:00 (中午)',
  '0 0 18 * * ?': '每天 18:00 (傍晚)',
  '0 0 2 ? * MON': '每周一 02:00',
  '0 0 2 ? * SUN': '每周日 02:00'
};

function getCronDescription(expr) {
  return cronDescMap[expr] || expr;
}

const TaskListTab = ({ 
  onCreateClick, 
  tasks, 
  loading, 
  pagination, 
  fetchTasks,
  startTask,
  pauseTask,
  resumeTask,
  stopTask,
  deleteTask
}) => {
  const [searchText, setSearchText] = useState('');
  const [statusFilter, setStatusFilter] = useState(null);

  const filteredData = (tasks || []).filter(item => {
    const matchSearch = !searchText ||
      item.taskName?.toLowerCase().includes(searchText.toLowerCase()) ||
      item.sourceAgentId?.toLowerCase().includes(searchText.toLowerCase()) ||
      item.sourceDir?.toLowerCase().includes(searchText.toLowerCase());
    const matchStatus = !statusFilter || item.status === statusFilter;
    return matchSearch && matchStatus;
  });

  const columns = [
    {
      title: '任务',
      key: 'task',
      width: 220,
      fixed: 'left',
      render: (_, record) => (
        <div>
          <div style={{ fontWeight: 600, fontSize: 13.5, color: '#1f1f1f', marginBottom: 2 }}>
            {record.taskName || '-'}
          </div>
          <div style={{ fontSize: 11.5, color: '#8c8c8c' }}>ID: {record.id}</div>
        </div>
      )
    },
    {
      title: '源节点',
      key: 'source',
      width: 220,
      render: (_, record) => (
        <Tooltip title={`${record.sourceAgentName || record.sourceAgentId || ''} → ${record.sourceDir || ''}`}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <CloudServerOutlined style={{ color: '#52c41a', fontSize: 12, flexShrink: 0 }} />
            <span style={{
              fontWeight: 500, fontSize: 12.5, color: '#1f1f1f',
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'
            }}>{record.sourceAgentName || record.sourceAgentId || '-'}</span>
            <FilterOutlined style={{ color: '#bfbfbf', fontSize: 10, flexShrink: 0 }} />
            <span style={{
              fontSize: 11.5, color: '#8c8c8c',
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'
            }}>{record.sourceDir || '-'}</span>
          </div>
        </Tooltip>
      )
    },
    {
      title: '目标',
      key: 'target',
      width: 220,
      render: (_, record) => {
        let names = [], dirs = [];
        try { names = record.targetAgentNames ? JSON.parse(record.targetAgentNames) : []; } catch (e) {}
        try { dirs = record.targetDirs ? record.targetDirs.split(';') : []; } catch (e) {}
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            {names.length > 0 ? names.map((name, idx) => (
              <Tooltip key={idx} title={`${name} → ${dirs[idx] || ''}`}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <ClusterOutlined style={{ color: '#722ed1', fontSize: 11, flexShrink: 0 }} />
                  <span style={{
                    fontWeight: 500, fontSize: 11.5, color: '#722ed1',
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0
                  }}>{name}</span>
                  <FilterOutlined style={{ color: '#d9d9d9', fontSize: 9, flexShrink: 0 }} />
                  <span style={{
                    fontSize: 11, color: '#8c8c8c',
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0
                  }}>{dirs[idx] || '-'}</span>
                </div>
              </Tooltip>
            )) : (
              <span style={{ color: '#bfbfbf', fontSize: 12 }}>-</span>
            )}
          </div>
        );
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      filters: [
        { text: '就绪', value: 'READY' },
        { text: '运行中', value: 'RUNNING' },
        { text: '已暂停', value: 'PAUSED' },
        { text: '已停止', value: 'STOPPED' },
        { text: '已完成', value: 'COMPLETED' },
        { text: '异常', value: 'ERROR' }
      ],
      onFilter: (value) => setStatusFilter(value),
      render: (status) => {
        const s = statusMap[status] || statusMap.READY;
        return (
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 5,
            padding: '3px 10px', borderRadius: 12, background: s.bg,
            border: `1px solid ${s.color}40`
          }}>
            <span style={{
              width: 7, height: 7, borderRadius: '50%', background: s.color,
              boxShadow: `0 0 4px ${s.color}60`
            }} />
            <span style={{ fontSize: 12, fontWeight: 500, color: s.color }}>{s.text}</span>
          </div>
        );
      }
    },
    {
      title: '传输策略',
      key: 'strategy',
      width: 160,
      render: (_, record) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
          <Tag color={transferModeMap[record.transferMode]?.color || 'default'} style={{ margin: 0, fontSize: 11.5, width: 'fit-content' }}>
            {transferModeMap[record.transferMode]?.text || record.transferMode}
          </Tag>
          <Tag color={routingMap[record.routingStrategy]?.color || 'default'} style={{ margin: 0, fontSize: 11, width: 'fit-content' }}>
            {routingMap[record.routingStrategy]?.text || record.routingStrategy}
          </Tag>
        </div>
      )
    },
    {
      title: '调度',
      dataIndex: 'scanCronExpression',
      key: 'cron',
      width: 130,
      render: (val) => val ? (
        <Tooltip title={getCronDescription(val)}>
          <span style={{
            fontFamily: 'Monaco, Consolas, monospace', fontSize: 11.5,
            background: '#fafafa', padding: '2px 6px', borderRadius: 4,
            border: '1px solid #f0f0f0'
          }}>{val.length > 14 ? val.slice(0, 14) + '..' : val}</span>
        </Tooltip>
      ) : (
        <Tag style={{ fontSize: 11, margin: 0 }} color="default">手动</Tag>
      )
    },
    {
      title: '重试',
      key: 'retry',
      width: 70,
      render: (_, record) => (
        <Tooltip title={`最大${record.maxRetryCount || 0}次 | 间隔${record.retryIntervalMin || 0}min | ${record.retryBackoffType === 'EXPONENTIAL' ? '指数' : '线性'}退避`}>
          <span style={{
            cursor: 'pointer', fontSize: 12, color: record.retryEnabled === 1 ? '#52c41a' : '#bfbfbf'
          }}>
            <ReloadOutlined spin={record.status === 'RUNNING'} /> {record.retryEnabled === 1 ? '开' : '关'}
          </span>
        </Tooltip>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 150,
      sorter: (a, b) => new Date(a.createTime) - new Date(b.createTime),
      render: (time) => time ? (
        <span style={{ fontSize: 12, color: '#666' }}>
          <ClockCircleOutlined style={{ marginRight: 4, fontSize: 11, color: '#bfbfbf' }} />
          {time.replace('T', ' ').slice(0, 16)}
        </span>
      ) : '-'
    },
    {
      title: '修改时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 150,
      render: (time) => time ? (
        <span style={{ fontSize: 12, color: '#666' }}>
          <ClockCircleOutlined style={{ marginRight: 4, fontSize: 11, color: '#bfbfbf' }} />
          {time.replace('T', ' ').slice(0, 16)}
        </span>
      ) : '-'
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      key: 'createBy',
      width: 100,
      render: (val) => val || '-'
    },
    {
      title: '更新人',
      dataIndex: 'updateBy',
      key: 'updateBy',
      width: 100,
      render: (val) => val || '-'
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => {
        const items = [];
        if (record.status === 'READY') {
          items.push({
            key: 'start', label: (<><PlayCircleOutlined /> 启动</>), onClick: () => startTask(record.id)
          });
        }
        if (record.status === 'RUNNING') {
          items.push({ key: 'pause', label: (<><PauseCircleOutlined /> 暂停</>), onClick: () => pauseTask(record.id) });
          items.push({
            key: 'stop', label: (<span style={{ color: '#ff4d4f' }}><StopOutlined /> 停止</span>),
            onClick: () => stopTask(record.id)
          });
        }
        if (record.status === 'PAUSED') {
          items.push({ key: 'resume', label: (<><CaretRightOutlined /> 恢复</>), onClick: () => resumeTask(record.id) });
          items.push({
            key: 'stop', label: (<span style={{ color: '#ff4d4f' }}><StopOutlined /> 停止</span>),
            onClick: () => stopTask(record.id)
          });
        }

        return (
          <Space size={2}>
            {items.map(item => (
              <Button key={item.key} type="link" size="small" onClick={item.onClick} style={{ padding: '0 4px' }}>
                {item.label}
              </Button>
            ))}
            <Popconfirm title="确定删除该任务？" description="删除后不可恢复" onConfirm={() => deleteTask([record.id])}
              okText="确认" cancelText="取消" okButtonProps={{ danger: true }}
            >
              <Button type="link" danger size="small" icon={<DeleteOutlined />} style={{ padding: '0 4px' }} />
            </Popconfirm>
          </Space>
        );
      }
    }
  ];

  const expandedRowRender = (record) => {
    let includePatterns = [], excludePatterns = [];
    try { includePatterns = JSON.parse(record.includePatterns || '[]'); } catch (e) {}
    try { excludePatterns = JSON.parse(record.excludePatterns || '[]'); } catch (e) {}

    return (
      <div style={{ background: '#fafafa', borderRadius: 8, padding: '16px 20px', margin: -4 }}>
        <Row gutter={[32, 12]}>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>📝 任务描述</div>
            <div style={{ fontSize: 13, color: '#333' }}>{record.taskDescription || '-'}</div>
          </Col>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>📂 包含模式</div>
            <div>{includePatterns.length > 0 ? includePatterns.map(p => <Tag key={p} color="green" style={{ marginBottom: 3, fontSize: 11.5 }}>{p}</Tag>) : <span style={{ color: '#bfbfbf' }}>全部文件</span>}</div>
          </Col>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>🚫 排除模式</div>
            <div>{excludePatterns.length > 0 ? excludePatterns.map(p => <Tag key={p} color="red" style={{ marginBottom: 3, fontSize: 11.5 }}>{p}</Tag>) : <span style={{ color: '#bfbfbf' }}>无排除</span>}</div>
          </Col>
        </Row>
        <Row gutter={[32, 12]} style={{ marginTop: 8 }}>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>📋 最大扫描数</div>
            <div style={{ fontSize: 13, fontWeight: 500 }}>{record.maxScanFiles || '-'} 个文件</div>
          </Col>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>📦 目录结构</div>
            <Tag color={record.preserveDirStructure === 1 ? 'blue' : 'default'} style={{ fontSize: 11.5 }}>
              {record.preserveDirStructure === 1 ? '保持原始结构' : '扁平化'}
            </Tag>
          </Col>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>✅ 传输后操作</div>
            <Tag color={
              record.postTransferAction === 'DELETE' ? 'red' :
              record.postTransferAction === 'BACKUP' ? 'orange' : 'default'
            } style={{ fontSize: 11.5 }}>
              {record.postTransferAction === 'NONE' ? '无操作' :
               record.postTransferAction === 'DELETE' ? '删除源文件' :
               record.postTransferAction === 'BACKUP' ? `备份 → ${record.backupDir || '-'}` : record.postTransferAction}
            </Tag>
          </Col>
        </Row>
      </div>
    );
  };

  return (
    <div>
      {/* 工具栏 */}
      <Card size="small" style={{
        borderRadius: 10, marginBottom: 12,
        border: '1px solid #f0f0f0', boxShadow: 'none'
      }} styles={{ body: { padding: '10px 16px' }}}>
        <Row align="middle" justify="space-between">
          <Col>
            <Space size={12}>
              <Search
                placeholder="搜索任务名、Agent ID、目录..."
                allowClear
                size="middle"
                prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                onChange={(e) => setSearchText(e.target.value)}
                style={{ width: 260, borderRadius: 6 }}
              />
              <Space.Compact>
                {Object.entries(statusMap).map(([key, val]) => (
                  <Button
                    key={key}
                    size="small"
                    type={statusFilter === key ? 'primary' : 'default'}
                    ghost={statusFilter === key}
                    onClick={() => setStatusFilter(statusFilter === key ? null : key)}
                    style={{
                      borderRadius: 6,
                      fontSize: 11.5,
                      ...(statusFilter === key ? {} : { borderColor: '#f0f0f0', color: '#666' })
                    }}
                  >
                    <span style={{
                      width: 6, height: 6, borderRadius: '50%',
                      background: val.color, display: 'inline-block', marginRight: 4
                    }}/>
                    {val.text}
                  </Button>
                ))}
              </Space.Compact>
            </Space>
          </Col>
          <Col>
            <Space size={6}>
              {onCreateClick && (
                <Button type="primary" icon={<PlusCircleOutlined />} size="small"
                  onClick={onCreateClick}
                  style={{ borderRadius: 6 }}
                >
                  新建任务
                </Button>
              )}
              <Button icon={<ReloadOutlined />} size="small"
                onClick={() => fetchTasks()}
                style={{ borderRadius: 6 }}
              >
                刷新
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 表格 */}
      <Table
        columns={columns}
        dataSource={filteredData}
        rowKey="id"
        loading={loading}
        expandable={{
          expandedRowRender,
          rowExpandable: () => true
        }}
        pagination={{
          ...pagination,
          current: pagination.current,
          total: filteredData.length,
          pageSize: pagination.pageSize || 10,
          showSizeChanger: true,
          showTotal: (total, range) => `${range[0]}-${range[1]} / ${total} 条`,
          pageSizeOptions: ['10', '20', '50'],
          size: 'small',
          style: { marginTop: 12 }
        }}
        onChange={(pag) => fetchTasks({ page: pag.current, size: pag.pageSize })}
        scroll={{ x: 1800 }}
        size="middle"
        rowClassName={(record) => record.status === 'RUNNING' ? 'table-row-running' : ''}
        style={{
          borderRadius: 10,
          '.table-row-running': { animation: 'pulse-bg 2s infinite' }
        }}
      />

      {/* 运行行动画 */}
      <style>{`
        .ant-table-tbody > tr.table-row-running > td {
          background: #f6ffed !important;
          transition: background 0.5s ease;
        }
        @keyframes pulse-bg {
          0%, 100% { background: #f6ffed; }
          50% { background: #ebfbee; }
        }
      `}</style>
    </div>
  );
};

export default TaskListTab;
