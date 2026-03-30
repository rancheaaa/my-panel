import React, { useState, useEffect, useRef } from 'react';
import { Table, Card, Button, Space, Form, Input, Modal, message, Popconfirm, Tooltip, Select, Tag, Dropdown, Row, Col, Upload } from 'antd';
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined, ExportOutlined, ImportOutlined, FileTextOutlined, DownOutlined, EyeOutlined, ColumnHeightOutlined } from '@ant-design/icons';
import { ResizableTitle } from '../../../components/ResizableTable';
import Editor, { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import { listConfig, getConfig, addConfig, updateConfig, delConfig, exportConfig, importConfig, previewConfig } from '../../../api/rc/config';

// 强制 loader 使用本地安装的 monaco-editor 实例
loader.config({ monaco });

import { listEnv } from '../../../api/rc/env';
import { listProject } from '../../../api/rc/project';

const { Option } = Select;

const ConfigCenter = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [tableSize, setTableSize] = useState('large');
  const [queryParams, setQueryParams] = useState({
    pageNum: 1,
    pageSize: 10,
    envId: undefined,
    projectId: undefined,
    configKey: undefined
  });

  const [envs, setEnvs] = useState([]);
  const [projects, setProjects] = useState([]);

  const [form] = Form.useForm();
  
  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增配置');
  const [modalForm] = Form.useForm();
  const [currentId, setCurrentId] = useState(null);

  // Export Modal State
  const [isExportModalOpen, setIsExportModalOpen] = useState(false);
  const [exportFormat, setExportFormat] = useState('excel');
  const [exportForm] = Form.useForm();

  // Import Modal State
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [importFileList, setImportFileList] = useState([]);
  const [importContent, setImportContent] = useState('');
  const [importLanguage, setImportLanguage] = useState('properties');
  const [importForm] = Form.useForm();

  // Preview Modal State
  const [isPreviewModalOpen, setIsPreviewModalOpen] = useState(false);
  const [previewContent, setPreviewContent] = useState('');
  const [previewFormat, setPreviewFormat] = useState('yml');
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewForm] = Form.useForm();

  const editorRef = useRef(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listConfig(queryParams);
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

  const fetchEnvs = async () => {
    try {
      const res = await listEnv({ pageSize: 1000 });
      if (res.code === 200) {
        setEnvs(res.data.rows);
      }
    } catch (error) {
      console.error(error);
    }
  };

  const fetchProjects = async () => {
    try {
      const res = await listProject({ pageSize: 1000 });
      if (res.code === 200) {
        setProjects(res.data.rows);
      }
    } catch (error) {
      console.error(error);
    }
  };

  useEffect(() => {
    fetchData();
  }, [queryParams]);

  useEffect(() => {
    fetchEnvs();
    fetchProjects();
  }, []);

  const handleSearch = () => {
    form.validateFields().then(values => {
      setQueryParams(prev => ({
        ...prev,
        ...values,
        pageNum: 1
      }));
    });
  };

  const handleReset = () => {
    form.resetFields();
    setQueryParams(prev => ({
      ...prev,
      envId: undefined,
      projectId: undefined,
      configKey: undefined,
      pageNum: 1
    }));
  };

  const handleAdd = () => {
    setModalTitle('新增配置');
    setCurrentId(null);
    modalForm.resetFields();
    setIsModalOpen(true);
  };

  const handleEdit = async (record) => {
    setModalTitle('修改配置');
    setCurrentId(record.id);
    try {
        const res = await getConfig(record.id);
        if (res.code === 200) {
            modalForm.setFieldsValue(res.data);
            setIsModalOpen(true);
        }
    } catch (error) {
        console.error(error);
        message.error('获取详情失败');
    }
  };

  const handleDelete = async (id) => {
    const ids = id || selectedRowKeys;
    if (!ids || ids.length === 0) {
      message.warning('请选择要删除的数据');
      return;
    }
    try {
      await delConfig(ids);
      message.success('删除成功');
      setSelectedRowKeys([]);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('删除失败');
    }
  };

  const handleExport = (format = 'excel') => {
    setExportFormat(format);
    // 预填当前已选择的查询条件
    exportForm.setFieldsValue({
      envId: queryParams.envId,
      projectId: queryParams.projectId
    });
    setIsExportModalOpen(true);
  };

  const confirmExport = async () => {
    try {
      const values = await exportForm.validateFields();
      const format = exportFormat;
      const exportParams = {
        ...queryParams,
        envId: values.envId,
        projectId: values.projectId,
        exportFormat: format
      };

      const response = await exportConfig(exportParams);
      
      const env = envs.find(e => e.id === values.envId);
      const project = projects.find(p => p.id === values.projectId);
      const envName = env ? env.envName : 'unknown_env';
      const projectName = project ? project.projectName : 'unknown_project';
      const uuid = Math.random().toString(36).substring(2, 10);
      const fileName = `${envName}_${projectName}_${uuid}`;

      let contentType = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
      let extension = 'xlsx';

      switch (format) {
        case 'txt':
          contentType = 'text/plain';
          extension = 'txt';
          break;
        case 'properties':
          contentType = 'text/plain';
          extension = 'properties';
          break;
        case 'yml':
        case 'yaml':
          contentType = 'application/x-yaml';
          extension = 'yml';
          break;
        case 'json':
          contentType = 'application/json';
          extension = 'json';
          break;
      }

      const blob = new Blob([response], { type: contentType });
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = `${fileName}.${extension}`;
      link.click();
      window.URL.revokeObjectURL(link.href);
      setIsExportModalOpen(false);
    } catch (error) {
      console.error(error);
      if (error.errorFields) {
        return; // 表单校验未通过
      }
      message.error('导出失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await modalForm.validateFields();
      if (currentId) {
        await updateConfig({ ...values, id: currentId });
        message.success('更新成功');
      } else {
        await addConfig(values);
        message.success('新增成功');
      }
      setIsModalOpen(false);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('操作失败');
    }
  };

  const handleImport = () => {
    setIsImportModalOpen(true);
    setImportFileList([]);
    setImportContent('');
    importForm.resetFields();
    // 延迟设置默认语言，确保编辑器已加载
    setTimeout(() => {
      setImportLanguage('properties');
    }, 100);
  };

  const handleBeforeUpload = (file) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target.result;
      setImportContent(content);
      const extension = file.name.split('.').pop().toLowerCase();
      if (extension === 'yml' || extension === 'yaml') {
        setImportLanguage('yaml');
      } else if (extension === 'properties') {
        setImportLanguage('properties');
      } else {
        setImportLanguage('ini'); // 回退到普通文本或类似格式
      }
    };
    reader.readAsText(file);
    setImportFileList([file]);
    return false;
  };

  const handleImportOk = async () => {
    try {
      const values = await importForm.validateFields();
      if (!importContent) {
        message.warning('请上传文件或输入配置内容');
        return;
      }
      await importConfig({
        ...values,
        content: importContent,
        format: importLanguage
      });
      message.success('导入成功');
      setIsImportModalOpen(false);
      fetchData();
    } catch (error) {
      console.error(error);
      message.error('导入失败');
    }
  };

  const handlePreview = () => {
    // 预填当前已选择的查询条件
    const envId = queryParams.envId;
    const projectId = queryParams.projectId;
    
    previewForm.setFieldsValue({
      envId: envId,
      projectId: projectId,
      exportFormat: previewFormat
    });
    setIsPreviewModalOpen(true);
    setPreviewContent(''); // 清空旧内容
  };

  const fetchPreviewContent = async (envId, projectId, format) => {
    if (!envId || !projectId) {
      message.warning('请选择环境和应用');
      return;
    }
    setPreviewLoading(true);
    setPreviewContent(''); // 开始请求前清空内容
    try {
      console.log('Fetching preview with:', { envId, projectId, format });
      const res = await previewConfig({
        envId,
        projectId,
        exportFormat: format
      });
      console.log('Preview response:', res);
      if (res.code === 200) {
        const content = res.data || '';
        console.log('Preview content length:', content.length);
        if (content.length > 0) {
          console.log('Preview content first 50 chars:', content.substring(0, 50));
        }
        setPreviewContent(content);
        if (!content) {
          if (res.msg && res.msg !== '操作成功' && res.msg !== '查询成功') {
            message.info(res.msg);
          } else {
            message.info('该环境下未找到配置数据');
          }
        }
      } else {
        message.error(res.msg || '预览失败');
        setPreviewContent('');
      }
    } catch (error) {
      console.error('Preview error:', error);
      message.error('预览接口调用失败');
    } finally {
      setPreviewLoading(false);
    }
  };

  const handlePreviewFormChange = (changedValues) => {
    if (changedValues.exportFormat) {
      setPreviewFormat(changedValues.exportFormat);
    }
  };

  const handleDoPreview = async () => {
    try {
      const values = await previewForm.validateFields();
      fetchPreviewContent(values.envId, values.projectId, values.exportFormat);
    } catch (error) {
      // 验证失败
    }
  };

  const [columns, setColumns] = useState([
    { title: '配置ID', dataIndex: 'id', key: 'id', align: 'center', width: 80 },
    { 
      title: '环境', 
      dataIndex: 'envId', 
      key: 'envId', 
      align: 'center',
      width: 100,
      ellipsis: true,
      render: (envId) => envs.find(e => e.id === envId)?.envName || envId
    },
    { 
      title: '应用', 
      dataIndex: 'projectId', 
      key: 'projectId', 
      align: 'center',
      width: 150,
      ellipsis: true,
      render: (projectId) => projects.find(p => p.id === projectId)?.projectName || projectId
    },
    { title: '配置键', dataIndex: 'configKey', key: 'configKey', align: 'center', width: 200, ellipsis: true },
    { title: '配置值', dataIndex: 'configValue', key: 'configValue', align: 'center', width: 250, ellipsis: true },
    { title: '配置描述', dataIndex: 'configDesc', key: 'configDesc', align: 'center', width: 200, ellipsis: true },
    { 
      title: '来源', 
      dataIndex: 'source', 
      key: 'source', 
      align: 'center',
      width: 100,
      render: (source) => source === '1' ? '批量导入' : '手工新增'
    },
    { title: '更新人', dataIndex: 'updateBy', key: 'updateBy', align: 'center', width: 100, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', align: 'center', width: 180 },
    { title: '创建人', dataIndex: 'createBy', key: 'createBy', align: 'center', width: 100, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', align: 'center', width: 180 },
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
        <Form form={form} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
          <Row gutter={[24, 16]} style={{ width: '100%' }}>
            <Col span={6}>
              <Form.Item name="envId" label="环境">
                <Select placeholder="请选择环境" allowClear>
                  {envs.map(env => (
                    <Option key={env.id} value={env.id}>{env.envName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="projectId" label="应用">
                <Select placeholder="请选择应用" allowClear>
                  {projects.map(app => (
                    <Option key={app.id} value={app.id}>{app.projectName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="configKey" label="配置键">
                <Input placeholder="请输入配置键" allowClear />
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
        <div className="table-toolbar" style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
          <Space size="small" style={{ flexWrap: 'wrap' }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
            <Button 
              danger 
              icon={<DeleteOutlined />} 
              disabled={selectedRowKeys.length === 0}
              onClick={() => handleDelete()}
            >
              批量删除
            </Button>
            <Dropdown
              menu={{
                items: [
                  { key: 'excel', label: '导出 Excel', onClick: () => handleExport('excel') },
                  { key: 'txt', label: '导出 TXT', onClick: () => handleExport('txt') },
                  { key: 'properties', label: '导出 Properties', onClick: () => handleExport('properties') },
                  { key: 'yml', label: '导出 YAML', onClick: () => handleExport('yml') },
                  { key: 'json', label: '导出 JSON', onClick: () => handleExport('json') },
                ],
              }}
            >
              <Button icon={<ExportOutlined />}>
                导出 <DownOutlined />
              </Button>
            </Dropdown>
            <Button icon={<ImportOutlined />} onClick={handleImport}>批量导入</Button>
            <Button icon={<EyeOutlined />} onClick={handlePreview}>预览</Button>
          </Space>
          <Space size="small">
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
        destroyOnClose
        width={600}
      >
        <Form
          form={modalForm}
          layout="vertical"
        >
          <Form.Item
            name="envId"
            label="环境"
            rules={[{ required: true, message: '请选择环境' }]}
          >
            <Select placeholder="请选择环境">
              {envs.map(env => (
                <Option key={env.id} value={env.id}>{env.envName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="projectId"
            label="应用"
            rules={[{ required: true, message: '请选择应用' }]}
          >
            <Select placeholder="请选择应用">
              {projects.map(project => (
                <Option key={project.id} value={project.id}>{project.projectName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="configKey"
            label="配置键"
            rules={[{ required: true, message: '请输入配置键' }]}
          >
            <Input placeholder="请输入配置键" />
          </Form.Item>
          <Form.Item
            name="configValue"
            label="配置值"
            rules={[{ required: true, message: '请输入配置值' }]}
          >
            <Input.TextArea placeholder="请输入配置值" rows={1} />
          </Form.Item>
          <Form.Item
            name="configDesc"
            label="配置描述"
          >
            <Input.TextArea placeholder="请输入配置描述" rows={8} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`导出确认 - ${exportFormat.toUpperCase()}`}
        open={isExportModalOpen}
        onOk={confirmExport}
        onCancel={() => setIsExportModalOpen(false)}
        destroyOnClose
      >
        <Form form={exportForm} layout="vertical">
          <Form.Item
            name="envId"
            label="环境"
            rules={[{ required: true, message: '请选择环境' }]}
          >
            <Select placeholder="请选择环境">
              {envs.map(env => (
                <Option key={env.id} value={env.id}>{env.envName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="projectId"
            label="应用"
            rules={[{ required: true, message: '请选择应用' }]}
          >
            <Select placeholder="请选择应用">
              {projects.map(project => (
                <Option key={project.id} value={project.id}>{project.projectName}</Option>
              ))}
            </Select>
          </Form.Item>
          <p style={{ color: '#666', fontSize: '12px' }}>
            提示：导出文件名将根据选择的环境和应用自动生成。
          </p>
        </Form>
      </Modal>

      <Modal
        title="批量导入配置"
        open={isImportModalOpen}
        onOk={handleImportOk}
        onCancel={() => setIsImportModalOpen(false)}
        width={800}
        destroyOnClose
      >
        <Form form={importForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="envId"
                label="环境"
                rules={[{ required: true, message: '请选择环境' }]}
              >
                <Select placeholder="请选择环境">
                  {envs.map(env => (
                    <Option key={env.id} value={env.id}>{env.envName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="projectId"
                label="应用"
                rules={[{ required: true, message: '请选择应用' }]}
              >
                <Select placeholder="请选择应用">
                  {projects.map(project => (
                    <Option key={project.id} value={project.id}>{project.projectName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="上传配置文件">
            <Upload
              accept=".properties,.yml,.yaml"
              beforeUpload={handleBeforeUpload}
              fileList={importFileList}
              onRemove={() => {
                setImportFileList([]);
                setImportContent('');
              }}
            >
              <Button icon={<FileTextOutlined />}>选择文件 (.properties, .yml, .yaml)</Button>
            </Upload>
          </Form.Item>
          <Form.Item label="内容预览">
            <div style={{ border: '1px solid #d9d9d9', borderRadius: '4px', overflow: 'hidden' }}>
              {isImportModalOpen && (
                <Editor
                  height="300px"
                  language={importLanguage}
                  value={importContent}
                  loading={<div style={{ padding: '20px', textAlign: 'center' }}>编辑器加载中...</div>}
                  options={{
                    readOnly: true,
                    minimap: { enabled: false },
                    scrollBeyondLastLine: false,
                    fontSize: 14,
                    domReadOnly: true,
                    automaticLayout: true,
                  }}
                />
              )}
            </div>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="内容预览"
        open={isPreviewModalOpen}
        onCancel={() => setIsPreviewModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsPreviewModalOpen(false)}>关闭</Button>,
          <Button key="export" type="primary" onClick={() => {
            setIsPreviewModalOpen(false);
            handleExport(previewFormat);
          }}>去导出</Button>
        ]}
        width={900}
        destroyOnClose
      >
        <Form form={previewForm} layout="inline" onValuesChange={handlePreviewFormChange} style={{ marginBottom: 16, display: 'flex', flexWrap: 'nowrap', alignItems: 'flex-start' }}>
          <Form.Item name="envId" label="环境" rules={[{ required: true, message: '请选择环境' }]} style={{ marginRight: 8 }}>
            <Select placeholder="环境" style={{ width: 130 }}>
              {envs.map(env => (
                <Option key={env.id} value={env.id}>{env.envName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="projectId" label="应用" rules={[{ required: true, message: '请选择应用' }]} style={{ marginRight: 8 }}>
            <Select placeholder="应用" style={{ width: 150 }}>
              {projects.map(project => (
                <Option key={project.id} value={project.id}>{project.projectName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="exportFormat" label="格式" style={{ marginRight: 8 }}>
            <Select style={{ width: 110 }}>
              <Option value="yml">YAML</Option>
              <Option value="json">JSON</Option>
              <Option value="properties">Properties</Option>
              <Option value="txt">TXT</Option>
            </Select>
          </Form.Item>
          <Form.Item style={{ marginRight: 0 }}>
            <Button type="primary" onClick={handleDoPreview} loading={previewLoading}>预览</Button>
          </Form.Item>
        </Form>
        <div style={{ border: '1px solid #d9d9d9', borderRadius: '4px', overflow: 'hidden', position: 'relative', height: '500px' }}>
          {previewLoading && (
            <div style={{
              position: 'absolute',
              top: 0, left: 0, right: 0, bottom: 0,
              background: 'rgba(255,255,255,0.7)',
              display: 'flex', justifyContent: 'center', alignItems: 'center',
              zIndex: 10
            }}>
              加载中...
            </div>
          )}
          {!previewLoading && !previewContent && (
            <div style={{ 
              position: 'absolute',
              top: 0, left: 0, right: 0, bottom: 0,
              display: 'flex', justifyContent: 'center', alignItems: 'center',
              flexDirection: 'column',
              color: '#999',
              zIndex: 5,
              padding: '20px',
              textAlign: 'center'
            }}>
              <div>所选环境下未找到配置数据</div>
              <div style={{ fontSize: '12px', marginTop: '8px' }}>请检查环境和应用是否选择正确，或该项目下是否有配置项</div>
            </div>
          )}
          <Editor
            height="100%"
            language={previewFormat === 'yml' ? 'yaml' : previewFormat}
            value={previewContent}
            loading={<div style={{ padding: '20px', textAlign: 'center' }}>编辑器加载中...</div>}
            options={{
              readOnly: true,
              minimap: { enabled: false },
              scrollBeyondLastLine: false,
              fontSize: 14,
              automaticLayout: true,
            }}
          />
        </div>
      </Modal>
    </div>
  );
};

export default ConfigCenter;
