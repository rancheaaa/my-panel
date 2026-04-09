import React, { useState, useCallback, useMemo, useEffect } from 'react';
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
  Upload
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
  SyncOutlined
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
}) => {
  const { setEdges } = useReactFlow();
  
  // 计算基础路径
  const getPath = () => {
    // 如果有自定义点，构造折线路径
    if (data?.points && data.points.length > 0) {
      let path = `M ${sourceX},${sourceY}`;
      data.points.forEach(point => {
        path += ` L ${point.x},${point.y}`;
      });
      path += ` L ${targetX},${targetY}`;
      return path;
    }
    
    // 默认使用贝塞尔曲线
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

  const [isHovered, setIsHovered] = useState(false);
  const edgePath = getPath();

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
      
      {/* 只有选中或悬浮时才显示控制点 */}
      {(selected || isHovered) && (
        <EdgeLabelRenderer>
          <div 
            style={{ position: 'relative', zIndex: selected ? 1000 : 900 }}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
          >
            {/* 控制点 */}
            {data?.points?.map((point, index) => (
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
          {data.type.toUpperCase()}
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
  const [hoveredEdgeId, setHoveredEdgeId] = useState(null);
  const [architectureName, setArchitectureName] = useState('默认架构');
  const [isConnecting, setIsConnecting] = useState(false);
  const connectSuccessful = React.useRef(false);
  const [customNodeTypes, setCustomNodeTypes] = useState([]);
  const [isCustomNodeModalOpen, setIsCustomNodeModalOpen] = useState(false);
  const [customNodeForm] = Form.useForm();
  
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  
  const [defaultNodes, setDefaultNodes] = useState([]);
  const [defaultEdges, setDefaultEdges] = useState([]);

  // 初始化默认架构
  const initializeDefaultArchitecture = useCallback(() => {
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
        data: { points: [] },
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
        data: { points: [] },
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
        data: { points: [] },
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
        data: { points: [] },
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
        data: { points: [] },
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
        data: { points: [] },
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
        data: { points: [] },
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
        data: { points: [] },
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
  }, [setNodes, setEdges]);

  useEffect(() => {
    initializeDefaultArchitecture();
  }, [initializeDefaultArchitecture]);

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
    setHoveredEdgeId(edge.id);
    // 鼠标移入时，将该线段移到数组末尾，使其在 SVG 中渲染在最上层
    setEdges((eds) => {
      const otherEdges = eds.filter((e) => e.id !== edge.id);
      return [...otherEdges, edge];
    });
  }, [setEdges]);

  const onEdgeMouseLeave = useCallback(() => {
    setHoveredEdgeId(null);
  }, []);

  const onEdgeDoubleClick = useCallback((event, edge) => {
    setSelectedEdge(edge);
    edgeForm.setFieldsValue({
      label: edge.label || '',
      id: edge.id,
      sourceHandle: edge.sourceHandle || 'bottom',
      targetHandle: edge.targetHandle || 'top',
      edgeType: edge.edgeType || edge.type || 'smoothstep',
      arrowType: edge.markerEnd?.type || edge.markerStart?.type || 'arrowclosed',
      arrowDirection: edge.markerStart && edge.markerEnd ? 'both' : 
                     edge.markerEnd ? 'target' : 
                     edge.markerStart ? 'source' : 'none'
    });
    setIsEdgeModalOpen(true);
  }, [edgeForm]);

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
      data: { points: [] },
      style: {
        stroke: defaultStroke,
        strokeWidth: defaultStrokeWidth
      },
      animated: true,
      markerEnd: {
        type: 'arrowclosed',
        color: defaultStroke
      }
    };
    
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
            type: defaultEdgeType,
            edgeType: defaultEdgeType,
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
            type: defaultEdgeType,
            edgeType: defaultEdgeType,
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
    
    message.success('连接重连成功');
  }, [nodes, edges]);

  const onReconnectStart = useCallback(() => {
    connectSuccessful.current = false;
  }, []);

  const onReconnectEnd = useCallback((_, edge) => {
    if (!connectSuccessful.current) {
      // 如果重连没有成功且拖拽结束在空白处，可以考虑删除原连线或保持原样
      // 这里我们选择保持原样，不做任何处理
    }
  }, []);

  const onConnectStart = useCallback((event, { nodeId, handleId, handleType }) => {
    setIsConnecting(true);
    connectSuccessful.current = false;
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

  const onConnectEnd = useCallback((event) => {
    // 如果没有通过 onConnect 建立连接，则视为取消
    if (!connectSuccessful.current) {
      setIsConnecting(false);
      message.info('连接已取消');
      
      // 强制刷新 edges 状态，有时能清除 ReactFlow 残留的临时连线
      setEdges((eds) => [...eds]);
    }
  }, [setEdges]);

  const onPaneClick = useCallback((event) => {
    // 如果正在连接，点击空白区域时取消连接
    if (isConnecting) {
      setIsConnecting(false);
      message.info('连接已取消');
      
      // 强制刷新 edges 状态
      setEdges((eds) => [...eds]);
    }
    
    // 点击空白区域时取消选择
    setSelectedNode(null);
    setSelectedEdge(null);
  }, [isConnecting, setEdges]);

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
        onOk: () => {
          setNodes((currentNodes) => currentNodes.filter((node) => node.id !== selectedNode.id));
          setEdges((currentEdges) => currentEdges.filter((edge) => 
            edge.source !== selectedNode.id && edge.target !== selectedNode.id
          ));
          setIsDrawerOpen(false);
          setSelectedNode(null);
          message.success('删除成功');
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
        data: { points: [] },
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
    modalForm.validateFields().then((values) => {
      const nodeId = currentNode?.id || values.id;
      const configStr = typeof values.configContent === 'string' ? values.configContent : '';
      const { configContent, ...rest } = values;
      
      if (currentNode) {
        setNodes((currentNodes) =>
          currentNodes.map((node) =>
            node.id === currentNode.id
              ? { ...node, data: { ...node.data, ...rest, config: configStr } }
              : node
          )
        );
        message.success('修改成功');
      } else {
        const newNode = {
          id: nodeId,
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
    });
  };

  const handleSaveArchitecture = () => {
    const ARCHITECTURE_DATA = {
      name: architectureName,
      nodes: nodes,
      edges: edges,
      createdAt: new Date().toISOString()
    };
    
    message.success('架构保存成功');
  };

  const handleReset = () => {
    initializeDefaultArchitecture();
    message.success('已重置为默认架构');
  };

  const handleRefresh = () => {
    setNodes([...nodes]);
    setEdges([...edges]);
    message.success('已刷新架构');
  };

  const handleAddCustomNodeType = () => {
    setIsCustomNodeModalOpen(true);
    customNodeForm.resetFields();
  };

  const handleCustomNodeModalOk = () => {
    customNodeForm.validateFields().then((values) => {
      const newNodeType = {
        type: values.type,
        name: values.name,
        description: values.description,
        icon: values.icon || 'ApiOutlined',
        color: values.color || '#1890ff'
      };
      
      const existingType = customNodeTypes.find(t => t.type === values.type);
      if (existingType) {
        message.warning('该节点类型已存在');
        return;
      }
      
      setCustomNodeTypes([...customNodeTypes, newNodeType]);
      message.success('自定义节点类型添加成功');
      setIsCustomNodeModalOpen(false);
    });
  };

  const handleCustomNodeModalCancel = () => {
    setIsCustomNodeModalOpen(false);
    customNodeForm.resetFields();
  };

  const handleDrawerClose = () => {
    setIsDrawerOpen(false);
    setSelectedNode(null);
    drawerForm.resetFields();
  };

  const handleEdgeModalOk = () => {
    edgeForm.validateFields().then((values) => {
      if (selectedEdge) {
        const markerStart = values.arrowDirection === 'source' || values.arrowDirection === 'both' 
          ? { type: values.arrowType, color: selectedEdge.style?.stroke || '#1890ff' }
          : undefined;
        
        const markerEnd = values.arrowDirection === 'target' || values.arrowDirection === 'both'
          ? { type: values.arrowType, color: selectedEdge.style?.stroke || '#1890ff' }
          : undefined;

        setEdges((currentEdges) =>
          currentEdges.map((edge) =>
            edge.id === selectedEdge.id
              ? { 
                  ...edge, 
                  label: values.label,
                  sourceHandle: values.sourceHandle,
                  targetHandle: values.targetHandle,
                  edgeType: values.edgeType,
                  type: values.edgeType,
                  markerStart,
                  markerEnd
                }
              : edge
          )
        );
        setDefaultEdges((defaultEdgesList) =>
          defaultEdgesList.map((edge) =>
            edge.id === selectedEdge.id
              ? { 
                  ...edge, 
                  label: values.label,
                  sourceHandle: values.sourceHandle,
                  targetHandle: values.targetHandle,
                  edgeType: values.edgeType,
                  type: values.edgeType,
                  markerStart,
                  markerEnd
                }
              : edge
          )
        );
        message.success('边缘修改成功');
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
        onOk: () => {
          setEdges((currentEdges) => currentEdges.filter((edge) => edge.id !== selectedEdge.id));
          setDefaultEdges((defaultEdgesList) => defaultEdgesList.filter((edge) => edge.id !== selectedEdge.id));
          setIsEdgeModalOpen(false);
          edgeForm.resetFields();
          setSelectedEdge(null);
          message.success('边缘删除成功');
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
        </div>
        
        <Space size="middle">
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
          >
            保存架构
          </Button>
          <Button 
            icon={<ReloadOutlined />} 
            onClick={handleReset}
            size="large"
            className="toolbar-button"
          >
            重置
          </Button>
          <Button 
            icon={<SyncOutlined />} 
            onClick={handleRefresh}
            size="large"
            className="toolbar-button"
          >
            刷新
          </Button>
          <Button 
            icon={<SettingOutlined />} 
            onClick={handleAddCustomNodeType}
            size="large"
            className="toolbar-button"
          >
            新增自定义节点类型
          </Button>
        </Space>
      </div>

      <div className="flow-canvas-container">
        <ReactFlow
          key={`${defaultNodes.length}-${defaultEdges.length}`}
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={onNodeClick}
          onEdgeClick={onEdgeClick}
          onEdgeMouseEnter={onEdgeMouseEnter}
          onEdgeMouseLeave={onEdgeMouseLeave}
          onNodeDoubleClick={onNodeDoubleClick}
          onEdgeDoubleClick={onEdgeDoubleClick}
          onPaneClick={onPaneClick}
          onConnect={onConnect}
          onConnectStart={onConnectStart}
          onConnectEnd={onConnectEnd}
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
            label="节点ID"
            rules={[{ required: true, message: '请输入节点ID' }]}
          >
            <Input 
              size="large" 
              placeholder="请输入节点ID" 
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
                  {selectedNode.data.type.toUpperCase()}
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
              <Form.Item name="id" label="节点ID">
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
                      {node.data.type.toUpperCase()}
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
        title="新增自定义节点类型"
        open={isCustomNodeModalOpen}
        onOk={handleCustomNodeModalOk}
        onCancel={handleCustomNodeModalCancel}
        width={600}
        okText="确定"
        cancelText="取消"
      >
        <Form form={customNodeForm} layout="vertical">
          <Form.Item
            label="节点类型标识"
            name="type"
            rules={[
              { required: true, message: '请输入节点类型标识' },
              { pattern: /^[a-zA-Z0-9_-]+$/, message: '只能包含字母、数字、下划线和连字符' }
            ]}
            tooltip="节点的唯一标识符，用于区分不同类型的节点"
          >
            <Input placeholder="例如：custom-service" />
          </Form.Item>

          <Form.Item
            label="节点类型名称"
            name="name"
            rules={[{ required: true, message: '请输入节点类型名称' }]}
            tooltip="节点类型的显示名称"
          >
            <Input placeholder="例如：自定义服务" />
          </Form.Item>

          <Form.Item
            label="节点类型描述"
            name="description"
            tooltip="节点类型的详细描述"
          >
            <Input.TextArea placeholder="请输入节点类型描述" rows={3} />
          </Form.Item>

          <Form.Item
            label="节点图标"
            name="icon"
            initialValue="ApiOutlined"
            tooltip="选择节点显示的图标"
          >
            <Select>
              <Select.Option value="ApiOutlined">API图标</Select.Option>
              <Select.Option value="ClusterOutlined">集群图标</Select.Option>
              <Select.Option value="DatabaseOutlined">数据库图标</Select.Option>
              <Select.Option value="SettingOutlined">设置图标</Select.Option>
              <Select.Option value="DeleteOutlined">删除图标</Select.Option>
              <Select.Option value="SyncOutlined">同步图标</Select.Option>
              <Select.Option value="PlusOutlined">加号图标</Select.Option>
              <Select.Option value="SaveOutlined">保存图标</Select.Option>
              <Select.Option value="ReloadOutlined">刷新图标</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            label="节点颜色"
            name="color"
            initialValue="#1890ff"
            tooltip="选择节点的主题颜色"
          >
            <Select>
              <Select.Option value="#1890ff">蓝色</Select.Option>
              <Select.Option value="#52c41a">绿色</Select.Option>
              <Select.Option value="#fa8c16">橙色</Select.Option>
              <Select.Option value="#722ed1">紫色</Select.Option>
              <Select.Option value="#eb2f96">粉色</Select.Option>
              <Select.Option value="#13c2c2">青色</Select.Option>
              <Select.Option value="#f5222d">红色</Select.Option>
              <Select.Option value="#faad14">黄色</Select.Option>
            </Select>
          </Form.Item>

          <div style={{
            padding: '12px',
            backgroundColor: '#f5f5f5',
            borderRadius: '4px',
            fontSize: '13px',
            color: '#595959'
          }}>
            <div style={{ marginBottom: '8px', fontWeight: '500' }}>
              <span style={{ color: '#1890ff' }}>自定义节点说明：</span>
            </div>
            <div style={{ marginBottom: '4px' }}>• 节点类型标识：唯一标识符，用于区分不同类型</div>
            <div style={{ marginBottom: '4px' }}>• 节点类型名称：显示在界面上的名称</div>
            <div style={{ marginBottom: '4px' }}>• 节点类型描述：详细描述节点类型的用途</div>
            <div style={{ marginBottom: '4px' }}>• 节点图标：选择节点显示的图标样式</div>
            <div>• 节点颜色：选择节点的主题颜色</div>
          </div>
        </Form>
      </Modal>
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
