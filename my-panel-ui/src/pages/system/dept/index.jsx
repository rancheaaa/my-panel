import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, InputNumber, Radio, TreeSelect, message, Popconfirm, Tag, Tooltip, Row, Col, Dropdown, Spin } from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ColumnHeightOutlined,
  HolderOutlined
} from '@ant-design/icons';
import { ResizableTitle } from '../../../components/ResizableTable';
import { DndContext, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import { restrictToVerticalAxis } from '@dnd-kit/modifiers';
import {
  arrayMove,
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { listDept, getDept, addDept, updateDept, delDept, listDeptExcludeChild, sortDept } from '../../../api/dept';
import { getDicts } from '../../../api/dict/data';
import './Dept.scss';

const { Option } = Select;

const SortableRow = (props) => {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: props['data-row-key'],
  });
  const style = {
    ...props.style,
    transform: CSS.Translate.toString(transform),
    transition,
    cursor: 'move',
    ...(isDragging ? { position: 'relative', zIndex: 9999 } : {}),
  };
  return <tr {...props} ref={setNodeRef} style={style} {...attributes} {...listeners} />;
};

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

// Find siblings of a node in the tree
const findSiblings = (tree, id) => {
  const findNode = (nodes, targetId, parent = null) => {
    for (const node of nodes) {
      if (node.deptId === targetId) {
        return parent ? parent.children : tree;
      }
      if (node.children && node.children.length > 0) {
        const result = findNode(node.children, targetId, node);
        if (result) return result;
      }
    }
    return null;
  };
  return findNode(tree, id);
};

