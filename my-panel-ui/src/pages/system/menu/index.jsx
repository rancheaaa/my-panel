import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, Modal, InputNumber, Radio, TreeSelect, message, Popconfirm, Tag, Tooltip, Row, Col, Dropdown, Switch, Spin } from 'antd';
import { 
  SearchOutlined, 
  ReloadOutlined, 
  PlusOutlined, 
  DeleteOutlined, 
  EditOutlined,
  ColumnHeightOutlined,
  MenuOutlined,
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
import { listMenu, getMenu, addMenu, updateMenu, delMenu, sortMenu } from '../../../api/menu';
import { getRouters } from '../../../api/auth';
import { getDicts } from '../../../api/dict/data';
import { getIcon } from '../../../utils/menuUtils';
import IconSelect from '../../../components/IconSelect';
import './Menu.scss';

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

const Menu = () => {
  const [data, setData] = useState([]);
  const [originalData, setOriginalData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [tableSize, setTableSize] = useState('large');
  const [queryParams, setQueryParams] = useState({
    menuName: undefined,
    status: undefined,
    path: undefined
  });
  
  const [form] = Form.useForm();
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增菜单');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);

  // Resizable Columns State
  const [columns, setColumns] = useState([
    {
      title: '排序',
      key: 'drag',
      width: 60,
      align: 'center',
      render: () => <HolderOutlined style={{ cursor: 'move', color: '#999' }} />,
    },
    { 
      title: '菜单名称', 
      dataIndex: 'menuName', 
      key: 'menuName', 
      width: 250, 
      ellipsis: true,
      render: (text, record) => {
        const levelColors = ['#1890ff', '#52c41a', '#faad14', '#ff4d4f'];
        const typeColors = { 'M': 'blue', 'C': 'green', 'F': 'orange' };
        const typeLabels = { 'M': '目录', 'C': '菜单', 'F': '按钮' };
        
        return (
          <Space>
            <span style={{ 
              color: levelColors[record.level] || '#666',
              fontWeight: record.level === 0 ? 600 : 400,
              fontSize: record.level === 0 ? '15px' : '14px'
            }}>
              {text}
            </span>
            <Tag color={typeColors[record.menuType]} bordered={false} style={{ fontSize: '11px', padding: '0 4px' }}>
              {typeLabels[record.menuType]}
            </Tag>
          </Space>
        );
      }
    },
    { 
        title: '图标', 
        dataIndex: 'icon', 
        key: 'icon', 
        align: 'center', 
        width: 100,
        render: (text) => text ? <span style={{ fontSize: '18px' }}>{getIcon(text)}</span> : null
    },
    { title: '排序', dataIndex: 'orderNum', key: 'orderNum', align: 'center', width: 80 },
    { title: '路由地址', dataIndex: 'path', key: 'path', width: 200, ellipsis: true },
    { title: '组件路径', dataIndex: 'component', key: 'component', width: 200, ellipsis: true },
    { title: '权限标识', dataIndex: 'perms', key: 'perms', width: 200, ellipsis: true },
    { 
        title: '显示状态', 
        dataIndex: 'visible', 
        key: 'visible', 
        align: 'center',
        width: 100,
        render: (text, record) => (
            <Switch
                checked={text === '0'}
                onChange={async (checked) => {
                    const newVisible = checked ? '0' : '1';
                    try {
                        await updateMenu({ ...record, visible: newVisible });
                        message.success('显示状态更新成功');
                        fetchData();
                        refreshMenuCache();
                    } catch (error) {
                        console.error(error);
                        message.error('显示状态更新失败');
                    }
                }}
                checkedChildren="显示"
                unCheckedChildren="隐藏"
            />
        )
    },
    { 
        title: '菜单状态', 
        dataIndex: 'status', 
        key: 'status', 
        align: 'center',
        width: 100,
        render: (text, record) => (
            <Switch
                checked={text === '0'}
                onChange={async (checked) => {
                    const newStatus = checked ? '0' : '1';
                    try {
                        await updateMenu({ ...record, status: newStatus });
                        message.success('状态更新成功');
                        fetchData();
                        refreshMenuCache();
                    } catch (error) {
                        console.error(error);
                        message.error('状态更新失败');
                    }
                }}
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
      width: 300,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} style={{ color: '#1890ff' }}>修改</Button>
          <Button type="text" icon={<PlusOutlined />} onClick={() => handleAdd(record)} style={{ color: '#1890ff' }}>新增</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.menuId)}>
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

  const [menuOptions, setMenuOptions] = useState([]);
  const [showIcon, setShowIcon] = useState(false);
  const [sysNormalDisable, setSysNormalDisable] = useState([]);
  const [sysShowHide, setSysShowHide] = useState([]);
  const [expandedRowKeys, setExpandedRowKeys] = useState([]);
  const [dragLoading, setDragLoading] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8,
      },
    })
  );

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listMenu(queryParams);
      if (res.code === 200) {
        // Sort by orderNum before tree construction to ensure children are in order
        const sortedData = [...res.data].sort((a, b) => a.orderNum - b.orderNum);
        const treeData = handleTree(sortedData, "menuId", "parentId");
        
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
          const res = await listMenu();
          if (res.code === 200) {
              const menu = { menuId: 0, menuName: '主类目', children: [] };
              const sortedData = [...res.data].sort((a, b) => a.orderNum - b.orderNum);
              menu.children = handleTree(sortedData, "menuId", "parentId");
              setMenuOptions([menu]);
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
    getDicts('sys_show_hide').then(res => {
        if (res.code === 200) {
            setSysShowHide(res.data);
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
      menuName: undefined,
      status: undefined,
      path: undefined
    });
  };

  // Find all siblings of a menu item
  const findSiblings = (tree, menuId) => {
    for (const node of tree) {
      if (node.children && node.children.some(child => child.menuId === menuId)) {
        return node.children;
      }
      if (node.children) {
        const result = findSiblings(node.children, menuId);
        if (result) return result;
      }
    }
    // If not found in children, check if it's a root node
    if (tree.some(node => node.menuId === menuId)) {
      return tree;
    }
    return null;
  };

  const onDragEnd = async ({ active, over }) => {
    if (active.id !== over?.id) {
      const siblings = findSiblings(data, active.id);
      if (!siblings) return;

      // Ensure 'over' is also in the same sibling group
      if (!siblings.some(s => s.menuId === over.id)) {
        message.warning('只能在同级菜单内拖拽排序');
        return;
      }

      const oldIndex = siblings.findIndex((i) => i.menuId === active.id);
      const newIndex = siblings.findIndex((i) => i.menuId === over.id);
      const newSiblings = arrayMove(siblings, oldIndex, newIndex);

      // Update parent's children with new order
      const updateTree = (nodes) => {
        // If the siblings are root-level (parentId is 0), return the new siblings directly
        if (siblings[0].parentId === 0) {
          return newSiblings;
        }
        
        return nodes.map(node => {
          if (node.menuId === siblings[0].parentId) {
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
        menuId: item.menuId,
        orderNum: index + 1
      }));

      setDragLoading(true);
      try {
        const res = await sortMenu(sortData);
        if (res.code === 200) {
          message.success('排序更新成功');
          await fetchData();
          refreshMenuCache();
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

  // Add Menu
  const handleAdd = async (row) => {
    setModalTitle('新增菜单');
    setCurrentId(null);
    modalForm.resetFields();
    getTreeselect();
    
    let parentId = 0;
    if (row != null && row.menuId) {
      parentId = row.menuId;
    }
    
    // Calculate auto sort order
    let maxSort = 0;
    try {
      const res = await listMenu();
      if (res.code === 200) {
        const siblings = res.data.filter(item => item.parentId === parentId);
        if (siblings.length > 0) {
          maxSort = Math.max(...siblings.map(item => item.orderNum || 0));
        }
      }
    } catch (e) {
      console.error('Calculate sort order failed:', e);
    }

    modalForm.setFieldsValue({ 
      parentId: parentId,
      orderNum: maxSort + 1
    });
    setIsModalOpen(true);
  };

  // Edit Menu
  const handleEdit = async (record) => {
    setModalTitle('编辑菜单');
    setCurrentId(record.menuId);
    getTreeselect();
    try {
        const res = await getMenu(record.menuId);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            setIsModalOpen(true);
        }
    } catch (error) {
        message.error('获取菜单详情失败');
    }
  };

  // Refresh Menu Cache
  const refreshMenuCache = async () => {
    try {
      const res = await getRouters();
      if (res.code === 200) {
        localStorage.setItem('userRouters', JSON.stringify(res.data));
        // Dispatch event to notify MainLayout
        window.dispatchEvent(new Event('sys:menu:refresh'));
      }
    } catch (error) {
      console.error('Refresh menu cache failed:', error);
    }
  };

  // Delete Menu
  const handleDelete = async (id) => {
    try {
      await delMenu(id);
      message.success('删除成功');
      fetchData();
      refreshMenuCache();
    } catch (error) {
      message.error('删除失败');
    }
  };

  // Handle Form Submit
  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentId) {
        await updateMenu({ ...values, menuId: currentId });
        message.success('更新成功');
      } else {
        await addMenu(values);
        message.success('新增成功');
      }
      setIsModalOpen(false);
      fetchData();
      refreshMenuCache();
    } catch (error) {
      console.error(error);
      // message.error('操作失败'); // 移除这行，避免表单验证失败时也报错
    }
  };

  // Flatten visible tree data for SortableContext
  const getFlattenIds = (tree) => {
    let ids = [];
    tree.forEach(node => {
      ids.push(node.menuId);
      if (node.children && expandedRowKeys.includes(node.menuId)) {
        ids = ids.concat(getFlattenIds(node.children));
      }
    });
    return ids;
  };

  return (
    <div className="menu-container">
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
              <Form.Item name="menuName" label="菜单名称">
                <Input placeholder="请输入菜单名称" allowClear />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="path" label="路由地址">
                <Input placeholder="请输入路由地址" allowClear />
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
              rowClassName={(record) => `menu-level-${record.level}`}
              columns={resizableColumns}
              dataSource={data}
              rowKey="menuId"
              loading={loading}
              size={tableSize}
              pagination={false}
              scroll={{ x: 1220, y: 'calc(100vh - 350px)' }}
              sticky
              expandable={{
                  childrenColumnName: 'children',
                  expandedRowKeys: expandedRowKeys,
                  onExpand: (expanded, record) => {
                      if (expanded) {
                          setExpandedRowKeys([...expandedRowKeys, record.menuId]);
                      } else {
                          setExpandedRowKeys(expandedRowKeys.filter(k => k !== record.menuId));
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
        width={680}
      >
        <Form form={modalForm} layout="vertical">
          <Form.Item name="parentId" label="上级菜单" rules={[{ required: true, message: '请选择上级菜单' }]}>
             <TreeSelect
                treeData={menuOptions}
                fieldNames={{ label: 'menuName', value: 'menuId', children: 'children' }}
                placeholder="选择上级菜单"
                treeDefaultExpandAll
             />
          </Form.Item>
          <Form.Item name="menuType" label="菜单类型" initialValue="M">
              <Radio.Group>
                  <Radio.Button value="M">目录</Radio.Button>
                  <Radio.Button value="C">菜单</Radio.Button>
                  <Radio.Button value="F">按钮</Radio.Button>
              </Radio.Group>
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prevValues, curValues) => prevValues.menuType !== curValues.menuType}
          >
            {({ getFieldValue }) => {
              return getFieldValue('menuType') !== 'F' ? (
                <Form.Item name="icon" label="菜单图标">
                  <IconSelect placeholder="点击选择图标" />
                </Form.Item>
              ) : null;
            }}
          </Form.Item>
          <Row gutter={16}>
              <Col span={12}>
                  <Form.Item name="menuName" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
                    <Input placeholder="请输入菜单名称" />
                  </Form.Item>
              </Col>
              <Col span={12}>
                  <Form.Item 
                    name="orderNum" 
                    label={
                      <span>
                        显示排序
                        <Tooltip title="系统自动生成，无需填写">
                          <span style={{ marginLeft: 4, color: '#999', cursor: 'help', fontSize: '12px' }}> (系统自动生成)</span>
                        </Tooltip>
                      </span>
                    } 
                    rules={[{ required: true, message: '请输入显示排序' }]}
                  >
                    <InputNumber min={0} style={{ width: '100%' }} disabled />
                  </Form.Item>
              </Col>
          </Row>
          <Row gutter={16}>
              <Col span={12}>
                  <Form.Item name="isFrame" label="是否外链" initialValue="1">
                      <Radio.Group>
                          <Radio value="0">是</Radio>
                          <Radio value="1">否</Radio>
                      </Radio.Group>
                  </Form.Item>
              </Col>
              <Col span={12}>
                  <Form.Item name="path" label="路由地址" rules={[{ required: true, message: '请输入路由地址' }]}>
                    <Input placeholder="请输入路由地址" />
                  </Form.Item>
              </Col>
          </Row>
          <Row gutter={16}>
              <Col span={12}>
                  <Form.Item name="component" label="组件路径">
                    <Input placeholder="请输入组件路径" />
                  </Form.Item>
              </Col>
              <Col span={12}>
                  <Form.Item name="perms" label="权限字符">
                    <Input placeholder="请输入权限字符" />
                  </Form.Item>
              </Col>
          </Row>
          <Row gutter={16}>
              <Col span={12}>
                   <Form.Item name="visible" label="显示状态" initialValue="0">
                      <Radio.Group>
                          {sysShowHide.map(dict => (
                              <Radio key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Radio>
                          ))}
                      </Radio.Group>
                  </Form.Item>
              </Col>
              <Col span={12}>
                   <Form.Item name="status" label="菜单状态" initialValue="0">
                      <Radio.Group>
                          {sysNormalDisable.map(dict => (
                              <Radio key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Radio>
                          ))}
                      </Radio.Group>
                  </Form.Item>
              </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};

export default Menu;