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
  DeleteOutlined, 
  SaveOutlined, 
  ReloadOutlined,
  SettingOutlined,
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
import 'reactflow/dist/style.css';

const { Option } = Select;

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
      cursor: 'pointer',
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
      <Handle
        type="target"
        position={Position.Top}
        id="top"
        style={{
          background: '#1890ff',
          width: '12px',
          height: '12px',
          border: '2px solid #fff',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
        }}
      />
      
      {/* 底部输出连接桩 */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="bottom"
        style={{
          background: '#52c41a',
          width: '12px',
          height: '12px',
          border: '2px solid #fff',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
        }}
      />
      
      {/* 左侧输入连接桩 */}
      <Handle
        type="target"
        position={Position.Left}
        id="left"
        style={{
          background: '#1890ff',
          width: '12px',
          height: '12px',
          border: '2px solid #fff',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
        }}
      />
      
      {/* 右侧输出连接桩 */}
      <Handle
        type="source"
        position={Position.Right}
        id="right"
        style={{
          background: '#52c41a',
          width: '12px',
          height: '12px',
          border: '2px solid #fff',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
        }}
      />
      
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
        {getNodeIcon(data.type)}
        <span style={{ 
          marginLeft: '12px', 
          fontWeight: 'bold', 
          fontSize: '16px',
          color: '#262626'
        }}>
          {data.label}
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
  const [modalForm] = Form.useForm();
  const [drawerForm] = Form.useForm();
  const [edgeForm] = Form.useForm();
  const [currentNode, setCurrentNode] = useState(null);
  const [selectedNode, setSelectedNode] = useState(null);
  const [selectedEdge, setSelectedEdge] = useState(null);
  const [architectureName, setArchitectureName] = useState('默认架构');
  
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  // 初始化默认架构
  const initializeDefaultArchitecture = useCallback(() => {
    const defaultNodes = [
      {
        id: 'nginx-1',
        type: 'nginx',
        position: { x: 975, y: 50 },
        data: {
          label: 'Nginx',
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
        data: {
          label: 'My-Panel-1',
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
        data: {
          label: 'My-Panel-2',
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
        data: {
          label: 'Proxy-1',
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
        data: {
          label: 'Proxy-2',
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
        data: {
          label: 'Proxy-3',
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
        data: {
          label: 'Proxy-4',
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
        data: {
          label: 'Proxy-5',
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
        data: {
          label: 'Proxy-6',
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

    const defaultEdges = [
      {
        id: 'e1',
        source: 'nginx-1',
        target: 'mypanel-1',
        label: 'HTTP请求',
        type: 'smoothstep',
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
        style: { 
          stroke: '#52c41a', 
          strokeWidth: 2
        },
        animated: true,
      },
    ];

    setNodes(defaultNodes);
    setEdges(defaultEdges);
  }, [setNodes, setEdges]);

  useEffect(() => {
    initializeDefaultArchitecture();
  }, [initializeDefaultArchitecture]);

  const onNodeClick = useCallback((event, node) => {
    setSelectedNode(node);
    setIsDrawerOpen(true);
    drawerForm.setFieldsValue(node.data);
  }, [drawerForm]);

  const onEdgeClick = useCallback((event, edge) => {
    setSelectedEdge(edge);
    edgeForm.setFieldsValue({
      label: edge.label || '',
      id: edge.id
    });
    setIsEdgeModalOpen(true);
  }, [edgeForm]);

  const handleAddNode = () => {
    modalForm.resetFields();
    setCurrentNode(null);
    setIsModalOpen(true);
  };

  const handleEditNode = () => {
    if (selectedNode) {
      modalForm.setFieldsValue(selectedNode.data);
      setCurrentNode(selectedNode);
      setIsModalOpen(true);
      setIsDrawerOpen(false);
    }
  };

  const handleDeleteNode = () => {
    if (selectedNode) {
      setNodes((nds) => nds.filter((node) => node.id !== selectedNode.id));
      setEdges((eds) => eds.filter((edge) => 
        edge.source !== selectedNode.id && edge.target !== selectedNode.id
      ));
      setIsDrawerOpen(false);
      setSelectedNode(null);
      message.success('删除成功');
    }
  };

  const handleModalOk = () => {
    modalForm.validateFields().then((values) => {
      const nodeId = currentNode?.id || `${values.type}-${Date.now()}`;
      
      if (currentNode) {
        setNodes((nds) =>
          nds.map((node) =>
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
          data: {
            label: values.label,
            type: values.type,
            description: values.description,
            status: values.status,
            port: values.port,
            config: values.config || {}
          },
        };
        setNodes((nds) => [...nds, newNode]);
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
    
    console.log('保存架构数据:', architectureData);
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
        setEdges((eds) =>
          eds.map((edge) =>
            edge.id === selectedEdge.id
              ? { ...edge, label: values.label }
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

  const nodeTypes = useMemo(() => ({
    nginx: CustomNode,
    'my-panel': CustomNode,
    proxy: CustomNode
  }), []);

  return (
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
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={onNodeClick}
          onEdgeClick={onEdgeClick}
          nodeTypes={nodeTypes}
          miniMap
          background
          flowProps={{
            fitView: true,
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
            name="label"
            label="节点名称"
            rules={[{ required: true, message: '请输入节点名称' }]}
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
        width={480}
        open={isDrawerOpen}
        onClose={handleDrawerClose}
        extra={
          <Space>
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
                {selectedNode.data.label}
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
              </Space>
            </div>
            
            <Divider />
            
            <Form form={drawerForm} layout="vertical" disabled>
              <Form.Item name="description" label="描述">
                <Input.TextArea rows={3} />
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
        onOk={handleEdgeModalOk}
        onCancel={handleEdgeModalCancel}
        width={500}
        okText="确定"
        cancelText="取消"
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
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>目标节点：</span>
                <span style={{ fontWeight: 'bold', color: '#262626' }}>{selectedEdge.target}</span>
              </div>
            </div>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default ArchitectureEdit;