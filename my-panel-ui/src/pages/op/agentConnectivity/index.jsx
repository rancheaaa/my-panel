import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react';
import { Card, Select, Button, Row, Col, Space, message, Tooltip, Table, Tag } from 'antd';
import {
  ApiOutlined,
  SwapOutlined,
  ReloadOutlined,
  CloudServerOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  TableOutlined,
  ApartmentOutlined
} from '@ant-design/icons';
import ReactFlow, { Background, Controls, Handle, Position, useReactFlow, getBezierPath, ReactFlowProvider } from 'reactflow';
import 'reactflow/dist/style.css';
import { batchApi } from '../../../api/batch';
import { listAgentRegistry } from '../../../api/agent';
import './connectivity-page.scss';

const nodeTypes = { agentNode: AgentNode };
const edgeTypes = { animated: AnimatedEdge };

function AgentNode({ data }) {
  const { label, ip, port, isOnline, isEnabled, exists, errorMsg, onClick, isSelected } = data;
  const fullLabel = label || '';
  const displayLabel = fullLabel.length > 20 ? fullLabel.substring(0, 20) + '...' : fullLabel;

  let borderColor = '#d9d9d9';
  let bgColor = '#fafafa';
  let statusColor = '#999';
  let statusText = '未知';

  if (!exists) {
    borderColor = '#ff4d4f'; bgColor = '#fff2f0'; statusColor = '#ff4d4f'; statusText = '不存在';
  } else if (!isOnline) {
    borderColor = '#faad14'; bgColor = '#fffbe6'; statusColor = '#faad14'; statusText = '离线';
  } else if (!isEnabled) {
    borderColor = '#faad14'; bgColor = '#fffbe6'; statusColor = '#faad14'; statusText = '已禁用';
  } else {
    borderColor = '#52c41a'; bgColor = '#f6ffed'; statusColor = '#52c41a'; statusText = '正常';
  }

  const tooltipContent = (
    <div>
      <div style={{ fontWeight: 600, marginBottom: 4 }}>{fullLabel}</div>
      {ip && port && <div style={{ fontSize: 12, color: '#999' }}>{ip}:{port}</div>}
      {errorMsg && <div style={{ fontSize: 12, color: '#ff4d4f', marginTop: 4 }}>{errorMsg}</div>}
    </div>
  );

  const nodeClass = `conn-node ${isSelected ? 'conn-node-selected' : ''} ${onClick ? 'conn-node-clickable' : ''}`;

  return (
    <Tooltip title={tooltipContent} placement="top" mouseEnterDelay={0.3}>
      <div className={nodeClass} style={{ borderColor, background: bgColor }} onClick={onClick}>
        <Handle type="source" position={Position.Right} id="out-upper" className="conn-handle conn-handle-upper" />
        <Handle type="target" position={Position.Right} id="in-lower" className="conn-handle conn-handle-lower" />
        <Handle type="target" position={Position.Left} id="in-upper" className="conn-handle conn-handle-upper" />
        <Handle type="source" position={Position.Left} id="out-lower" className="conn-handle conn-handle-lower" />
        <div className="conn-node-header">
          <div className="conn-node-icon"><CloudServerOutlined /></div>
          <div className="conn-node-title">{displayLabel}</div>
          <Tag color={statusColor} className="conn-node-status-tag">{statusText}</Tag>
        </div>
        {ip && port && <div className="conn-node-info">{ip}:{port}</div>}
        {errorMsg && <div className="conn-node-error"><CloseCircleOutlined /> {errorMsg.length > 20 ? errorMsg.substring(0, 20) + '...' : errorMsg}</div>}
      </div>
    </Tooltip>
  );
}

function AnimatedEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data
}) {
  const [edgePath, labelX, labelY] = useMemo(() => {
    return getBezierPath({
      sourceX,
      sourceY,
      sourcePosition,
      targetX,
      targetY,
      targetPosition,
      curvature: 0.35
    });
  }, [sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition]);

  const reachable = data?.reachable;
  const checking = data?.checking;
  const label = data?.label || '';

  let strokeColor = '#d9d9d9';
  let strokeWidth = 2;
  let animClass = '';
  let strokeDasharray;

  if (checking) {
    strokeColor = '#1890ff'; strokeWidth = 2.5; animClass = 'conn-edge-checking'; strokeDasharray = '8 4';
  } else if (reachable === true) {
    strokeColor = '#52c41a'; strokeWidth = 2.5; animClass = 'conn-edge-success'; strokeDasharray = '8 4';
  } else if (reachable === false) {
    strokeColor = '#ff4d4f'; strokeWidth = 2; animClass = ''; strokeDasharray = undefined;
  }

  const markerId = id ? `arrow-${id}` : 'arrow-default';

  return (
    <>
      <defs>
        <marker id={markerId} viewBox="0 0 10 10" refX="9" refY="5"
          markerWidth="8" markerHeight="8" orient="auto-start-reverse">
          <path d="M 0 1 L 9 5 L 0 9 z" fill={strokeColor} />
        </marker>
      </defs>
      <path d={edgePath} fill="none" stroke={strokeColor} strokeWidth={strokeWidth}
        className={`conn-edge-path ${animClass}`} strokeLinecap="round"
        strokeDasharray={strokeDasharray}
        markerEnd={`url(#${markerId})`} />
      {reachable === true && (
        <path d={edgePath} fill="none" stroke="#52c41a" strokeWidth={6}
          className="conn-edge-glow" opacity={0.2} strokeLinecap="round" />
      )}
      <g className="conn-edge-label-group" transform={`translate(${labelX}, ${labelY})`}>
        <rect x={-40} y={-12} width={80} height={24} rx={12} fill="white" stroke="#e8e8e8" strokeWidth={1} opacity={0.95} />
        <text textAnchor="middle" dominantBaseline="central" fontSize={11} fill="#595959" fontWeight={500}>
          {checking ? '探测中...' : label}
        </text>
      </g>
    </>
  );
}

function ConnectivityFlowView({ nodes, edges }) {
  const { fitView } = useReactFlow();

  useEffect(() => {
    if (nodes.length > 0) {
      setTimeout(() => fitView({ padding: 0.15, duration: 400 }), 100);
    }
  }, [nodes, fitView]);

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      fitView
      fitViewOptions={{ padding: 0.15 }}
      proOptions={{ hideAttribution: true }}
      minZoom={0.3}
      maxZoom={1.5}
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable={false}
    >
      <Background color="#e8e8e8" gap={16} size={1} />
      <Controls showInteractive={false} />
    </ReactFlow>
  );
}

