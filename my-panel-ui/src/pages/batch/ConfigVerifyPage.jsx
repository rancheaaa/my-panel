import React, { useState, useEffect } from 'react';
import { Card, Select, Button, Table, Tag, Typography, message, Descriptions, Alert, Popconfirm, Tooltip } from 'antd';
import { CloudServerOutlined, SafetyCertificateOutlined, CloudUploadOutlined, CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined, DesktopOutlined, GlobalOutlined, DiffOutlined } from '@ant-design/icons';
import { listAgentRegistry } from '../../api/agent';
import { batchApi } from '../../api/batch';
import './index.scss';

const { Text, Title } = Typography;

const ConfigVerifyPage = () => {
  const [agentList, setAgentList] = useState([]);
  const [selectedAgentId, setSelectedAgentId] = useState(null);
  const [agentListLoading, setAgentListLoading] = useState(false);
  const [verifyLoading, setVerifyLoading] = useState(false);
  const [pushLoading, setPushLoading] = useState(false);
  const [verifyResult, setVerifyResult] = useState(null);

  useEffect(() => {
    loadAgentList();
  }, []);

  const loadAgentList = async () => {
    setAgentListLoading(true);
    try {
      const res = await listAgentRegistry({ pageNum: 1, pageSize: 1000 });
      if (res.data?.rows) {
        setAgentList(res.data.rows);
      }
    } catch (error) {
      message.error('加载节点列表失败');
    } finally {
      setAgentListLoading(false);
    }
  };

  const handleVerify = async () => {
    if (!selectedAgentId) {
      message.warning('请先选择一个源节点');
      return;
    }
    setVerifyLoading(true);
    setVerifyResult(null);
    try {
      const res = await batchApi.verifyAgentConfig(selectedAgentId);
      if (res?.code === 200 && res?.data) {
        setVerifyResult(res.data);
        if (res.data.allMatch) {
          message.success('配置校验通过，所有任务配置完全一致');
        } else {
          message.warning('配置校验发现差异，请查看详细结果');
        }
      } else {
        message.error(res?.msg || '校验失败');
      }
    } catch (error) {
      console.error('校验失败:', error);
      message.error('校验失败: ' + (error.message || '未知错误'));
    } finally {
      setVerifyLoading(false);
    }
  };

  const handlePush = async () => {
    if (!selectedAgentId) return;
    setPushLoading(true);
    try {
      const res = await batchApi.pushAgentConfig(selectedAgentId);
      if (res?.code === 200) {
        message.success(`配置推送成功，已更新 ${res.data} 个文件`);
        // 推送后自动重新校验
        setTimeout(() => handleVerify(), 500);
      } else {
        message.error(res?.msg || '推送失败');
      }
    } catch (error) {
      console.error('推送失败:', error);
      message.error('推送失败: ' + (error.message || '未知错误'));
    } finally {
      setPushLoading(false);
    }
  };

  const selectedAgent = agentList.find(a => a.id === selectedAgentId);

  const taskColumns = [
    {
      title: '任务ID',
      dataIndex: 'taskId',
      key: 'taskId',
      width: 120,
      render: (text) => <Text code style={{ fontSize: 12, fontFamily: 'monospace' }}>{text}</Text>
    },
    {
      title: '任务名称',
      dataIndex: 'taskName',
      key: 'taskName',
      width: 180,
      render: (text) => text ? <Text style={{ fontSize: 12 }}>{text}</Text> : <Text type="secondary">-</Text>
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (_, record) => {
        if (!record.exists) {
          return <Tag color="red" icon={<CloseCircleOutlined />}>Agent端缺失</Tag>;
        }
        return record.match
          ? <Tag color="green" icon={<CheckCircleOutlined />}>一致</Tag>
          : <Tag color="orange" icon={<ExclamationCircleOutlined />}>{record.diffs?.length || 0} 个字段差异</Tag>;
      }
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => {
        if (record.match) return <Text type="secondary" style={{ fontSize: 12 }}>-</Text>;
        return (
          <Tooltip title="查看字段差异">
            <Tag color="blue" icon={<DiffOutlined />} style={{ cursor: 'pointer' }}>
              查看详情
            </Tag>
          </Tooltip>
        );
      }
    }
  ];

  const expandedRowRender = (record) => {
    if (record.match || !record.diffs || record.diffs.length === 0) {
      return <Text type="secondary" style={{ padding: '8px 0' }}>所有字段一致</Text>;
    }
    const diffColumns = [
      {
        title: '字段',
        dataIndex: 'fieldPath',
        key: 'fieldPath',
        width: 260,
        render: (text) => <Text code style={{ fontSize: 11 }}>{text}</Text>
      },
      {
        title: '服务端值',
        dataIndex: 'serverValue',
        key: 'serverValue',
        width: 300,
        render: (text) => text ? <Text style={{ fontSize: 11 }}>{String(text)}</Text> : <Text type="secondary">-</Text>
      },
      {
        title: 'Agent端值',
        dataIndex: 'agentValue',
        key: 'agentValue',
        width: 300,
        render: (text) => text ? <Text style={{ fontSize: 11 }}>{String(text)}</Text> : <Text type="secondary">-</Text>
      }
    ];
    return (
      <Table
        columns={diffColumns}
        dataSource={record.diffs}
        rowKey="fieldPath"
        pagination={false}
        size="small"
        bordered
        showHeader={true}
        style={{ margin: '0 16px 8px 16px', borderRadius: 6 }}
      />
    );
  };

  const diffCount = verifyResult?.tasks?.filter(t => !t.match).length || 0;

  return (
    <div className="batch-subtask-page">
      <div className="batch-subtask-container" style={{ padding: '24px 32px' }}>
        <Card
          bordered={false}
          style={{ borderRadius: 14, border: '1px solid #e4e9f0', boxShadow: '0 2px 6px rgba(0,0,0,0.05)' }}
        >
          <div style={{ marginBottom: 24 }}>
            <Title level={4} style={{ margin: 0, marginBottom: 8 }}>
              <SafetyCertificateOutlined style={{ marginRight: 8, color: '#1890ff' }} />
              配置校验
            </Title>
            <Text type="secondary">
              选择一个源节点，逐字段对比服务端与 Agent 端的传输任务配置，支持强制推送更新
            </Text>
          </div>

          <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
            {/* 左侧：节点选择 */}
            <div style={{ flex: 1, minWidth: 360 }}>
              <div style={{
                padding: '20px 24px',
                background: 'linear-gradient(135deg, #f6ffed 0%, #f0ffe8 100%)',
                borderRadius: 12,
                border: '1px solid #d3f5b9'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
                  <CloudServerOutlined style={{ fontSize: 16, color: '#52c41a' }} />
                  <Text strong style={{ fontSize: 14, color: '#389e0d' }}>选择源节点</Text>
                </div>
                <Select
                  style={{ width: '100%' }}
                  placeholder="请选择源节点"
                  showSearch
                  loading={agentListLoading}
                  value={selectedAgentId}
                  onChange={(val) => { setSelectedAgentId(val); setVerifyResult(null); }}
                  filterOption={(input, option) => {
                    const agent = agentList[option.value];
                    if (!agent) return false;
                    const searchStr = `${agent.nodeName || ''} ${agent.agentIp || ''} ${agent.id || ''}`.toLowerCase();
                    return searchStr.includes(input.toLowerCase());
                  }}
                  options={agentList.map((agent) => ({
                    value: agent.id,
                    label: (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <CloudServerOutlined style={{ color: '#52c41a', fontSize: 13 }} />
                        <span style={{ fontWeight: 500 }}>{agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}</span>
                        <Tag color={agent.nodeStatus === 1 ? 'green' : 'default'} style={{ fontSize: 10, margin: 0, lineHeight: '18px' }}>
                          {agent.nodeStatus === 1 ? '在线' : '离线'}
                        </Tag>
                        {agent.osType && (
                          <Tag color="blue" style={{ fontSize: 10, margin: 0, lineHeight: '18px' }}>
                            <DesktopOutlined /> {agent.osType}
                          </Tag>
                        )}
                      </div>
                    )
                  }))}
                />
              </div>
            </div>

            {/* 右侧：节点信息 */}
            <div style={{ flex: 1, minWidth: 360 }}>
              {selectedAgent ? (
                <div style={{
                  padding: '20px 24px',
                  background: '#fafbfc',
                  borderRadius: 12,
                  border: '1px solid #eee'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
                    <ExclamationCircleOutlined style={{ fontSize: 16, color: '#1890ff' }} />
                    <Text strong style={{ fontSize: 14 }}>节点信息</Text>
                  </div>
                  <Descriptions column={1} size="small" colon={false} labelStyle={{ color: '#8c8c8c', fontSize: 12 }} contentStyle={{ fontSize: 13 }}>
                    <Descriptions.Item label="节点ID">{selectedAgent.id}</Descriptions.Item>
                    <Descriptions.Item label="节点名称">{selectedAgent.nodeName || '-'}</Descriptions.Item>
                    <Descriptions.Item label="IP地址">{selectedAgent.agentIp}:{selectedAgent.agentPort}</Descriptions.Item>
                    <Descriptions.Item label="操作系统">{selectedAgent.osType || '-'}</Descriptions.Item>
                    <Descriptions.Item label="状态">
                      <Tag color={selectedAgent.nodeStatus === 1 ? 'green' : 'default'}>
                        {selectedAgent.nodeStatus === 1 ? '在线' : '离线'}
                      </Tag>
                    </Descriptions.Item>
                  </Descriptions>
                </div>
              ) : (
                <div style={{
                  padding: '40px 24px',
                  background: '#fafbfc',
                  borderRadius: 12,
                  border: '1px dashed #d9d9d9',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 8
                }}>
                  <GlobalOutlined style={{ fontSize: 32, color: '#d9d9d9' }} />
                  <Text type="secondary">请先选择一个源节点</Text>
                </div>
              )}
            </div>
          </div>

          {/* 操作按钮 */}
          <div style={{ marginTop: 24, display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
            <Button
              icon={<SafetyCertificateOutlined />}
              loading={verifyLoading}
              disabled={!selectedAgentId}
              onClick={handleVerify}
              style={{ fontWeight: 600, height: 40, padding: '0 24px', borderRadius: 8 }}
            >
              校验配置
            </Button>
            <Popconfirm
              title="确定强制推送配置？"
              description="此操作将覆盖Agent端所有配置文件，请确认"
              onConfirm={handlePush}
              okText="确定推送"
              cancelText="取消"
            >
              <Button
                type="primary"
                danger
                icon={<CloudUploadOutlined />}
                loading={pushLoading}
                disabled={!selectedAgentId}
                style={{ fontWeight: 600, height: 40, padding: '0 24px', borderRadius: 8 }}
              >
                强制推送配置
              </Button>
            </Popconfirm>
          </div>

          {/* 校验结果 */}
          {verifyResult && (
            <div style={{ marginTop: 24 }}>
              <Alert
                style={{ marginBottom: 16, borderRadius: 8 }}
                type={verifyResult.allMatch ? 'success' : 'warning'}
                showIcon
                icon={verifyResult.allMatch ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
                message={verifyResult.allMatch
                  ? `节点 ${verifyResult.sourceAgentName} 配置校验通过，共 ${verifyResult.tasks?.length || 0} 个任务全部一致`
                  : `节点 ${verifyResult.sourceAgentName} 配置校验发现差异，共 ${diffCount} 个任务不一致`}
              />
              <Table
                columns={taskColumns}
                dataSource={verifyResult.tasks || []}
                rowKey="taskId"
                pagination={false}
                size="small"
                bordered
                expandable={{
                  expandedRowRender,
                  rowExpandable: (record) => !record.match,
                  expandRowByClick: true
                }}
                rowClassName={(record) => record.match ? '' : 'row-error'}
                style={{ borderRadius: 8, overflow: 'hidden' }}
              />
            </div>
          )}
        </Card>
      </div>
    </div>
  );
};

export default ConfigVerifyPage;
