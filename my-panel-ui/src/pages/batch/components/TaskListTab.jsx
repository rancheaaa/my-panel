import React, { useState } from 'react';
import { Table, Tag, Space, Button, Popconfirm, Tooltip, Card, Input, Pagination, Dropdown, Form, Select, Drawer, message, Col, Row } from 'antd';
const { Search } = Input;
const { Option } = Select;
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
  SwapOutlined,
  ColumnHeightOutlined,
  DownOutlined,
  UpOutlined,
  DownloadOutlined,
  ApiOutlined,
  FolderOpenOutlined
} from '@ant-design/icons';
import { batchApi } from '../../../api/batch';
import '../index.scss';

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

const TaskListTab = ({
  onCreateClick,
  onEditClick,
  onViewClick,
  onConnectivityClick,
  onDirCheckClick,
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
  const [searchForm] = Form.useForm();
  const [tableSize, setTableSize] = useState('middle');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);

  const handleSearch = (values) => {
    const params = {
      page: 1,
      size: pagination.pageSize || 10,
      ...values
    };
    fetchTasks(params);
    setDrawerOpen(false);
  };

  const handleReset = () => {
    searchForm.resetFields();
    fetchTasks({ page: 1, size: pagination.pageSize || 10 });
    setDrawerOpen(false);
  };

  const handlePageChange = (page, size) => {
    const values = searchForm.getFieldsValue();
    fetchTasks({ page, size, ...values });
  };

  const handleExport = async () => {
    try {
      const values = searchForm.getFieldsValue();
      const ids = selectedRowKeys.length > 0 ? selectedRowKeys : undefined;
      const res = await batchApi.exportTasks(values, ids);
      const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = `batch_task_${new Date().getTime()}.xlsx`;
      link.click();
      window.URL.revokeObjectURL(link.href);
      message.success(`导出成功，共 ${ids?.length || pagination.total} 条数据`);
    } catch (error) {
      console.error('导出失败:', error);
      message.error('导出失败');
    }
  };

  const columns = [
    {
      title: '任务ID',
      key: 'taskId',
      width: 100,
      fixed: 'left',
      render: (_, record) => {
        const t = record.task || record;
        return (
          <Tooltip color="#fff" overlayInnerStyle={{ color: '#333' }} title={`任务ID: ${t.id}`}>
            <span style={{ fontFamily: 'Monaco, Consolas, monospace', fontSize: 12, color: '#8c8c8c' }}>{t.id}</span>
          </Tooltip>
        );
      }
    },
    {
      title: '任务名称',
      key: 'taskName',
      width: 150,
      fixed: 'left',
      render: (_, record) => {
        const t = record.task || record;
        return (
          <Tooltip color="#fff" overlayInnerStyle={{ color: '#333' }} title={t.taskName || '-'}>
            <span style={{ fontWeight: 600, fontSize: 13, color: '#1f1f1f' }}>{t.taskName || '-'}</span>
          </Tooltip>
        );
      }
    },
    {
      title: '发送节点',
      key: 'source',
      width: 300,
      render: (_, record) => {
        const t = record.task || record;
        const agentName = t.sourceAgentName || t.sourceAgentId || '-';
        const dir = t.sourceDir || '-';

        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <CloudServerOutlined style={{ color: '#1890ff', fontSize: 12, flexShrink: 0 }} />
            <Tooltip title={t.sourceAgentId || agentName} placement="topLeft">
              <span style={{
                fontWeight: 500, fontSize: 12.5, color: '#1f1f1f',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'
              }}>{agentName}</span>
            </Tooltip>
            <FilterOutlined style={{ color: '#52c41a', fontSize: 10, flexShrink: 0 }} />
            <Tooltip title={dir} placement="topLeft">
              <span style={{
                fontSize: 11.5, color: '#52c41a',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'
              }}>{dir}</span>
            </Tooltip>
          </div>
        );
      }
    },
    {
      title: '目标节点',
      key: 'target',
      width: 300,
      render: (_, record) => {
        const t = record.task || record;
        let targetNames = [];
        let targetDirs = [];
        let targetAgentIds = [];
        try { targetNames = JSON.parse(t.targetAgentNames || '[]'); } catch (e) { targetNames = (t.targetAgentNames || '').split(';').filter(Boolean); }
        try { targetDirs = JSON.parse(t.targetDirs || '[]'); } catch (e) { targetDirs = (t.targetDirs || '').split(';').filter(Boolean); }
        try { targetAgentIds = JSON.parse(t.targetAgentIds || '[]'); } catch (e) { targetAgentIds = (t.targetAgentIds || '').split(';').filter(Boolean); }

        if (targetNames.length === 0) {
          return <span style={{ color: '#bfbfbf', fontSize: 12 }}>-</span>;
        }
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            {targetNames.map((name, idx) => {
              const dir = targetDirs[idx] || '-';
              const agentId = targetAgentIds[idx] || name || '-';
              return (
                <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <ClusterOutlined style={{ color: '#722ed1', fontSize: 11, flexShrink: 0 }} />
                  <Tooltip title={agentId} placement="topLeft">
                    <span style={{
                      fontWeight: 500, fontSize: 11.5, color: '#722ed1',
                      overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0
                    }}>{name || '-'}</span>
                  </Tooltip>
                  <FilterOutlined style={{ color: '#52c41a', fontSize: 9, flexShrink: 0 }} />
                  <Tooltip title={dir} placement="topLeft">
                    <span style={{
                      fontSize: 11, color: '#52c41a',
                      overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0
                    }}>{dir}</span>
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
      width: 260,
      render: (_, record) => {
        const t = record.task || record;
        return (
          <div style={{ display: 'flex', flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 4, justifyContent: 'center' }}>
            <Tag color={transferModeMap[t.transferMode]?.color || 'default'} style={{ margin: 0, fontSize: 11.5 }}>
              {transferModeMap[t.transferMode]?.text || t.transferMode}
            </Tag>
            <Tag color={routingMap[t.routingStrategy]?.color || 'default'} style={{ margin: 0, fontSize: 11 }}>
              {routingMap[t.routingStrategy]?.text || t.routingStrategy}
            </Tag>
            {t.routingStrategy === 'REGION_BASED' && t.routingConfig && (
              <Tooltip color="#fff" overlayInnerStyle={{ color: '#333', maxWidth: 400 }} title={
                <div style={{ maxWidth: 380 }}>
                  <pre style={{ margin: 0, fontSize: 11, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{t.routingConfig}</pre>
                </div>
              }>
                <Tag color="purple" style={{ margin: 0, fontSize: 10, cursor: 'pointer' }}>
                  区域配置 ✓
                </Tag>
              </Tooltip>
            )}
            <Tag color={t.preserveDirStructure === 1 ? 'blue' : 'default'} style={{ margin: 0, fontSize: 10.5 }}>
              {t.preserveDirStructure === 1 ? '保持目录' : '扁平化'}
            </Tag>
          </div>
        );
      }
    },
    {
      title: '扫描时间CRON',
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
      title: '扫描上限',
      key: 'maxScanFiles',
      width: 90,
      align: 'center',
      render: (_, record) => {
        const t = record.task || record;
        return t.maxScanFiles ? (
          <span style={{ fontSize: 12, color: '#333', fontWeight: 500 }}>{t.maxScanFiles}</span>
        ) : '-';
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
      width: 150,
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
      title: '启动时间',
      key: 'startedAt',
      width: 150,
      render: (_, record) => {
        const t = record.task || record;
        return t.startedAt ? (
          <span style={{ fontSize: 12, color: '#1890ff' }}>
            <PlayCircleOutlined style={{ marginRight: 4, fontSize: 11, color: '#1890ff' }} />
            {t.startedAt.replace('T', ' ').slice(0, 16)}
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
      title: '任务描述',
      key: 'taskDescription',
      width: 200,
      ellipsis: true,
      render: (_, record) => {
        const t = record.task || record;
        return t.taskDescription ? (
          <Tooltip color="#fff" overlayInnerStyle={{ color: '#333' }} title={t.taskDescription}>
            <span style={{ fontSize: 12, color: '#666' }}>{t.taskDescription}</span>
          </Tooltip>
        ) : '-';
      }
    },
    {
      title: '操作',
      key: 'action',
      width: 410,
      fixed: 'right',
      render: (_, record) => {
        const t = record.task || record;
        const items = [];
          if (t.status === 'READY') {
              items.push({
                  key: 'start',
                  type: 'confirm',
                  title: '确定启动该任务？',
                  description: '任务启动后将按照设定的调度策略开始执行',
                  onConfirm: () => startTask(t.id),
                  content: <><PlayCircleOutlined /> 启动</>
              });
          }
          if (t.status === 'RUNNING') {
              items.push({
                  key: 'pause',
                  type: 'confirm',
                  title: '确定暂停该任务？',
                  description: '任务暂停后将停止执行，可通过恢复按钮重新启动',
                  onConfirm: () => pauseTask(t.id),
                  content: <><PauseCircleOutlined /> 暂停</>
              });
          }
          if (t.status === 'PAUSED') {
              items.push({
                  key: 'resume',
                  type: 'confirm',
                  title: '确定恢复该任务？',
                  description: '任务恢复后将继续执行',
                  onConfirm: () => resumeTask(t.id),
                  content: <><CaretRightOutlined /> 恢复</>
              });
          }
        items.push({
          key: 'edit', content: <><EditOutlined /> 修改</>, onClick: () => onEditClick(t)
        });
        items.push({
          key: 'view', content: <><EyeOutlined /> 详情</>, onClick: () => onViewClick(t)
        });
        items.push({
          key: 'connectivity', content: <><ApiOutlined /> 连通性</>, onClick: () => onConnectivityClick(t)
        });
        items.push({
          key: 'dircheck', content: <><FolderOpenOutlined /> 目录检测</>, onClick: () => onDirCheckClick(t)
        });

        return (
          <Space size={2}>
            {items.map(item => {
              if (item.type === 'confirm') {
                return (
                  <Popconfirm
                    key={item.key}
                    title={item.title}
                    description={item.description}
                    onConfirm={item.onConfirm}
                    okText="确定"
                    cancelText="取消"
                  >
                    <Button type="link" size="small" style={{ padding: '0 4px' }}>
                      {item.content}
                    </Button>
                  </Popconfirm>
                );
              }
              return (
                <Button key={item.key} type="link" size="small" onClick={item.onClick} style={{ padding: '0 4px' }}>
                  {item.content}
                </Button>
              );
            })}
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

  const renderSearchDrawer = () => {
    const allFields = (
      <>
        <Col span={12}>
          <Form.Item name="id" label="任务ID">
            <Input placeholder="精准匹配" allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="taskName" label="任务名称">
            <Input placeholder="模糊搜索" allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="status" label="任务状态">
            <Select placeholder="精确匹配" allowClear>
              <Option value="READY">就绪</Option>
              <Option value="RUNNING">运行中</Option>
              <Option value="PAUSED">已暂停</Option>
            </Select>
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="sourceAgentId" label="源Agent ID">
            <Input placeholder="精确匹配" allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="sourceAgentName" label="源节点名称">
            <Input placeholder="模糊搜索" allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="sourceDir" label="源目录">
            <Input placeholder="模糊搜索" allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="targetAgentId" label="目标Agent ID">
            <Input placeholder="模糊搜索" allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="targetAgentName" label="目标节点名称">
            <Input placeholder="模糊搜索" allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="targetDir" label="目标目录">
            <Input placeholder="模糊搜索" allowClear />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="transferMode" label="传输模式">
            <Select placeholder="精确匹配" allowClear>
              <Option value="ONE_TO_ONE">一对一</Option>
              <Option value="ONE_TO_MANY">一对多</Option>
            </Select>
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="routingStrategy" label="路由策略">
            <Select placeholder="精确匹配" allowClear>
              <Option value="ROUND_ROBIN">轮询</Option>
              <Option value="RANDOM">随机</Option>
              <Option value="REGION_BASED">区域</Option>
              <Option value="BROADCAST">广播</Option>
            </Select>
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="postTransferAction" label="传输后操作">
            <Select placeholder="精确匹配" allowClear>
              <Option value="NONE">无操作</Option>
              <Option value="DELETE">删除源文件</Option>
              <Option value="BACKUP">备份</Option>
            </Select>
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="retryEnabled" label="启用重试">
            <Select placeholder="精确匹配" allowClear>
              <Option value={1}>是</Option>
              <Option value={0}>否</Option>
            </Select>
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="preserveDirStructure" label="保持目录结构">
            <Select placeholder="精确匹配" allowClear>
              <Option value={1}>是</Option>
              <Option value={0}>否</Option>
            </Select>
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="taskDescription" label="任务描述">
            <Input placeholder="模糊搜索" allowClear />
          </Form.Item>
        </Col>
      </>
    );

    return (
      <Drawer
        title="筛选条件"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={520}
        styles={{ body: { padding: '16px 24px' } }}
      >
        <Form
          form={searchForm}
          layout="vertical"
          onFinish={handleSearch}
          autoComplete="off"
        >
          <Row gutter={[16, 0]}>
            {allFields}
            <Col span={24} style={{ marginTop: 16 }}>
              <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
                <Button onClick={() => setDrawerOpen(false)}>取消</Button>
                <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button>
                <Button onClick={handleReset}>重置</Button>
              </Space>
            </Col>
          </Row>
        </Form>
      </Drawer>
    );
  };

  return (
    <>
      {renderSearchDrawer()}

      <Card size="small" className="table-card" bordered={false}>
        <div className="subtask-toolbar">
          <Space size={12}>
            <Button type="primary" icon={<PlusCircleOutlined />} onClick={onCreateClick}>新建任务</Button>
            <Button icon={<FilterOutlined />} onClick={() => setDrawerOpen(true)}>筛选</Button>
            <Button icon={<DownloadOutlined />} onClick={handleExport}>
              {selectedRowKeys.length > 0 ? `导出选中(${selectedRowKeys.length})` : '导出全部'}
            </Button>
          </Space>
          {pagination.total > 0 && (
            <div style={{
              display: 'flex', alignItems: 'center', gap: 16, padding: '4px 16px',
              background: 'linear-gradient(135deg, #f0f5ff 0%, #e6f4ff 100%)',
              borderRadius: 10, border: '1px solid #d6e8ff'
            }}>
              <span style={{ fontSize: 12, color: '#8c8c8c' }}>总计</span>
              <span style={{ fontSize: 18, fontWeight: 700, color: '#1890ff', fontFamily: "'SF Mono', Monaco, Consolas, monospace" }}>{pagination.total}</span>
              <span style={{ fontSize: 12, color: '#8c8c8c' }}>条任务</span>
            </div>
          )}
          <div style={{ flex: 1 }}></div>
          <Space size={12}>
            <Tooltip title="刷新">
              <Button icon={<ReloadOutlined />} onClick={() => fetchTasks()} shape="circle" />
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

        <div className="subtask-table-container">
          <Table
            columns={columns}
            dataSource={tasks || []}
            rowKey={(record) => { const t = record.task || record; return t.id; }}
            loading={loading}
            rowSelection={{
              selectedRowKeys,
              onChange: (keys) => setSelectedRowKeys(keys)
            }}
            expandable={undefined}
            scroll={{ x: 2250 }}
            pagination={false}
            size={tableSize}
          />
        </div>

        <div className="fixed-pagination-bar">
          <Pagination
            current={pagination.current}
            pageSize={pagination.pageSize || 10}
            total={pagination.total}
            showTotal={(t) => `共 ${t} 条`}
            onChange={handlePageChange}
            showSizeChanger
            pageSizeOptions={['10', '20', '50']}
            showQuickJumper
            size="default"
          />
        </div>
      </Card>
    </>
  );
};

export default TaskListTab;
