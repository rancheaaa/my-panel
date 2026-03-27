import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, Tree, message, Popconfirm, Row, Col, Switch, InputNumber, Tooltip, Dropdown } from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ColumnHeightOutlined,
  DatabaseOutlined
} from '@ant-design/icons';
import { listRole, getRole, addRole, updateRole, delRole, changeRoleStatus } from '../../../api/role';
import { treeselect, roleMenuTreeselect } from '../../../api/menu';
import { getDicts } from '../../../api/dict/data';

const { Option } = Select;

// Handle Tree Data
const handleTree = (data, id, parentId, children) => {
  const config = {
    id: id || 'id',
    parentId: parentId || 'parentId',
    childrenList: children || 'children'
  };

  var childrenListMap = {};
  var nodeIds = {};
  var tree = [];

  for (let d of data) {
    let parentId = d[config.parentId];
    if (childrenListMap[parentId] == null) {
      childrenListMap[parentId] = [];
    }
    nodeIds[d[config.id]] = d;
    childrenListMap[parentId].push(d);
  }

  for (let d of data) {
    let parentId = d[config.parentId];
    if (nodeIds[parentId] == null) {
      tree.push(d);
    }
  }

  function adaptToChildrenList(o) {
    if (childrenListMap[o[config.id]] !== null) {
      o[config.childrenList] = childrenListMap[o[config.id]];
    }
    if (o[config.childrenList]) {
      for (let c of o[config.childrenList]) {
        adaptToChildrenList(c);
      }
    }
  }

  for (let t of tree) {
    adaptToChildrenList(t);
  }
  return tree;
}

