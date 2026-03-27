import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Modal, message, Popconfirm, Tooltip, Select, Tag, InputNumber } from 'antd';
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined, ExportOutlined, PoweroffOutlined } from '@ant-design/icons';
import { 
  listAgentRegistry, 
  getAgentRegistry, 
  addAgentRegistry, 
  updateAgentRegistry, 
  delAgentRegistry, 
  exportAgentRegistry,
  offlineTimeoutNodes
} from '../../../api/agent';

const { Option } = Select;

const AgentManage = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
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
        message.error('获取详情失败');
    }
  };

  const handleModalAfterOpenChange = (open) => {
    if (open) {
      // Modal完全打开后，再设置表单值
      setTimeout(() => {
        if (currentId && editData) {
          // 修改模式：设置编辑数据
          modalForm.resetFields();
          modalForm.setFieldsValue(editData);
        } else {
          // 新增模式：设置默认值
          modalForm.resetFields();
          modalForm.setFieldsValue({
            nodeEnabled: "0"
          });
        }
      }, 100);
    } else {
      // Modal关闭时，清空编辑数据
      setEditData(null);
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
      message.error('下线失败');
    }
  };

  const columns = [
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
        const textMap = {
          0: '启用',
          1: '临时关闭',
          2: '永久关闭'
        };
        return (
          <Tag color={colorMap[nodeEnabled]}>
            {textMap[nodeEnabled]}
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
    { title: '创建人', dataIndex: 'createBy', key: 'createBy', align: 'center', width: 100, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 180 },
    { title: '更新人', dataIndex: 'updateBy', key: 'updateBy', align: 'center', width: 100, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: 180 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
            <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline">
          <Form.Item name="nodeName" label="节点名称">
            <Input placeholder="请输入节点名称" allowClear style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="osType" label="操作系统">
            <Select placeholder="请选择操作系统" style={{ width: 120 }} allowClear>
              <Option value="Linux">Linux</Option>
              <Option value="Windows">Windows</Option>
              <Option value="Mac">Mac</Option>
              <Option value="Other">其他</Option>
            </Select>
          </Form.Item>
          <Form.Item name="appId" label="应用ID">
            <Input placeholder="请输入应用ID" allowClear style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="agentIp" label="Agent IP">
            <Input placeholder="请输入Agent IP" allowClear style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="agentPort" label="Agent端口">
            <Input placeholder="请输入Agent端口" allowClear style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="nodeEnabled" label="节点启用">
            <Select placeholder="请选择状态" style={{ width: 120 }} allowClear>
              <Option value="0">启用</Option>
              <Option value="1">临时关闭</Option>
              <Option value="2">永久关闭</Option>
            </Select>
          </Form.Item>
          <Form.Item name="nodeStatus" label="节点状态">
            <Select placeholder="请选择状态" style={{ width: 120 }} allowClear>
              <Option value="0">离线</Option>
              <Option value="1">在线</Option>
              <Option value="2">未知</Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
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
            <Tooltip title="刷新">
                <Button icon={<ReloadOutlined />} onClick={fetchData} shape="circle" />
            </Tooltip>
          </Space>
        </div>

        <Table
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          columns={columns}
          dataSource={data}
          loading={loading}
          rowKey="id"
          scroll={{ x: 1600 }}
          pagination={{
            total: total,
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            onChange: (page, pageSize) => {
              setQueryParams({ ...queryParams, pageNum: page, pageSize: pageSize });
            },
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
        />
      </Card>

      <Modal
        title={modalTitle}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        afterOpenChange={handleModalAfterOpenChange}
        width={600}
        maskClosable={false}
        keyboard={false}
        autoFocus={false}
        focusTrap={false}
        getContainer={false}
      >
        <Form
          form={modalForm}
          layout="vertical"
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
          >
            <Select placeholder="请选择操作系统">
              <Option value="Linux">Linux</Option>
              <Option value="Windows">Windows</Option>
              <Option value="Mac">Mac</Option>
              <Option value="Other">其他</Option>
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
            rules={[{ required: true, message: '请输入Agent端口' }]}
          >
            <Input placeholder="请输入Agent端口" type="number" min={1} max={65535} />
          </Form.Item>
          <Form.Item
            name="nodeEnabled"
            label="节点启用"
            rules={[{ required: true, message: '请选择节点启用状态' }]}
          >
            <Select placeholder="请选择节点启用状态">
              <Option value="0">启用</Option>
              <Option value="1">临时关闭</Option>
              <Option value="2">永久关闭</Option>
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
            rules={[{ required: true, message: '请输入超时时间' }]}
            extra="超过此时间未发送心跳的节点将被标记为离线"
          >
            <Input placeholder="请输入超时时间" type="number" min={60} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AgentManage;