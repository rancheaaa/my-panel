import React, { useState } from 'react';
import { Table, Tag, Space, Button, Popconfirm, Tooltip, Card, Row, Col, Input } from 'antd';
const { Search } = Input;
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  CaretRightOutlined,
  DeleteOutlined,
  SearchOutlined,
  ReloadOutlined,
  PlusCircleOutlined,
  EyeOutlined,
  EditOutlined,
  CloudServerOutlined,
  ClusterOutlined,
  FilterOutlined,
  ClockCircleOutlined,
  SyncOutlined,
  SwapOutlined
} from '@ant-design/icons';

const statusMap = {
  READY: { color: '#d9d9d9', bg: '#f5f5f5', text: '就绪' },
  RUNNING: { color: '#52c41a', bg: '#f6ffed', text: '运行中' },
  PAUSED: { color: '#faad14', bg: '#fffbe6', text: '已暂停' },
  STOPPED: { color: '#ff4d4f', bg: '#fff2f0', text: '已停止' },
  COMPLETED: { color: '#1890ff', bg: '#e6f7ff', text: '已完成' },
  ERROR: { color: '#ff4d4f', bg: '#fff1f0', text: '异常' }
};

const transferModeMap = {
  ONE_TO_ONE: { color: 'cyan', text: '一对一' },
  ONE_TO_MANY: { color: 'blue', text: '一对多' }
};

const routingMap = {
  ROUND_ROBIN: { color: 'blue', text: '轮询' },
  RANDOM: { color: 'geekblue', text: '随机' },
  REGION_BASED: { color: 'purple', text: '区域' },
  BROADCAST: { color: 'orange', text: '广播' }
};

const cronDescriptions = {
  '0 */1 * * * ?': '每隔 1 分钟',
  '0 */5 * * * ?': '每隔 5 分钟',
  '0 */10 * * * ?': '每隔 10 分钟',
  '0 */30 * * * ?': '每隔 30 分钟',
  '0 0 */1 * * ?': '每隔 1 小时',
  '0 0 0 * * ?': '每天 00:00',
  '0 0 2 * * ?': '每天 02:00',
  '0 0 12 * * ?': '每天 12:00',
  '0 0 18 * * ?': '每天 18:00',
  '0 0 2 ? * MON': '每周一 02:00',
  '0 0 2 ? * SUN': '每周日 02:00',
  '0 0 0 ? * MON': '每周一 00:00',
  '0 0 0 1 * ?': '每月1号'
};

const getCronDescription = (cron) => cronDescriptions[cron] || `自定义: ${cron}`;

const tooltipStyle = {
  backgroundColor: '#ffffff',
  color: '#333',
  boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
  border: '1px solid #e8e8e8'
};

const nodeStatusText = (nodeStatus) => {
  if (nodeStatus === 1) return '在线';
  if (nodeStatus === 0) return '离线';
  return '未知';
};

const dirExistsText = (dirExists, nodeStatus) => {
  if (nodeStatus === 0) return '未知';
  if (dirExists === true) return '存在';
  if (dirExists === false) return '不存在';
  return '未知';
};

