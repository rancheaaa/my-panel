import React, { useState, useMemo, useEffect } from 'react';
import { Modal, Tag, Button, Tooltip, Table } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
  CloudServerOutlined,
  ApiOutlined,
  ExclamationCircleOutlined,
  TableOutlined,
  ApartmentOutlined
} from '@ant-design/icons';
import {
  ReactFlow,
  Background,
  Controls,
  ReactFlowProvider,
  useReactFlow,
  Handle,
  Position,
  getBezierPath
} from 'reactflow';
import 'reactflow/dist/style.css';
import { batchApi } from '../../../api/batch';
import './connectivity.scss';

function AgentNode({ data }) {
  const { label, ip, port, isOnline, isEnabled, exists, errorMsg, onClick, isSelected } = data;
  const isSource = data.role === 'source';
  const fullLabel = label || '';
  const displayLabel = fullLabel.length > 14 ? fullLabel.substring(0, 14) + '...' : fullLabel;

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

  const nodeClass = `conn-node ${isSelected ? 'conn-node-selected' : ''} ${!isSource && onClick ? 'conn-node-clickable' : ''}`;

  return (
    <Tooltip title={tooltipContent} placement="top" mouseEnterDelay={0.3}>
      <div
        className={nodeClass}
        style={{ borderColor, background: bgColor }}
        onClick={!isSource ? onClick : undefined}
        role={!isSource ? 'button' : undefined}
        tabIndex={!isSource ? 0 : undefined}
      >
        {isSource ? (
          <>
            <Handle type="source" position={Position.Right} id="out-upper" className="conn-handle conn-handle-upper" />
            <Handle type="target" position={Position.Right} id="in-lower" className="conn-handle conn-handle-lower" />
          </>
        ) : (
          <>
            <Handle type="target" position={Position.Left} id="in-upper" className="conn-handle conn-handle-upper" />
            <Handle type="source" position={Position.Left} id="out-lower" className="conn-handle conn-handle-lower" />
          </>
        )}
        <div className="conn-node-header">
          <div className="conn-node-icon"><CloudServerOutlined /></div>
          <div className="conn-node-title">{displayLabel}</div>
          <Tag color={statusColor} className="conn-node-status-tag">{statusText}</Tag>
        </div>
        {ip && port && (
          <div className="conn-node-info">{ip}:{port}</div>
        )}
        {errorMsg && (
          <div className="conn-node-error">
            <ExclamationCircleOutlined /> {errorMsg.length > 20 ? errorMsg.substring(0, 20) + '...' : errorMsg}
          </div>
        )}
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
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    curvature: 0.35
  });

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

  const markerId = `conn-arrow-${id}`;

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
        <rect x={-52} y={-12} width={104} height={24} rx={12}
          fill="white" fillOpacity={0.95} stroke={strokeColor} strokeWidth={1}
          filter="drop-shadow(0 1px 3px rgba(0,0,0,0.08))" />
        <text textAnchor="middle" dominantBaseline="central" fontSize={10} fontWeight={600} fill={strokeColor}>
          {checking ? '探测中...' : label}
        </text>
      </g>

    </>
  );
}

const nodeTypes = { agentNode: AgentNode };
const edgeTypes = { animated: AnimatedEdge };

