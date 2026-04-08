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
  InputNumber
} from 'antd';
import { 
  PlusOutlined, 
  SaveOutlined, 
  ReloadOutlined, 
  SettingOutlined,
  DeleteOutlined,
  ClusterOutlined,
  ApiOutlined,
  DatabaseOutlined
} from '@ant-design/icons';
import { 
  FlowView, 
  useNodesState, 
  useEdgesState,
  MiniMap,
  Background,
  FlowPanel,
  Handle,
  Position
} from '@ant-design/pro-flow';
import { Handle as ReactFlowHandle, Position as ReactFlowPosition } from 'reactflow';
import 'reactflow/dist/style.css';

const { Option } = Select;

const styles = `
  .custom-handle {
    z-index: 1000 !important;
  }
  
  .custom-handle:hover {
    transform: scale(1.3) !important;
    box-shadow: 0 0 12px rgba(24, 144, 255, 0.6) !important;
  }
  
  .react-flow__handle {
    transition: all 0.3s ease !important;
    z-index: 1000 !important;
  }
  
  .react-flow__handle-connecting {
    background: #1890ff !important;
    transform: scale(1.2) !important;
    box-shadow: 0 0 16px rgba(24, 144, 255, 0.8) !important;
  }
  
  .react-flow__handle-valid {
    background: #52c41a !important;
  }
  
  .react-flow__edge-path {
    stroke-width: 2 !important;
  }
  
  .react-flow__edge.selected .react-flow__edge-path {
    stroke: #1890ff !important;
    stroke-width: 3 !important;
  }
  
  .react-flow__node {
    pointer-events: all !important;
  }
`;