export default function AgentConnectivityPage() {
  const [agentList, setAgentList] = useState([]);
  const [sourceNodeName, setSourceNodeName] = useState(null);
  const [targetNodeName, setTargetNodeName] = useState(null);
  const [results, setResults] = useState({});
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState({});
  const [selectedTarget, setSelectedTarget] = useState(0);
  const [viewMode, setViewMode] = useState('flow');

  useEffect(() => {
    loadAgents();
  }, []);

  const loadAgents = async () => {
    try {
      const res = await listAgentRegistry({ pageNum: 1, pageSize: 1000 });
      if (res.data?.rows) {
        setAgentList(res.data.rows.filter(a => a.nodeStatus === 1));
      }
    } catch (e) {
      console.error('加载Agent列表失败', e);
    }
  };

  const handleSwap = () => {
    const tmp = sourceNodeName;
    setSourceNodeName(targetNodeName);
    setTargetNodeName(tmp);
  };

  const runCheck = useCallback(async () => {
    if (!sourceNodeName || !targetNodeName) {
      message.warning('请选择源节点和目标节点');
      return;
    }
    if (sourceNodeName === targetNodeName) {
      message.warning('源节点和目标节点不能相同');
      return;
    }

    setLoading(true);
    setResults({});
    setChecking({ forward: true, reverse: true });

    try {
      const res = await batchApi.checkConnectivityOp(sourceNodeName, targetNodeName);
      if (res.code === 200 && res.data) {
        const d = res.data;
        const result = {};
        result.sourceExists = d.sourceExists;
        result.sourceOnline = d.sourceOnline;
        result.sourceEnabled = d.sourceEnabled;
        result.sourceIp = d.sourceIp;
        result.sourcePort = d.sourcePort;
        result.targetExists = d.targetExists;
        result.targetOnline = d.targetOnline;
        result.targetEnabled = d.targetEnabled;
        result.targetIp = d.targetIp;
        result.targetPort = d.targetPort;

        if (d.sourceToTarget) {
          result.sourceToTarget = d.sourceToTarget;
        }
        if (d.targetToSource) {
          result.targetToSource = d.targetToSource;
        }
        if (d.checkDetails) result.checkDetails = d.checkDetails;
        if (d.reverseCheckDetails) result.reverseCheckDetails = d.reverseCheckDetails;
        if (d.connectivityStatus) result.connectivityStatus = d.connectivityStatus;
        if (d.failureReason) result.failureReason = d.failureReason;
        if (d.reverseStatus) result.reverseStatus = d.reverseStatus;
        if (d.reverseFailureReason) result.reverseFailureReason = d.reverseFailureReason;

        setResults({ forward: result, target_0: result });
      }
    } catch (e) {
      console.error('连通性检测失败', e);
      message.error('连通性检测失败');
    } finally {
      setChecking({});
      setLoading(false);
    }
  }, [sourceNodeName, targetNodeName]);

  const getEdgeResult = (r, direction) => {
    if (!r) return { reachable: undefined, failureReason: null };
    if (direction === 'forward') {
      if (r.sourceToTarget) return r.sourceToTarget;
      if (r.connectivityStatus && r.connectivityStatus !== 'REACHABLE') {
        return { reachable: false, failureReason: r.failureReason || r.connectivityStatus };
      }
      return { reachable: undefined, failureReason: null };
    }
    if (r.targetToSource) return r.targetToSource;
    if (r.reverseStatus && r.reverseStatus !== 'REACHABLE') {
      return { reachable: false, failureReason: r.reverseFailureReason || r.reverseStatus };
    }
    return { reachable: undefined, failureReason: null };
  };

  const nodes = useMemo(() => {
    const r = results.forward;
    const sourceExists = r?.sourceExists ?? true;
    const sourceOnline = r?.sourceOnline ?? true;
    const sourceEnabled = r?.sourceEnabled ?? true;
    const sourceError = sourceExists ? (!sourceOnline ? '节点离线' : !sourceEnabled ? '节点已禁用' : null) : '节点不存在';

    const targetExists = r?.targetExists ?? true;
    const targetOnline = r?.targetOnline ?? true;
    const targetEnabled = r?.targetEnabled ?? true;
    const targetError = targetExists ? (!targetOnline ? '节点离线' : !targetEnabled ? '节点已禁用' : null) : '节点不存在';

    return [
      {
        id: 'source',
        type: 'agentNode',
        position: { x: 60, y: 100 },
        data: {
          label: sourceNodeName || '源节点',
          ip: r?.sourceIp,
          port: r?.sourcePort,
          role: 'source',
          exists: sourceExists,
          isOnline: sourceOnline,
          isEnabled: sourceEnabled,
          errorMsg: sourceError
        },
        draggable: false
      },
      {
        id: 'target_0',
        type: 'agentNode',
        position: { x: 580, y: 100 },
        data: {
          label: targetNodeName || '目标节点',
          ip: r?.targetIp,
          port: r?.targetPort,
          role: 'target',
          exists: targetExists,
          isOnline: targetOnline,
          isEnabled: targetEnabled,
          errorMsg: targetError,
          onClick: () => setSelectedTarget(0),
          isSelected: selectedTarget === 0
        },
        draggable: false
      }
    ];
  }, [sourceNodeName, targetNodeName, results, selectedTarget]);

  const edges = useMemo(() => {
    const r = results.target_0;
    const fwd = getEdgeResult(r, 'forward');
    const rev = getEdgeResult(r, 'reverse');

    return [
      {
        id: 'source-target',
        source: 'source',
        target: 'target_0',
        sourceHandle: 'out-upper',
        targetHandle: 'in-upper',
        type: 'animated',
        data: { direction: 'forward', label: '发送→接收', reachable: fwd.reachable, checking: checking.forward, failureReason: fwd.failureReason }
      },
      {
        id: 'target-source',
        source: 'target_0',
        target: 'source',
        sourceHandle: 'out-lower',
        targetHandle: 'in-lower',
        type: 'animated',
        data: { direction: 'reverse', label: '接收→发送', reachable: rev.reachable, checking: checking.reverse, failureReason: rev.failureReason }
      }
    ];
  }, [results, checking]);

  const currentDetails = useMemo(() => {
    const r = results.target_0;
    if (!r) return { forward: [], reverse: [] };
    return {
      forward: r.checkDetails || [],
      reverse: r.reverseCheckDetails || []
    };
  }, [results]);

  const tableColumns = useMemo(() => [
    { title: '发送节点', dataIndex: 'source', key: 'source', align: 'center', width: 180 },
    { title: '接收节点', dataIndex: 'target', key: 'target', align: 'center', width: 180 },
    {
      title: '发送→接收', dataIndex: 'forward', key: 'forward', align: 'center', width: 140,
      render: (val) => {
        if (val === undefined) return <Tag color="#d9d9d9">待检测</Tag>;
        return val ? <Tag color="success" icon={<CheckCircleOutlined />}>可达</Tag> : <Tag color="error" icon={<CloseCircleOutlined />}>不可达</Tag>;
      }
    },
    {
      title: '接收→发送', dataIndex: 'reverse', key: 'reverse', align: 'center', width: 140,
      render: (val) => {
        if (val === undefined) return <Tag color="#d9d9d9">待检测</Tag>;
        return val ? <Tag color="success" icon={<CheckCircleOutlined />}>可达</Tag> : <Tag color="error" icon={<CloseCircleOutlined />}>不可达</Tag>;
      }
    },
    {
      title: '失败原因', dataIndex: 'failureReason', key: 'failureReason', align: 'center',
      render: (text) => text || '-'
    }
  ], []);

  const tableData = useMemo(() => {
    const r = results.target_0;
    const fwd = getEdgeResult(r, 'forward');
    const rev = getEdgeResult(r, 'reverse');
    const reason = fwd.failureReason || rev.failureReason || null;
    return [{
      key: 0,
      source: sourceNodeName || '-',
      target: targetNodeName || '-',
      forward: fwd.reachable,
      reverse: rev.reachable,
      failureReason: reason,
      forwardDetails: r?.checkDetails || [],
      reverseDetails: r?.reverseCheckDetails || []
    }];
  }, [results, sourceNodeName, targetNodeName]);

  const hasResults = Object.keys(results).length > 0;

  return (
    <div className="agent-conn-page">
      <Card className="agent-conn-select-card" size="small">
        <Row gutter={16} align="middle">
          <Col flex="1">
            <div className="agent-conn-label">源节点</div>
            <Select
              showSearch
              placeholder="搜索并选择源节点 (例如: dell@172.20.10.5:7777)"
              value={sourceNodeName}
              onChange={setSourceNodeName}
              style={{ width: '100%' }}
              optionFilterProp="label"
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
            >
              {agentList.map(agent => (
                <Select.Option
                  key={agent.id}
                  value={agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}
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
          <Col>
            <Button icon={<SwapOutlined />} onClick={handleSwap} className="agent-conn-swap-btn" title="交换节点" />
          </Col>
          <Col flex="1">
            <div className="agent-conn-label">目标节点</div>
            <Select
              showSearch
              placeholder="搜索并选择目标节点 (例如: dell@172.20.10.5:7777)"
              value={targetNodeName}
              onChange={setTargetNodeName}
              style={{ width: '100%' }}
              optionFilterProp="label"
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
            >
              {agentList.map(agent => (
                <Select.Option
                  key={agent.id}
                  value={agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}
                  label={agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}
                >
                  <Space size="small">
                    <CloudServerOutlined style={{ color: '#722ed1', fontSize: 12 }} />
                    <span style={{ fontWeight: 500 }}>{agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}</span>
                    {agent.nodeStatus === 1 && <Tag color="green" style={{ fontSize: 10 }}>在线</Tag>}
                    {agent.nodeStatus === 0 && <Tag color="red" style={{ fontSize: 10 }}>离线</Tag>}
                    {agent.osType && <Tag color="blue" style={{ fontSize: 10 }}>{agent.osType}</Tag>}
                  </Space>
                </Select.Option>
              ))}
            </Select>
          </Col>
          <Col>
            <Space>
              <Button
                icon={viewMode === 'flow' ? <TableOutlined /> : <ApartmentOutlined />}
                onClick={() => setViewMode(viewMode === 'flow' ? 'table' : 'flow')}
                size="middle"
              >
                {viewMode === 'flow' ? '表格视图' : '流程图'}
              </Button>
              <Button type="primary" icon={<ApiOutlined />} onClick={runCheck} loading={loading} size="middle">
                开始检测
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Card className="agent-conn-result-card" size="small">
        {!hasResults && !loading && (
          <div className="agent-conn-empty">
            <ApiOutlined style={{ fontSize: 48, color: '#d9d9d9', marginBottom: 16 }} />
            <div style={{ color: '#bfbfbf', fontSize: 14 }}>选择两个节点后点击"开始检测"</div>
          </div>
        )}

        {loading && !hasResults && (
          <div className="agent-conn-empty">
            <ReloadOutlined spin style={{ fontSize: 48, color: '#1890ff', marginBottom: 16 }} />
            <div style={{ color: '#1890ff', fontSize: 14 }}>正在检测连通性...</div>
          </div>
        )}

        {hasResults && viewMode === 'flow' && (
          <div className="agent-conn-flow-wrapper">
            <div className="conn-flow-container" style={{ height: 280 }}>
              <ReactFlowProvider>
                <ConnectivityFlowView nodes={nodes} edges={edges} />
              </ReactFlowProvider>
            </div>
            <div className="conn-legend">
              <div className="conn-legend-item"><span className="conn-legend-line conn-legend-ok" /> 可达</div>
              <div className="conn-legend-item"><span className="conn-legend-line conn-legend-fail" /> 不可达</div>
              <div className="conn-legend-item"><span className="conn-legend-line conn-legend-checking" /> 探测中</div>
            </div>
            <div className="conn-detail-panel">
              <div className="conn-detail-header">
                <span className="conn-detail-title">检测详情</span>
              </div>
              <div className="conn-detail-body">
                <div className="conn-detail-section">
                  <div className="conn-detail-section-title">
                    <span className="conn-detail-arrow">→</span> 发送→接收
                  </div>
                  <div className="conn-detail-items">
                    {currentDetails.forward.length > 0 ? currentDetails.forward.map((d, i) => (
                      <div key={i} className={`conn-detail-item ${d.startsWith('✅') ? 'ok' : d.startsWith('❌') ? 'fail' : ''}`}>
                        {d}
                      </div>
                    )) : <div className="conn-detail-item empty">暂无数据</div>}
                  </div>
                </div>
                <div className="conn-detail-divider" />
                <div className="conn-detail-section">
                  <div className="conn-detail-section-title">
                    <span className="conn-detail-arrow">←</span> 接收→发送
                  </div>
                  <div className="conn-detail-items">
                    {currentDetails.reverse.length > 0 ? currentDetails.reverse.map((d, i) => (
                      <div key={i} className={`conn-detail-item ${d.startsWith('✅') ? 'ok' : d.startsWith('❌') ? 'fail' : ''}`}>
                        {d}
                      </div>
                    )) : <div className="conn-detail-item empty">暂无数据</div>}
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {hasResults && viewMode === 'table' && (
          <div className="conn-table-container">
            <Table
              dataSource={tableData}
              columns={tableColumns}
              pagination={false}
              size="middle"
              bordered
              className="conn-table"
              rowKey="key"
              expandable={{
                expandedRowRender: (record) => (
                  <div className="conn-table-expand">
                    <div className="conn-table-expand-section">
                      <div className="conn-table-expand-title">发送→接收 详情</div>
                      {record.forwardDetails.length > 0 ? record.forwardDetails.map((d, i) => (
                        <div key={i} className={`conn-detail-item ${d.startsWith('✅') ? 'ok' : d.startsWith('❌') ? 'fail' : ''}`}>{d}</div>
                      )) : <div className="conn-detail-item empty">暂无数据</div>}
                    </div>
                    <div className="conn-table-expand-section">
                      <div className="conn-table-expand-title">接收→发送 详情</div>
                      {record.reverseDetails.length > 0 ? record.reverseDetails.map((d, i) => (
                        <div key={i} className={`conn-detail-item ${d.startsWith('✅') ? 'ok' : d.startsWith('❌') ? 'fail' : ''}`}>{d}</div>
                      )) : <div className="conn-detail-item empty">暂无数据</div>}
                    </div>
                  </div>
                )
              }}
            />
          </div>
        )}
      </Card>
    </div>
  );
}