function ConnectivityFlow({ task, visible }) {
  const { fitView } = useReactFlow();
  const [results, setResults] = useState({});
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState({});
  const [selectedTarget, setSelectedTarget] = useState(0);
  const [viewMode, setViewMode] = useState('flow');

  const sourceNodeName = task?.sourceAgentName || '';
  const targetNodeNames = useMemo(() => {
    if (!task?.targetAgentNames) return [];
    try {
      const parsed = JSON.parse(task.targetAgentNames);
      return Array.isArray(parsed) ? parsed : [task.targetAgentNames];
    } catch {
      return task.targetAgentNames.split(';').filter(Boolean);
    }
  }, [task?.targetAgentNames]);

  const getEdgeResult = (r, direction) => {
    if (!r) return { reachable: undefined, failureReason: null };
    if (direction === 'forward') {
      if (r.sourceToTarget) return r.sourceToTarget;
      if (r.connectivityStatus && r.connectivityStatus !== 'REACHABLE') {
        return { reachable: false, failureReason: r.failureReason || statusLabel(r.connectivityStatus) };
      }
      return { reachable: undefined, failureReason: null };
    }
    if (r.targetToSource) return r.targetToSource;
    if (r.reverseStatus && r.reverseStatus !== 'REACHABLE') {
      return { reachable: false, failureReason: r.reverseFailureReason || statusLabel(r.reverseStatus) };
    }
    return { reachable: undefined, failureReason: null };
  };

  const statusLabel = (status) => {
    const map = {
      SOURCE_NOT_FOUND: '源节点不存在',
      TARGET_NOT_FOUND: '目标节点不存在',
      SOURCE_OFFLINE: '源节点离线',
      TARGET_OFFLINE: '目标节点离线',
      SOURCE_DISABLED: '源节点已禁用',
      TARGET_DISABLED: '目标节点已禁用',
      ADMIN_TO_SOURCE_UNREACHABLE: 'Admin无法连接源节点',
      ADMIN_TO_TARGET_UNREACHABLE: 'Admin无法连接目标节点',
      SOURCE_TO_TARGET_UNREACHABLE: '源节点无法连接目标节点',
      UNREACHABLE: '不可达',
      UNKNOWN_ERROR: '未知错误'
    };
    return map[status] || status;
  };

  const nodes = useMemo(() => {
    const targetCount = Math.max(targetNodeNames.length, 1);
    const totalHeight = Math.max(targetCount * 120, 280);
    const centerY = totalHeight / 2 + 30;

    const fwdResult = results.forward;
    const sourceExists = fwdResult?.sourceExists ?? true;
    const sourceOnline = fwdResult?.sourceOnline ?? true;
    const sourceEnabled = fwdResult?.sourceEnabled ?? true;
    const sourceError = sourceExists ? (!sourceOnline ? '节点离线' : !sourceEnabled ? '节点已禁用' : null) : '节点不存在';

    const n = [
      {
        id: 'source',
        type: 'agentNode',
        position: { x: 60, y: centerY - 30 },
        data: {
          label: sourceNodeName || '源节点',
          ip: fwdResult?.sourceIp,
          port: fwdResult?.sourcePort,
          role: 'source',
          exists: sourceExists,
          isOnline: sourceOnline,
          isEnabled: sourceEnabled,
          errorMsg: sourceError
        },
        draggable: false
      }
    ];

    const targetStartY = 20;
    const targetSpacing = targetCount > 1 ? totalHeight / (targetCount - 1) : 0;

    targetNodeNames.forEach((name, idx) => {
      const r = results[`target_${idx}`];
      const targetExists = r?.targetExists ?? true;
      const targetOnline = r?.targetOnline ?? true;
      const targetEnabled = r?.targetEnabled ?? true;
      const targetError = targetExists ? (!targetOnline ? '节点离线' : !targetEnabled ? '节点已禁用' : null) : '节点不存在';

      n.push({
        id: `target_${idx}`,
        type: 'agentNode',
        position: { x: 580, y: targetCount === 1 ? centerY - 30 : targetStartY + idx * targetSpacing },
        data: {
          label: name || `目标节点${idx + 1}`,
          ip: r?.targetIp,
          port: r?.targetPort,
          role: 'target',
          exists: targetExists,
          isOnline: targetOnline,
          isEnabled: targetEnabled,
          errorMsg: targetError,
          onClick: () => setSelectedTarget(idx),
          isSelected: selectedTarget === idx
        },
        draggable: false
      });
    });

    return n;
  }, [sourceNodeName, targetNodeNames, results, selectedTarget]);

  const edges = useMemo(() => {
    const e = [];

    targetNodeNames.forEach((_, idx) => {
      const r = results[`target_${idx}`];
      const fwd = getEdgeResult(r, 'forward');
      const rev = getEdgeResult(r, 'reverse');

      e.push({
        id: `source-target_${idx}`,
        source: 'source',
        target: `target_${idx}`,
        sourceHandle: 'out-upper',
        targetHandle: 'in-upper',
        type: 'animated',
        data: {
          direction: 'forward',
          label: '发送→接收',
          reachable: fwd.reachable,
          checking: checking[`forward_${idx}`],
          failureReason: fwd.failureReason
        }
      });

      e.push({
        id: `target-source_${idx}`,
        source: `target_${idx}`,
        target: 'source',
        sourceHandle: 'out-lower',
        targetHandle: 'in-lower',
        type: 'animated',
        data: {
          direction: 'reverse',
          label: '接收→发送',
          reachable: rev.reachable,
          checking: checking[`reverse_${idx}`],
          failureReason: rev.failureReason
        }
      });
    });

    return e;
  }, [targetNodeNames, results, checking]);

  const runCheck = async () => {
    setLoading(true);
    setResults({});
    setChecking({});

    const newResults = {};
    const newChecking = {};

    for (let idx = 0; idx < targetNodeNames.length; idx++) {
      const targetName = targetNodeNames[idx];

      newChecking[`forward_${idx}`] = true;
      newChecking[`reverse_${idx}`] = true;
      setChecking({ ...newChecking });

      try {
        const [fwdRes, revRes] = await Promise.all([
          batchApi.checkConnectivity(sourceNodeName, targetName),
          batchApi.checkConnectivity(targetName, sourceNodeName)
        ]);

        const result = {};

        if (fwdRes.code === 200 && fwdRes.data) {
          const d = fwdRes.data;
          if (idx === 0) {
            result.sourceIp = d.sourceIp;
            result.sourcePort = d.sourcePort;
            result.sourceExists = d.sourceExists;
            result.sourceOnline = d.sourceOnline;
            result.sourceEnabled = d.sourceEnabled;
          }
          result.targetIp = d.targetIp;
          result.targetPort = d.targetPort;
          result.targetExists = d.targetExists;
          result.targetOnline = d.targetOnline;
          result.targetEnabled = d.targetEnabled;
          result.sourceToTarget = d.sourceToTarget;
          result.connectivityStatus = d.connectivityStatus;
          result.failureReason = d.failureReason;
          result.checkDetails = d.checkDetails;
        }

        if (revRes.code === 200 && revRes.data) {
          const d = revRes.data;
          result.targetToSource = d.sourceToTarget;
          result.reverseStatus = d.connectivityStatus;
          result.reverseFailureReason = d.failureReason;
          result.reverseCheckDetails = d.checkDetails;
        }

        newResults[`target_${idx}`] = result;
      } catch (err) {
        console.error(`检测 ${sourceNodeName} ↔ ${targetName} 失败:`, err);
        newResults[`target_${idx}`] = {
          sourceToTarget: { reachable: false, failureReason: '接口调用失败: ' + (err.message || '未知错误') },
          targetToSource: { reachable: false, failureReason: '接口调用失败: ' + (err.message || '未知错误') }
        };
      }

      newChecking[`forward_${idx}`] = false;
      newChecking[`reverse_${idx}`] = false;
      setResults({ ...newResults });
      setChecking({ ...newChecking });
    }

    if (newResults.target_0) {
      newResults.forward = newResults.target_0;
      setResults({ ...newResults });
    }

    setLoading(false);
  };

  useEffect(() => {
    if (visible && task) {
      runCheck();
    }
  }, [visible, task]);

  useEffect(() => {
    if (nodes.length > 0) {
      setTimeout(() => fitView({ padding: 0.15, duration: 400 }), 100);
    }
  }, [nodes, fitView]);

  const overallStatus = useMemo(() => {
    const allResults = Object.entries(results).filter(([k]) => k.startsWith('target_')).map(([, v]) => v);
    if (allResults.length === 0) return null;

    for (const r of allResults) {
      const fwd = getEdgeResult(r, 'forward');
      const rev = getEdgeResult(r, 'reverse');
      if (fwd.reachable === false) return { status: 'fail', reason: fwd.failureReason };
      if (rev.reachable === false) return { status: 'fail', reason: rev.failureReason };
    }

    const allChecked = allResults.every(r => {
      const fwd = getEdgeResult(r, 'forward');
      const rev = getEdgeResult(r, 'reverse');
      return fwd.reachable === true && rev.reachable === true;
    });
    if (allChecked) return { status: 'REACHABLE' };
    return null;
  }, [results]);

  const currentDetails = useMemo(() => {
    const r = results[`target_${selectedTarget}`];
    if (!r) return { forward: [], reverse: [] };
    return {
      forward: r.checkDetails || [],
      reverse: r.reverseCheckDetails || []
    };
  }, [results, selectedTarget]);

  const tableColumns = useMemo(() => [
    { title: '发送节点', dataIndex: 'source', key: 'source', align: 'center', width: 160 },
    { title: '接收节点', dataIndex: 'target', key: 'target', align: 'center', width: 160 },
    {
      title: '发送→接收', dataIndex: 'forward', key: 'forward', align: 'center', width: 140,
      render: (val) => {
        if (val === undefined) return <Tag color="#d9d9d9">待检测</Tag>;
        return val
          ? <Tag color="success" icon={<CheckCircleOutlined />}>可达</Tag>
          : <Tag color="error" icon={<CloseCircleOutlined />}>不可达</Tag>;
      }
    },
    {
      title: '接收→发送', dataIndex: 'reverse', key: 'reverse', align: 'center', width: 140,
      render: (val) => {
        if (val === undefined) return <Tag color="#d9d9d9">待检测</Tag>;
        return val
          ? <Tag color="success" icon={<CheckCircleOutlined />}>可达</Tag>
          : <Tag color="error" icon={<CloseCircleOutlined />}>不可达</Tag>;
      }
    },
    {
      title: '失败原因', dataIndex: 'failureReason', key: 'failureReason', align: 'center',
      render: (text) => text || '-'
    }
  ], []);

  const tableData = useMemo(() => {
    return targetNodeNames.map((name, idx) => {
      const r = results[`target_${idx}`];
      const fwd = getEdgeResult(r, 'forward');
      const rev = getEdgeResult(r, 'reverse');
      const reason = fwd.failureReason || rev.failureReason || null;
      return {
        key: idx,
        source: sourceNodeName || '源节点',
        target: name || `目标节点${idx + 1}`,
        forward: fwd.reachable,
        reverse: rev.reachable,
        failureReason: reason,
        forwardDetails: r?.checkDetails || [],
        reverseDetails: r?.reverseCheckDetails || []
      };
    });
  }, [targetNodeNames, results, sourceNodeName]);

  return (
    <div className="connectivity-flow">
      <div className="conn-toolbar">
        <div className="conn-toolbar-left">
          <ApiOutlined className="conn-toolbar-icon" />
          <span className="conn-toolbar-title">节点连通性检测</span>
          {overallStatus && (
            <Tag color={overallStatus.status === 'REACHABLE' ? 'success' : 'error'}
              className="conn-overall-tag"
              icon={overallStatus.status === 'REACHABLE' ? <CheckCircleOutlined /> : <CloseCircleOutlined />}>
              {overallStatus.status === 'REACHABLE' ? '双向可达' : '存在不可达'}
            </Tag>
          )}
        </div>
        <div className="conn-toolbar-right">
          <Button
            icon={viewMode === 'flow' ? <TableOutlined /> : <ApartmentOutlined />}
            onClick={() => setViewMode(viewMode === 'flow' ? 'table' : 'flow')}
            size="small"
            className="conn-view-toggle-btn"
          >
            {viewMode === 'flow' ? '表格视图' : '流程图'}
          </Button>
          <Button icon={<ReloadOutlined />} onClick={runCheck} loading={loading}
            size="small" className="conn-recheck-btn">重新检测</Button>
        </div>
      </div>

      {overallStatus && overallStatus.reason && (
        <div className="conn-failure-banner">
          <CloseCircleOutlined style={{ marginRight: 8, color: '#ff4d4f' }} />
          {overallStatus.reason}
        </div>
      )}

      {viewMode === 'flow' ? (
        <div className="conn-flow-container">
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
        </div>
      ) : (
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
                      <div key={i} className={`conn-detail-item ${d.startsWith('✅') ? 'ok' : d.startsWith('❌') ? 'fail' : ''}`}>
                        {d}
                      </div>
                    )) : <div className="conn-detail-item empty">暂无数据</div>}
                  </div>
                  <div className="conn-table-expand-section">
                    <div className="conn-table-expand-title">接收→发送 详情</div>
                    {record.reverseDetails.length > 0 ? record.reverseDetails.map((d, i) => (
                      <div key={i} className={`conn-detail-item ${d.startsWith('✅') ? 'ok' : d.startsWith('❌') ? 'fail' : ''}`}>
                        {d}
                      </div>
                    )) : <div className="conn-detail-item empty">暂无数据</div>}
                  </div>
                </div>
              )
            }}
          />
        </div>
      )}

      {viewMode === 'flow' && (
        <div className="conn-detail-panel">
          <div className="conn-detail-header">
            <span className="conn-detail-title">检测详情</span>
            {targetNodeNames.length > 1 && (
              <div className="conn-detail-tabs">
                {targetNodeNames.map((name, idx) => (
                  <Tooltip title={name} key={idx} placement="top" mouseEnterDelay={0.3}>
                    <span
                      className={`conn-detail-tab ${selectedTarget === idx ? 'active' : ''}`}
                      onClick={() => setSelectedTarget(idx)}
                    >
                      {name.length > 12 ? name.substring(0, 12) + '...' : name}
                    </span>
                  </Tooltip>
                ))}
              </div>
            )}
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
      )}

      {viewMode === 'flow' && (
        <div className="conn-legend">
          <div className="conn-legend-item"><span className="conn-legend-line conn-legend-ok" /> 可达</div>
          <div className="conn-legend-item"><span className="conn-legend-line conn-legend-fail" /> 不可达</div>
          <div className="conn-legend-item"><span className="conn-legend-line conn-legend-checking" /> 探测中</div>
        </div>
      )}
    </div>
  );
}

export default function ConnectivityModal({ open, onClose, task }) {
  return (
    <Modal title={null} open={open} onCancel={onClose} width={1200}
      footer={null} destroyOnClose className="connectivity-modal" closable
      style={{ top: 20 }}>
      <ReactFlowProvider>
        {task && <ConnectivityFlow task={task} visible={open} />}
      </ReactFlowProvider>
    </Modal>
  );
}
