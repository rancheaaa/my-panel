import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, InputNumber, Radio, TreeSelect, message, Popconfirm, Tag, Tooltip, Row, Col } from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ColumnHeightOutlined
} from '@ant-design/icons';
import { listDept, getDept, addDept, updateDept, delDept, listDeptExcludeChild } from '../../../api/dept';
import { getDicts } from '../../../api/dict/data';
import './Dept.scss';

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

const Dept = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [queryParams, setQueryParams] = useState({
    deptName: undefined,
    status: undefined
  });
  
  const [form] = Form.useForm();
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增部门');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);
  const [deptOptions, setDeptOptions] = useState([]);
  const [sysNormalDisable, setSysNormalDisable] = useState([]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listDept(queryParams);
      if (res.code === 200) {
        const treeData = handleTree(res.data, "deptId", "parentId");
        setData(treeData);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };
  
  const getTreeselect = async () => {
      try {
          const res = await listDept();
          if (res.code === 200) {
              const dept = { deptId: 0, deptName: '主类目', children: [] };
              dept.children = handleTree(res.data, "deptId", "parentId");
              setDeptOptions([dept]);
          }
      } catch (error) {
          console.error(error);
      }
  };

  useEffect(() => {
    fetchData();
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
        ...values
      });
    });
  };

  const handleReset = () => {
    form.resetFields();
    setQueryParams({
      deptName: undefined,
      status: undefined
    });
  };

  // Add Dept
  const handleAdd = (row) => {
    setModalTitle('新增部门');
    setCurrentId(null);
    modalForm.resetFields();
    getTreeselect();
    if (row != null && row.deptId) {
      modalForm.setFieldsValue({ parentId: row.deptId });
    } else {
      modalForm.setFieldsValue({ parentId: 0 });
    }
    setIsModalOpen(true);
  };

  // Edit Dept
  const handleEdit = async (record) => {
    setModalTitle('编辑部门');
    setCurrentId(record.deptId);
    try {
        const res = await getDept(record.deptId);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            const deptRes = await listDeptExcludeChild(record.deptId);
            if (deptRes.code === 200) {
                const dept = { deptId: 0, deptName: '主类目', children: [] };
                dept.children = handleTree(deptRes.data, "deptId", "parentId");
                setDeptOptions([dept]);
            }
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取部门详情失败');
    }
  };

  // Delete Dept
  const handleDelete = async (id) => {
    try {
      await delDept(id);
      message.success('删除成功');
      fetchData();
    } catch (error) {
      message.error('删除失败');
    }
  };

  // Handle Form Submit
  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentId) {
        await updateDept({ ...values, deptId: currentId });
        message.success('更新成功');
      } else {
        await addDept(values);
        message.success('新增成功');
      }
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('操作失败');
    }
  };

  const columns = [
    { title: '部门名称', dataIndex: 'deptName', key: 'deptName', width: 260, ellipsis: true },
    { title: '排序', dataIndex: 'orderNum', key: 'orderNum', align: 'center', width: 100 },
    { 
        title: '状态', 
        dataIndex: 'status', 
        key: 'status', 
        align: 'center',
        width: 100,
        render: (text) => (
            <Tag color={text === '0' ? 'success' : 'error'}>
                {text === '0' ? '正常' : '停用'}
            </Tag>
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
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
          <Button type="text" icon={<PlusOutlined />} onClick={() => handleAdd(record)} style={{ color: '#1890ff' }}>新增</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.deptId)}>
             <Button type="text" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="dept-container">
      <Card bordered={false} className="search-card">
        <Form form={form} layout="inline">
          <Form.Item name="deptName" label="部门名称">
            <Input placeholder="请输入部门名称" allowClear />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择状态" allowClear style={{ width: 120 }}>
                {sysNormalDisable.map(dict => (
                    <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                ))}
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
        <div className="table-toolbar">
          <Space size="middle">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => handleAdd()}>新增</Button>
             <Tooltip title="刷新">
                <Button icon={<ReloadOutlined />} onClick={fetchData} shape="circle" />
             </Tooltip>
             <Tooltip title="密度">
                <Button icon={<ColumnHeightOutlined />} shape="circle" />
             </Tooltip>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={data}
          rowKey="deptId"
          loading={loading}
          pagination={false}
          scroll={{ x: 860 }}
          expandable={{
              childrenColumnName: 'children',
              defaultExpandAllRows: true
          }}
        />
      </Card>

      <Modal
        title={modalTitle}
        open={isModalOpen}
        onOk={handleModalOk}
        onCancel={() => setIsModalOpen(false)}
        destroyOnClose
        width={600}
      >
        <Form form={modalForm} layout="vertical">
          <Form.Item name="parentId" label="上级部门" rules={[{ required: true, message: '请选择上级部门' }]}>
             <TreeSelect
                treeData={deptOptions}
                fieldNames={{ label: 'deptName', value: 'deptId', children: 'children' }}
                placeholder="选择上级部门"
                treeDefaultExpandAll
             />
          </Form.Item>
          <Row gutter={16}>
              <Col span={12}>
                  <Form.Item name="deptName" label="部门名称" rules={[{ required: true, message: '请输入部门名称' }]}>
                    <Input placeholder="请输入部门名称" />
                  </Form.Item>
              </Col>
              <Col span={12}>
                  <Form.Item name="orderNum" label="显示排序" rules={[{ required: true, message: '请输入显示排序' }]}>
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
              </Col>
          </Row>
          <Row gutter={16}>
              <Col span={12}>
                  <Form.Item name="leader" label="负责人">
                    <Input placeholder="请输入负责人" />
                  </Form.Item>
              </Col>
              <Col span={12}>
                  <Form.Item name="phone" label="联系电话">
                    <Input placeholder="请输入联系电话" />
                  </Form.Item>
              </Col>
          </Row>
          <Row gutter={16}>
              <Col span={12}>
                  <Form.Item name="email" label="邮箱">
                    <Input placeholder="请输入邮箱" />
                  </Form.Item>
              </Col>
              <Col span={12}>
                  <Form.Item name="status" label="部门状态" initialValue="0">
                      <Radio.Group>
                          <Radio value="0">正常</Radio>
                          <Radio value="1">停用</Radio>
                      </Radio.Group>
                  </Form.Item>
              </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};

export default Dept;
