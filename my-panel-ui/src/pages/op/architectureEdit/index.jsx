import React, { useState, useCallback, useMemo, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Button, 
  Space, 
  Form, 
  Input, 
  message, 
  Modal,
  Drawer,
  Divider,
  Tag,
  Select,
  InputNumber,
  Upload,
  Spin
} from 'antd';
import { 
  PlusOutlined, 
  SaveOutlined, 
  ReloadOutlined, 
  SettingOutlined,
  DeleteOutlined,
  ClusterOutlined,
  ApiOutlined,
  DatabaseOutlined,
  CloseOutlined,
  SyncOutlined,
  CloudUploadOutlined,
  HistoryOutlined,
  SendOutlined,
  ArrowLeftOutlined
} from '@ant-design/icons';
import { 
  useNodesState, 
  useEdgesState
} from '@ant-design/pro-flow';
import { 
  Handle as ReactFlowHandle, 
  Position as ReactFlowPosition, 
  ReactFlow, 
  Background, 
  MiniMap, 
  Controls,
  ReactFlowProvider,
  useReactFlow,
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  getSmoothStepPath,
  getStraightPath
} from 'reactflow';
import 'reactflow/dist/style.css';
import './index.scss';

const normalizeConfigValue = (value) => {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

const buildDiagramSignature = (diagramName, nodes, edges) => {
  const normalizedNodes = (nodes || [])
    .map((node) => ({
      id: String(node.id ?? ''),
      type: String(node.type ?? ''),
      positionX: Math.round(node.position?.x ?? 0),
      positionY: Math.round(node.position?.y ?? 0),
      data: {
        name: String(node.data?.name ?? ''),
        type: String(node.data?.type ?? ''),
        description: String(node.data?.description ?? ''),
        status: String(node.data?.status ?? ''),
        ip: String(node.data?.ip ?? ''),
        port: String(node.data?.port ?? ''),
        config: normalizeConfigValue(node.data?.config),
      },
    }))
    .sort((a, b) => a.id.localeCompare(b.id));

  const normalizedEdges = (edges || [])
    .map((edge) => {
      const style = edge.style || {};
      const points = Array.isArray(edge.data?.points) ? edge.data.points : [];
      return {
        id: String(edge.id ?? ''),
        source: String(edge.source ?? ''),
        target: String(edge.target ?? ''),
        sourceHandle: String(edge.sourceHandle ?? ''),
        targetHandle: String(edge.targetHandle ?? ''),
        label: String(edge.label ?? ''),
        edgeType: String(edge.edgeType ?? edge.data?.edgeType ?? ''),
        animated: Boolean(edge.animated),
        style: {
          stroke: String(style.stroke ?? ''),
          strokeWidth: Number(style.strokeWidth ?? 0),
          strokeDasharray: style.strokeDasharray == null ? '' : String(style.strokeDasharray),
        },
        markerStart: edge.markerStart
          ? { type: String(edge.markerStart.type ?? ''), color: String(edge.markerStart.color ?? '') }
          : null,
        markerEnd: edge.markerEnd
          ? { type: String(edge.markerEnd.type ?? ''), color: String(edge.markerEnd.color ?? '') }
          : null,
        points: points.map((p) => ({ x: Math.round(p.x ?? 0), y: Math.round(p.y ?? 0) })),
      };
    })
    .sort((a, b) => a.id.localeCompare(b.id));

  return JSON.stringify({
    diagramName: String(diagramName ?? ''),
    nodes: normalizedNodes,
    edges: normalizedEdges,
  });
};

// 导入架构相关的 API
import { 
  getDiagram, 
  loadDiagramData, 
  replaceDiagramData,
  updateDiagram,
  publishDiagram,
  addNode,
  updateNode,
  delNode,
  addEdge,
  updateEdge,
  delEdge
} from '@/api/op/architecture';
import { listAllNodeType } from '@/api/op/archNodeType';
import { listHistoryByDiagramId, createSnapshot, restoreVersion } from '@/api/op/archHistory';

const { Option } = Select;

// 自定义可编辑连线组件
const EditableEdge = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  style = {},
  markerEnd,
  selected,
  data,
  label,
}) => {
  const { setEdges } = useReactFlow();
  const resolvedEdgeType = data?.edgeType || 'smoothstep';
  const points = useMemo(() => {
    if (!data?.points) return [];
    if (Array.isArray(data.points)) return data.points;
    if (typeof data.points === 'string') {
      try {
        const parsed = JSON.parse(data.points);
        return Array.isArray(parsed) ? parsed : [];
      } catch {
        return [];
      }
    }
    return [];
  }, [data?.points]);
  
  // 计算基础路径
  const getPath = () => {
    // 如果有自定义点，构造折线路径
    if (points.length > 0) {
      let path = `M ${sourceX},${sourceY}`;
      points.forEach(point => {
        path += ` L ${point.x},${point.y}`;
      });
      path += ` L ${targetX},${targetY}`;
      return path;
    }
    
    if (resolvedEdgeType === 'straight') {
      const [path] = getStraightPath({
        sourceX,
        sourceY,
        targetX,
        targetY,
      });
      return path;
    }

    if (resolvedEdgeType === 'step') {
      const [path] = getSmoothStepPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
        borderRadius: 0,
      });
      return path;
    }

    if (resolvedEdgeType === 'smoothstep') {
      const [path] = getSmoothStepPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
      });
      return path;
    }

    if (resolvedEdgeType === 'editable') {
      return `M ${sourceX},${sourceY} L ${targetX},${targetY}`;
    }

    const [path] = getBezierPath({
      sourceX,
      sourceY,
      sourcePosition,
      targetX,
      targetY,
      targetPosition,
    });
    return path;
  };

  // 计算标签位置
  const getLabelPosition = () => {
    if (points.length > 0) {
      // 如果有折点，取中间折点的位置作为标签位置
      const midIndex = Math.floor(points.length / 2);
      return { labelX: points[midIndex].x, labelY: points[midIndex].y };
    }
    
    if (resolvedEdgeType === 'editable' || resolvedEdgeType === 'straight') {
      return { labelX: (sourceX + targetX) / 2, labelY: (sourceY + targetY) / 2 };
    }

    if (resolvedEdgeType === 'step') {
      const [, labelX, labelY] = getSmoothStepPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
        borderRadius: 0,
      });
      return { labelX, labelY };
    }

    if (resolvedEdgeType === 'smoothstep') {
      const [, labelX, labelY] = getSmoothStepPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
      });
      return { labelX, labelY };
    }

    const [, labelX, labelY] = getBezierPath({
      sourceX,
      sourceY,
      sourcePosition,
      targetX,
      targetY,
      targetPosition,
    });
    return { labelX, labelY };
  };

  const [isHovered, setIsHovered] = useState(false);
  const edgePath = getPath();
  const { labelX, labelY } = getLabelPosition();

  // 计算点到线段的距离
  const getDistanceToSegment = (x, y, x1, y1, x2, y2) => {
    const L2 = (x2 - x1) ** 2 + (y2 - y1) ** 2;
    if (L2 === 0) return Math.sqrt((x - x1) ** 2 + (y - y1) ** 2);
    let t = ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / L2;
    t = Math.max(0, Math.min(1, t));
    return Math.sqrt((x - (x1 + t * (x2 - x1))) ** 2 + (y - (y1 + t * (y2 - y1))) ** 2);
  };

  // 处理控制点拖拽
  const onHandleDrag = (event, index) => {
    if (event.button !== 0) return; // 只响应左键
    event.stopPropagation();
    const pane = document.querySelector('.react-flow__pane');
    if (!pane) return;

    const rect = pane.getBoundingClientRect();
    const transform = pane.style.transform.match(/translate\((.+)px, (.+)px\) scale\((.+)\)/);
    const tx = transform ? parseFloat(transform[1]) : 0;
    const ty = transform ? parseFloat(transform[2]) : 0;
    const s = transform ? parseFloat(transform[3]) : 1;

    const handleMouseMove = (e) => {
      const x = (e.clientX - rect.left - tx) / s;
      const y = (e.clientY - rect.top - ty) / s;

      setEdges((eds) =>
        eds.map((edge) => {
          if (edge.id === id) {
            const newPoints = [...(edge.data?.points || [])];
            newPoints[index] = { x, y };
            return {
              ...edge,
              data: {
                ...edge.data,
                points: newPoints,
              },
            };
          }
          return edge;
        })
      );
    };

    const handleMouseUp = () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  };

  // 在连线中间点击并拖拽
  const onEdgeMouseDown = (event) => {
    if (event.button !== 0) return; // 只响应左键
    
    // 如果是双击（短时间内两次点击），则不添加点，让 ReactFlow 处理 onEdgeDoubleClick
    if (event.detail > 1) {
      return;
    }

    event.stopPropagation();

    const pane = document.querySelector('.react-flow__pane');
    if (!pane) return;

    const rect = pane.getBoundingClientRect();
    const transform = pane.style.transform.match(/translate\((.+)px, (.+)px\) scale\((.+)\)/);
    const tx = transform ? parseFloat(transform[1]) : 0;
    const ty = transform ? parseFloat(transform[2]) : 0;
    const s = transform ? parseFloat(transform[3]) : 1;

    const mouseX = (event.clientX - rect.left - tx) / s;
    const mouseY = (event.clientY - rect.top - ty) / s;

    // 获取所有点（包括起点和终点）
    const allPoints = [
      { x: sourceX, y: sourceY },
      ...(data?.points || []),
      { x: targetX, y: targetY }
    ];

    // 寻找最近的线段
    let minDistance = Infinity;
    let insertIndex = 0;

    for (let i = 0; i < allPoints.length - 1; i++) {
      const p1 = allPoints[i];
      const p2 = allPoints[i + 1];
      const dist = getDistanceToSegment(mouseX, mouseY, p1.x, p1.y, p2.x, p2.y);
      if (dist < minDistance) {
        minDistance = dist;
        insertIndex = i;
      }
    }

    // 插入新点
    const newPoint = { x: mouseX, y: mouseY };
    setEdges((eds) =>
      eds.map((edge) => {
        if (edge.id === id) {
          const points = [...(edge.data?.points || [])];
          points.splice(insertIndex, 0, newPoint);
          return {
            ...edge,
            data: { ...edge.data, points },
          };
        }
        return edge;
      })
    );

    // 立即开始拖拽这个新点
    onHandleDrag(event, insertIndex);
  };

  // 删除指定的控制点
  const onRemovePoint = (event, index) => {
    event.stopPropagation();
    setEdges((eds) =>
      eds.map((edge) => {
        if (edge.id === id) {
          const newPoints = [...(edge.data?.points || [])];
          newPoints.splice(index, 1);
          return {
            ...edge,
            data: {
              ...edge.data,
              points: newPoints,
            },
          };
        }
        return edge;
      })
    );
  };

  const handleEditLabel = (e) => {
    e.stopPropagation();
    // 触发自定义事件
    window.dispatchEvent(new CustomEvent('edit-edge-label', { 
      detail: { id } 
    }));
  };

  return (
    <g
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      className={`editable-edge-group ${selected ? 'selected' : ''} ${isHovered ? 'hovered' : ''}`}
      style={{ 
        cursor: 'crosshair',
        zIndex: (selected || isHovered) ? 10 : 1,
      }}
    >
      {/* 基础连线 */}
      <BaseEdge 
        path={edgePath} 
        markerEnd={markerEnd} 
        style={{
          ...style,
          strokeWidth: (selected || isHovered) ? (style.strokeWidth || 2) + 1 : style.strokeWidth,
          stroke: (selected || isHovered) ? '#1890ff' : style.stroke,
          transition: 'all 0.2s ease',
        }} 
      />

      {/* 交互层：极大幅度加宽的透明路径，极大提高点击和拖拽的容错率 */}
      <path
        d={edgePath}
        fill="none"
        stroke="transparent"
        strokeWidth={isHovered || selected ? 80 : 50}
        onMouseDown={onEdgeMouseDown}
        style={{ 
          cursor: 'crosshair', 
          pointerEvents: 'stroke',
          transition: 'stroke-width 0.2s ease'
        }}
      />
      
      {/* 标签渲染 */}
      <EdgeLabelRenderer>
        <div
          style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            pointerEvents: 'all',
            zIndex: 1000,
          }}
          className="nodrag nopan"
        >
          <div 
            className={`editable-edge-label-container ${isHovered ? 'hovered' : ''}`}
            onDoubleClick={handleEditLabel}
            title="双击编辑标签"
          >
            {label && (
              <div className="editable-edge-label">
                {label}
              </div>
            )}
            <div className="edge-edit-action" onClick={handleEditLabel} title="修改连线属性">
              <SettingOutlined />
            </div>
          </div>
        </div>
      </EdgeLabelRenderer>
      
      {/* 只有选中或悬浮时才显示控制点 */}
      {(selected || isHovered) && (
        <EdgeLabelRenderer>
          <div 
            style={{ position: 'relative', zIndex: selected ? 1000 : 900 }}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
          >
            {/* 控制点 */}
            {points.map((point, index) => (
              <div
                key={`${id}-point-${index}`}
                style={{
                  position: 'absolute',
                  transform: `translate(-50%, -50%) translate(${point.x}px,${point.y}px)`,
                  pointerEvents: 'all',
                }}
                className="nodrag nopan"
              >
                <div
                  onMouseDown={(e) => onHandleDrag(e, index)}
                  onDoubleClick={(e) => onRemovePoint(e, index)}
                  className={`editable-edge-handle ${selected ? 'selected' : ''}`}
                >
                  <button
                    className="remove-point-button"
                    onMouseDown={(e) => e.stopPropagation()}
                    onClick={(e) => onRemovePoint(e, index)}
                    title="删除顶点"
                  >
                    <CloseOutlined />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </EdgeLabelRenderer>
      )}
    </g>
  );
};

// 自定义节点组件
const CustomNode = ({ data, selected, customNodeTypes = [] }) => {
  const getNodeIcon = (type) => {
    const customType = customNodeTypes.find(t => t.type === type);
    if (customType) {
      const iconMap = {
        'ApiOutlined': ApiOutlined,
        'ClusterOutlined': ClusterOutlined,
        'DatabaseOutlined': DatabaseOutlined,
        'SettingOutlined': SettingOutlined,
        'DeleteOutlined': DeleteOutlined,
        'SyncOutlined': SyncOutlined,
        'PlusOutlined': PlusOutlined,
        'SaveOutlined': SaveOutlined,
        'ReloadOutlined': ReloadOutlined
      };
      const IconComponent = iconMap[customType.icon] || ApiOutlined;
      return <IconComponent style={{ fontSize: '32px', color: customType.color }} />;
    }
    
    switch (type) {
      case 'nginx':
        return <ClusterOutlined style={{ fontSize: '32px', color: '#1890ff' }} />;
      case 'my-panel':
        return <ApiOutlined style={{ fontSize: '32px', color: '#52c41a' }} />;
      case 'proxy':
        return <DatabaseOutlined style={{ fontSize: '32px', color: '#fa8c16' }} />;
      default:
        return <ApiOutlined />;
    }
  };

  const getNodeColor = (type) => {
    const customType = customNodeTypes.find(t => t.type === type);
    if (customType) {
      const color = customType.color;
      const bgColor = hexToRgba(color, 0.1);
      return { bg: bgColor, border: color };
    }
    
    switch (type) {
      case 'nginx':
        return { bg: '#e6f7ff', border: '#1890ff' };
      case 'my-panel':
        return { bg: '#f6ffed', border: '#52c41a' };
      case 'proxy':
        return { bg: '#fff7e6', border: '#fa8c16' };
      default:
        return { bg: '#f0f0f0', border: '#d9d9d9' };
    }
  };

  const getNodeHandleColor = (type) => {
    const customType = customNodeTypes.find(t => t.type === type);
    if (customType) {
      return customType.color;
    }
    
    switch (type) {
      case 'nginx':
        return '#1890ff';
      case 'my-panel':
        return '#52c41a';
      case 'proxy':
        return '#fa8c16';
      default:
        return '#1890ff';
    }
  };

  const colors = getNodeColor(data.type);
  const handleColor = getNodeHandleColor(data.type);

  // 辅助函数：将hex颜色转换为rgba
  function hexToRgba(hex, alpha) {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }

  return (
    <div 
      className={`custom-node-wrapper ${selected ? 'selected' : ''}`}
      style={{
        border: `3px solid ${colors.border}`,
        outline: selected ? `3px solid ${colors.border}` : 'none',
        outlineOffset: selected ? '2px' : '0',
        backgroundColor: colors.bg,
        boxShadow: selected 
          ? `0 0 20px ${colors.border}88, 0 8px 24px rgba(0,0,0,0.18)` 
          : '0 4px 16px rgba(0,0,0,0.12)',
      }}
    >
      {/* 顶部连接桩 - 支持双向连接 */}
      <ReactFlowHandle
        type="source"
        position={ReactFlowPosition.Top}
        id="top"
        style={{
          background: handleColor,
          width: '16px',
          height: '16px',
          border: '3px solid #fff',
          boxShadow: `0 2px 8px ${handleColor}66`,
          transition: 'all 0.3s ease',
          cursor: 'crosshair',
          zIndex: 1000
        }}
        className="custom-handle"
      />
      
      {/* 底部连接桩 - 支持双向连接 */}
      <ReactFlowHandle
        type="source"
        position={ReactFlowPosition.Bottom}
        id="bottom"
        style={{
          background: handleColor,
          width: '16px',
          height: '16px',
          border: '3px solid #fff',
          boxShadow: `0 2px 8px ${handleColor}66`,
          transition: 'all 0.3s ease',
          cursor: 'crosshair',
          zIndex: 1000
        }}
        className="custom-handle"
      />
      
      {/* 左侧连接桩 - 支持双向连接 */}
      <ReactFlowHandle
        type="source"
        position={ReactFlowPosition.Left}
        id="left"
        style={{
          background: handleColor,
          width: '16px',
          height: '16px',
          border: '3px solid #fff',
          boxShadow: `0 2px 8px ${handleColor}66`,
          transition: 'all 0.3s ease',
          cursor: 'crosshair',
          zIndex: 1000
        }}
        className="custom-handle"
      />
      
      {/* 右侧连接桩 - 支持双向连接 */}
      <ReactFlowHandle
        type="source"
        position={ReactFlowPosition.Right}
        id="right"
        style={{
          background: handleColor,
          width: '16px',
          height: '16px',
          border: '3px solid #fff',
          boxShadow: `0 2px 8px ${handleColor}66`,
          transition: 'all 0.3s ease',
          cursor: 'crosshair',
          zIndex: 1000
        }}
        className="custom-handle"
      />
      
      <div className="node-header">
        {getNodeIcon(data.type)}
        <span className="node-title">
          {data.name}
        </span>
      </div>
      
      <div className="node-description">
        {data.description}
      </div>
      
      <div className="node-tags">
        <Tag color="blue">
          {(data.type || 'unknown').toUpperCase()}
        </Tag>
        <Tag color={data.status === 'running' ? 'green' : 'red'}>
          {data.status === 'running' ? '运行中' : '已停止'}
        </Tag>
        {data.port && (
          <Tag color="purple">
            端口: {data.port}
          </Tag>
        )}
        {data.ip && (
          <Tag color="cyan">
            IP: {data.ip}
          </Tag>
        )}
      </div>

      {data.config && (
        <div className="node-config-info">
          <div className="config-title">配置信息:</div>
          <div className="config-item">
            <span className="config-value">
              {typeof data.config === 'string'
                ? (data.config.length > 120 ? data.config.slice(0, 120) + '…' : data.config)
                : (() => {
                    const s = JSON.stringify(data.config);
                    return s.length > 120 ? s.slice(0, 120) + '…' : s;
                  })()
              }
            </span>
          </div>
        </div>
      )}
    </div>
  );
};

const ArchitectureFlow = () => {
  const { id: routeId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const diagramId = routeId;
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isEdgeModalOpen, setIsEdgeModalOpen] = useState(false);
  const [isConnectionModalOpen, setIsConnectionModalOpen] = useState(false);
  const [modalForm] = Form.useForm();
  const [drawerForm] = Form.useForm();
  const [edgeForm] = Form.useForm();
  const [connectionForm] = Form.useForm();
  const [currentNode, setCurrentNode] = useState(null);
  const [selectedNode, setSelectedNode] = useState(null);
  const [selectedEdge, setSelectedEdge] = useState(null);
  const [architectureName, setArchitectureName] = useState('加载中...');
  const [isConnecting, setIsConnecting] = useState(false);
  const connectSuccessful = React.useRef(false);
  const connectionActionRef = React.useRef(null);
  const [customNodeTypes, setCustomNodeTypes] = useState([]);
  const [hoverHintTarget, setHoverHintTarget] = useState(null);
  const [hoverHintPos, setHoverHintPos] = useState({ x: 0, y: 0 });
  const [isHoverHintVisible, setIsHoverHintVisible] = useState(false);
  const hoverHintTimerRef = React.useRef(null);
  
  // 版本历史相关状态
  const [isHistoryDrawerOpen, setIsHistoryDrawerOpen] = useState(false);
  const [historyList, setHistoryList] = useState([]);
  const [isSnapshotModalOpen, setIsSnapshotModalOpen] = useState(false);
  const [snapshotForm] = Form.useForm();
  
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  
  const [defaultNodes, setDefaultNodes] = useState([]);
  const [defaultEdges, setDefaultEdges] = useState([]);
  const savedSignatureRef = React.useRef('');
  const initializeDefaultArchitectureRef = React.useRef(null);

  const currentSignature = useMemo(
    () => buildDiagramSignature(architectureName, nodes, edges),
    [architectureName, nodes, edges]
  );
  const isModified = useMemo(() => {
    if (!savedSignatureRef.current) return false;
    return currentSignature !== savedSignatureRef.current;
  }, [currentSignature]);

  useEffect(() => {
    if (hoverHintTimerRef.current) {
      clearTimeout(hoverHintTimerRef.current);
      hoverHintTimerRef.current = null;
    }
    setIsHoverHintVisible(false);
    if (!hoverHintTarget) return;

    hoverHintTimerRef.current = setTimeout(() => {
      setIsHoverHintVisible(true);
    }, 1000);

    return () => {
      if (hoverHintTimerRef.current) {
        clearTimeout(hoverHintTimerRef.current);
        hoverHintTimerRef.current = null;
      }
    };
  }, [hoverHintTarget]);

  useEffect(() => {
    if (!hoverHintTarget) return;
    const onMouseMove = (event) => {
      setHoverHintPos({ x: event.clientX, y: event.clientY });
    };
    window.addEventListener('mousemove', onMouseMove, { passive: true });
    return () => {
      window.removeEventListener('mousemove', onMouseMove);
    };
  }, [hoverHintTarget]);

  // 加载节点类型
  const loadNodeTypes = useCallback(async () => {
    try {
      const response = await listAllNodeType();
      if (response.code === 200) {
        const safeJsonParse = (value) => {
          if (!value) return null;
          if (typeof value === 'object') return value;
          try {
            return JSON.parse(value);
          } catch {
            return null;
          }
        };

        const extractPrimaryColor = (defaultStyle) => {
          const styleObj = safeJsonParse(defaultStyle);
          return styleObj?.borderColor || styleObj?.backgroundColor || styleObj?.color || '#1890ff';
        };

        // 后端返回的节点类型转换为前端使用的格式
        const types = response.data.map(item => ({
          type: item.typeCode,
          name: item.typeName,
          description: item.remark,
          icon: item.icon || 'ApiOutlined',
          color: extractPrimaryColor(item.defaultStyle),
          defaultStyle: safeJsonParse(item.defaultStyle)
        }));
        setCustomNodeTypes(types);
      }
    } catch (error) {
      console.error('加载节点类型失败:', error);
      message.error('加载节点类型失败');
    }
  }, []);

  // 加载架构图数据
  const loadData = useCallback(async (id) => {
    if (!id) {
      initializeDefaultArchitectureRef.current?.('默认架构');
      return;
    }

    setLoading(true);
    try {
      // 1. 获取架构图基本信息
      let nextDiagramName = architectureName;
      const diagRes = await getDiagram(id);
      if (diagRes.code === 200) {
        nextDiagramName = diagRes.data.diagramName;
        setArchitectureName(nextDiagramName);
      }

      // 2. 获取架构图节点和连线数据
      const dataRes = await loadDiagramData(id);
      if (dataRes.code === 200 && dataRes.data) {
        const { nodes: backendNodes = [], edges: backendEdges = [] } = dataRes.data;
        
        // 转换节点格式
        const formattedNodes = (backendNodes || []).map(node => {
          let properties = {};
          try {
            properties = node.nodeProperties ? JSON.parse(node.nodeProperties) : {};
          } catch (e) {
            console.error('解析节点属性失败', e);
          }

          return {
            id: node.id.toString(),
            type: node.nodeType || 'unknown',
            position: { 
              x: node.positionX ?? node.xPosition ?? 0, 
              y: node.positionY ?? node.yPosition ?? 0 
            },
            data: {
              name: node.nodeName,
              type: node.nodeType || 'unknown',
              description: node.description || node.remark,
              status: node.status === '0' ? 'running' : 'stopped',
              ip: properties.ip || node.ip,
              port: properties.port || node.port,
              config: properties.config || node.config
            }
          };
        });

        // 转换连线格式
        const formattedEdges = (backendEdges || []).map(edge => {
          let style = {};
          try {
            style = edge.edgeStyle ? JSON.parse(edge.edgeStyle) : {};
          } catch (e) {
            console.error('解析连线样式失败', e);
          }

          let properties = {};
          try {
            properties = edge.edgeProperties ? JSON.parse(edge.edgeProperties) : {};
          } catch (e) {
            console.error('解析连线属性失败', e);
          }

          let points = properties.points || [];
          if (typeof points === 'string') {
            try {
              points = JSON.parse(points);
            } catch (e) {
              console.error('加载连线折点失败', e);
              points = [];
            }
          }

          return {
            id: edge.id.toString(),
            source: edge.sourceNodeId.toString(),
            target: edge.targetNodeId.toString(),
            sourceHandle: edge.sourceHandle || edge.sourceAnchor || 'bottom',
            targetHandle: edge.targetHandle || edge.targetAnchor || 'top',
            label: edge.edgeLabel,
            type: 'editable',
            edgeType: edge.edgeType || 'smoothstep',
            data: { points: Array.isArray(points) ? points : [], edgeType: edge.edgeType || 'smoothstep' },
            style: {
              stroke: style.stroke || '#1890ff',
              strokeWidth: style.strokeWidth || edge.weight || 2,
              strokeDasharray: style.strokeDasharray
            },
            animated: edge.animated === '1',
            markerEnd: {
              type: 'arrowclosed',
              color: style.stroke || '#1890ff'
            }
          };
        });

        setNodes(formattedNodes);
        setEdges(formattedEdges);
        setDefaultNodes(formattedNodes);
        setDefaultEdges(formattedEdges);
        savedSignatureRef.current = buildDiagramSignature(nextDiagramName, formattedNodes, formattedEdges);
      } else {
        // 如果数据为空，则初始化默认架构
        initializeDefaultArchitectureRef.current?.('默认架构');
      }
    } catch (error) {
      console.error('加载架构图数据失败:', error);
      message.error('加载架构图数据失败，将显示默认架构');
      initializeDefaultArchitectureRef.current?.('默认架构');
    } finally {
      setLoading(false);
    }
  }, [architectureName, setEdges, setNodes]);

  // 初始化默认架构
  const initializeDefaultArchitecture = useCallback((nextName = '默认架构') => {
      setArchitectureName(nextName);
      const initialNodes = [
      {
        id: 'nginx-1',
        type: 'nginx',
        position: { x: 975, y: 50 },
        connectable: true,
        data: {
          name: 'Nginx-负载均衡服务器',
          type: 'nginx',
          description: '负载均衡服务器',
          status: 'running',
          port: 80,
          ip: '192.168.1.100',
          config: 'worker_processes 4;\nworker_connections 1024;\nkeepalive_timeout 65;'
        },
      },
      {
        id: 'mypanel-1',
        type: 'my-panel',
        position: { x: 450, y: 350 },
        connectable: true,
        data: {
          name: 'My-Panel-后端管理服务1',
          type: 'my-panel',
          description: '后端管理服务1',
          status: 'running',
          port: 8080,
          ip: '192.168.1.101',
          config: 'JAVA_VERSION=17\nMAX_MEMORY=2G\nTHREAD_POOL_SIZE=200'
        },
      },
      {
        id: 'mypanel-2',
        type: 'my-panel',
        position: { x: 1500, y: 350 },
        connectable: true,
        data: {
          name: 'My-Panel-后端管理服务2',
          type: 'my-panel',
          description: '后端管理服务2',
          status: 'running',
          port: 8081,
          ip: '192.168.1.102',
          config: 'JAVA_VERSION=17\nMAX_MEMORY=2G\nTHREAD_POOL_SIZE=200'
        },
      },
      {
        id: 'proxy-1',
        type: 'proxy',
        position: { x: 100, y: 650 },
        connectable: true,
        data: {
          name: 'Proxy-代理服务1',
          type: 'proxy',
          description: '代理服务实例1',
          status: 'running',
          port: 9001,
          ip: '192.168.1.201',
          config: 'REGISTRY_ENABLED=true\nHEALTH_CHECK_INTERVAL=30\nMAX_CONNECTIONS=1000'
        },
      },
      {
        id: 'proxy-2',
        type: 'proxy',
        position: { x: 450, y: 650 },
        connectable: true,
        data: {
          name: 'Proxy-代理服务2',
          type: 'proxy',
          description: '代理服务实例2',
          status: 'running',
          port: 9002,
          ip: '192.168.1.202',
          config: 'REGISTRY_ENABLED=true\nHEALTH_CHECK_INTERVAL=30\nMAX_CONNECTIONS=1000'
        },
      },
      {
        id: 'proxy-3',
        type: 'proxy',
        position: { x: 800, y: 650 },
        connectable: true,
        data: {
          name: 'Proxy-代理服务3',
          type: 'proxy',
          description: '代理服务实例3',
          status: 'running',
          port: 9003,
          ip: '192.168.1.203',
          config: 'REGISTRY_ENABLED=true\nHEALTH_CHECK_INTERVAL=30\nMAX_CONNECTIONS=1000'
        },
      },
      {
        id: 'proxy-4',
        type: 'proxy',
        position: { x: 1150, y: 650 },
        connectable: true,
        data: {
          name: 'Proxy-代理服务4',
          type: 'proxy',
          description: '代理服务实例4',
          status: 'running',
          port: 9004,
          ip: '192.168.1.204',
          config: 'REGISTRY_ENABLED=true\nHEALTH_CHECK_INTERVAL=30\nMAX_CONNECTIONS=1000'
        },
      },
      {
        id: 'proxy-5',
        type: 'proxy',
        position: { x: 1500, y: 650 },
        connectable: true,
        data: {
          name: 'Proxy-代理服务5',
          type: 'proxy',
          description: '代理服务实例5',
          status: 'running',
          port: 9005,
          ip: '192.168.1.205',
          config: 'REGISTRY_ENABLED=true\nHEALTH_CHECK_INTERVAL=30\nMAX_CONNECTIONS=1000'
        },
      },
      {
        id: 'proxy-6',
        type: 'proxy',
        position: { x: 1850, y: 650 },
        connectable: true,
        data: {
          name: 'Proxy-代理服务6',
          type: 'proxy',
          description: '代理服务实例6',
          status: 'running',
          port: 9006,
          ip: '192.168.1.206',
          config: 'REGISTRY_ENABLED=true\nHEALTH_CHECK_INTERVAL=30\nMAX_CONNECTIONS=1000'
        },
      },
    ];

    const initialEdges = [
      {
        id: 'e1',
        source: 'nginx-1',
        sourceHandle: 'bottom',
        target: 'mypanel-1',
        targetHandle: 'top',
        label: 'HTTP请求',
        type: 'editable',
        edgeType: 'editable',
        data: { points: [], edgeType: 'editable' },
        style: { 
          stroke: '#1890ff', 
          strokeWidth: 3,
          strokeDasharray: '8,4'
        },
        animated: true,
        markerEnd: {
          type: 'arrowclosed',
          color: '#1890ff',
        },
      },
      {
        id: 'e2',
        source: 'nginx-1',
        sourceHandle: 'bottom',
        target: 'mypanel-2',
        targetHandle: 'top',
        label: 'HTTP请求',
        type: 'editable',
        edgeType: 'editable',
        data: { points: [], edgeType: 'editable' },
        style: { 
          stroke: '#1890ff', 
          strokeWidth: 3,
          strokeDasharray: '8,4'
        },
        animated: true,
        markerEnd: {
          type: 'arrowclosed',
          color: '#1890ff',
        },
      },
      {
        id: 'e3',
        source: 'mypanel-1',
        sourceHandle: 'bottom',
        target: 'proxy-1',
        targetHandle: 'top',
        label: '服务调用',
        type: 'editable',
        edgeType: 'editable',
        data: { points: [], edgeType: 'editable' },
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e4',
        source: 'mypanel-1',
        sourceHandle: 'bottom',
        target: 'proxy-2',
        targetHandle: 'top',
        label: '服务调用',
        type: 'editable',
        edgeType: 'editable',
        data: { points: [], edgeType: 'editable' },
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e5',
        source: 'mypanel-1',
        sourceHandle: 'bottom',
        target: 'proxy-3',
        targetHandle: 'top',
        label: '服务调用',
        type: 'editable',
        edgeType: 'editable',
        data: { points: [], edgeType: 'editable' },
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e6',
        source: 'mypanel-2',
        sourceHandle: 'bottom',
        target: 'proxy-4',
        targetHandle: 'top',
        label: '服务调用',
        type: 'editable',
        edgeType: 'editable',
        data: { points: [], edgeType: 'editable' },
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e7',
        source: 'mypanel-2',
        sourceHandle: 'bottom',
        target: 'proxy-5',
        targetHandle: 'top',
        label: '服务调用',
        type: 'editable',
        edgeType: 'editable',
        data: { points: [], edgeType: 'editable' },
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e8',
        source: 'mypanel-2',
        sourceHandle: 'bottom',
        target: 'proxy-6',
        targetHandle: 'top',
        label: '服务调用',
        type: 'editable',
        edgeType: 'editable',
        data: { points: [], edgeType: 'editable' },
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
    ];

    setDefaultNodes(initialNodes);
    setDefaultEdges(initialEdges);
    setNodes(initialNodes);
    setEdges(initialEdges);
    savedSignatureRef.current = buildDiagramSignature(nextName, initialNodes, initialEdges);
  }, [setNodes, setEdges]);

  useEffect(() => {
    initializeDefaultArchitectureRef.current = initializeDefaultArchitecture;
  }, [initializeDefaultArchitecture]);

  useEffect(() => {
    loadNodeTypes();
    loadData(diagramId);
  }, [loadNodeTypes, loadData, diagramId]);

  useEffect(() => {
    const handleKeyDown = (event) => {
      // 检查当前焦点是否在输入框或文本域中
      const isInput = event.target.tagName === 'INPUT' || 
                      event.target.tagName === 'TEXTAREA' || 
                      event.target.isContentEditable;
      
      if (isInput) return;

      if (event.key === 'Escape' && isConnecting) {
        setIsConnecting(false);
        message.info('连接已取消');
      }

      // 添加删除快捷键支持 (Delete 或 Backspace)
      if (event.key === 'Delete' || event.key === 'Backspace') {
        const selectedNodes = nodes.filter(n => n.selected);
        const selectedEdges = edges.filter(e => e.selected);
        
        if (selectedNodes.length > 0 || selectedEdges.length > 0) {
          const content = `确定要删除选中的 ${selectedNodes.length > 0 ? `${selectedNodes.length} 个节点` : ''}${selectedNodes.length > 0 && selectedEdges.length > 0 ? '和 ' : ''}${selectedEdges.length > 0 ? `${selectedEdges.length} 条连线` : ''} 吗？`;
          
          Modal.confirm({
            title: '确认删除',
            content,
            okText: '确定',
            cancelText: '取消',
            okButtonProps: { danger: true },
            onOk: () => {
              if (selectedNodes.length > 0) {
                setNodes((nds) => nds.filter((node) => !node.selected));
                setSelectedNode(null);
              }
              if (selectedEdges.length > 0) {
                setEdges((eds) => eds.filter((edge) => !edge.selected));
                setSelectedEdge(null);
              }
              message.success('已删除选中元素');
            }
          });
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isConnecting, nodes, edges, setNodes, setEdges]);

  const onNodeClick = useCallback((event, node) => {
    setSelectedNode(node);
  }, []);

  const onNodeMouseEnter = useCallback((event, node) => {
    setHoverHintTarget({ kind: 'node', id: node.id });
    setHoverHintPos({ x: event.clientX, y: event.clientY });
  }, []);

  const onNodeMouseLeave = useCallback(() => {
    setHoverHintTarget(null);
  }, []);

  const onNodeDoubleClick = useCallback((event, node) => {
    setSelectedNode(node);
    setIsDrawerOpen(true);
    drawerForm.setFieldsValue({
      id: node.id,
      name: node.data.name,
      description: node.data.description,
      ip: node.data.ip,
      port: node.data.port,
      status: node.data.status
    });
  }, [drawerForm]);

  const onEdgeClick = useCallback((event, edge) => {
    setSelectedEdge(edge);
  }, []);

  const onEdgeMouseEnter = useCallback((event, edge) => {
    setHoverHintTarget({ kind: 'edge', id: edge.id });
    setHoverHintPos({ x: event.clientX, y: event.clientY });
    // 鼠标移入时，将该线段移到数组末尾，使其在 SVG 中渲染在最上层
    setEdges((eds) => {
      const otherEdges = eds.filter((e) => e.id !== edge.id);
      return [...otherEdges, edge];
    });
  }, [setEdges]);

  const onEdgeMouseLeave = useCallback(() => {
    setHoverHintTarget(null);
  }, []);

  const getEdgeEffectValue = (edge) => {
    const dash = edge?.style?.strokeDasharray;
    const normalizedDash = typeof dash === 'string' ? dash.replace(/\s+/g, '') : '';
    const isDashed = normalizedDash === '8,4';
    const isDotted = normalizedDash === '2,6';

    if (edge?.animated) {
      if (isDashed) return 'dashedFlow';
      if (isDotted) return 'dottedFlow';
      return 'flow';
    }

    if (isDashed) return 'dashed';
    if (isDotted) return 'dotted';
    return 'none';
  };

  const onEdgeDoubleClick = useCallback((event, edge) => {
    setSelectedEdge(edge);
    edgeForm.setFieldsValue({
      label: edge.label || '',
      id: edge.id,
      sourceHandle: edge.sourceHandle || 'bottom',
      targetHandle: edge.targetHandle || 'top',
      edgeType: edge.edgeType || edge.type || 'smoothstep',
      effect: getEdgeEffectValue(edge),
      strokeWidth: edge.style?.strokeWidth || 2,
      arrowType: edge.markerEnd?.type || edge.markerStart?.type || 'arrowclosed',
      arrowDirection: edge.markerStart && edge.markerEnd ? 'both' : 
                     edge.markerEnd ? 'target' : 
                     edge.markerStart ? 'source' : 'none'
    });
    setIsEdgeModalOpen(true);
  }, [edgeForm]);

  useEffect(() => {
    const handleEditEdgeLabel = (event) => {
      const { id } = event.detail;
      const edge = edges.find(e => e.id === id);
      if (edge) {
        onEdgeDoubleClick(null, edge);
      }
    };

    window.addEventListener('edit-edge-label', handleEditEdgeLabel);
    return () => {
      window.removeEventListener('edit-edge-label', handleEditEdgeLabel);
    };
  }, [edges, onEdgeDoubleClick]);

  const onConnect = useCallback((connection) => {
    // 如果连接信息不完整，直接返回null来阻止连接
    if (!connection.source || !connection.target) {
      setIsConnecting(false);
      return null;
    }
    
    const sourceNode = nodes.find(node => node.id === connection.source);
    const targetNode = nodes.find(node => node.id === connection.target);
    
    if (!sourceNode || !targetNode) {
      setIsConnecting(false);
      return null;
    }
    
    if (connection.source === connection.target) {
      setIsConnecting(false);
      return null;
    }
    
    const sourceHandle = connection.sourceHandle || 'bottom';
    const targetHandle = connection.targetHandle || 'top';
    
    const existingEdge = edges.find(edge => 
      edge.source === connection.source && 
      edge.target === connection.target &&
      edge.sourceHandle === sourceHandle &&
      edge.targetHandle === targetHandle
    );
    
    if (existingEdge) {
      setIsConnecting(false);
      connectSuccessful.current = true; // 标记为已处理，避免 onConnectEnd 弹出“连接已取消”
      message.warning('不可重复连接');
      return null;
    }
    
    const sourceType = sourceNode.data.type;
    const targetType = targetNode.data.type;
    
    let defaultLabel = '连接';
    let defaultEdgeType = 'smoothstep';
    let defaultStroke = '#1890ff';
    let defaultStrokeWidth = 2;
    
    if (sourceType === 'nginx' && targetType === 'my-panel') {
      defaultLabel = 'HTTP请求';
      defaultEdgeType = 'smoothstep';
      defaultStroke = '#1890ff';
      defaultStrokeWidth = 3;
    }
    else if (sourceType === 'my-panel' && targetType === 'proxy') {
      defaultLabel = '服务调用';
      defaultEdgeType = 'default';
      defaultStroke = '#52c41a';
      defaultStrokeWidth = 2;
    }
    else if (sourceType === 'nginx' && targetType === 'proxy') {
      defaultLabel = '直接代理';
      defaultEdgeType = 'straight';
      defaultStroke = '#fa8c16';
      defaultStrokeWidth = 2;
    }
    else if (sourceType === 'proxy' && targetType === 'my-panel') {
      defaultLabel = '反向调用';
      defaultEdgeType = 'default';
      defaultStroke = '#722ed1';
      defaultStrokeWidth = 2;
    }
    else if (sourceType === 'my-panel' && targetType === 'my-panel') {
      defaultLabel = '服务间调用';
      defaultEdgeType = 'step';
      defaultStroke = '#eb2f96';
      defaultStrokeWidth = 2;
    }
    else if (sourceType === 'proxy' && targetType === 'proxy') {
      defaultLabel = '代理间通信';
      defaultEdgeType = 'smoothstep';
      defaultStroke = '#13c2c2';
      defaultStrokeWidth = 2;
    }
    else {
      const customSourceType = customNodeTypes.find(t => t.type === sourceType);
      const customTargetType = customNodeTypes.find(t => t.type === targetType);
      
      if (customSourceType || customTargetType) {
        defaultLabel = '自定义连接';
        defaultEdgeType = 'smoothstep';
        defaultStroke = customSourceType ? customSourceType.color : '#1890ff';
        defaultStrokeWidth = 2;
      }
    }
    
    const newEdge = {
      id: `edge-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      source: connection.source,
      sourceHandle: sourceHandle,
      target: connection.target,
      targetHandle: targetHandle,
      label: defaultLabel,
      type: 'editable',
      edgeType: defaultEdgeType,
      data: { points: [], edgeType: defaultEdgeType },
      style: {
        stroke: defaultStroke,
        strokeWidth: defaultStrokeWidth,
        strokeDasharray: '6,4'
      },
      animated: true,
      markerEnd: {
        type: 'arrowclosed',
        color: defaultStroke
      }
    };
    
    // 如果有架构图ID，实时持久化连线
    if (diagramId) {
      try {
        addEdge({
          diagramId: diagramId,
          sourceNodeId: newEdge.source,
          targetNodeId: newEdge.target,
          sourceHandle: newEdge.sourceHandle,
          targetHandle: newEdge.targetHandle,
          edgeLabel: newEdge.label,
          edgeType: newEdge.edgeType,
          edgeStyle: JSON.stringify(newEdge.style),
          animated: newEdge.animated ? '1' : '0',
          color: newEdge.style.stroke,
          weight: newEdge.style.strokeWidth
        }).then(res => {
          if (res.code === 200) {
            newEdge.id = res.data.id.toString();
          }
        });
      } catch (error) {
        console.error('实时保存连线失败:', error);
      }
    }

    setEdges((currentEdges) => {
      const updatedEdges = [...currentEdges, newEdge];
      return updatedEdges;
    });
    setDefaultEdges((defaultEdgesList) => [...defaultEdgesList, newEdge]);
    setIsConnecting(false);
    connectSuccessful.current = true;
    message.success('连接创建成功');
    
    return newEdge;
  }, [nodes, edges, isConnecting]);

  const onReconnect = useCallback((oldEdge, newConnection) => {
    // 检查是否重连到同一个地方
    if (oldEdge.source === newConnection.source && 
        oldEdge.target === newConnection.target && 
        oldEdge.sourceHandle === newConnection.sourceHandle && 
        oldEdge.targetHandle === newConnection.targetHandle) {
      return;
    }

    // 重连逻辑
    const sourceNode = nodes.find(node => node.id === newConnection.source);
    const targetNode = nodes.find(node => node.id === newConnection.target);
    
    if (!sourceNode || !targetNode || newConnection.source === newConnection.target) {
      return;
    }

    // 检查新连接是否已经存在
    const existingEdge = edges.find(edge => 
      edge.id !== oldEdge.id &&
      edge.source === newConnection.source && 
      edge.target === newConnection.target &&
      edge.sourceHandle === newConnection.sourceHandle &&
      edge.targetHandle === newConnection.targetHandle
    );
    
    if (existingEdge) {
      message.warning('不可重复连接');
      return;
    }

    setEdges((els) => {
      return els.map((edge) => {
        if (edge.id === oldEdge.id) {
          // 重新计算样式和标签
          const sourceType = sourceNode.data.type;
          const targetType = targetNode.data.type;
          
          let defaultLabel = '连接';
          let defaultEdgeType = 'smoothstep';
          let defaultStroke = '#1890ff';
          let defaultStrokeWidth = 2;
          
          if (sourceType === 'nginx' && targetType === 'my-panel') {
            defaultLabel = 'HTTP请求';
            defaultEdgeType = 'smoothstep';
            defaultStroke = '#1890ff';
            defaultStrokeWidth = 3;
          }
          else if (sourceType === 'my-panel' && targetType === 'proxy') {
            defaultLabel = '服务调用';
            defaultEdgeType = 'default';
            defaultStroke = '#52c41a';
            defaultStrokeWidth = 2;
          }
          else if (sourceType === 'nginx' && targetType === 'proxy') {
            defaultLabel = '直接代理';
            defaultEdgeType = 'straight';
            defaultStroke = '#fa8c16';
            defaultStrokeWidth = 2;
          }
          else if (sourceType === 'proxy' && targetType === 'my-panel') {
            defaultLabel = '反向调用';
            defaultEdgeType = 'default';
            defaultStroke = '#722ed1';
            defaultStrokeWidth = 2;
          }
          else if (sourceType === 'my-panel' && targetType === 'my-panel') {
            defaultLabel = '服务间调用';
            defaultEdgeType = 'step';
            defaultStroke = '#eb2f96';
            defaultStrokeWidth = 2;
          }
          else if (sourceType === 'proxy' && targetType === 'proxy') {
            defaultLabel = '代理间通信';
            defaultEdgeType = 'smoothstep';
            defaultStroke = '#13c2c2';
            defaultStrokeWidth = 2;
          }
          else {
            const customSourceType = customNodeTypes.find(t => t.type === sourceType);
            const customTargetType = customNodeTypes.find(t => t.type === targetType);
            
            if (customSourceType || customTargetType) {
              defaultLabel = '自定义连接';
              defaultEdgeType = 'smoothstep';
              defaultStroke = customSourceType ? customSourceType.color : '#1890ff';
              defaultStrokeWidth = 2;
            }
          }

          return {
            ...edge,
            source: newConnection.source,
            sourceHandle: newConnection.sourceHandle,
            target: newConnection.target,
            targetHandle: newConnection.targetHandle,
            label: defaultLabel,
            type: 'editable',
            edgeType: defaultEdgeType,
            data: { ...(edge.data || {}), edgeType: defaultEdgeType },
            style: {
              ...edge.style,
              stroke: defaultStroke,
              strokeWidth: defaultStrokeWidth
            },
            markerEnd: {
              ...edge.markerEnd,
              color: defaultStroke
            }
          };
        }
        return edge;
      });
    });

    setDefaultEdges((els) => {
      return els.map((edge) => {
        if (edge.id === oldEdge.id) {
          const sourceType = sourceNode.data.type;
          const targetType = targetNode.data.type;
          
          let defaultLabel = '连接';
          let defaultEdgeType = 'smoothstep';
          let defaultStroke = '#1890ff';
          let defaultStrokeWidth = 2;
          
          if (sourceType === 'nginx' && targetType === 'my-panel') {
            defaultLabel = 'HTTP请求';
            defaultEdgeType = 'smoothstep';
            defaultStroke = '#1890ff';
            defaultStrokeWidth = 3;
          }
          else if (sourceType === 'my-panel' && targetType === 'proxy') {
            defaultLabel = '服务调用';
            defaultEdgeType = 'default';
            defaultStroke = '#52c41a';
            defaultStrokeWidth = 2;
          }
          else if (sourceType === 'nginx' && targetType === 'proxy') {
            defaultLabel = '直接代理';
            defaultEdgeType = 'straight';
            defaultStroke = '#fa8c16';
            defaultStrokeWidth = 2;
          }
          else if (sourceType === 'proxy' && targetType === 'my-panel') {
            defaultLabel = '反向调用';
            defaultEdgeType = 'default';
            defaultStroke = '#722ed1';
            defaultStrokeWidth = 2;
          }
          else if (sourceType === 'my-panel' && targetType === 'my-panel') {
            defaultLabel = '服务间调用';
            defaultEdgeType = 'step';
            defaultStroke = '#eb2f96';
            defaultStrokeWidth = 2;
          }
          else if (sourceType === 'proxy' && targetType === 'proxy') {
            defaultLabel = '代理间通信';
            defaultEdgeType = 'smoothstep';
            defaultStroke = '#13c2c2';
            defaultStrokeWidth = 2;
          }

          return {
            ...edge,
            source: newConnection.source,
            sourceHandle: newConnection.sourceHandle,
            target: newConnection.target,
            targetHandle: newConnection.targetHandle,
            label: defaultLabel,
            type: 'editable',
            edgeType: defaultEdgeType,
            data: { ...(edge.data || {}), edgeType: defaultEdgeType },
            style: {
              ...edge.style,
              stroke: defaultStroke,
              strokeWidth: defaultStrokeWidth
            },
            markerEnd: {
              ...edge.markerEnd,
              color: defaultStroke
            }
          };
        }
        return edge;
      });
    });
    
    connectSuccessful.current = true;
    message.success('连接重连成功');
  }, [nodes, edges]);

  const onReconnectStart = useCallback(() => {
    connectSuccessful.current = false;
    connectionActionRef.current = 'reconnect';
  }, []);

  const onReconnectEnd = useCallback(() => {
    if (!connectSuccessful.current) {
      // 如果重连没有成功且拖拽结束在空白处，可以考虑删除原连线或保持原样
      // 这里我们选择保持原样，不做任何处理
    }
    connectionActionRef.current = null;
    connectSuccessful.current = false;
  }, []);

  const onConnectStart = useCallback((event, { nodeId }) => {
    setIsConnecting(true);
    connectSuccessful.current = false;
    connectionActionRef.current = 'connect';
    const node = nodes.find(n => n.id === nodeId);
    if (node) {
      message.info(`开始从 "${node.data.name}" 创建连接，松开鼠标取消`);
    }
  }, [nodes]);

  const isValidConnection = useCallback((connection) => {
    if (!connection.source || !connection.target) {
      return false;
    }
    
    const sourceNode = nodes.find(node => node.id === connection.source);
    const targetNode = nodes.find(node => node.id === connection.target);
    
    if (!sourceNode || !targetNode) {
      return false;
    }
    
    if (connection.source === connection.target) {
      return false;
    }
    
    const sourceHandle = connection.sourceHandle || 'bottom';
    const targetHandle = connection.targetHandle || 'top';
    
    const existingEdge = edges.find(edge => 
      edge.source === connection.source && 
      edge.target === connection.target &&
      edge.sourceHandle === sourceHandle &&
      edge.targetHandle === targetHandle
    );
    
    if (existingEdge) {
      return false;
    }
    
    return true;
  }, [nodes, edges]);

  const onConnectEnd = useCallback(() => {
    // 如果没有通过 onConnect 建立连接，则视为取消
    if (connectionActionRef.current === 'connect' && !connectSuccessful.current) {
      setIsConnecting(false);
      message.info('连接已取消');
      
      // 强制刷新 edges 状态，有时能清除 ReactFlow 残留的临时连线
      setEdges((eds) => [...eds]);
    }
    connectionActionRef.current = null;
    connectSuccessful.current = false;
  }, [setEdges]);

  const onPaneClick = useCallback(() => {
    // 如果正在连接，点击空白区域时取消连接
    if (isConnecting && connectionActionRef.current === 'connect') {
      setIsConnecting(false);
      message.info('连接已取消');
      
      // 强制刷新 edges 状态
      setEdges((eds) => [...eds]);
    }
    
    // 点击空白区域时取消选择
    setSelectedNode(null);
    setSelectedEdge(null);
  }, [isConnecting, setEdges]);

  const onNodeDragStop = useCallback(() => {}, []);

  const handleAddNode = () => {
    modalForm.resetFields();
    modalForm.setFieldsValue({
      type: customNodeTypes.length > 0 ? customNodeTypes[0].type : 'proxy',
      name: '',
      description: '',
      ip: '',
      port: 80,
      status: 'running',
      configContent: ''
    });
    setCurrentNode(null);
    setIsModalOpen(true);
  };

  const handleEditNode = () => {
    if (selectedNode) {
      const configContent = typeof selectedNode.data.config === 'string'
        ? selectedNode.data.config
        : selectedNode.data.config
          ? JSON.stringify(selectedNode.data.config, null, 2)
          : '';
      modalForm.setFieldsValue({
        type: selectedNode.data.type,
        id: selectedNode.id,
        name: selectedNode.data.name,
        description: selectedNode.data.description,
        ip: selectedNode.data.ip,
        port: selectedNode.data.port,
        status: selectedNode.data.status,
        configContent
      });
      setCurrentNode(selectedNode);
      setIsModalOpen(true);
      setIsDrawerOpen(false);
    }
  };

  const handleDeleteNode = () => {
    if (selectedNode) {
      Modal.confirm({
        title: '确认删除',
        content: `确定要删除节点 "${selectedNode.data.name}" 吗？这也会删除所有与之相关的连线。`,
        okText: '确定',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: async () => {
          setLoading(true);
          try {
            if (diagramId) {
              await delNode(selectedNode.id);
            }
            setNodes((currentNodes) => currentNodes.filter((node) => node.id !== selectedNode.id));
            setEdges((currentEdges) => currentEdges.filter((edge) => 
              edge.source !== selectedNode.id && edge.target !== selectedNode.id
            ));
            setIsDrawerOpen(false);
            setSelectedNode(null);
            message.success('删除成功');
          } catch (error) {
            console.error(error);
            message.error('删除节点失败');
          } finally {
            setLoading(false);
          }
        }
      });
    }
  };

  const handleCreateConnection = () => {
    if (selectedNode) {
      connectionForm.resetFields();
      connectionForm.setFieldsValue({
        source: selectedNode.id,
        sourceName: selectedNode.data.name
      });
      setIsConnectionModalOpen(true);
    }
  };

  const handleConnectionModalOk = () => {
    connectionForm.validateFields().then((values) => {
      const sourceNode = nodes.find(node => node.id === values.source);
      const targetNode = nodes.find(node => node.id === values.target);
      
      if (!sourceNode || !targetNode) {
        message.warning('找不到源节点或目标节点');
        return;
      }
      
      const existingEdge = edges.find(edge => 
        edge.source === values.source && edge.target === values.target
      );
      
      if (existingEdge) {
        message.warning('不可重复连接');
        return;
      }
      
      if (values.source === values.target) {
        message.warning('不能连接到自身');
        return;
      }
      
      const sourceType = sourceNode.data.type;
      const targetType = targetNode.data.type;
      
      let defaultLabel = '连接';
      let defaultEdgeType = 'smoothstep';
      let defaultStroke = '#1890ff';
      let defaultStrokeWidth = 2;
      
      if (sourceType === 'nginx' && targetType === 'my-panel') {
        defaultLabel = 'HTTP请求';
        defaultEdgeType = 'smoothstep';
        defaultStroke = '#1890ff';
        defaultStrokeWidth = 3;
      }
      else if (sourceType === 'my-panel' && targetType === 'proxy') {
        defaultLabel = '服务调用';
        defaultEdgeType = 'default';
        defaultStroke = '#52c41a';
        defaultStrokeWidth = 2;
      }
      else if (sourceType === 'nginx' && targetType === 'proxy') {
        defaultLabel = '直接代理';
        defaultEdgeType = 'straight';
        defaultStroke = '#fa8c16';
        defaultStrokeWidth = 2;
      }
      else if (sourceType === 'proxy' && targetType === 'my-panel') {
        defaultLabel = '反向调用';
        defaultEdgeType = 'default';
        defaultStroke = '#722ed1';
        defaultStrokeWidth = 2;
      }
      else if (sourceType === 'my-panel' && targetType === 'my-panel') {
        defaultLabel = '服务间调用';
        defaultEdgeType = 'step';
        defaultStroke = '#eb2f96';
        defaultStrokeWidth = 2;
      }
      else if (sourceType === 'proxy' && targetType === 'proxy') {
        defaultLabel = '代理间通信';
        defaultEdgeType = 'smoothstep';
        defaultStroke = '#13c2c2';
        defaultStrokeWidth = 2;
      }
      else {
        const customSourceType = customNodeTypes.find(t => t.type === sourceType);
        const customTargetType = customNodeTypes.find(t => t.type === targetType);
        
        if (customSourceType || customTargetType) {
          defaultLabel = '自定义连接';
          defaultEdgeType = 'smoothstep';
          defaultStroke = customSourceType ? customSourceType.color : '#1890ff';
          defaultStrokeWidth = 2;
        }
      }
      
      const newEdge = {
        id: `edge-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        source: values.source,
        target: values.target,
        label: values.label || defaultLabel,
        type: 'editable',
        edgeType: values.edgeType || defaultEdgeType,
        data: { points: [], edgeType: values.edgeType || defaultEdgeType },
        style: {
          stroke: defaultStroke,
          strokeWidth: defaultStrokeWidth
        },
        animated: true,
        markerStart: values.arrowDirection === 'source' || values.arrowDirection === 'both'
          ? { type: values.arrowType || 'arrowclosed', color: defaultStroke }
          : undefined,
        markerEnd: values.arrowDirection === 'target' || values.arrowDirection === 'both'
          ? { type: values.arrowType || 'arrowclosed', color: defaultStroke }
          : undefined
      };
      
      setEdges((currentEdges) => [...currentEdges, newEdge]);
      setDefaultEdges((defaultEdgesList) => [...defaultEdgesList, newEdge]);
      setIsConnectionModalOpen(false);
      connectionForm.resetFields();
      message.success('连接创建成功');
    });
  };

  const handleConnectionModalCancel = () => {
    setIsConnectionModalOpen(false);
    connectionForm.resetFields();
  };

  const handleModalOk = () => {
    modalForm.validateFields().then(async (values) => {
      const { configContent, ...rest } = values;
      const configStr = typeof configContent === 'string' ? configContent : '';
      
      setLoading(true);
      try {
        if (currentNode) {
          // 修改节点
          const nodeData = {
            id: currentNode.id,
            diagramId: diagramId,
            nodeName: rest.name,
            nodeCode: rest.id,
            nodeType: rest.type,
            ip: rest.ip,
            port: rest.port,
            status: rest.status === 'running' ? '0' : '1',
            remark: rest.description,
            config: configStr
          };
          
          if (diagramId) {
            await updateNode(nodeData);
          }

          setNodes((currentNodes) =>
            currentNodes.map((node) =>
              node.id === currentNode.id
                ? { ...node, data: { ...node.data, ...rest, config: configStr } }
                : node
            )
          );
          message.success('修改成功');
        } else {
          // 新增节点
          const nodeData = {
            diagramId: diagramId,
            nodeName: rest.name,
            nodeCode: rest.id, // 映射前端的“节点ID”到后端的“节点编码”
            nodeType: rest.type,
            positionX: 400,
            positionY: 400,
            ip: rest.ip,
            port: rest.port,
            status: rest.status === 'running' ? '0' : '1',
            remark: rest.description,
            config: configStr
          };

          let newNodeId = values.id || `node-${Date.now()}`;
          if (diagramId) {
            const res = await addNode(nodeData);
            if (res.code === 200) {
              newNodeId = res.data.id.toString();
            }
          }

          const newNode = {
            id: newNodeId,
            type: rest.type,
            position: { x: 400, y: 400 },
            connectable: true,
            data: {
              name: rest.name,
              type: rest.type,
              description: rest.description,
              status: rest.status,
              ip: rest.ip,
              port: rest.port,
              config: configStr
            },
          };
          setNodes((currentNodes) => [...currentNodes, newNode]);
          message.success('新增成功');
        }
        
        setIsModalOpen(false);
        modalForm.resetFields();
        setCurrentNode(null);
      } catch (error) {
        console.error(error);
        message.error('保存节点失败');
      } finally {
        setLoading(false);
      }
    });
  };

  const handleSaveArchitecture = async () => {
    if (!diagramId) {
      message.warning('请先选择或创建一个架构图');
      return;
    }

    setLoading(true);
    try {
      // 1. 更新架构图名称
      await updateDiagram({ id: diagramId, diagramName: architectureName });

      // 2. 转换节点格式供后端保存
      const backendNodes = nodes.map(node => ({
        frontId: node.id, // 保留前端使用的ID（可能是数字字符串或临时字符串）
        diagramId: diagramId,
        nodeName: node.data.name,
        nodeType: node.type,
        positionX: Math.round(node.position.x),
        positionY: Math.round(node.position.y),
        ip: node.data.ip,
        port: node.data.port,
        status: node.data.status === 'running' ? '0' : '1',
        remark: node.data.description,
        config: node.data.config
      }));

      // 3. 转换连线格式供后端保存
      const nodeIdSet = new Set(nodes.map((n) => n.id));
      const backendEdges = edges
        .filter((edge) => edge?.source && edge?.target && nodeIdSet.has(edge.source) && nodeIdSet.has(edge.target))
        .map((edge) => {
        const style = edge.style || {};
        const edgeStyle = JSON.stringify({
          stroke: style.stroke || '#1890ff',
          strokeWidth: style.strokeWidth || 2,
          ...(style.strokeDasharray ? { strokeDasharray: style.strokeDasharray } : {})
        });

        return {
          diagramId: diagramId,
          sourceFrontId: edge.source,
          targetFrontId: edge.target,
          sourceHandle: edge.sourceHandle,
          targetHandle: edge.targetHandle,
          edgeLabel: edge.label,
          edgeType: edge.edgeType || 'smoothstep',
          points: edge.data?.points ? JSON.stringify(edge.data.points) : '[]',
          edgeStyle,
          animated: edge.animated ? '1' : '0',
          color: style.stroke || '#1890ff',
          weight: style.strokeWidth || 2
        };
      });

      // 4. 调用全量更新接口
      const response = await replaceDiagramData(diagramId, {
        nodes: backendNodes,
        edges: backendEdges
      });

      if (response.code === 200) {
        message.success('架构保存成功');
        savedSignatureRef.current = currentSignature;
        loadData(diagramId); // 重新加载数据以获取后端生成的ID
      }
    } catch (error) {
      console.error('保存架构失败:', error);
      message.error('保存架构失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = () => {
    Modal.confirm({
      title: '确认刷新',
      content: '刷新将放弃当前未保存的所有更改，并从服务器重新加载数据。确定吗？',
      onOk: () => {
        loadData(diagramId);
        message.success('已刷新');
      }
    });
  };

  // 版本历史处理函数
  const handleOpenHistory = async () => {
    if (!diagramId) return;
    setLoading(true);
    try {
      const res = await listHistoryByDiagramId(diagramId);
      if (res.code === 200) {
        setHistoryList(res.data);
        setIsHistoryDrawerOpen(true);
      }
    } catch (error) {
      console.error(error);
      message.error('加载历史记录失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateSnapshot = () => {
    snapshotForm.resetFields();
    // 默认生成一个新的版本号 (简单处理)
    const nextVersion = `V${new Date().getTime().toString().slice(-6)}`;
    snapshotForm.setFieldsValue({ version: nextVersion });
    setIsSnapshotModalOpen(true);
  };

  const handleSnapshotModalOk = async () => {
    try {
      const values = await snapshotForm.validateFields();
      setLoading(true);
      const res = await createSnapshot({
        diagramId: diagramId,
        version: values.version,
        versionName: values.versionName,
        changeSummary: values.changeSummary
      });
      if (res.code === 200) {
        message.success('版本快照创建成功');
        setIsSnapshotModalOpen(false);
        // 如果历史侧边栏已打开，刷新它
        if (isHistoryDrawerOpen) {
          handleOpenHistory();
        }
      }
    } catch (error) {
      console.error(error);
      message.error('创建快照失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRestoreVersion = async (historyId) => {
    Modal.confirm({
      title: '确认恢复',
      content: '确定要将当前架构图恢复到该版本吗？这将覆盖当前所有未保存的内容。',
      onOk: async () => {
        setLoading(true);
        try {
          const res = await restoreVersion(historyId);
          if (res.code === 200) {
            message.success('版本恢复成功');
            loadData(diagramId);
            setIsHistoryDrawerOpen(false);
          }
        } catch (error) {
          console.error(error);
          message.error('恢复版本失败');
        } finally {
          setLoading(false);
        }
      }
    });
  };

  const handleDrawerClose = () => {
    setIsDrawerOpen(false);
    setSelectedNode(null);
    drawerForm.resetFields();
  };

  const handleEdgeModalOk = () => {
    edgeForm.validateFields().then(async (values) => {
      if (selectedEdge) {
        const effectToConfig = {
          none: { animated: false, dash: undefined },
          flow: { animated: true, dash: '6,4' },
          dashed: { animated: false, dash: '8,4' },
          dashedFlow: { animated: true, dash: '8,4' },
          dotted: { animated: false, dash: '2,6' },
          dottedFlow: { animated: true, dash: '2,6' },
        };

        const effectConfig = effectToConfig[values.effect] || effectToConfig.none;
        const strokeWidth = Number(values.strokeWidth || selectedEdge.style?.strokeWidth || 2);
        const nextStyle = {
          ...(selectedEdge.style || {}),
          strokeWidth,
          ...(effectConfig.dash ? { strokeDasharray: effectConfig.dash } : {}),
        };
        if (!effectConfig.dash && nextStyle.strokeDasharray != null) {
          delete nextStyle.strokeDasharray;
        }

        const edgeStyleForBackend = JSON.stringify({
          stroke: nextStyle.stroke || '#1890ff',
          strokeWidth,
          ...(effectConfig.dash ? { strokeDasharray: effectConfig.dash } : {}),
        });

        const markerStart = values.arrowDirection === 'source' || values.arrowDirection === 'both' 
          ? { type: values.arrowType, color: selectedEdge.style?.stroke || '#1890ff' }
          : undefined;
        
        const markerEnd = values.arrowDirection === 'target' || values.arrowDirection === 'both'
          ? { type: values.arrowType, color: selectedEdge.style?.stroke || '#1890ff' }
          : undefined;

        setLoading(true);
        try {
          if (diagramId) {
            await updateEdge({
              id: selectedEdge.id,
              diagramId: diagramId,
              sourceNodeId: selectedEdge.source,
              targetNodeId: selectedEdge.target,
              sourceHandle: values.sourceHandle,
              targetHandle: values.targetHandle,
              edgeLabel: values.label,
              edgeType: values.edgeType,
              edgeStyle: edgeStyleForBackend,
              animated: effectConfig.animated ? '1' : '0',
              color: nextStyle.stroke || '#1890ff',
              weight: strokeWidth
            });
          }

          setEdges((currentEdges) =>
            currentEdges.map((edge) =>
              edge.id === selectedEdge.id
                ? { 
                    ...edge, 
                    label: values.label,
                    sourceHandle: values.sourceHandle,
                    targetHandle: values.targetHandle,
                    edgeType: values.edgeType,
                    type: 'editable',
                    animated: effectConfig.animated,
                    style: nextStyle,
                    markerStart,
                    markerEnd,
                    data: { ...(edge.data || {}), edgeType: values.edgeType }
                  }
                : edge
            )
          );
          setDefaultEdges((currentEdges) =>
            currentEdges.map((edge) =>
              edge.id === selectedEdge.id
                ? { 
                    ...edge, 
                    label: values.label,
                    sourceHandle: values.sourceHandle,
                    targetHandle: values.targetHandle,
                    edgeType: values.edgeType,
                    type: 'editable',
                    animated: effectConfig.animated,
                    style: nextStyle,
                    markerStart,
                    markerEnd,
                    data: { ...(edge.data || {}), edgeType: values.edgeType }
                  }
                : edge
            )
          );
          message.success('边缘修改成功');
        } catch (error) {
          console.error(error);
          message.error('修改连线失败');
        } finally {
          setLoading(false);
        }
      }
      
      setIsEdgeModalOpen(false);
      edgeForm.resetFields();
      setSelectedEdge(null);
    });
  };

  const handleEdgeModalCancel = () => {
    setIsEdgeModalOpen(false);
    edgeForm.resetFields();
    setSelectedEdge(null);
  };

  const handleDeleteEdge = () => {
    if (selectedEdge) {
      Modal.confirm({
        title: '确认删除',
        content: `确定要删除边缘 "${selectedEdge.label}" 吗？`,
        okText: '确定',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: async () => {
          setLoading(true);
          try {
            if (diagramId) {
              await delEdge(selectedEdge.id);
            }
            setEdges((currentEdges) => currentEdges.filter((edge) => edge.id !== selectedEdge.id));
            setDefaultEdges((defaultEdgesList) => defaultEdgesList.filter((edge) => edge.id !== selectedEdge.id));
            setIsEdgeModalOpen(false);
            edgeForm.resetFields();
            setSelectedEdge(null);
            message.success('边缘删除成功');
          } catch (error) {
            console.error(error);
            message.error('删除连线失败');
          } finally {
            setLoading(false);
          }
        }
      });
    }
  };

  const nodeTypes = useMemo(() => {
    const NodeRenderer = (props) => (
      <CustomNode {...props} customNodeTypes={customNodeTypes} />
    );
    const types = {
      nginx: NodeRenderer,
      'my-panel': NodeRenderer,
      proxy: NodeRenderer,
    };
    customNodeTypes.forEach((customType) => {
      types[customType.type] = NodeRenderer;
    });
    types.custom = NodeRenderer;
    return types;
  }, [customNodeTypes]);

  const edgeTypes = useMemo(() => ({
    editable: EditableEdge
  }), []);

  return (
    <div className="architecture-edit-container">
      <div className="header-toolbar">
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <h2 className="title">
            <ClusterOutlined style={{ color: '#1890ff' }} />
            架构编排
          </h2>
          <Input
            placeholder="请输入架构名称"
            className="architecture-name-input"
            value={architectureName}
            onChange={(e) => setArchitectureName(e.target.value)}
            size="large"
          />
          {isModified && (
            <Tag color="orange" style={{ margin: 0, fontWeight: 600 }}>
              已修改
            </Tag>
          )}
        </div>
        
        <Space size="middle">
          <Button 
            icon={<ArrowLeftOutlined />} 
            onClick={() => navigate('/architecture/arch-diagram')}
            size="large"
            className="toolbar-button"
          >
            返回列表
          </Button>
          <Button 
            type="primary" 
            icon={<PlusOutlined />} 
            onClick={handleAddNode}
            size="large"
            className="toolbar-button primary"
          >
            新增节点
          </Button>
          <Button 
            icon={<SaveOutlined />} 
            onClick={handleSaveArchitecture}
            size="large"
            className="toolbar-button"
            loading={loading}
          >
            保存架构
          </Button>
          <Button 
            icon={<CloudUploadOutlined />} 
            onClick={handleCreateSnapshot}
            size="large"
            className="toolbar-button"
            loading={loading}
          >
            创建快照
          </Button>
          <Button 
            icon={<HistoryOutlined />} 
            onClick={handleOpenHistory}
            size="large"
            className="toolbar-button"
            loading={loading}
          >
            版本历史
          </Button>
          <Button 
            icon={<SendOutlined />} 
            onClick={async () => {
              if (diagramId) {
                setLoading(true);
                try {
                  const res = await publishDiagram(diagramId);
                  if (res.code === 200) {
                    message.success('架构已发布');
                    loadData(diagramId);
                  }
                } catch (error) {
                  console.error(error);
                  message.error('发布失败');
                } finally {
                  setLoading(false);
                }
              }
            }}
            size="large"
            className="toolbar-button"
            loading={loading}
            style={{ backgroundColor: '#52c41a', borderColor: '#52c41a', color: '#fff' }}
          >
            发布架构
          </Button>
          <Button 
            icon={<ReloadOutlined />} 
            onClick={handleRefresh}
            size="large"
            className="toolbar-button"
          >
            刷新
          </Button>
        </Space>
      </div>

      <div className="flow-canvas-container">
        <Spin spinning={loading} size="large" tip="加载架构中..." style={{ height: '100%' }}>
          <ReactFlow
            key={`${defaultNodes.length}-${defaultEdges.length}`}
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={onNodeClick}
          onNodeMouseEnter={onNodeMouseEnter}
          onNodeMouseLeave={onNodeMouseLeave}
          onEdgeClick={onEdgeClick}
          onEdgeMouseEnter={onEdgeMouseEnter}
          onEdgeMouseLeave={onEdgeMouseLeave}
          onNodeDoubleClick={onNodeDoubleClick}
          onEdgeDoubleClick={onEdgeDoubleClick}
          onNodeDragStop={onNodeDragStop}
          onPaneClick={onPaneClick}
          onConnect={onConnect}
          onConnectStart={onConnectStart}
          onConnectEnd={onConnectEnd}
          isValidConnection={isValidConnection}
          onReconnect={onReconnect}
          onReconnectStart={onReconnectStart}
          onReconnectEnd={onReconnectEnd}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          fitView
          nodesDraggable={true}
          nodesConnectable={true}
          edgesUpdatable={true}
          elementsSelectable={true}
          panOnDrag={true}
          zoomOnScroll={true}
          zoomOnPinch={true}
          connectOnClick={false}
          connectionMode="loose"
          connectionLineType="smoothstep"
          connectionLineStyle={{
            stroke: '#1890ff',
            strokeWidth: 2,
            strokeDasharray: '5,5',
            opacity: 0.6
          }}
          snapToGrid={true}
          snapGrid={[15, 15]}
          autoPanOnConnect={true}
          autoPanSpeed={0.8}
          preventScrolling={false}
          defaultEdgeOptions={{
            animated: true,
            style: {
              strokeWidth: 2,
            },
          }}
          onNodesDelete={() => {
            // 我们在快捷键处理函数中已经处理了确认逻辑，所以这里不需要重复处理
            // 或者我们可以移除 deleteKeyCode 让快捷键完全由 useEffect 掌控
          }}
          onEdgesDelete={() => {
          }}
          deleteKeyCode={null} // 禁用 ReactFlow 默认删除快捷键，统一使用我们的全局监听器控制
          multiSelectionKeyCode="Shift"
          panActivationKeyCode="Space"
          zoomActivationKeyCode="Meta"
          className="react-flow-wrapper"
        >
          <Background />
          <MiniMap />
          <Controls />
        </ReactFlow>
        </Spin>
        {isHoverHintVisible && hoverHintTarget && (
          <div
            className="hover-edit-hint"
            style={{
              left: hoverHintPos.x + 12,
              top: hoverHintPos.y + 12,
            }}
          >
            {hoverHintTarget.kind === 'node' ? '双击打开节点编辑' : '双击打开连线编辑'}
          </div>
        )}
      </div>

      <Modal
        title={currentNode ? '修改节点' : '新增节点'}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => {
          setIsModalOpen(false);
          modalForm.resetFields();
          setCurrentNode(null);
        }}
        width={700}
        okText="确定"
        cancelText="取消"
        style={{ borderRadius: '8px' }}
      >
        <Form form={modalForm} layout="vertical" size="large">
          <Form.Item
            name="type"
            label="节点类型"
            rules={[{ required: true, message: '请选择节点类型' }]}
            initialValue="proxy"
          >
            <Select size="large" placeholder="请选择节点类型">
              <Option value="nginx">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <ClusterOutlined style={{ color: '#1890ff' }} />
                  <span>Nginx - 负载均衡服务器</span>
                </div>
              </Option>
              <Option value="my-panel">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <ApiOutlined style={{ color: '#52c41a' }} />
                  <span>My-Panel - 后端管理服务</span>
                </div>
              </Option>
              <Option value="proxy">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <DatabaseOutlined style={{ color: '#fa8c16' }} />
                  <span>Proxy - 代理服务实例</span>
                </div>
              </Option>
              {customNodeTypes.map(customType => {
                const iconMap = {
                  'ApiOutlined': ApiOutlined,
                  'ClusterOutlined': ClusterOutlined,
                  'DatabaseOutlined': DatabaseOutlined,
                  'SettingOutlined': SettingOutlined,
                  'DeleteOutlined': DeleteOutlined,
                  'SyncOutlined': SyncOutlined,
                  'PlusOutlined': PlusOutlined,
                  'SaveOutlined': SaveOutlined,
                  'ReloadOutlined': ReloadOutlined
                };
                const IconComponent = iconMap[customType.icon] || ApiOutlined;
                return (
                  <Option key={customType.type} value={customType.type}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <IconComponent style={{ color: customType.color }} />
                      <span>{customType.name} - {customType.description}</span>
                    </div>
                  </Option>
                );
              })}
            </Select>
          </Form.Item>
          
          <Form.Item
            name="id"
            label="节点编码"
            rules={[{ required: true, message: '请输入节点编码' }]}
          >
            <Input 
              size="large" 
              placeholder="请输入节点编码 (例如: nginx-1)" 
              disabled={!!currentNode}
              style={{ backgroundColor: currentNode ? '#f5f5f5' : '#fff', cursor: currentNode ? 'not-allowed' : 'text' }}
            />
          </Form.Item>
          
          <Form.Item
            name="name"
            label="节点名称"
            rules={[{ required: true, message: '请输入节点名称' }]}
            initialValue=""
          >
            <Input size="large" placeholder="请输入节点名称" />
          </Form.Item>
          
          <Form.Item
            name="description"
            label="描述"
            rules={[{ required: true, message: '请输入描述' }]}
          >
            <Input.TextArea size="large" placeholder="请输入描述" rows={3} />
          </Form.Item>
          
          <Form.Item
            name="ip"
            label="IP地址"
            rules={[{ required: true, message: '请输入IP地址' }]}
          >
            <Input size="large" placeholder="请输入IP地址" />
          </Form.Item>
          
          <Form.Item
            name="port"
            label="端口"
            rules={[{ required: true, message: '请输入端口' }]}
          >
            <InputNumber size="large" placeholder="请输入端口号" min={1} max={65535} style={{ width: '100%' }} />
          </Form.Item>
          
          <Form.Item
            name="status"
            label="状态"
            rules={[{ required: true, message: '请选择状态' }]}
            initialValue="running"
          >
            <Select size="large">
              <Option value="running">
                <Tag color="green">运行中</Tag>
              </Option>
              <Option value="stopped">
                <Tag color="red">已停止</Tag>
              </Option>
            </Select>
          </Form.Item>

          <Divider />
          <div style={{ marginBottom: '8px', fontWeight: 'bold', color: '#262626' }}>配置信息</div>
          <Upload.Dragger
            name="file"
            multiple={false}
            showUploadList={false}
            beforeUpload={(file) => {
              const reader = new FileReader();
              reader.onload = (e) => {
                modalForm.setFieldsValue({ configContent: e.target.result });
                message.success('配置文件已加载');
              };
              reader.onerror = () => {
                message.error('读取配置文件失败');
              };
              reader.readAsText(file);
              return false;
            }}
            style={{ marginBottom: '12px' }}
          >
            <p className="ant-upload-drag-icon">
              <PlusOutlined />
            </p>
            <p className="ant-upload-text">点击或拖拽文件到此处上传配置文件</p>
            <p className="ant-upload-hint">支持通过文件上传或在下方文本框中粘贴配置内容</p>
          </Upload.Dragger>
          <Form.Item name="configContent">
            <Input.TextArea rows={8} placeholder="在此粘贴配置内容，或通过上方上传配置文件自动填充" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <SettingOutlined style={{ fontSize: '20px', color: '#1890ff' }} />
            <span style={{ fontSize: '18px', fontWeight: 'bold' }}>节点详情</span>
          </div>
        }
        placement="right"
        width={550}
        open={isDrawerOpen}
        onClose={handleDrawerClose}
        extra={
          <Space>
            <Button 
              type="primary"
              icon={<ApiOutlined />}
              onClick={handleCreateConnection}
              size="large"
              style={{ borderRadius: '6px' }}
            >
              创建连接
            </Button>
            <Button 
              icon={<SettingOutlined />} 
              onClick={handleEditNode}
              size="large"
              style={{ borderRadius: '6px' }}
            >
              编辑
            </Button>
            <Button 
              danger 
              icon={<DeleteOutlined />} 
              onClick={handleDeleteNode}
              size="large"
              style={{ borderRadius: '6px' }}
            >
              删除
            </Button>
          </Space>
        }
        style={{ borderRadius: '8px' }}
      >
        {selectedNode && (
          <div>
            <div style={{ marginBottom: '24px', padding: '16px', backgroundColor: '#f0f9ff', borderRadius: '8px' }}>
              <h3 style={{ 
                marginBottom: '12px', 
                color: '#1890ff',
                fontSize: '20px',
                fontWeight: 'bold',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}>
                {selectedNode.data.type === 'nginx' && <ClusterOutlined />}
                {selectedNode.data.type === 'my-panel' && <ApiOutlined />}
                {selectedNode.data.type === 'proxy' && <DatabaseOutlined />}
                {selectedNode.data.name || '无节点名称'}
              </h3>
              <Space size="middle">
                <Tag color="blue" style={{ fontSize: '13px', fontWeight: '500' }}>
                  {(selectedNode.data.type || 'unknown').toUpperCase()}
                </Tag>
                <Tag 
                  color={selectedNode.data.status === 'running' ? 'green' : 'red'}
                  style={{ fontSize: '13px', fontWeight: '500' }}
                >
                  {selectedNode.data.status === 'running' ? '运行中' : '已停止'}
                </Tag>
                {selectedNode.data.port && (
                  <Tag color="purple" style={{ fontSize: '13px', fontWeight: '500' }}>
                    端口: {selectedNode.data.port}
                  </Tag>
                )}
                {selectedNode.data.ip && (
                  <Tag color="cyan" style={{ fontSize: '13px', fontWeight: '500' }}>
                    IP: {selectedNode.data.ip}
                  </Tag>
                )}
              </Space>
            </div>
            
            <Divider />
            
            <Form form={drawerForm} layout="vertical" disabled>
              <Form.Item name="id" label="节点编码">
                <Input disabled style={{ backgroundColor: '#f5f5f5', cursor: 'not-allowed' }} />
              </Form.Item>
              
              <Form.Item name="name" label="节点名称">
                <Input />
              </Form.Item>
              
              <Form.Item name="description" label="描述">
                <Input.TextArea rows={3} />
              </Form.Item>
              
              <Form.Item name="ip" label="IP地址">
                <Input />
              </Form.Item>
              
              <Form.Item name="port" label="端口">
                <Input />
              </Form.Item>
              
              <Form.Item name="status" label="状态">
                <Select>
                  <Option value="running">
                    <Tag color="green">运行中</Tag>
                  </Option>
                  <Option value="stopped">
                    <Tag color="red">已停止</Tag>
                  </Option>
                </Select>
              </Form.Item>
              
              {selectedNode.data.config && (
                <div style={{ marginTop: '20px' }}>
                  <h4 style={{ 
                    marginBottom: '12px', 
                    fontSize: '16px',
                    fontWeight: 'bold',
                    color: '#262626'
                  }}>
                    配置信息
                  </h4>
                  <div style={{
                    backgroundColor: '#f5f5f5',
                    padding: '16px',
                    borderRadius: '8px',
                    fontSize: '13px',
                    overflow: 'auto',
                    border: '1px solid #e8e8e8',
                    whiteSpace: 'pre-wrap',
                    lineHeight: 1.6
                  }}>
                    {typeof selectedNode.data.config === 'string'
                      ? selectedNode.data.config
                      : JSON.stringify(selectedNode.data.config, null, 2)}
                  </div>
                </div>
              )}
            </Form>
          </div>
        )}
      </Drawer>

      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <SettingOutlined style={{ fontSize: '20px', color: '#1890ff' }} />
            <span style={{ fontSize: '18px', fontWeight: 'bold' }}>修改边缘标签</span>
          </div>
        }
        open={isEdgeModalOpen}
        onCancel={handleEdgeModalCancel}
        width={500}
        footer={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Button 
              danger 
              icon={<DeleteOutlined />} 
              onClick={handleDeleteEdge}
              size="large"
              style={{ borderRadius: '6px' }}
            >
              删除边缘
            </Button>
            <Space>
              <Button 
                onClick={handleEdgeModalCancel}
                size="large"
                style={{ borderRadius: '6px' }}
              >
                取消
              </Button>
              <Button 
                type="primary" 
                onClick={handleEdgeModalOk}
                size="large"
                style={{ borderRadius: '6px' }}
              >
                确定
              </Button>
            </Space>
          </div>
        }
        style={{ borderRadius: '8px' }}
      >
        <Form form={edgeForm} layout="vertical" size="large">
          <Form.Item name="id" label="边缘ID" hidden>
            <Input />
          </Form.Item>
          
          <Form.Item
            name="label"
            label="边缘描述"
            rules={[{ required: true, message: '请输入边缘描述' }]}
          >
            <Input size="large" placeholder="请输入边缘描述，如：HTTP请求、服务调用等" />
          </Form.Item>
          
          <Form.Item
            name="sourceHandle"
            label="源节点连接点"
            rules={[{ required: true, message: '请选择源节点连接点' }]}
            initialValue="bottom"
          >
            <Select size="large" placeholder="请选择源节点连接点">
              <Option value="top">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>⬆️</span>
                  <span>顶部（Top）</span>
                </div>
              </Option>
              <Option value="bottom">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>⬇️</span>
                  <span>底部（Bottom）</span>
                </div>
              </Option>
              <Option value="left">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>⬅️</span>
                  <span>左侧（Left）</span>
                </div>
              </Option>
              <Option value="right">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>➡️</span>
                  <span>右侧（Right）</span>
                </div>
              </Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="targetHandle"
            label="目标节点连接点"
            rules={[{ required: true, message: '请选择目标节点连接点' }]}
            initialValue="top"
          >
            <Select size="large" placeholder="请选择目标节点连接点">
              <Option value="top">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>⬆️</span>
                  <span>顶部（Top）</span>
                </div>
              </Option>
              <Option value="bottom">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>⬇️</span>
                  <span>底部（Bottom）</span>
                </div>
              </Option>
              <Option value="left">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>⬅️</span>
                  <span>左侧（Left）</span>
                </div>
              </Option>
              <Option value="right">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>➡️</span>
                  <span>右侧（Right）</span>
                </div>
              </Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="edgeType"
            label="连线类型"
            rules={[{ required: true, message: '请选择连线类型' }]}
            initialValue="smoothstep"
          >
            <Select size="large" placeholder="请选择连线类型">
              <Option value="smoothstep">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📏</span>
                  <span>直线 - SmoothStep</span>
                </div>
              </Option>
              <Option value="default">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📐</span>
                  <span>贝塞尔曲线 - Default</span>
                </div>
              </Option>
              <Option value="straight">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📏</span>
                  <span>直线 - Straight</span>
                </div>
              </Option>
              <Option value="step">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📊</span>
                  <span>阶梯线 - Step</span>
                </div>
              </Option>
              <Option value="editable">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>✍️</span>
                  <span>可编辑折线 - Editable</span>
                </div>
              </Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="effect"
            label="连线特效"
            rules={[{ required: true, message: '请选择连线特效' }]}
            initialValue="flow"
          >
            <Select size="large" placeholder="请选择连线特效">
              <Option value="none">无特效（静态实线）</Option>
              <Option value="flow">流动动画（默认）</Option>
              <Option value="dashed">虚线（静态）</Option>
              <Option value="dashedFlow">虚线流动（动画）</Option>
              <Option value="dotted">点线（静态）</Option>
              <Option value="dottedFlow">点线流动（动画）</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="strokeWidth"
            label="线宽"
            rules={[{ required: true, message: '请选择线宽' }]}
            initialValue={2}
          >
            <Select size="large" placeholder="请选择线宽">
              <Option value={1}>1 - 细</Option>
              <Option value={2}>2 - 标准</Option>
              <Option value={3}>3 - 加粗</Option>
              <Option value={4}>4</Option>
              <Option value={5}>5</Option>
              <Option value={6}>6</Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="arrowType"
            label="箭头类型"
            rules={[{ required: true, message: '请选择箭头类型' }]}
            initialValue="arrowclosed"
          >
            <Select size="large" placeholder="请选择箭头类型">
              <Option value="arrow">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>➤</span>
                  <span>普通箭头</span>
                </div>
              </Option>
              <Option value="arrowclosed">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>▶</span>
                  <span>实心箭头</span>
                </div>
              </Option>
              <Option value="arrowhead">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>▲</span>
                  <span>箭头头部</span>
                </div>
              </Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="arrowDirection"
            label="箭头方向"
            rules={[{ required: true, message: '请选择箭头方向' }]}
            initialValue="target"
          >
            <Select size="large" placeholder="请选择箭头方向">
              <Option value="target">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>→</span>
                  <span>指向目标（右侧）</span>
                </div>
              </Option>
              <Option value="source">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>←</span>
                  <span>指向源（左侧）</span>
                </div>
              </Option>
              <Option value="both">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>↔</span>
                  <span>双向箭头</span>
                </div>
              </Option>
              <Option value="none">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>—</span>
                  <span>无箭头</span>
                </div>
              </Option>
            </Select>
          </Form.Item>
          
          {selectedEdge && (
            <div style={{ 
              marginTop: '16px', 
              padding: '12px', 
              backgroundColor: '#f0f9ff', 
              borderRadius: '6px',
              fontSize: '13px',
              color: '#595959'
            }}>
              <div style={{ marginBottom: '8px', fontWeight: '500' }}>
                <span style={{ color: '#1890ff' }}>连接信息：</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span>源节点：</span>
                <span style={{ fontWeight: 'bold', color: '#262626' }}>{selectedEdge.source}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span>目标节点：</span>
                <span style={{ fontWeight: 'bold', color: '#262626' }}>{selectedEdge.target}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span>源节点连接点：</span>
                <span style={{ fontWeight: 'bold', color: '#262626' }}>
                  {selectedEdge.sourceHandle || 'bottom'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span>目标节点连接点：</span>
                <span style={{ fontWeight: 'bold', color: '#262626' }}>
                  {selectedEdge.targetHandle || 'top'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span>当前箭头类型：</span>
                <span style={{ fontWeight: 'bold', color: '#262626' }}>
                  {selectedEdge.markerEnd?.type || 'arrowclosed'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>当前箭头方向：</span>
                <span style={{ fontWeight: 'bold', color: '#262626' }}>
                  {selectedEdge.markerStart && selectedEdge.markerEnd ? '双向' : 
                   selectedEdge.markerEnd ? '指向目标' : 
                   selectedEdge.markerStart ? '指向源' : '无箭头'}
                </span>
              </div>
            </div>
          )}
        </Form>
      </Modal>

      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <ApiOutlined style={{ fontSize: '20px', color: '#1890ff' }} />
            <span style={{ fontSize: '18px', fontWeight: 'bold' }}>创建连接</span>
          </div>
        }
        open={isConnectionModalOpen}
        onCancel={handleConnectionModalCancel}
        width={600}
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
            <Space>
              <Button 
                onClick={handleConnectionModalCancel}
                size="large"
                style={{ borderRadius: '6px' }}
              >
                取消
              </Button>
              <Button 
                type="primary" 
                onClick={handleConnectionModalOk}
                size="large"
                style={{ borderRadius: '6px' }}
              >
                创建连接
              </Button>
            </Space>
          </div>
        }
        style={{ borderRadius: '8px' }}
      >
        <Form form={connectionForm} layout="vertical" size="large">
          <Form.Item name="source" label="源节点ID" hidden>
            <Input />
          </Form.Item>
          
          <Form.Item name="sourceName" label="源节点">
            <Input disabled style={{ backgroundColor: '#f5f5f5', cursor: 'not-allowed' }} />
          </Form.Item>
          
          <Form.Item
            name="target"
            label="目标节点"
            rules={[{ required: true, message: '请选择目标节点' }]}
          >
            <Select 
              size="large" 
              placeholder="请选择目标节点"
              showSearch
              optionFilterProp="children"
            >
              {nodes.map(node => (
                <Option key={node.id} value={node.id}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {node.data.type === 'nginx' && <ClusterOutlined style={{ color: '#1890ff' }} />}
                    {node.data.type === 'my-panel' && <ApiOutlined style={{ color: '#52c41a' }} />}
                    {node.data.type === 'proxy' && <DatabaseOutlined style={{ color: '#fa8c16' }} />}
                    <span>{node.data.name}</span>
                      <Tag color="blue" style={{ fontSize: '12px', marginLeft: '8px' }}>
                        {(node.data.type || 'unknown').toUpperCase()}
                      </Tag>
                  </div>
                </Option>
              ))}
            </Select>
          </Form.Item>
          
          <Form.Item
            name="label"
            label="连接描述"
            rules={[{ required: true, message: '请输入连接描述' }]}
          >
            <Input size="large" placeholder="请输入连接描述，如：HTTP请求、服务调用等" />
          </Form.Item>
          
          <Form.Item
            name="edgeType"
            label="连线类型"
            rules={[{ required: true, message: '请选择连线类型' }]}
            initialValue="smoothstep"
          >
            <Select size="large" placeholder="请选择连线类型">
              <Option value="smoothstep">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📏</span>
                  <span>直线 - SmoothStep</span>
                </div>
              </Option>
              <Option value="default">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📐</span>
                  <span>贝塞尔曲线 - Default</span>
                </div>
              </Option>
              <Option value="straight">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📏</span>
                  <span>直线 - Straight</span>
                </div>
              </Option>
              <Option value="step">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📊</span>
                  <span>阶梯线 - Step</span>
                </div>
              </Option>
              <Option value="editable">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>✍️</span>
                  <span>可编辑折线 - Editable</span>
                </div>
              </Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="arrowType"
            label="箭头类型"
            rules={[{ required: true, message: '请选择箭头类型' }]}
            initialValue="arrowclosed"
          >
            <Select size="large" placeholder="请选择箭头类型">
              <Option value="arrow">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>➤</span>
                  <span>普通箭头</span>
                </div>
              </Option>
              <Option value="arrowclosed">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>▶</span>
                  <span>实心箭头</span>
                </div>
              </Option>
              <Option value="arrowhead">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>▲</span>
                  <span>箭头头部</span>
                </div>
              </Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="arrowDirection"
            label="箭头方向"
            rules={[{ required: true, message: '请选择箭头方向' }]}
            initialValue="target"
          >
            <Select size="large" placeholder="请选择箭头方向">
              <Option value="target">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>→</span>
                  <span>指向目标（右侧）</span>
                </div>
              </Option>
              <Option value="source">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>←</span>
                  <span>指向源（左侧）</span>
                </div>
              </Option>
              <Option value="both">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>↔</span>
                  <span>双向箭头</span>
                </div>
              </Option>
              <Option value="none">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>—</span>
                  <span>无箭头</span>
                </div>
              </Option>
            </Select>
          </Form.Item>
          
          <div style={{ 
            marginTop: '16px', 
            padding: '12px', 
            backgroundColor: '#f0f9ff', 
            borderRadius: '6px',
            fontSize: '13px',
            color: '#595959'
          }}>
            <div style={{ marginBottom: '8px', fontWeight: '500' }}>
              <span style={{ color: '#1890ff' }}>连接说明：</span>
            </div>
            <div style={{ marginBottom: '4px' }}>• 源节点：当前选中的节点</div>
            <div style={{ marginBottom: '4px' }}>• 目标节点：从下拉列表中选择</div>
            <div style={{ marginBottom: '4px' }}>• 连接描述：自定义连接的名称</div>
            <div style={{ marginBottom: '4px' }}>• 连线类型：选择连接线的样式</div>
            <div style={{ marginBottom: '4px' }}>• 箭头类型：选择箭头的样式</div>
            <div>• 箭头方向：选择箭头的指向方向</div>
          </div>
        </Form>
      </Modal>

      <Modal
        title="创建版本快照"
        open={isSnapshotModalOpen}
        onOk={handleSnapshotModalOk}
        onCancel={() => setIsSnapshotModalOpen(false)}
        okText="创建"
        cancelText="取消"
      >
        <Form form={snapshotForm} layout="vertical">
          <Form.Item
            name="version"
            label="版本号"
            rules={[{ required: true, message: '请输入版本号' }]}
          >
            <Input placeholder="例如: V1.0.0" />
          </Form.Item>
          <Form.Item
            name="versionName"
            label="版本名称"
          >
            <Input placeholder="请输入版本名称" />
          </Form.Item>
          <Form.Item
            name="changeSummary"
            label="变更摘要"
          >
            <Input.TextArea placeholder="请输入变更说明" rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="版本历史"
        placement="right"
        width={400}
        onClose={() => setIsHistoryDrawerOpen(false)}
        open={isHistoryDrawerOpen}
      >
        {historyList.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>
            暂无历史记录
          </div>
        ) : (
          <div className="history-list">
            {historyList.map((item) => (
              <Card 
                key={item.id} 
                size="small" 
                style={{ marginBottom: '12px', border: item.isCurrent === '1' ? '1px solid #1890ff' : undefined }}
                title={
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>{item.version}</span>
                    {item.isCurrent === '1' && <Tag color="blue">当前版本</Tag>}
                  </div>
                }
                extra={
                  <Button 
                    type="link" 
                    size="small" 
                    onClick={() => handleRestoreVersion(item.id)}
                    disabled={item.isCurrent === '1'}
                  >
                    恢复此版本
                  </Button>
                }
              >
                <div style={{ fontSize: '12px', color: '#666' }}>
                  <p><strong>版本名称:</strong> {item.versionName || '-'}</p>
                  <p><strong>变更说明:</strong> {item.changeSummary || '-'}</p>
                  <p><strong>保存时间:</strong> {item.createTime}</p>
                </div>
              </Card>
            ))}
          </div>
        )}
      </Drawer>
    </div>
  );
};

const ArchitectureEdit = () => {
  return (
    <ReactFlowProvider>
      <ArchitectureFlow />
    </ReactFlowProvider>
  );
};

export default ArchitectureEdit;