const Role = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tableSize, setTableSize] = useState('large');
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    roleName: undefined,
    roleKey: undefined,
    status: undefined
  });
  
  const [form] = Form.useForm();
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增角色');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);
  const [menuCheckedKeys, setMenuCheckedKeys] = useState([]);
  const [menuHalfCheckedKeys, setMenuHalfCheckedKeys] = useState([]);
  const [menuCheckStrictly, setMenuCheckStrictly] = useState(false);
  const [menuOptions, setMenuOptions] = useState([]);
  const [sysNormalDisable, setSysNormalDisable] = useState([]);
  
  // Data Scope Modal
  const [isDataScopeOpen, setIsDataScopeOpen] = useState(false);
  const [dataScopeForm] = Form.useForm();
  const [deptOptions, setDeptOptions] = useState([]);
  const [deptCheckedKeys, setDeptCheckedKeys] = useState([]);
  const [deptCheckStrictly, setDeptCheckStrictly] = useState(false);
  const [showDeptTree, setShowDeptTree] = useState(false);

  const getAllParentIds = (treeData, ids = []) => {
      for (const node of treeData) {
          if (node.children && node.children.length > 0) {
              ids.push(node.id);
              getAllParentIds(node.children, ids);
          }
      }
      return ids;
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listRole(queryParams);
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
  
  const getMenuTreeselect = async () => {
      try {
          const res = await treeselect();
          if (res.code === 200) {
              setMenuOptions(res.data);
          }
      } catch (error) {
          console.error(error);
      }
  };

  useEffect(() => {
    fetchData();
    getMenuTreeselect();
    getDicts('sys_normal_disable').then(res => {
      if (res.code === 200) {
        setSysNormalDisable(res.data);
      }
    });
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
      ...queryParams,
      roleName: undefined,
      roleKey: undefined,
      status: undefined,
      pageNum: 1
    });
  };
  
  const onSelectChange = (newSelectedRowKeys) => {
    setSelectedRowKeys(newSelectedRowKeys);
  };

  const rowSelection = {
    selectedRowKeys,
    onChange: onSelectChange,
  };

  // Add Role
  const handleAdd = () => {
    setModalTitle('新增角色');
    setCurrentId(null);
    modalForm.resetFields();
    setMenuCheckedKeys([]);
    setMenuHalfCheckedKeys([]);
    setMenuCheckStrictly(false);
    setIsModalOpen(true);
  };

  // Edit Role
  const handleEdit = async (record) => {
    setModalTitle('编辑角色');
    setCurrentId(record.roleId);
    setMenuCheckStrictly(false);
    try {
        const roleMenuRes = await roleMenuTreeselect(record.roleId);
        const res = await getRole(record.roleId);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            
            // Filter out parent IDs to avoid auto-checking children
            const parentIds = getAllParentIds(roleMenuRes.data.menus);
            const leafKeys = roleMenuRes.data.checkedKeys.filter(key => !parentIds.includes(key));
            const parentKeys = roleMenuRes.data.checkedKeys.filter(key => parentIds.includes(key));
            
            setMenuCheckedKeys(leafKeys);
            setMenuHalfCheckedKeys(parentKeys);
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取角色详情失败');
    }
  };

  // Delete Role
  const handleDelete = async (id) => {
    try {
      await delRole(id);
      message.success('删除成功');
      fetchData();
      setSelectedRowKeys([]);
    } catch (error) {
      message.error('删除失败');
    }
  };
  
  const handleBatchDelete = async () => {
      if (!selectedRowKeys.length) return;
      try {
          await delRole(selectedRowKeys.join(','));
          message.success('删除成功');
          fetchData();
          setSelectedRowKeys([]);
      } catch (error) {
          message.error('删除失败');
      }
  };

  // Handle Form Submit
  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      values.menuIds = [...menuCheckedKeys, ...menuHalfCheckedKeys];
      if (currentId) {
        await updateRole({ ...values, roleId: currentId });
        message.success('更新成功');
      } else {
        await addRole(values);
        message.success('新增成功');
      }
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('操作失败');
    }
  };
  
  // Status Change
  const handleStatusChange = async (checked, record) => {
      const status = checked ? "0" : "1";
      try {
          await changeRoleStatus(record.roleId, status);
          message.success('状态修改成功');
          fetchData();
      } catch (error) {
          message.error('状态修改失败');
      }
  };
  
  const onCheck = (checkedKeys, info) => {
      setMenuCheckedKeys(checkedKeys);
      setMenuHalfCheckedKeys(info.halfCheckedKeys);
  };

  const columns = [
    { title: '角色编号', dataIndex: 'roleId', key: 'roleId', align: 'center', width: 100 },
    { title: '角色名称', dataIndex: 'roleName', key: 'roleName', align: 'center', width: 150, ellipsis: true },
    { title: '权限字符', dataIndex: 'roleKey', key: 'roleKey', align: 'center', width: 150, ellipsis: true },
    { title: '显示顺序', dataIndex: 'roleSort', key: 'roleSort', align: 'center', width: 100 },
    { 
        title: '状态', 
        dataIndex: 'status', 
        key: 'status', 
        align: 'center',
        width: 100,
        render: (text, record) => (
            <Switch 
                checked={text === '0'} 
                onChange={(checked) => handleStatusChange(checked, record)}
                checkedChildren="正常"
                unCheckedChildren="停用"
            />
        )
    },
    { title: '创建者', dataIndex: 'createBy', key: 'createBy', align: 'center', width: 100, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 160 },
    { title: '更新者', dataIndex: 'updateBy', key: 'updateBy', align: 'center', width: 100, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: 160 },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>编辑</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.roleId)}>
             <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="app-container">
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item name="roleName" label="角色名称">
                <Input placeholder="请输入角色名称" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="roleKey" label="权限字符">
                <Input placeholder="请输入权限字符" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择状态" allowClear>
                  {sysNormalDisable.map(dict => (
                    <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={6} style={{ textAlign: 'right' }}>
              <Space>
                <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
                <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
              </Space>
            </Col>
          </Row>
        </Form>
      </Card>

      <Card bordered={false} className="table-card">
        <div className="table-toolbar">
          <Space size="middle">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
            <Button 
                danger 
                icon={<DeleteOutlined />} 
                disabled={selectedRowKeys.length === 0} 
                onClick={handleBatchDelete}
            >
                批量删除
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
          rowSelection={rowSelection}
          columns={columns}
          dataSource={data}
          rowKey="roleId"
          loading={loading}
          size={tableSize}
          scroll={{ x: 1000 }}
          pagination={{
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            total: total,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
                setQueryParams(prev => ({ ...prev, pageNum: page, pageSize }));
            }
          }}
        />
      </Card>

      <Modal
        title={modalTitle}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        destroyOnClose
      >
        <Form form={modalForm} layout="vertical">
          <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input placeholder="请输入角色名称" />
          </Form.Item>
          <Form.Item name="roleKey" label="权限字符" rules={[{ required: true, message: '请输入权限字符' }]}>
             <Input placeholder="请输入权限字符" />
          </Form.Item>
          <Form.Item name="roleSort" label="显示顺序" rules={[{ required: true, message: '请输入显示顺序' }]}>
             <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="0">
            <Select>
                {sysNormalDisable.map(dict => (
                    <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                ))}
            </Select>
          </Form.Item>
          <Form.Item label="菜单权限">
              <div style={{ border: '1px solid #d9d9d9', borderRadius: '2px', maxHeight: '200px', overflow: 'auto' }}>
                <Tree
                    checkable
                    onCheck={onCheck}
                    checkedKeys={menuCheckedKeys}
                    treeData={menuOptions}
                    fieldNames={{ title: 'label', key: 'id', children: 'children' }}
                />
              </div>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="请输入备注" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Role;