const TaskListTab = ({ 
  onCreateClick, 
  onEditClick,
  onViewClick,
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
    const t = item.task || item;
    const matchSearch = !searchText ||
      t.taskName?.toLowerCase().includes(searchText.toLowerCase()) ||
      t.sourceAgentId?.toLowerCase().includes(searchText.toLowerCase()) ||
      t.sourceDir?.toLowerCase().includes(searchText.toLowerCase());
    const matchStatus = !statusFilter || t.status === statusFilter;
    return matchSearch && matchStatus;
  });

  const columns = [
    {
      title: '任务',
      key: 'task',
      width: 220,
      fixed: 'left',
      render: (_, record) => {
        const t = record.task || record;
        return (
          <div>
            <div style={{ fontWeight: 600, fontSize: 13.5, color: '#1f1f1f', marginBottom: 2 }}>
              {t.taskName || '-'}
            </div>
            <div style={{ fontSize: 11.5, color: '#8c8c8c' }}>ID: {t.id}</div>
          </div>
        );
      }
    },
    {
      title: '发送节点',
      key: 'source',
      width: 220,
      render: (_, record) => {
        const t = record.task || record;
        const sourceStatus = record.sourceNodeStatus;
        const agentName = sourceStatus?.agentName || t.sourceAgentName || t.sourceAgentId || '-';
        const dir = t.sourceDir || '-';
        const isOffline = sourceStatus?.nodeStatus === 0;
        const isOnline = sourceStatus?.nodeStatus === 1;
        const dirNotExists = sourceStatus?.dirExists === false && isOnline;

        const nodeStatusLabel = nodeStatusText(sourceStatus?.nodeStatus);
        const dirExistsLabel = dirExistsText(sourceStatus?.dirExists, sourceStatus?.nodeStatus);

        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <Tooltip color="#fff" overlayInnerStyle={{ color: '#333' }} title={`节点：${nodeStatusLabel}`}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                <CloudServerOutlined style={{ color: isOffline ? '#ff4d4f' : isOnline ? '#52c41a' : '#bfbfbf', fontSize: 12, flexShrink: 0 }} />
                <span style={{
                  fontWeight: 500, fontSize: 12.5,
                  color: isOffline ? '#ff4d4f' : isOnline ? '#1f1f1f' : '#8c8c8c',
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  cursor: 'help'
                }}>{agentName}</span>
              </div>
            </Tooltip>
            <Tooltip color="#fff" overlayInnerStyle={{ color: '#333' }} title={`目录：${dirExistsLabel}`}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                <FilterOutlined style={{ color: dirNotExists ? '#ff4d4f' : isOnline ? '#52c41a' : '#bfbfbf', fontSize: 10, flexShrink: 0 }} />
                <span style={{
                  fontSize: 11.5,
                  color: dirNotExists ? '#ff4d4f' : isOnline ? '#52c41a' : '#bfbfbf',
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  cursor: 'help'
                }}>{dir}</span>
              </div>
            </Tooltip>
          </div>
        );
      }
    },
    {
      title: '目标节点',
      key: 'target',
      width: 220,
      render: (_, record) => {
        const targetStatusList = record.targetNodeStatusList || [];
        if (targetStatusList.length === 0) {
          return <span style={{ color: '#bfbfbf', fontSize: 12 }}>-</span>;
        }
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            {targetStatusList.map((status, idx) => {
              const agentName = status.agentName || '-';
              const dir = status.dirPath || '-';
              const isOffline = status.nodeStatus === 0;
              const isOnline = status.nodeStatus === 1;
              const dirNotExists = status.dirExists === false && isOnline;

              const nodeStatusLabel = nodeStatusText(status.nodeStatus);
              const dirExistsLabel = dirExistsText(status.dirExists, status.nodeStatus);

              return (
                <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Tooltip color="#fff" overlayInnerStyle={{ color: '#333' }} title={`节点：${nodeStatusLabel}`}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                      <ClusterOutlined style={{ color: isOffline ? '#ff4d4f' : isOnline ? '#722ed1' : '#bfbfbf', fontSize: 11, flexShrink: 0 }} />
                      <span style={{
                        fontWeight: 500, fontSize: 11.5,
                        color: isOffline ? '#ff4d4f' : isOnline ? '#722ed1' : '#8c8c8c',
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0,
                        cursor: 'help'
                      }}>{agentName}</span>
                    </div>
                  </Tooltip>
                  <Tooltip color="#fff" overlayInnerStyle={{ color: '#333' }} title={`目录：${dirExistsLabel}`}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                      <FilterOutlined style={{ color: dirNotExists ? '#ff4d4f' : isOnline ? '#52c41a' : '#bfbfbf', fontSize: 9, flexShrink: 0 }} />
                      <span style={{
                        fontSize: 11,
                        color: dirNotExists ? '#ff4d4f' : isOnline ? '#52c41a' : '#bfbfbf',
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0,
                        cursor: 'help'
                      }}>{dir}</span>
                    </div>
                  </Tooltip>
                </div>
              );
            })}
          </div>
        );
      }
    },
    {
      title: '状态',
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
      render: (_, record) => {
        const t = record.task || record;
        const s = statusMap[t.status] || statusMap.READY;
        return (
          <Tag color={s.color.replace('#', '')} style={{ margin: 0 }}>{s.text}</Tag>
        );
      }
    },
    {
      title: '传输策略',
      key: 'strategy',
      width: 160,
      render: (_, record) => {
        const t = record.task || record;
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            <Tag color={transferModeMap[t.transferMode]?.color || 'default'} style={{ margin: 0, fontSize: 11.5, width: 'fit-content' }}>
              {transferModeMap[t.transferMode]?.text || t.transferMode}
            </Tag>
            <Tag color={routingMap[t.routingStrategy]?.color || 'default'} style={{ margin: 0, fontSize: 11, width: 'fit-content' }}>
              {routingMap[t.routingStrategy]?.text || t.routingStrategy}
            </Tag>
            <Tag color={t.preserveDirStructure === 1 ? 'blue' : 'default'} style={{ margin: 0, fontSize: 10.5, width: 'fit-content' }}>
              {t.preserveDirStructure === 1 ? '保持目录' : '扁平化'}
            </Tag>
          </div>
        );
      }
    },
    {
      title: '调度',
      key: 'cron',
      width: 130,
      render: (_, record) => {
        const t = record.task || record;
        return t.scanCronExpression ? (
          <Tooltip color="#fff" overlayInnerStyle={{ color: '#333' }} title={getCronDescription(t.scanCronExpression)}>
            <span style={{
              fontFamily: 'Monaco, Consolas, monospace', fontSize: 11.5,
              background: '#fafafa', padding: '2px 6px', borderRadius: 4,
              border: '1px solid #f0f0f0'
            }}>{t.scanCronExpression.length > 14 ? t.scanCronExpression.slice(0, 14) + '..' : t.scanCronExpression}</span>
          </Tooltip>
        ) : (
          <Tag style={{ fontSize: 11, margin: 0 }} color="default">手动</Tag>
        );
      }
    },
    {
      title: '重试',
      key: 'retry',
      width: 70,
      render: (_, record) => {
        const t = record.task || record;
        return (
          <Tooltip color="#fff" overlayInnerStyle={{ color: '#333' }} title={`最大${t.maxRetryCount || 0}次 | 间隔${t.retryIntervalMin || 0}min | 保留${t.retryMaxDays || 0}天 | ${t.retryBackoffType === 'EXPONENTIAL' ? '指数' : '线性'}退避`}>
            <span style={{
              cursor: 'pointer', fontSize: 12, color: t.retryEnabled === 1 ? '#52c41a' : '#bfbfbf'
            }}>
              <ReloadOutlined spin={t.status === 'RUNNING'} /> {t.retryEnabled === 1 ? '开' : '关'}
            </span>
          </Tooltip>
        );
      }
    },
    {
      title: '文件匹配规则',
      key: 'filePatterns',
      width: 200,
      render: (_, record) => {
        const t = record.task || record;
        let includePatterns = [], excludePatterns = [];
        try { includePatterns = JSON.parse(t.includePatterns || '[]'); } catch (e) {}
        try { excludePatterns = JSON.parse(t.excludePatterns || '[]'); } catch (e) {}

        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            {includePatterns.length > 0 ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ fontSize: 10, color: '#8c8c8c', flexShrink: 0 }}>包含:</span>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
                  {includePatterns.slice(0, 3).map(p => (
                    <Tag key={p} color="green" style={{ margin: 0, fontSize: 10.5 }}>{p}</Tag>
                  ))}
                  {includePatterns.length > 3 && <span style={{ fontSize: 10, color: '#bfbfbf' }}>+{includePatterns.length - 3}</span>}
                </div>
              </div>
            ) : null}
            {excludePatterns.length > 0 ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ fontSize: 10, color: '#8c8c8c', flexShrink: 0 }}>排除:</span>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
                  {excludePatterns.slice(0, 3).map(p => (
                    <Tag key={p} color="red" style={{ margin: 0, fontSize: 10.5 }}>{p}</Tag>
                  ))}
                  {excludePatterns.length > 3 && <span style={{ fontSize: 10, color: '#bfbfbf' }}>+{excludePatterns.length - 3}</span>}
                </div>
              </div>
            ) : null}
            {includePatterns.length === 0 && excludePatterns.length === 0 && (
              <span style={{ fontSize: 11, color: '#bfbfbf' }}>未设置</span>
            )}
          </div>
        );
      }
    },
    {
      title: '传输后操作',
      key: 'postAction',
      width: 120,
      render: (_, record) => {
        const t = record.task || record;
        const action = t.postTransferAction || 'NONE';
        const actionMap = {
          NONE: { color: 'default', text: '无操作' },
          DELETE: { color: 'red', text: '删除源文件' },
          BACKUP: { color: 'orange', text: '备份' }
        };
        const a = actionMap[action] || actionMap.NONE;
        const detail = action === 'BACKUP' && t.backupDir ? ` → ${t.backupDir}` : '';
        return (
          <Tag color={a.color} style={{ margin: 0, fontSize: 11.5 }}>{a.text}{detail}</Tag>
        );
      }
    },
    {
      title: '创建时间',
      key: 'createTime',
      width: 150,
      sorter: (a, b) => new Date((a.task || a).createTime) - new Date((b.task || b).createTime),
      render: (_, record) => {
        const t = record.task || record;
        return t.createTime ? (
          <span style={{ fontSize: 12, color: '#666' }}>
            <ClockCircleOutlined style={{ marginRight: 4, fontSize: 11, color: '#bfbfbf' }} />
            {t.createTime.replace('T', ' ').slice(0, 16)}
          </span>
        ) : '-';
      }
    },
    {
      title: '修改时间',
      key: 'updateTime',
      width: 150,
      render: (_, record) => {
        const t = record.task || record;
        return t.updateTime ? (
          <span style={{ fontSize: 12, color: '#666' }}>
            <ClockCircleOutlined style={{ marginRight: 4, fontSize: 11, color: '#bfbfbf' }} />
            {t.updateTime.replace('T', ' ').slice(0, 16)}
          </span>
        ) : '-';
      }
    },
    {
      title: '创建人',
      key: 'createBy',
      width: 100,
      render: (_, record) => {
        const t = record.task || record;
        return t.createBy || '-';
      }
    },
    {
      title: '更新人',
      key: 'updateBy',
      width: 100,
      render: (_, record) => {
        const t = record.task || record;
        return t.updateBy || '-';
      }
    },
    {
      title: '操作',
      key: 'action',
      width: 270,
      fixed: 'right',
      render: (_, record) => {
        const t = record.task || record;
        const items = [];
          if (t.status === 'READY') {
              items.push({
                  key: 'start', label: (<><PlayCircleOutlined /> 启动</>), onClick: () => startTask(t.id)
              });
          }
          if (t.status === 'RUNNING') {
              items.push({ key: 'pause', label: (<><PauseCircleOutlined /> 暂停</>), onClick: () => pauseTask(t.id) });
          }
          if (t.status === 'PAUSED') {
              items.push({ key: 'resume', label: (<><CaretRightOutlined /> 恢复</>), onClick: () => resumeTask(t.id) });
          }
        items.push({
          key: 'edit', label: (<><EditOutlined /> 修改</>), onClick: () => onEditClick(t)
        });
        items.push({
          key: 'view', label: (<><EyeOutlined /> 详情</>), onClick: () => onViewClick(t)
        });

        return (
          <Space size={2}>
            {items.map(item => (
              <Button key={item.key} type="link" size="small" onClick={item.onClick} style={{ padding: '0 4px' }}>
                {item.label}
              </Button>
            ))}
            <Popconfirm title="确定删除该任务？" description="删除后不可恢复" onConfirm={() => deleteTask([t.id])}
              okText="确认" cancelText="取消" okButtonProps={{ danger: true }}
            >
              <Button type="link" danger size="small" icon={<DeleteOutlined />} style={{ padding: '0 4px' }}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      }
    }
  ];

  const expandedRowRender = (record) => {
    const t = record.task || record;
    let includePatterns = [], excludePatterns = [];
    try { includePatterns = JSON.parse(t.includePatterns || '[]'); } catch (e) {}
    try { excludePatterns = JSON.parse(t.excludePatterns || '[]'); } catch (e) {}

    return (
      <div style={{ background: '#fafafa', borderRadius: 8, padding: '16px 20px', margin: -4 }}>
        <Row gutter={[32, 12]}>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>任务描述</div>
            <div style={{ fontSize: 13, color: '#333' }}>{t.taskDescription || '-'}</div>
          </Col>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>包含模式</div>
            <div>{includePatterns.length > 0 ? includePatterns.map(p => <Tag key={p} color="green" style={{ marginBottom: 3, fontSize: 11.5 }}>{p}</Tag>) : <span style={{ color: '#bfbfbf' }}>全部文件</span>}</div>
          </Col>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>排除模式</div>
            <div>{excludePatterns.length > 0 ? excludePatterns.map(p => <Tag key={p} color="red" style={{ marginBottom: 3, fontSize: 11.5 }}>{p}</Tag>) : <span style={{ color: '#bfbfbf' }}>无排除</span>}</div>
          </Col>
        </Row>
        <Row gutter={[32, 12]} style={{ marginTop: 8 }}>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>最大扫描数</div>
            <div style={{ fontSize: 13, fontWeight: 500 }}>{t.maxScanFiles || '-'} 个文件</div>
          </Col>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>目录结构</div>
            <Tag color={t.preserveDirStructure === 1 ? 'blue' : 'default'} style={{ fontSize: 11.5 }}>
              {t.preserveDirStructure === 1 ? '保持原始结构' : '扁平化'}
            </Tag>
          </Col>
          <Col span={8}>
            <div style={{ fontSize: 11.5, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>传输后操作</div>
            <Tag color={
              t.postTransferAction === 'DELETE' ? 'red' :
              t.postTransferAction === 'BACKUP' ? 'orange' : 'default'
            } style={{ fontSize: 11.5 }}>
              {t.postTransferAction === 'NONE' ? '无操作' :
               t.postTransferAction === 'DELETE' ? '删除源文件' :
               t.postTransferAction === 'BACKUP' ? `备份 → ${t.backupDir || '-'}` : t.postTransferAction}
            </Tag>
          </Col>
        </Row>
      </div>
    );
  };

  return (
    <div>
      {/* 搜索卡片 */}
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Search
          placeholder="搜索任务名、Agent ID、目录..."
          allowClear
          size="middle"
          prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
          onChange={(e) => setSearchText(e.target.value)}
          style={{ width: 400 }}
          onSearch={() => fetchTasks()}
        />
      </Card>

      {/* 任务卡片 */}
      <Card bordered={false}>
        {/* 工具栏 */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Space size="large">
            <Button type="primary" icon={<PlusCircleOutlined />} onClick={onCreateClick}>新建任务</Button>
          </Space>
          <Space size="large">
            <Tooltip title="刷新">
              <Button icon={<ReloadOutlined />} onClick={() => fetchTasks()} shape="circle" />
            </Tooltip>
          </Space>
        </div>

        {/* 表格 */}
        <Table
          columns={columns}
          dataSource={filteredData}
          rowKey={(record) => { const t = record.task || record; return t.id; }}
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
            size: "small",
            style: { marginTop: 16 },
            onChange: (page, size) => fetchTasks({ page, size, status: statusFilter, keyword: searchText }),
            onShowSizeChange: (_, size) => fetchTasks({ page: 1, size, status: statusFilter, keyword: searchText })
          }}
          scroll={{ x: 1920, y: 'calc(100vh - 380px)' }}
          size="middle"
        />
      </Card>
    </div>
  );
};

export default TaskListTab;
