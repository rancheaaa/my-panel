import React, { useState, useEffect } from 'react';
import { Card, Select, Input, Button, Row, Col, Space, Spin, message, Tag, Progress, Tooltip } from 'antd';
import {
  FolderOpenOutlined,
  ReloadOutlined,
  SearchOutlined,
  CloudServerOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons';
import { listAgentRegistry, checkAgentDirectory } from '../../../api/agent';
import './dir-check-page.scss';

const formatMB = (mb) => {
  if (mb == null) return '-';
  if (mb >= 1024) return (mb / 1024).toFixed(1) + ' GB';
  return mb.toLocaleString() + ' MB';
};

const BoolTag = ({ value, trueText, falseText }) => {
  if (value == null) return <Tag color="#d9d9d9">未知</Tag>;
  return value
    ? <Tag color="success" icon={<CheckCircleOutlined />}>{trueText || '是'}</Tag>
    : <Tag color="error" icon={<CloseCircleOutlined />}>{falseText || '否'}</Tag>;
};

function ResultCard({ data }) {
  if (!data) return null;

  const hasError = data.errorMessage;
  const isOffline = data.agentOnline === false;
  const notExists = data.exists === false;

  return (
    <div className={`dcp-result-card ${hasError || isOffline || notExists ? 'has-error' : 'ok'}`}>
      <div className="dcp-card-header">
        <CloudServerOutlined style={{ color: '#1890ff', fontSize: 16, marginRight: 8 }} />
        <span className="dcp-card-title">节点信息</span>
        {data.agentName && <span className="dcp-card-agent">{data.agentName}</span>}
        {data.agentIp && data.agentPort && (
          <span className="dcp-card-addr">{data.agentIp}:{data.agentPort}</span>
        )}
        {isOffline && <Tag color="error" style={{ marginLeft: 8 }}>离线</Tag>}
      </div>

      <div className="dcp-card-path">
        <FolderOpenOutlined style={{ marginRight: 6, color: '#fa8c16' }} />
        <span className="dcp-path-text">{data.dirPath || '-'}</span>
      </div>

      {hasError && !isOffline ? (
        <div className="dcp-card-error">
          <ExclamationCircleOutlined style={{ marginRight: 6 }} />
          {data.errorMessage}
        </div>
      ) : isOffline ? (
        <div className="dcp-card-error">
          <ExclamationCircleOutlined style={{ marginRight: 6 }} />
          节点离线，无法检测
        </div>
      ) : (
        <>
          <div className="dcp-card-section">
            <div className="dcp-section-label">目录状态</div>
            <div className="dcp-section-content">
              <div className="dcp-item">
                <span className="dcp-item-label">是否存在</span>
                <BoolTag value={data.exists} trueText="存在" falseText="不存在" />
              </div>
              {data.exists && (
                <div className="dcp-item">
                  <span className="dcp-item-label">是否为目录</span>
                  <BoolTag value={data.isDirectory} trueText="是" falseText="否" falseColor="warning" />
                </div>
              )}
            </div>
          </div>

          {data.exists && (
            <>
              <div className="dcp-card-section">
                <div className="dcp-section-label">访问权限</div>
                <div className="dcp-section-content">
                  <div className="dcp-item">
                    <span className="dcp-item-label">读取</span>
                    <BoolTag value={data.canRead} trueText="可读" falseText="不可读" />
                  </div>
                  <div className="dcp-item">
                    <span className="dcp-item-label">写入</span>
                    <BoolTag value={data.canWrite} trueText="可写" falseText="不可写" />
                  </div>
                  <div className="dcp-item">
                    <span className="dcp-item-label">执行</span>
                    <BoolTag value={data.canExecute} trueText="可执行" falseText="不可执行" />
                  </div>
                  {data.posixPermissions && (
                    <div className="dcp-item">
                      <span className="dcp-item-label">权限码</span>
                      <code className="dcp-perm-code">{data.posixPermissions}</code>
                    </div>
                  )}
                </div>
              </div>

              <div className="dcp-card-section">
                <div className="dcp-section-label">磁盘空间</div>
                <div className="dcp-section-content">
                  <div className="dcp-item">
                    <span className="dcp-item-label">总空间</span>
                    <span className="dcp-item-value">{formatMB(data.diskTotalMB)}</span>
                  </div>
                  <div className="dcp-item">
                    <span className="dcp-item-label">可用空间</span>
                    <span className="dcp-item-value">{formatMB(data.diskUsableMB)}</span>
                  </div>
                  <div className="dcp-item">
                    <span className="dcp-item-label">剩余空间</span>
                    <span className="dcp-item-value">{formatMB(data.diskFreeMB)}</span>
                  </div>
                  <div className="dcp-item">
                    <span className="dcp-item-label">空间充足</span>
                    <BoolTag value={data.diskSufficient} trueText="充足" falseText="不足" />
                  </div>
                  {data.diskTotalMB > 0 && (
                    <div className="dcp-disk-bar">
                      <Progress
                        percent={Math.round(((data.diskTotalMB - data.diskUsableMB) / data.diskTotalMB) * 100)}
                        size="small"
                        strokeColor={data.diskUsableMB < 100 ? '#ff4d4f' : '#52c41a'}
                        format={() => `已用 ${formatMB(data.diskTotalMB - data.diskUsableMB)}`}
                      />
                    </div>
                  )}
                </div>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}

export default function DirCheckPage() {
  const [agentList, setAgentList] = useState([]);
  const [selectedAgentId, setSelectedAgentId] = useState(null);
  const [dirPath, setDirPath] = useState('');
  const [loading, setLoading] = useState(false);
  const [agentLoading, setAgentLoading] = useState(false);
  const [result, setResult] = useState(null);

  useEffect(() => {
    loadAgents();
  }, []);

  const loadAgents = async () => {
    setAgentLoading(true);
    try {
      const res = await listAgentRegistry({ pageNum: 1, pageSize: 1000 });
      if (res.code === 200 && res.data?.rows) {
        setAgentList(res.data.rows);
      }
    } catch (e) {
      console.error('加载Agent列表失败', e);
    } finally {
      setAgentLoading(false);
    }
  };

  const runCheck = async () => {
    if (!selectedAgentId) {
      message.warning('请先选择一个Agent节点');
      return;
    }
    if (!dirPath || !dirPath.trim()) {
      message.warning('请输入要检测的目录路径');
      return;
    }

    setLoading(true);
    setResult(null);
    try {
      const res = await checkAgentDirectory(selectedAgentId, dirPath.trim());
      if (res.code === 200) {
        setResult(res.data);
      } else {
        message.error(res.msg || '目录检测失败');
      }
    } catch (e) {
      console.error('目录检测失败', e);
      message.error('目录检测失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDirPathKeyDown = (e) => {
    if (e.key === 'Enter') {
      runCheck();
    }
  };

  return (
    <div className="dir-check-page">
      <Card className="dcp-select-card" size="small">
        <Row gutter={[16, 12]} align="middle">
          <Col xs={24} sm={10} md={8}>
            <div className="dcp-label">选择节点</div>
            <Select
              showSearch
              placeholder="搜索并选择Agent节点 (例如: dell@172.20.10.5:7777)"
              value={selectedAgentId}
              onChange={(val) => { setSelectedAgentId(val); setResult(null); }}
              style={{ width: '100%' }}
              loading={agentLoading}
              optionFilterProp="label"
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
            >
              {agentList.map(agent => (
                <Select.Option
                  key={agent.id}
                  value={agent.id}
                  label={agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}
                >
                  <Space size="small">
                    <CloudServerOutlined style={{ color: '#52c41a', fontSize: 12 }} />
                    <span style={{ fontWeight: 500 }}>{agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}</span>
                    {agent.nodeStatus === 1 && <Tag color="green" style={{ fontSize: 10 }}>在线</Tag>}
                    {agent.nodeStatus === 0 && <Tag color="red" style={{ fontSize: 10 }}>离线</Tag>}
                    {agent.osType && <Tag color="blue" style={{ fontSize: 10 }}>{agent.osType}</Tag>}
                  </Space>
                </Select.Option>
              ))}
            </Select>
          </Col>
          <Col xs={24} sm={10} md={10}>
            <div className="dcp-label">目录路径</div>
            <Input
              placeholder="例如：/data/upload 或 D:\\workspace\\logs"
              value={dirPath}
              onChange={(e) => setDirPath(e.target.value)}
              onKeyDown={handleDirPathKeyDown}
              allowClear
            />
          </Col>
          <Col xs={24} sm={4} md={6}>
            <div className="dcp-label">&nbsp;</div>
            <Space>
              <Button
                type="primary"
                icon={<SearchOutlined />}
                onClick={runCheck}
                loading={loading}
              >
                开始检测
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={runCheck}
                loading={loading}
                disabled={!result}
              >
                重新检测
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Card className="dcp-result-card-wrapper" size="small">
        {!result && !loading && (
          <div className="dcp-empty">
            <FolderOpenOutlined style={{ fontSize: 48, color: '#d9d9d9', marginBottom: 16 }} />
            <div style={{ color: '#bfbfbf', fontSize: 14 }}>
              选择Agent节点并输入目录路径后点击"开始检测"
            </div>
          </div>
        )}

        {loading && !result && (
          <div className="dcp-empty">
            <Spin size="large" tip="正在检测目录...">
              <div style={{ height: 200 }} />
            </Spin>
          </div>
        )}

        {result && <ResultCard data={result} />}
      </Card>
    </div>
  );
}