// 自定义节点组件
const CustomNode = ({ data }) => {
  const getNodeIcon = (type) => {
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

  const colors = getNodeColor(data.type);

  return (
    <div style={{
      padding: '20px',
      borderRadius: '12px',
      border: `3px solid ${colors.border}`,
      backgroundColor: colors.bg,
      minWidth: '200px',
      maxWidth: '280px',
      boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
      transition: 'all 0.3s ease',
      cursor: 'default',
      position: 'relative'
    }}
    onMouseEnter={(e) => {
      e.currentTarget.style.transform = 'translateY(-4px)';
      e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.18)';
    }}
    onMouseLeave={(e) => {
      e.currentTarget.style.transform = 'translateY(0)';
      e.currentTarget.style.boxShadow = '0 4px 16px rgba(0,0,0,0.12)';
    }}
    >
      {/* 顶部输入连接桩 */}
      <ReactFlowHandle
        type="target"
        position={ReactFlowPosition.Top}
        id="top"
        isConnectable={true}
        style={{
          background: '#1890ff',
          width: '16px',
          height: '16px',
          border: '3px solid #fff',
          boxShadow: '0 2px 8px rgba(24, 144, 255, 0.4)',
          transition: 'all 0.3s ease',
          cursor: 'crosshair'
        }}
        className="custom-handle"
      />
      
      {/* 底部输出连接桩 */}
      <ReactFlowHandle
        type="source"
        position={ReactFlowPosition.Bottom}
        id="bottom"
        isConnectable={true}
        style={{
          background: '#52c41a',
          width: '16px',
          height: '16px',
          border: '3px solid #fff',
          boxShadow: '0 2px 8px rgba(82, 196, 26, 0.4)',
          transition: 'all 0.3s ease',
          cursor: 'crosshair'
        }}
        className="custom-handle"
      />
      
      {/* 左侧输入连接桩 */}
      <ReactFlowHandle
        type="target"
        position={ReactFlowPosition.Left}
        id="left"
        isConnectable={true}
        style={{
          background: '#1890ff',
          width: '16px',
          height: '16px',
          border: '3px solid #fff',
          boxShadow: '0 2px 8px rgba(24, 144, 255, 0.4)',
          transition: 'all 0.3s ease',
          cursor: 'crosshair'
        }}
        className="custom-handle"
      />
      
      {/* 右侧输出连接桩 */}
      <ReactFlowHandle
        type="source"
        position={ReactFlowPosition.Right}
        id="right"
        isConnectable={true}
        style={{
          background: '#52c41a',
          width: '16px',
          height: '16px',
          border: '3px solid #fff',
          boxShadow: '0 2px 8px rgba(82, 196, 26, 0.4)',
          transition: 'all 0.3s ease',
          cursor: 'crosshair'
        }}
        className="custom-handle"
      />
      
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
        {getNodeIcon(data.type)}
        <span style={{ 
          marginLeft: '12px', 
          fontWeight: 'bold', 
          fontSize: '16px',
          color: '#262626'
        }}>
          {data.name}
        </span>
      </div>
      
      <div style={{ 
        fontSize: '13px', 
        color: '#8c8c8c', 
        marginBottom: '10px',
        lineHeight: '1.5'
      }}>
        {data.description}
      </div>
      
      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '8px' }}>
        <Tag color="blue" style={{ fontSize: '12px', margin: '0', fontWeight: '500' }}>
          {data.type.toUpperCase()}
        </Tag>
        <Tag 
          color={data.status === 'running' ? 'green' : 'red'} 
          style={{ fontSize: '12px', margin: '0', fontWeight: '500' }}
        >
          {data.status === 'running' ? '运行中' : '已停止'}
        </Tag>
        {data.port && (
          <Tag color="purple" style={{ fontSize: '12px', margin: '0', fontWeight: '500' }}>
            端口: {data.port}
          </Tag>
        )}
        {data.ip && (
          <Tag color="cyan" style={{ fontSize: '12px', margin: '0', fontWeight: '500' }}>
            IP: {data.ip}
          </Tag>
        )}
      </div>

      {data.config && Object.keys(data.config).length > 0 && (
        <div style={{
          marginTop: '8px',
          padding: '8px 12px',
          backgroundColor: 'rgba(0,0,0,0.04)',
          borderRadius: '6px',
          fontSize: '11px',
          color: '#595959'
        }}>
          <div style={{ fontWeight: '500', marginBottom: '4px' }}>配置信息:</div>
          {Object.entries(data.config).map(([key, value]) => (
            <div key={key} style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: '#8c8c8c' }}>{key}:</span>
              <span style={{ fontWeight: '500' }}>{String(value)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

const ArchitectureEdit = () => {
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
  const [architectureName, setArchitectureName] = useState('默认架构');
  
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  
  const [defaultNodes, setDefaultNodes] = useState([]);
  const [defaultEdges, setDefaultEdges] = useState([]);

  // 初始化默认架构
  const initializeDefaultArchitecture = useCallback(() => {
    const generateNodeLabel = () => {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
      });
    };

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
          config: {
            worker_processes: 4,
            worker_connections: 1024,
            keepalive_timeout: 65
          }
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
          config: {
            java_version: '17',
            max_memory: '2G',
            thread_pool_size: 200
          }
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
          config: {
            java_version: '17',
            max_memory: '2G',
            thread_pool_size: 200
          }
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
          config: {
            registry_enabled: true,
            health_check_interval: 30,
            max_connections: 1000
          }
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
          config: {
            registry_enabled: true,
            health_check_interval: 30,
            max_connections: 1000
          }
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
          config: {
            registry_enabled: true,
            health_check_interval: 30,
            max_connections: 1000
          }
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
          config: {
            registry_enabled: true,
            health_check_interval: 30,
            max_connections: 1000
          }
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
          config: {
            registry_enabled: true,
            health_check_interval: 30,
            max_connections: 1000
          }
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
          config: {
            registry_enabled: true,
            health_check_interval: 30,
            max_connections: 1000
          }
        },
      },
    ];

    const initialEdges = [
      {
        id: 'e1',
        source: 'nginx-1',
        target: 'mypanel-1',
        label: 'HTTP请求',
        type: 'smoothstep',
        edgeType: 'smoothstep',
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
        target: 'mypanel-2',
        label: 'HTTP请求',
        type: 'smoothstep',
        edgeType: 'smoothstep',
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
        target: 'proxy-1',
        label: '服务调用',
        type: 'default',
        edgeType: 'default',
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e4',
        source: 'mypanel-1',
        target: 'proxy-2',
        label: '服务调用',
        type: 'default',
        edgeType: 'default',
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e5',
        source: 'mypanel-1',
        target: 'proxy-3',
        label: '服务调用',
        type: 'default',
        edgeType: 'default',
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e6',
        source: 'mypanel-2',
        target: 'proxy-4',
        label: '服务调用',
        type: 'default',
        edgeType: 'default',
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e7',
        source: 'mypanel-2',
        target: 'proxy-5',
        label: '服务调用',
        type: 'default',
        edgeType: 'default',
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
      {
        id: 'e8',
        source: 'mypanel-2',
        target: 'proxy-6',
        label: '服务调用',
        type: 'default',
        edgeType: 'default',
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

  const onNodeClick = useCallback((event, node) => {
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
    edgeForm.setFieldsValue({
      label: edge.label || '',
      id: edge.id,
      edgeType: edge.edgeType || edge.type || 'smoothstep',
      arrowType: edge.markerEnd?.type || edge.markerStart?.type || 'arrowclosed',
      arrowDirection: edge.markerStart && edge.markerEnd ? 'both' : 
                     edge.markerEnd ? 'target' : 
                     edge.markerStart ? 'source' : 'none'
    });
    setIsEdgeModalOpen(true);
  }, [edgeForm]);

  const onConnect = useCallback((connection) => {
    const sourceNode = nodes.find(node => node.id === connection.source);
    const targetNode = nodes.find(node => node.id === connection.target);
    
    if (!sourceNode || !targetNode) {
      message.warning('找不到源节点或目标节点');
      return;
    }
    
    const existingEdge = edges.find(edge => 
      edge.source === connection.source && edge.target === connection.target
    );
    
    if (existingEdge) {
      message.warning('该连接已存在');
      return;
    }
    
    if (connection.source === connection.target) {
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
    
    const newEdge = {
      id: `edge-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      source: connection.source,
      target: connection.target,
      label: defaultLabel,
      type: defaultEdgeType,
      edgeType: defaultEdgeType,
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
    
    setEdges((currentEdges) => [...currentEdges, newEdge]);
    setDefaultEdges((defaultEdgesList) => [...defaultEdgesList, newEdge]);
    message.success('连接创建成功');
  }, [nodes, edges]);

  const onConnectStart = useCallback((event, { nodeId, handleId, handleType }) => {
    const node = nodes.find(n => n.id === nodeId);
    if (node) {
      message.info(`开始从 "${node.data.name}" 创建连接`);
    }
  }, [nodes]);

  const onConnectEnd = useCallback((event, { nodeId, handleId }) => {
    if (!nodeId) {
      message.warning('连接已取消');
    }
  }, []);

  const handleAddNode = () => {
    modalForm.resetFields();
    modalForm.setFieldsValue({
      type: 'proxy',
      name: '',
      description: '',
      ip: '',
      port: 80,
      status: 'running'
    });
    setCurrentNode(null);
    setIsModalOpen(true);
  };

  const handleEditNode = () => {
    if (selectedNode) {
      modalForm.setFieldsValue({
        type: selectedNode.data.type,
        id: selectedNode.id,
        name: selectedNode.data.name,
        description: selectedNode.data.description,
        ip: selectedNode.data.ip,
        port: selectedNode.data.port,
        status: selectedNode.data.status,
        config: selectedNode.data.config
      });
      setCurrentNode(selectedNode);
      setIsModalOpen(true);
      setIsDrawerOpen(false);
    }
  };

  const handleDeleteNode = () => {
    if (selectedNode) {
      setNodes((currentNodes) => currentNodes.filter((node) => node.id !== selectedNode.id));
      setEdges((currentEdges) => currentEdges.filter((edge) => 
        edge.source !== selectedNode.id && edge.target !== selectedNode.id
      ));
      setIsDrawerOpen(false);
      setSelectedNode(null);
      message.success('删除成功');
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
        message.warning('该连接已存在');
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
      
      const newEdge = {
        id: `edge-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        source: values.source,
        target: values.target,
        label: values.label || defaultLabel,
        type: values.edgeType || defaultEdgeType,
        edgeType: values.edgeType || defaultEdgeType,
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
      
      if (currentNode) {
        setNodes((currentNodes) =>
          currentNodes.map((node) =>
            node.id === currentNode.id
              ? { ...node, data: { ...node.data, ...values } }
              : node
          )
        );
        message.success('修改成功');
      } else {
        const newNode = {
          id: nodeId,
          type: values.type,
          position: { x: 400, y: 400 },
          connectable: true,
          data: {
            name: values.name,
            type: values.type,
            description: values.description,
            status: values.status,
            ip: values.ip,
            port: values.port,
            config: values.config || {}
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
    const architectureData = {
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

  const nodeTypes = useMemo(() => ({
    nginx: CustomNode,
    'my-panel': CustomNode,
    proxy: CustomNode
  }), []);

  return (
    <>
      <style>{styles}</style>
      <div style={{ height: '100vh', display: 'flex', flexDirection: 'column', backgroundColor: '#f5f5f5' }}>
      <div style={{ 
        padding: '16px 24px', 
        backgroundColor: '#fff', 
        borderBottom: '2px solid #e8e8e8',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        boxShadow: '0 2px 8px rgba(0,0,0,0.06)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <h2 style={{ 
            margin: 0, 
            fontSize: '22px', 
            fontWeight: 'bold',
            color: '#262626',
            display: 'flex',
            alignItems: 'center',
            gap: '12px'
          }}>
            <ClusterOutlined style={{ color: '#1890ff' }} />
            架构编排
          </h2>
          <Input
            placeholder="请输入架构名称"
            value={architectureName}
            onChange={(e) => setArchitectureName(e.target.value)}
            style={{ 
              width: 320,
              borderRadius: '6px'
            }}
            size="large"
          />
        </div>
        
        <Space size="middle">
          <Button 
            type="primary" 
            icon={<PlusOutlined />} 
            onClick={handleAddNode}
            size="large"
            style={{ 
              borderRadius: '6px',
              fontWeight: '500',
              boxShadow: '0 2px 4px rgba(24,144,255,0.2)'
            }}
          >
            新增节点
          </Button>
          <Button 
            icon={<SaveOutlined />} 
            onClick={handleSaveArchitecture}
            size="large"
            style={{ 
              borderRadius: '6px',
              fontWeight: '500'
            }}
          >
            保存架构
          </Button>
          <Button 
            icon={<ReloadOutlined />} 
            onClick={handleReset}
            size="large"
            style={{ 
              borderRadius: '6px',
              fontWeight: '500'
            }}
          >
            重置
          </Button>
        </Space>
      </div>

      <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        <FlowView
          key={`${defaultNodes.length}-${defaultEdges.length}`}
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={onNodeClick}
          onEdgeClick={onEdgeClick}
          onConnect={onConnect}
          onConnectStart={onConnectStart}
          onConnectEnd={onConnectEnd}
          nodeTypes={nodeTypes}
          miniMap
          background
          flowProps={{
            fitView: true,
            nodesDraggable: true,
            nodesConnectable: true,
            elementsSelectable: true,
            panOnDrag: true,
            zoomOnScroll: true,
            zoomOnPinch: true,
            connectOnClick: true,
            connectionMode: 'loose',
            connectionLineType: 'smoothstep',
            defaultEdgeOptions: {
              animated: true,
              style: {
                strokeWidth: 2,
              },
            },
          }}
          style={{ 
            backgroundColor: '#fafafa',
            backgroundImage: `
              radial-gradient(circle at 1px 1px, #ddd 1px, transparent 0);
              background-size: 20px 20px
            `
          }}
        >
          <FlowPanel />
        </FlowView>
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
              
              {selectedNode.data.config && Object.keys(selectedNode.data.config).length > 0 && (
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
                    border: '1px solid #e8e8e8'
                  }}>
                    {Object.entries(selectedNode.data.config).map(([key, value]) => (
                      <div key={key} style={{ 
                        display: 'flex', 
                        justifyContent: 'space-between',
                        marginBottom: '8px',
                        paddingBottom: '8px',
                        borderBottom: '1px solid #e8e8e8'
                      }}>
                        <span style={{ 
                          color: '#8c8c8c',
                          fontWeight: '500',
                          flex: 1
                        }}>
                          {key}:
                        </span>
                        <span style={{ 
                          fontWeight: 'bold',
                          color: '#262626',
                          flex: 2,
                          textAlign: 'right'
                        }}>
                          {String(value)}
                        </span>
                      </div>
                    ))}
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
      </div>
    </>
  );
};

export default ArchitectureEdit;