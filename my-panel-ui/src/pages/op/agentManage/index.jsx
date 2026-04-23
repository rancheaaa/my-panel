import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Card, Button, Space, Form, Input, Modal, message, Popconfirm, Tooltip, Select, Tag, InputNumber, Dropdown, Row, Col } from 'antd';
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined, ExportOutlined, PoweroffOutlined, ColumnHeightOutlined, DownOutlined, UpOutlined, PlayCircleOutlined, ConsoleSqlOutlined, HistoryOutlined } from '@ant-design/icons';
import { ResizableTitle } from '../../../components/ResizableTable';
import { 
  listAgentRegistry, 
  getAgentRegistry, 
  addAgentRegistry, 
  updateAgentRegistry, 
  delAgentRegistry, 
  exportAgentRegistry,
  offlineTimeoutNodes,
  executeAgentCommand
} from '../../../api/agent';
import { getDicts } from '../../../api/dict/data';
import { listType } from '../../../api/dict/type';

const { Option } = Select;

const AgentManage = () => {
  const navigate = useNavigate();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [tableSize, setTableSize] = useState('large');
  const [expand, setExpand] = useState(true);
  const [osTypeOptions, setOsTypeOptions] = useState([]);
  const [nodeSwitchOptions, setNodeSwitchOptions] = useState([]);
  const [dictTypeMeta, setDictTypeMeta] = useState({
    agent_os_type: { dictType: 'agent_os_type', dictName: '' },
    agent_node_switch: { dictType: 'agent_node_switch', dictName: '' },
  });
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    nodeName: undefined,
    osType: undefined,
    appId: undefined,
    agentIp: undefined,
    agentPort: undefined,
    nodeEnabled: undefined,
    nodeStatus: undefined
  });

  const [form] = Form.useForm();
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增Agent');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);
  const [editData, setEditData] = useState(null);

  // Offline Modal State
  const [isOfflineModalOpen, setIsOfflineModalOpen] = useState(false);
  const [offlineForm] = Form.useForm();

  // Execute Command Modal State
  const [isExecuteModalOpen, setIsExecuteModalOpen] = useState(false);
  const [executeForm] = Form.useForm();
  const [currentAgent, setCurrentAgent] = useState(null);
  const [executing, setExecuting] = useState(false);
  const [commandResult, setCommandResult] = useState('');
  const [commandHistory, setCommandHistory] = useState([]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listAgentRegistry(queryParams);
      if (res.code === 200) {
        setData(res.data.rows);
        setTotal(res.data.total);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [queryParams]);

  useEffect(() => {
    getDicts('agent_os_type').then(res => {
      if (res.code === 200) {
        setOsTypeOptions(res.data);
      }
    });
    getDicts('agent_node_switch').then(res => {
      if (res.code === 200) {
        setNodeSwitchOptions(res.data);
      }
    });
    Promise.all([
      listType({ pageNum: 1, pageSize: 1, dictType: 'agent_os_type' }),
      listType({ pageNum: 1, pageSize: 1, dictType: 'agent_node_switch' }),
    ]).then(([osRes, nodeRes]) => {
      setDictTypeMeta(prev => ({
        ...prev,
        agent_os_type: {
          dictType: 'agent_os_type',
          dictName: osRes?.data?.rows?.[0]?.dictName || prev.agent_os_type.dictName,
        },
        agent_node_switch: {
          dictType: 'agent_node_switch',
          dictName: nodeRes?.data?.rows?.[0]?.dictName || prev.agent_node_switch.dictName,
        },
      }));
    });
  }, []);

  const handleSearch = () => {
    form.validateFields().then(values => {
      setQueryParams({
        ...queryParams,
        ...values,
        pageNum: 1
      });
    });
  };

  const handleReset = () => {
    form.resetFields();
    setQueryParams({
      pageNum: 1,
      pageSize: 10,
      nodeName: undefined,
      osType: undefined,
      appId: undefined,
      agentIp: undefined,
      agentPort: undefined,
      nodeEnabled: undefined,
      nodeStatus: undefined
    });
  };

  const handleAdd = () => {
    setModalTitle('新增Agent');
    setCurrentId(null);
    setIsModalOpen(true);
  };

  const handleEdit = async (record) => {
    setModalTitle('修改Agent');
    setCurrentId(record.id);
    try {
        const res = await getAgentRegistry(record.id);
        if (res.code === 200) {
            setEditData(res.data);
            setIsModalOpen(true);
        }
    } catch (error) {
        console.error(error);
        message.error('获取详情失败');
    }
  };

  const handleModalAfterOpenChange = (open) => {
    if (open) {
      if (currentId && editData) {
        // 修改模式：确保 nodeEnabled 是数字类型以匹配 Select 选项
        const formattedData = {
          ...editData,
          nodeEnabled: editData.nodeEnabled !== undefined ? Number(editData.nodeEnabled) : undefined
        };
        modalForm.setFieldsValue(formattedData);
      } else {
        // 新增模式：设置默认值
        modalForm.resetFields();
        modalForm.setFieldsValue({
          nodeEnabled: 0
        });
      }
    } else {
      // Modal关闭时，清空数据
      setEditData(null);
      setCurrentId(null);
      modalForm.resetFields();
    }
  };

  const handleDelete = async (id) => {
    const ids = id || selectedRowKeys;
    if (!ids || ids.length === 0) {
      message.warning('请选择要删除的数据');
      return;
    }
    try {
      await delAgentRegistry(ids);
      message.success('删除成功');
      setSelectedRowKeys([]);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('删除失败');
    }
  };

  const handleExport = async () => {
    try {
      const response = await exportAgentRegistry(queryParams);
      const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = `Agent注册信息_${new Date().getTime()}.xlsx`;
      link.click();
      message.success('导出成功');
    } catch (error) {
      console.error(error);
      message.error('导出失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentId) {
        await updateAgentRegistry({ ...values, id: currentId });
        message.success('更新成功');
      } else {
        await addAgentRegistry(values);
        message.success('新增成功');
      }
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error(error.message || '操作失败');
    }
  };

  const handleOffline = () => {
    offlineForm.resetFields();
    offlineForm.setFieldsValue({ timeoutSeconds: 300 });
    setIsOfflineModalOpen(true);
  };

  const handleOfflineOk = async () => {
    try {
      const values = await offlineForm.validateFields();
      const res = await offlineTimeoutNodes(values.timeoutSeconds);
      if (res.code === 200) {
        message.success(`成功下线 ${res.data} 个超时节点`);
        setIsOfflineModalOpen(false);
        fetchData();
      }
    } catch (error) {
      console.error(error);
      message.error('下线失败');
    }
  };

  // 执行命令相关函数
  const handleExecute = (record) => {
    setCurrentAgent(record);
    setCommandResult('');
    setCommandHistory([]);
    executeForm.resetFields();
    executeForm.setFieldsValue({
      timeout: 30
    });
    setIsExecuteModalOpen(true);
  };

  const handleExecuteOk = async () => {
    if (!currentAgent) return;
    
    try {
      const values = await executeForm.validateFields();
      const { command, timeout } = values;
      
      if (!command || command.trim() === '') {
        message.warning('请输入要执行的命令');
        return;
      }

      setExecuting(true);
      setCommandResult('执行中...\n');
      
      // 添加到命令历史
      const newCommand = {
        command: command,
        timestamp: new Date().toLocaleString(),
        agent: `${currentAgent.nodeName} (${currentAgent.agentIp}:${currentAgent.agentPort})`
      };
      setCommandHistory(prev => [newCommand, ...prev.slice(0, 9)]); // 保留最近10条历史

      // 调用后端接口执行命令
      const res = await executeAgentCommand(currentAgent.id, command, timeout);
      
      if (res.code === 200) {
        const { success, exitCode, output, error } = res.data;
        
        let resultText = `[${currentAgent.nodeName}] ${currentAgent.agentIp}:${currentAgent.agentPort}\n`;
        resultText += `执行命令: ${command}\n`;
        resultText += `执行结果: ${success ? '成功' : '失败'} (退出码: ${exitCode})\n`;
        resultText += '='.repeat(50) + '\n';
        
        if (output && output.trim()) {
          resultText += '标准输出:\n';
          resultText += output + '\n';
        }
        
        if (error && error.trim()) {
          resultText += '错误输出:\n';
          resultText += error + '\n';
        }
        
        if (!output && !error) {
          resultText += '命令执行完成，无输出内容\n';
        }
        
        setCommandResult(resultText);
      } else {
        setCommandResult(`执行失败: ${res.msg || '未知错误'}`);
      }
    } catch (error) {
      console.error('执行命令失败:', error);
      let errorMsg = '执行命令失败: ';
      if (error.response) {
        errorMsg += `HTTP ${error.response.status} - ${error.response.data?.msg || '服务器错误'}`;
      } else if (error.request) {
        errorMsg += '网络连接失败，请检查Agent服务是否正常运行';
      } else {
        errorMsg += error.message;
      }
      setCommandResult(errorMsg);
    } finally {
      setExecuting(false);
    }
  };

  const handleExecuteCancel = () => {
    setIsExecuteModalOpen(false);
    setCurrentAgent(null);
    setCommandResult('');
    setExecuting(false);
  };

  const clearResult = () => {
    setCommandResult('');
  };

  const [columns, setColumns] = useState([
    { title: '节点ID', dataIndex: 'id', key: 'id', align: 'center', width: 200, ellipsis: true },
    { title: '节点名称', dataIndex: 'nodeName', key: 'nodeName', align: 'center', width: 150, ellipsis: true },
    { title: '操作系统', dataIndex: 'osType', key: 'osType', align: 'center', width: 100 },
    { title: '应用ID', dataIndex: 'appId', key: 'appId', align: 'center', width: 120, ellipsis: true },
    { title: 'Agent IP', dataIndex: 'agentIp', key: 'agentIp', align: 'center', width: 150 },
    { title: 'Agent端口', dataIndex: 'agentPort', key: 'agentPort', align: 'center', width: 100 },
    { 
      title: '节点启用', 
      dataIndex: 'nodeEnabled', 
      key: 'nodeEnabled', 
      align: 'center',
      width: 100,
      render: (nodeEnabled) => {
        const colorMap = {
          0: 'green',
          1: 'orange',
          2: 'red'
        };
        const dictLabel = nodeSwitchOptions.find(d => Number(d.dictValue) === Number(nodeEnabled))?.dictLabel;
        const textMap = {
          0: '启用',
          1: '临时关闭',
          2: '永久关闭'
        };
        return (
          <Tag color={colorMap[nodeEnabled]}>
            {dictLabel ?? textMap[nodeEnabled]}
          </Tag>
        );
      }
    },
    { 
      title: '节点状态', 
      dataIndex: 'nodeStatus', 
      key: 'nodeStatus', 
      align: 'center',
      width: 100,
      render: (nodeStatus) => {
        const colorMap = {
          0: 'red',
          1: 'green',
          2: 'default'
        };
        const textMap = {
          0: '离线',
          1: '在线',
          2: '未知'
        };
        return (
          <Tag color={colorMap[nodeStatus]}>
            {textMap[nodeStatus]}
          </Tag>
        );
      }
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', align: 'center', width: 200, ellipsis: true },
    { title: '最后刷新时间', dataIndex: 'lastRefreshTime', key: 'lastRefreshTime', align: 'center', width: 180 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 180 },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: 180 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 400,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Tooltip title="执行命令">
            <Button
              type="text"
              icon={<ConsoleSqlOutlined />}
              onClick={() => handleExecute(record)}
              style={{ color: '#52c41a' }}
              disabled={record.nodeStatus !== 1}
            >
              执行
            </Button>
          </Tooltip>
          <Tooltip title="命令历史">
            <Button
              type="text"
              icon={<HistoryOutlined />}
              onClick={() => navigate('/op/command-history?agentId=' + record.id)}
              style={{ color: '#722ed1' }}
            >
              历史
            </Button>
          </Tooltip>
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
            <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]);

  const handleResize = (index) => (e, { size }) => {
    setColumns((prevColumns) => {
      const nextColumns = [...prevColumns];
      nextColumns[index] = {
        ...nextColumns[index],
        width: size.width,
      };
      return nextColumns;
    });
  };

  const resizableColumns = columns.map((col, index) => ({
    ...col,
    onHeaderCell: (column) => ({
      width: column.width,
      onResize: handleResize(index),
    }),
  }));

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }}>
          <Row gutter={[24, 16]}>
            <Col span={6}>
              <Form.Item name="nodeName" label="节点名称">
                <Input placeholder="请输入节点名称" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="osType" label="操作系统">
                <Select placeholder="请选择操作系统" allowClear>
                  {osTypeOptions.map(dict => (
                    <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="appId" label="应用ID">
                <Input placeholder="请输入应用ID" allowClear />
              </Form.Item>
            </Col>
            
            {expand && (
              <>
                <Col span={6}>
                  <Form.Item name="agentIp" label="Agent IP">
                    <Input placeholder="请输入Agent IP" allowClear />
                  </Form.Item>
                </Col>
                <Col span={6}>
                  <Form.Item name="agentPort" label="Agent端口">
                    <InputNumber placeholder="请输入Agent端口" min={1} max={65535} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={6}>
                  <Form.Item name="nodeEnabled" label="节点启用">
                    <Select placeholder="请选择状态" allowClear>
                      {nodeSwitchOptions.map(dict => (
                        <Option
                          key={dict.dictValue}
                          value={Number.isNaN(Number(dict.dictValue)) ? dict.dictValue : Number(dict.dictValue)}
                        >
                          {dict.dictLabel}
                        </Option>
                      ))}
                    </Select>
                  </Form.Item>
                </Col>
                <Col span={6}>
                  <Form.Item name="nodeStatus" label="节点状态">
                    <Select placeholder="请选择状态" allowClear>
                      <Option value={0}>离线</Option>
                      <Option value={1}>在线</Option>
                      <Option value={2}>未知</Option>
                    </Select>
                  </Form.Item>
                </Col>
              </>
            )}

            <Col span={24} style={{ textAlign: 'right', marginTop: '8px' }}>
              <Space size="small">
                <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
                <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
                <Button 
                    type="link" 
                    onClick={() => setExpand(!expand)}
                    icon={expand ? <UpOutlined /> : <DownOutlined />}
                >
                  {expand ? '收起' : '展开'}
                </Button>
              </Space>
            </Col>
          </Row>
        </Form>
      </Card>

      <Card bordered={false} className="table-card">
        <div className="table-toolbar" style={{ marginBottom: 16 }}>
          <Space size="middle">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
            <Button 
              danger 
              icon={<DeleteOutlined />} 
              disabled={selectedRowKeys.length === 0}
              onClick={() => handleDelete()}
            >
              批量删除
            </Button>
            <Button icon={<ExportOutlined />} onClick={handleExport}>导出</Button>
            <Button 
              icon={<PoweroffOutlined />} 
              onClick={handleOffline}
              style={{ backgroundColor: '#faad14', borderColor: '#faad14', color: '#fff' }}
            >
              下线超时节点
            </Button>
            <Button 
              icon={<HistoryOutlined />} 
              onClick={() => navigate('/op/command-history')}
              style={{ color: '#722ed1' }}
            >
              命令历史
            </Button>
            <Tooltip title="刷新">
                <Button icon={<ReloadOutlined />} onClick={fetchData} shape="circle" />
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

        <Table
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          components={{
            header: {
              cell: ResizableTitle,
            },
          }}
          columns={resizableColumns}
          dataSource={data}
          loading={loading}
          rowKey="id"
          size={tableSize}
          scroll={{ x: 1400 }}
          pagination={{
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            total: total,
            showTotal: (total, range) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
              setQueryParams({ ...queryParams, pageNum: page, pageSize });
            },
            position: ['bottomRight'],
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100']
          }}
        />
      </Card>

      <Modal
        title="执行命令"
        open={isExecuteModalOpen}
        onOk={handleExecuteOk}
        onCancel={handleExecuteCancel}
        width={800}
        maskClosable={false}
        confirmLoading={executing}
        footer={[
          <Button key="clear" onClick={clearResult} disabled={executing}>
            清空结果
          </Button>,
          <Button key="cancel" onClick={handleExecuteCancel} disabled={executing}>
            取消
          </Button>,
          <Button key="execute" type="primary" onClick={handleExecuteOk} loading={executing}>
            执行
          </Button>
        ]}
      >
        <Form
          form={executeForm}
          layout="vertical"
        >
          <Form.Item
            name="command"
            label="命令"
            rules={[{ required: true, message: '请输入要执行的命令' }]}
          >
            <Input.TextArea 
              placeholder="请输入要执行的命令（如：ls -la, ipconfig, ping 127.0.0.1等）" 
              rows={3}
              disabled={executing}
            />
          </Form.Item>
          <Form.Item
            name="timeout"
            label="超时时间（秒）"
            rules={[
              { required: true, message: '请输入超时时间' },
              { type: 'number', min: 1, max: 300, message: '超时时间范围为1-300秒' }
            ]}
            extra="命令执行的最大等待时间，超过此时间将自动终止"
          >
            <InputNumber placeholder="请输入超时时间" min={1} max={300} style={{ width: '100%' }} disabled={executing} />
          </Form.Item>
          <Form.Item label="执行结果">
            <Input.TextArea 
              value={commandResult} 
              readOnly 
              rows={10}
              placeholder="执行结果将显示在这里..."
              style={{ fontFamily: 'monospace', fontSize: '12px' }}
            />
          </Form.Item>
          {commandHistory.length > 0 && (
            <Form.Item label="最近执行的命令">
              <div style={{ maxHeight: '150px', overflow: 'auto', border: '1px solid #d9d9d9', borderRadius: '6px', padding: '8px' }}>
                {commandHistory.map((item, index) => (
                  <div key={index} style={{ marginBottom: '4px', fontSize: '12px', color: '#666' }}>
                    <div><strong>{item.timestamp}</strong> - {item.agent}</div>
                    <div style={{ fontFamily: 'monospace' }}>{item.command}</div>
                  </div>
                ))}
              </div>
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        title={modalTitle}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        afterOpenChange={handleModalAfterOpenChange}
        width={600}
        maskClosable={false}
      >
        <Form
          form={modalForm}
          layout="vertical"
          component="div"
        >
          <Form.Item
            name="nodeName"
            label="节点名称"
            rules={[{ required: true, message: '请输入节点名称' }]}
          >
            <Input placeholder="请输入节点名称" />
          </Form.Item>
          <Form.Item
            name="osType"
            label="操作系统"
            extra={`字典来源：字典类型 ${dictTypeMeta.agent_os_type.dictType}${dictTypeMeta.agent_os_type.dictName ? `，字典名称 ${dictTypeMeta.agent_os_type.dictName}` : ''}`}
          >
            <Select placeholder="请选择操作系统">
              {osTypeOptions.map(dict => (
                <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="appId"
            label="应用ID"
          >
            <Input placeholder="请输入应用ID" />
          </Form.Item>
          <Form.Item
            name="agentIp"
            label="Agent IP"
            rules={[{ required: true, message: '请输入Agent IP' }]}
          >
            <Input placeholder="请输入Agent IP" />
          </Form.Item>
          <Form.Item
            name="agentPort"
            label="Agent端口"
            rules={[
              { required: true, message: '请输入Agent端口' },
              { type: 'number', min: 1, max: 65535, message: '端口范围为1-65535' }
            ]}
          >
            <InputNumber placeholder="请输入Agent端口" min={1} max={65535} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="nodeEnabled"
            label="节点启用"
            rules={[{ required: true, message: '请选择节点启用状态' }]}
            extra={`字典来源：字典类型 ${dictTypeMeta.agent_node_switch.dictType}${dictTypeMeta.agent_node_switch.dictName ? `，字典名称 ${dictTypeMeta.agent_node_switch.dictName}` : ''}`}
          >
            <Select placeholder="请选择节点启用状态">
              {nodeSwitchOptions.map(dict => (
                <Option
                  key={dict.dictValue}
                  value={Number.isNaN(Number(dict.dictValue)) ? dict.dictValue : Number(dict.dictValue)}
                >
                  {dict.dictLabel}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="remark"
            label="备注"
          >
            <Input.TextArea placeholder="请输入备注" rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="下线超时节点"
        open={isOfflineModalOpen}
        onOk={handleOfflineOk}
        onCancel={() => setIsOfflineModalOpen(false)}
        maskClosable={false}
        keyboard={false}
      >
        <Form
          form={offlineForm}
          layout="vertical"
        >
          <Form.Item
            name="timeoutSeconds"
            label="超时时间（秒）"
            rules={[
              { required: true, message: '请输入超时时间' },
              { type: 'number', min: 60, message: '超时时间不能少于60秒' }
            ]}
            extra="超过此时间未发送心跳的节点将被标记为离线"
          >
            <InputNumber placeholder="请输入超时时间" min={60} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AgentManage;