const Dept = () => {
  const [data, setData] = useState([]);
  const [originalData, setOriginalData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [tableSize, setTableSize] = useState('large');
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
  const [expandedRowKeys, setExpandedRowKeys] = useState([]);
  const [dragLoading, setDragLoading] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8,
      },
    })
  );

  const [columns, setColumns] = useState([
    {
      title: '排序',
      key: 'drag',
      width: 60,
      align: 'center',
      render: () => <HolderOutlined style={{ cursor: 'move', color: '#999' }} />,
    },
    { 
      title: '部门名称', 
      dataIndex: 'deptName', 
      key: 'deptName', 
      width: 260, 
      ellipsis: true,
      render: (text, record) => {
        const levelColors = ['#1890ff', '#52c41a', '#faad14', '#ff4d4f'];
        
        return (
          <span style={{ 
            color: levelColors[record.level] || '#666',
            fontWeight: record.level === 0 ? 600 : 400,
            fontSize: record.level === 0 ? '15px' : '14px'
          }}>
            {text}
          </span>
        );
      }
    },
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
      width: 300,
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

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listDept(queryParams);
      if (res.code === 200) {
        // Sort by orderNum before tree construction to ensure children are in order
        const sortedData = [...res.data].sort((a, b) => a.orderNum - b.orderNum);
        const treeData = handleTree(sortedData, "deptId", "parentId");
        
        // Add level information for styling
        const addLevel = (nodes, level = 0) => {
          return nodes.map(node => {
            const newNode = { ...node, level };
            if (newNode.children && newNode.children.length > 0) {
              newNode.children = addLevel(newNode.children, level + 1);
            }
            return newNode;
          });
        };
        
        const finalData = addLevel(treeData);
        setData(finalData);
        setOriginalData(finalData);
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
  const handleAdd = async (row) => {
    setModalTitle('新增部门');
    setCurrentId(null);
    modalForm.resetFields();
    getTreeselect();
    
    // Calculate auto sort order
    let maxSort = 0;
    try {
      const res = await listDept();
      if (res.code === 200) {
        const allDepts = res.data || [];
        if (allDepts.length > 0) {
          maxSort = Math.max(...allDepts.map(item => item.orderNum || 0));
        }
      }
    } catch (e) {
      console.error('Calculate sort order failed:', e);
    }

    if (row != null && row.deptId) {
      modalForm.setFieldsValue({ parentId: row.deptId });
    } else {
      modalForm.setFieldsValue({ parentId: 0 });
    }
    
    modalForm.setFieldsValue({ 
      orderNum: maxSort + 1
    });
    
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
        console.error(error);
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
      console.error(error);
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

  // Handle drag end
  const onDragEnd = async ({ active, over }) => {
    if (active.id !== over?.id) {
      const siblings = findSiblings(data, active.id);
      if (!siblings) return;

      // Ensure 'over' is also in the same sibling group
      if (!siblings.some(s => s.deptId === over.id)) {
        message.warning('只能在同级部门内拖拽排序');
        return;
      }

      const oldIndex = siblings.findIndex((i) => i.deptId === active.id);
      const newIndex = siblings.findIndex((i) => i.deptId === over.id);
      const newSiblings = arrayMove(siblings, oldIndex, newIndex);

      // Update parent's children with new order
      const updateTree = (nodes) => {
        return nodes.map(node => {
          if (node.deptId === siblings[0].parentId) {
            return { ...node, children: newSiblings };
          }
          if (node.children && node.children.length > 0) {
            return { ...node, children: updateTree(node.children) };
          }
          return node;
        });
      };
      
      const newData = updateTree(data);
      
      // Immediately update UI to show the new order
      setData(newData);

      // Prepare batch update data
      const sortData = newSiblings.map((item, index) => ({
        deptId: item.deptId,
        orderNum: index + 1
      }));

      setDragLoading(true);
      try {
        const res = await sortDept(sortData);
        if (res.code === 200) {
          message.success('排序更新成功');
          await fetchData();
        } else {
          message.error(res.msg || '排序更新失败');
          // Revert to original data on error
          setData(originalData);
        }
      } catch (error) {
        console.error(error);
        message.error('排序更新失败，请重试');
        // Revert to original data on error
        setData(originalData);
      } finally {
        setDragLoading(false);
      }
    }
  };

  // Flatten visible tree data for SortableContext
  const getFlattenIds = (tree) => {
    let ids = [];
    tree.forEach(node => {
      ids.push(node.deptId);
      if (node.children && expandedRowKeys.includes(node.deptId)) {
        ids = ids.concat(getFlattenIds(node.children));
      }
    });
    return ids;
  };

  return (
    <div className="dept-container">
      {dragLoading && (
        <div className="drag-loading-overlay">
          <div className="drag-loading-content">
            <Spin size="large" />
            <span className="drag-loading-text">正在保存排序...</span>
          </div>
        </div>
      )}
      <Card bordered={false} className="search-card" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item name="deptName" label="部门名称">
                <Input placeholder="请输入部门名称" allowClear />
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
            <Col span={12} style={{ textAlign: 'right' }}>
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
            <Button type="primary" icon={<PlusOutlined />} onClick={() => handleAdd()}>新增</Button>
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

        <DndContext sensors={sensors} modifiers={[restrictToVerticalAxis]} onDragEnd={onDragEnd}>
          <SortableContext items={getFlattenIds(data)} strategy={verticalListSortingStrategy}>
            <Table
              components={{
                header: {
                  cell: ResizableTitle,
                },
                body: {
                  row: SortableRow,
                },
              }}
              rowClassName={(record) => `dept-level-${record.level}`}
              columns={resizableColumns}
              dataSource={data}
              rowKey="deptId"
              loading={loading}
              size={tableSize}
              pagination={false}
              scroll={{ x: 860, y: 'calc(100vh - 350px)' }}
              sticky
              expandable={{
                  childrenColumnName: 'children',
                  expandedRowKeys: expandedRowKeys,
                  onExpand: (expanded, record) => {
                      if (expanded) {
                          setExpandedRowKeys([...expandedRowKeys, record.deptId]);
                      } else {
                          setExpandedRowKeys(expandedRowKeys.filter(k => k !== record.deptId));
                      }
                  }
              }}
            />
          </SortableContext>
        </DndContext>
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
                  <Form.Item name="orderNum" label="显示排序">
                    <InputNumber min={0} style={{ width: '100%' }} disabled placeholder="拖拽排序自动计算" />
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