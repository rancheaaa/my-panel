import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Tag, Tooltip, Modal, message, Popconfirm, Row, Col, Switch, Tree, TreeSelect, DatePicker, Dropdown } from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ColumnHeightOutlined,
  KeyOutlined,
  DownOutlined,
  UpOutlined,
  DownloadOutlined,
  UsergroupAddOutlined
} from '@ant-design/icons';
import { listUser, addUser, updateUser, delUser, resetUserPwd, changeUserStatus, getUser, exportUser, getAuthRole, updateAuthRole } from '../../../api/user';
import { listDept } from '../../../api/dept';
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

const User = () => {
  const [deptData, setDeptData] = useState([]);
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tableSize, setTableSize] = useState('large');
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    userName: undefined,
    phonenumber: undefined,
    status: undefined
  });
  
  const [expand, setExpand] = useState(false);
  const [form] = Form.useForm();
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增用户');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);
  
  // Password Reset Modal
  const [isPwdModalOpen, setIsPwdModalOpen] = useState(false);
  const [pwdForm] = Form.useForm();
  const [currentResetId, setCurrentResetId] = useState(null);
  const [roleOptions, setRoleOptions] = useState([]);
  const [postOptions, setPostOptions] = useState([]);
  const [sysUserSex, setSysUserSex] = useState([]);
  const [sysNormalDisable, setSysNormalDisable] = useState([]);

  // Auth Role Modal State
  const [isAuthRoleOpen, setIsAuthRoleOpen] = useState(false);
  const [authRoleUser, setAuthRoleUser] = useState({});
  const [authRoleList, setAuthRoleList] = useState([]);
  const [selectedRoleIds, setSelectedRoleIds] = useState([]);

  useEffect(() => {
    fetchDept();
    getDicts('sys_user_sex').then(res => {
        if (res.code === 200) {
            setSysUserSex(res.data);
        }
    });
    getDicts('sys_normal_disable').then(res => {
        if (res.code === 200) {
            setSysNormalDisable(res.data);
        }
    });
  }, []);

  const fetchDept = async () => {
      try {
          const res = await listDept();
          if (res.code === 200) {
              setDeptData(handleTree(res.data, "deptId", "parentId"));
          }
      } catch (error) {
          console.error(error);
      }
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listUser(queryParams);
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
      const { dateRange, ...rest } = values;
      const searchParams = {
        ...queryParams,
        ...rest,
        pageNum: 1,
        params: {}
      };
      
      if (dateRange && dateRange.length === 2) {
          searchParams.params = {
              beginTime: dateRange[0].format('YYYY-MM-DD HH:mm:ss'),
              endTime: dateRange[1].format('YYYY-MM-DD HH:mm:ss')
          };
      }
      
      setQueryParams(searchParams);
    });
  };

  const handleReset = () => {
    form.resetFields();
    setQueryParams({
      ...queryParams,
      userName: undefined,
      phonenumber: undefined,
      status: undefined,
      deptId: undefined,
      params: undefined,
      pageNum: 1
    });
  };
  
  const onDeptSelect = (selectedKeys, info) => {
      if (selectedKeys.length > 0) {
          setQueryParams(prev => ({ ...prev, deptId: selectedKeys[0], pageNum: 1 }));
      } else {
          setQueryParams(prev => ({ ...prev, deptId: undefined, pageNum: 1 }));
      }
  };

  const onSelectChange = (newSelectedRowKeys) => {
    setSelectedRowKeys(newSelectedRowKeys);
  };

  const rowSelection = {
    selectedRowKeys,
    onChange: onSelectChange,
  };

  // Add User
  const handleAdd = async () => {
    setModalTitle('新增用户');
    setCurrentId(null);
    modalForm.resetFields();
    try {
        const res = await getUser();
        if (res.code === 200) {
            setRoleOptions(res.data.roles || []);
            setPostOptions(res.data.posts || []);
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取初始化数据失败');
    }
  };

  // Edit User
  const handleUpdate = async (record) => {
    const userId = record?.userId || selectedRowKeys[0];
    if (!userId) {
        message.warning('请选择一条记录');
        return;
    }
    setModalTitle('修改用户');
    setCurrentId(userId);
    try {
        const res = await getUser(userId);
        if (res.code === 200) {
            setRoleOptions(res.data.roles || []);
            setPostOptions(res.data.posts || []);
            modalForm.setFieldsValue({
                ...res.data.user,
                postIds: res.data.postIds || [],
                roleIds: res.data.roleIds || [],
                password: '' // Don't show password
            });
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取用户详情失败');
    }
  };

  // Export User
  const handleExport = async () => {
      try {
          const res = await exportUser(queryParams);
          const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
          const link = document.createElement('a');
          link.href = window.URL.createObjectURL(blob);
          link.download = `user_${new Date().getTime()}.xlsx`;
          link.click();
          window.URL.revokeObjectURL(link.href);
          message.success('导出成功');
      } catch (error) {
          message.error('导出失败');
      }
  };

  // Delete User
  const handleDelete = async (id) => {
    try {
      await delUser(id);
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
          await delUser(selectedRowKeys.join(','));
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
      if (currentId) {
        await updateUser({ ...values, userId: currentId });
        message.success('更新成功');
      } else {
        await addUser(values);
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
      const status = checked ? "0" : "1"; // 0: active, 1: inactive
      try {
          await changeUserStatus(record.userId, status);
          message.success('状态修改成功');
          fetchData();
      } catch (error) {
          message.error('状态修改失败');
      }
  };
  
  // Password Reset
  const handleResetPwd = (record) => {
      setCurrentResetId(record.userId);
      pwdForm.resetFields();
      setIsPwdModalOpen(true);
  };
  
  const handlePwdModalOk = async () => {
      try {
          const values = await pwdForm.validateFields();
          await resetUserPwd(currentResetId, values.password);
          message.success('密码重置成功');
          setIsPwdModalOpen(false);
      } catch (error) {
          message.error('密码重置失败');
      }
  };

  // Auth Role
  const handleAuthRole = async (record) => {
    setAuthRoleUser(record);
    try {
        const res = await getAuthRole(record.userId);
        if (res.code === 200) {
            setAuthRoleList(res.data.roles);
            // returns roles with flag=true if assigned
            const assignedIds = res.data.roles.filter(r => r.flag).map(r => r.roleId);
            setSelectedRoleIds(assignedIds);
            setIsAuthRoleOpen(true);
        }
    } catch (error) {
        message.error('获取授权角色失败');
    }
  };

  const handleAuthRoleOk = async () => {
    try {
        await updateAuthRole({ userId: authRoleUser.userId, roleIds: selectedRoleIds.join(',') });
        message.success('授权角色成功');
        setIsAuthRoleOpen(false);
    } catch (error) {
        message.error('授权角色失败');
    }
  };

  const columns = [
    { title: '用户ID', dataIndex: 'userId', key: 'userId', align: 'center', width: 80 },
    { title: '用户名称', dataIndex: 'userName', key: 'userName', align: 'center', width: 120, ellipsis: true },
    { title: '用户昵称', dataIndex: 'nickName', key: 'nickName', align: 'center', width: 120, ellipsis: true },
    { title: '部门', dataIndex: ['dept', 'deptName'], key: 'deptName', align: 'center', width: 150, ellipsis: true },
    { 
        title: '角色', 
        dataIndex: 'roles', 
        key: 'roles', 
        align: 'center',
        width: 180,
        ellipsis: true,
        render: (roles) => (
            <>
                {roles && roles.map(role => (
                    <Tag color="blue" key={role.roleId}>
                        {role.roleName}
                    </Tag>
                ))}
            </>
        )
    },
    { title: '手机号码', dataIndex: 'phonenumber', key: 'phonenumber', align: 'center', width: 120 },
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
          <Tooltip title="修改">
            <Button type="text" icon={<EditOutlined />} onClick={() => handleUpdate(record)} style={{ color: '#1890ff' }} />
          </Tooltip>
          <Tooltip title="重置密码">
            <Button type="text" icon={<KeyOutlined />} onClick={() => handleResetPwd(record)} style={{ color: '#faad14' }} />
          </Tooltip>
          <Tooltip title="分配角色">
            <Button type="text" icon={<UsergroupAddOutlined />} onClick={() => handleAuthRole(record)} style={{ color: '#13c2c2' }} />
          </Tooltip>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.userId)}>
            <Tooltip title="删除">
              <Button type="text" icon={<DeleteOutlined />} danger />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="app-container">
      <Row gutter={16} style={{ height: '100%' }}>
        <Col span={4} style={{ height: '100%' }}>
            <Card bordered={false} className="dept-card" style={{ height: '100%', overflow: 'auto' }}>
                <Input.Search style={{ marginBottom: 8 }} placeholder="请输入部门名称" />
                <Tree
                    treeData={deptData}
                    fieldNames={{ title: 'deptName', key: 'deptId', children: 'children' }}
                    defaultExpandAll
                    onSelect={onDeptSelect}
                />
            </Card>
        </Col>
        <Col span={20} style={{ height: '100%', display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
                <Form form={form} component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }}>
                    <Row gutter={[24, 16]}>
                        <Col span={6}>
                            <Form.Item name="userName" label="用户名称">
                                <Input placeholder="请输入用户名称" allowClear />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="phonenumber" label="手机号码">
                                <Input placeholder="请输入手机号码" allowClear />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="status" label="用户状态">
                                <Select placeholder="请选择状态" allowClear>
                                    {sysNormalDisable.map(dict => (
                                        <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Col>
                        {expand && (
                             <Col span={6}>
                                <Form.Item name="dateRange" label="创建时间">
                                    <DatePicker.RangePicker style={{ width: '100%' }} />
                                </Form.Item>
                             </Col>
                        )}
                        <Col span={expand ? 24 : 6} style={{ textAlign: 'right' }}>
                             <Space>
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
                <div className="table-toolbar">
                <Space size="middle">
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
                    <Button 
                        icon={<EditOutlined />} 
                        disabled={selectedRowKeys.length !== 1} 
                        onClick={() => handleUpdate()}
                        style={selectedRowKeys.length === 1 ? { backgroundColor: '#52c41a', borderColor: '#52c41a', color: '#fff' } : {}}
                    >
                        修改
                    </Button>
                    <Button 
                        danger 
                        icon={<DeleteOutlined />} 
                        disabled={selectedRowKeys.length === 0} 
                        onClick={handleBatchDelete}
                    >
                        删除
                    </Button>
                    <Button 
                        icon={<DownloadOutlined />} 
                        onClick={handleExport}
                        style={{ backgroundColor: '#faad14', borderColor: '#faad14', color: '#fff' }}
                    >
                        导出
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
                rowKey="userId"
                loading={loading}
                size={tableSize}
                scroll={{ x: 1200 }}
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
        </Col>
      </Row>

      <Modal
        title={modalTitle}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        destroyOnClose
      >
        <Form form={modalForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="nickName" label="用户昵称" rules={[{ required: true, message: '请输入用户昵称' }]}>
                <Input placeholder="请输入用户昵称" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="deptId" label="归属部门">
                <TreeSelect
                    treeData={deptData}
                    fieldNames={{ label: 'deptName', value: 'deptId', children: 'children' }}
                    placeholder="请选择归属部门"
                    treeDefaultExpandAll
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="phonenumber" label="手机号码" rules={[{ pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, message: '请输入正确的手机号码' }]}>
                <Input placeholder="请输入手机号码" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '请输入正确的邮箱地址' }]}>
                <Input placeholder="请输入邮箱" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="userName" label="用户名称" rules={[{ required: true, message: '请输入用户名称' }]}>
                 <Input placeholder="请输入用户名称" disabled={!!currentId} />
              </Form.Item>
            </Col>
            <Col span={12}>
              {!currentId && (
                  <Form.Item name="password" label="用户密码" rules={[{ required: true, message: '请输入用户密码' }]}>
                    <Input.Password placeholder="请输入用户密码" />
                  </Form.Item>
              )}
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="sex" label="用户性别">
                <Select placeholder="请选择性别">
                    {sysUserSex.map(dict => (
                        <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                    ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="状态" initialValue="0">
                <Select>
                    {sysNormalDisable.map(dict => (
                        <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                    ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="postIds" label="岗位">
                <Select mode="multiple" placeholder="请选择岗位">
                    {postOptions.map(item => (
                        <Option key={item.postId} value={item.postId} disabled={item.status === '1'}>
                            {item.postName}
                        </Option>
                    ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="roleIds" label="角色">
                <Select mode="multiple" placeholder="请选择角色">
                    {roleOptions.map(item => (
                        <Option key={item.roleId} value={item.roleId} disabled={item.status === '1'}>
                            {item.roleName}
                        </Option>
                    ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="请输入备注" />
          </Form.Item>
        </Form>
      </Modal>
      
      <Modal
        title="重置密码"
        open={isPwdModalOpen}
        onOk={handlePwdModalOk}
        onCancel={() => setIsPwdModalOpen(false)}
        destroyOnClose
      >
        <Form form={pwdForm} layout="vertical">
            <Form.Item name="password" label="新密码" rules={[{ required: true, message: '请输入新密码' }]}>
                <Input.Password placeholder="请输入新密码" />
            </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="分配角色"
        open={isAuthRoleOpen}
        onOk={handleAuthRoleOk}
        onCancel={() => setIsAuthRoleOpen(false)}
        destroyOnClose
        width={800}
      >
        <Form layout="inline" style={{ marginBottom: 16 }}>
            <Form.Item label="用户昵称">
                <Input value={authRoleUser.nickName} disabled style={{ width: 200, color: 'rgba(0, 0, 0, 0.85)', cursor: 'default', backgroundColor: '#fff' }} bordered={false} />
            </Form.Item>
            <Form.Item label="用户账号">
                <Input value={authRoleUser.userName} disabled style={{ width: 200, color: 'rgba(0, 0, 0, 0.85)', cursor: 'default', backgroundColor: '#fff' }} bordered={false} />
            </Form.Item>
        </Form>
        <Table
            rowKey="roleId"
            columns={[
                { title: '角色编号', dataIndex: 'roleId', align: 'center' },
                { title: '角色名称', dataIndex: 'roleName', align: 'center' },
                { title: '权限字符', dataIndex: 'roleKey', align: 'center' },
                { title: '创建时间', dataIndex: 'createTime', align: 'center' }
            ]}
            dataSource={authRoleList}
            pagination={false}
            rowSelection={{
                selectedRowKeys: selectedRoleIds,
                onChange: (newSelectedKeys) => setSelectedRoleIds(newSelectedKeys),
                preserveSelectedRowKeys: true
            }}
            scroll={{ y: 400 }}
        />
      </Modal>
    </div>
  );
};

export default User;
