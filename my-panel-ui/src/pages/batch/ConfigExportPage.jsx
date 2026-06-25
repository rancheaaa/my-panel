import React, { useState, useEffect } from 'react';
import { Card, Select, Button, Tag, Typography, message, Descriptions, Alert } from 'antd';
import { CloudServerOutlined, DownloadOutlined, InfoCircleOutlined, DesktopOutlined, GlobalOutlined } from '@ant-design/icons';
import { listAgentRegistry } from '../../api/agent';
import { batchApi } from '../../api/batch';
import './index.scss';

const { Text, Title } = Typography;

const ConfigExportPage = () => {
  const [agentList, setAgentList] = useState([]);
  const [selectedAgentId, setSelectedAgentId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [agentListLoading, setAgentListLoading] = useState(false);

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

  const handleExport = async () => {
    if (!selectedAgentId) {
      message.warning('请先选择一个源节点');
      return;
    }
    setLoading(true);
    try {
      const res = await batchApi.exportAgentConfig(selectedAgentId);
      const blob = new Blob([res], { type: 'application/zip' });
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      const selectedAgent = agentList.find(a => a.id === selectedAgentId);
      const agentLabel = selectedAgent?.nodeName || selectedAgent?.agentIp || selectedAgentId;
      link.download = `batch-config-${agentLabel}-${new Date().getTime()}.zip`;
      link.click();
      window.URL.revokeObjectURL(link.href);
      message.success('配置文件导出成功');
    } catch (error) {
      console.error('导出失败:', error);
      message.error('导出失败，请检查该节点是否有传输任务');
    } finally {
      setLoading(false);
    }
  };

  const selectedAgent = agentList.find(a => a.id === selectedAgentId);

  return (
    <div className="batch-subtask-page">
      <div className="batch-subtask-container" style={{ padding: '24px 32px' }}>
        <Card
          bordered={false}
          style={{ borderRadius: 14, border: '1px solid #e4e9f0', boxShadow: '0 2px 6px rgba(0,0,0,0.05)' }}
        >
          <div style={{ marginBottom: 24 }}>
            <Title level={4} style={{ margin: 0, marginBottom: 8 }}>
              <DownloadOutlined style={{ marginRight: 8, color: '#1890ff' }} />
              Agent 配置文件导出
            </Title>
            <Text type="secondary">
              选择一个源节点，导出该节点的所有传输任务配置文件（tasks.meta.json + task_*.json），打包为 ZIP 下载
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
                  onChange={setSelectedAgentId}
                  filterOption={(input, option) => {
                    const agent = agentList[option.value];
                    if (!agent) return false;
                    const searchStr = `${agent.nodeName || ''} ${agent.agentIp || ''} ${agent.id || ''}`.toLowerCase();
                    return searchStr.includes(input.toLowerCase());
                  }}
                  options={agentList.map(agent => ({
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
                    <InfoCircleOutlined style={{ fontSize: 16, color: '#1890ff' }} />
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

          {/* 说明 */}
          <Alert
            style={{ marginTop: 20, borderRadius: 8 }}
            type="info"
            showIcon
            message="导出说明"
            description={
              <ul style={{ margin: 0, paddingLeft: 16, lineHeight: '24px' }}>
                <li>导出的 ZIP 包包含 <Text code>tasks.meta.json</Text>（元数据索引）和 <Text code>task_{'{taskId}'}.json</Text>（各任务配置）</li>
                <li>配置文件格式与 Agent 端 <Text code>batch-config</Text> 目录下的文件完全一致</li>
                <li>可将解压后的文件直接复制到 Agent 的 batch-config 目录下使用</li>
              </ul>
            }
          />

          {/* 操作按钮 */}
          <div style={{ marginTop: 24, display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
            <Button onClick={() => { setSelectedAgentId(null); }}>
              重置
            </Button>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              loading={loading}
              disabled={!selectedAgentId}
              onClick={handleExport}
              style={{
                background: 'linear-gradient(135deg, #1890ff 0%, #096dd9 100%)',
                border: 'none',
                boxShadow: '0 2px 6px rgba(24, 144, 255, 0.25)',
                fontWeight: 600,
                height: 40,
                padding: '0 24px',
                borderRadius: 8
              }}
            >
              导出配置文件
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
};

export default ConfigExportPage;
