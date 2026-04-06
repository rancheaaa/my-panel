import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Table, Card, Button, Space, Form, Input, Select, message, Popconfirm, Tag, Switch, Modal, Radio, Row, Col, Descriptions, Tabs, InputNumber, Tooltip, Dropdown, DatePicker, Divider, AutoComplete } from 'antd';
import zhCN from 'antd/es/locale/zh_CN';
import { SearchOutlined, ReloadOutlined, DeleteOutlined, PlusOutlined, EditOutlined, PlayCircleOutlined, EyeOutlined, FileTextOutlined, ExportOutlined, SettingOutlined, ColumnHeightOutlined, DownOutlined, UpOutlined, DownloadOutlined, MinusCircleOutlined, PlusCircleOutlined } from '@ant-design/icons';
import { listJob, getJob, addJob, updateJob, delJob, changeJobStatus, runJob, exportJob, getJobGroups } from '../../../api/monitor/job';
import { listJobLog, delJobLog, cleanJobLog, exportJobLog } from '../../../api/monitor/jobLog';
import { getDicts } from '../../../api/dict/data';
import { ResizableTitle } from '../../../components/ResizableTable';
import './index.scss';

import Editor from '@monaco-editor/react';

const { Option } = Select;

// 受控的Monaco Editor组件
const ControlledEditor = ({ value, onChange }) => {
    const [editorValue, setEditorValue] = useState(value || '');
    const [isFocused, setIsFocused] = useState(false);
    
    // 当外部value变化且编辑器未聚焦时，更新编辑器内容
    useEffect(() => {
        if (!isFocused && value !== undefined && value !== null && value !== editorValue) {
            setEditorValue(value);
        }
    }, [value, editorValue, isFocused]);
    
    const handleEditorChange = (newValue) => {
        setEditorValue(newValue);
        onChange?.(newValue);
    };

    const handleEditorFocus = () => {
        setIsFocused(true);
    };

    const handleEditorBlur = () => {
        setIsFocused(false);
    };
    
    return (
        <div style={{ border: '1px solid #d9d9d9', borderRadius: '4px', overflow: 'hidden' }}>
            <Editor
                height="150px"
                defaultLanguage="json"
                theme="vs-light"
                value={editorValue}
                onChange={handleEditorChange}
                onDidFocus={handleEditorFocus}
                onDidBlur={handleEditorBlur}
                options={{
                    minimap: { enabled: false },
                    fontSize: 12,
                    lineNumbers: 'on',
                    scrollBeyondLastLine: false,
                    wordWrap: 'on'
                }}
            />
        </div>
    );
};

// --- 新的 Cron 表达式生成组件 ---
const CronGenerator = ({ value, onChange, onBlur }) => {
    const [visible, setVisible] = useState(false);
    const [cronValues, setCronValues] = useState({
        second: '*',
        minute: '*',
        hour: '*',
        day: '*',
        month: '*',
        week: '?',
        year: '*'
    });

    // 解析初始值
    useEffect(() => {
        if (value && typeof value === 'string') {
            const parts = value.split(' ');
            if (parts.length >= 6) {
                setCronValues({
                    second: parts[0] || '*',
                    minute: parts[1] || '*',
                    hour: parts[2] || '*',
                    day: parts[3] || '*',
                    month: parts[4] || '*',
                    week: parts[5] || '?',
                    year: parts[6] || '*'
                });
            }
        }
    }, [value, visible]);

    const handleConfirm = () => {
        const { second, minute, hour, day, month, week, year } = cronValues;
        const newValue = `${second} ${minute} ${hour} ${day} ${month} ${week}${year !== '*' ? ' ' + year : ''}`;
        onChange?.(newValue);
        setVisible(false);
    };

    const updateCronPart = (part, val) => {
        setCronValues(prev => ({ ...prev, [part]: val }));
    };

    // 渲染各个部分的配置界面
    const renderCronPart = (part, label, min, max, allowQuestion = false) => {
        const val = cronValues[part];
        const type = val === '*' ? 'all' : (val === '?' ? 'none' : (val.includes('-') ? 'range' : (val.includes('/') ? 'step' : 'specific')));
        
        return (
            <div style={{ padding: '16px 0' }}>
                <Radio.Group 
                    value={type} 
                    onChange={(e) => {
                        const t = e.target.value;
                        if (t === 'all') updateCronPart(part, '*');
                        else if (t === 'none') updateCronPart(part, '?');
                        else if (t === 'range') updateCronPart(part, `${min}-${min + 1}`);
                        else if (t === 'step') updateCronPart(part, `${min}/1`);
                        else updateCronPart(part, `${min}`);
                    }}
                >
                    <Space direction="vertical">
                        <Radio value="all">每{label} ( * )</Radio>
                        {allowQuestion && <Radio value="none">不指定 ( ? )</Radio>}
                        <Radio value="range">
                            周期从 <InputNumber size="small" min={min} max={max} value={val.includes('-') ? parseInt(val.split('-')[0]) : min} onChange={v => updateCronPart(part, `${v}-${val.includes('-') ? val.split('-')[1] : v + 1}`)} /> 
                            到 <InputNumber size="small" min={min} max={max} value={val.includes('-') ? parseInt(val.split('-')[1]) : min + 1} onChange={v => updateCronPart(part, `${val.includes('-') ? val.split('-')[0] : min}-${v}`)} /> {label}
                        </Radio>
                        <Radio value="step">
                            从 <InputNumber size="small" min={min} max={max} value={val.includes('/') ? parseInt(val.split('/')[0]) : min} onChange={v => updateCronPart(part, `${v}/${val.includes('/') ? val.split('/')[1] : 1}`)} /> {label}开始，
                            每隔 <InputNumber size="small" min={1} max={max} value={val.includes('/') ? parseInt(val.split('/')[1]) : 1} onChange={v => updateCronPart(part, `${val.includes('/') ? val.split('/')[0] : min}/${v}`)} /> {label}执行一次
                        </Radio>
                        <Radio value="specific">
                            指定 {label} (可多选)
                            <div style={{ marginTop: 8 }}>
                                <Select
                                    mode="multiple"
                                    style={{ width: '100%', minWidth: 400 }}
                                    placeholder="请选择"
                                    value={type === 'specific' ? val.split(',') : []}
                                    onChange={v => updateCronPart(part, v.length > 0 ? v.join(',') : '*')}
                                >
                                    {Array.from({ length: max - min + 1 }, (_, i) => i + min).map(i => (
                                        <Option key={i} value={String(i)}>{i < 10 ? '0' + i : i}</Option>
                                    ))}
                                </Select>
                            </div>
                        </Radio>
                    </Space>
                </Radio.Group>
            </div>
        );
    };

    const items = [
        { key: 'second', label: '秒', children: renderCronPart('second', '秒', 0, 59) },
        { key: 'minute', label: '分', children: renderCronPart('minute', '分', 0, 59) },
        { key: 'hour', label: '时', children: renderCronPart('hour', '时', 0, 23) },
        { key: 'day', label: '日', children: renderCronPart('day', '日', 1, 31, true) },
        { key: 'month', label: '月', children: renderCronPart('month', '月', 1, 12) },
        { key: 'week', label: '周', children: renderCronPart('week', '周', 1, 7, true) },
        { key: 'year', label: '年', children: renderCronPart('year', '年', 2024, 2099) },
    ];

    return (
        <>
            <Input 
                value={value} 
                placeholder="请输入 Cron 表达式"
                onChange={e => onChange?.(e.target.value)}
                onBlur={onBlur}
                suffix={<SettingOutlined style={{ cursor: 'pointer', color: '#1890ff' }} onClick={() => setVisible(true)} />}
            />
            <Modal
                title="Cron 表达式生成器"
                open={visible}
                onOk={handleConfirm}
                onCancel={() => setVisible(false)}
                width={650}
                destroyOnClose
                centered
            >
                <div style={{ marginBottom: 16, padding: '12px', background: '#f5f5f5', borderRadius: '4px' }}>
                    <strong>当前表达式：</strong>
                    <code style={{ color: '#1890ff', fontSize: '16px', marginLeft: 8 }}>
                        {`${cronValues.second} ${cronValues.minute} ${cronValues.hour} ${cronValues.day} ${cronValues.month} ${cronValues.week}${cronValues.year !== '*' ? ' ' + cronValues.year : ''}`}
                    </code>
                </div>
                <Tabs items={items} type="card" />
            </Modal>
        </>
    );
};

// --- 调度日志组件 ---
const JobLog = ({ visible, onCancel, jobName: defaultJobName, jobGroup: defaultJobGroup }) => {
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [total, setTotal] = useState(0);
    const [selectedRowKeys, setSelectedRowKeys] = useState([]);
    const [tableSize, setTableSize] = useState('middle');
    const [expand, setExpand] = useState(true);
    const [queryParams, setQueryParams] = useState({
      pageNum: 1,
      pageSize: 10,
      jobName: defaultJobName,
      jobGroup: defaultJobGroup,
      status: undefined,
      startTimeStart: undefined,
      startTimeEnd: undefined,
      endTimeStart: undefined,
      endTimeEnd: undefined
    });
  
    const [sysJobGroup, setSysJobGroup] = useState([]);
    const [jobGroupOptions, setJobGroupOptions] = useState([]);
    const [sysCommonStatus, setSysCommonStatus] = useState([]);
    const [form] = Form.useForm();
    
    // Detail Modal
    const [detailOpen, setDetailOpen] = useState(false);
    const [currentLog, setCurrentLog] = useState({});
  
    const loadJobGroupOptions = async () => {
      try {
        const res = await getJobGroups('');
        if (res.code === 200) {
          setJobGroupOptions(res.data.map(group => ({
            value: group,
            label: group
          })));
        }
      } catch (error) {
        console.error('加载任务组名失败:', error);
      }
    };

    const handleJobGroupSearch = async (value) => {
      try {
        const res = await getJobGroups(value);
        if (res.code === 200) {
          setJobGroupOptions(res.data.map(group => ({
            value: group,
            label: group
          })));
        }
      } catch (error) {
        console.error('搜索任务组名失败:', error);
      }
    };
  
    useEffect(() => {
      if (visible) {
        getDicts('sys_job_group').then(res => res.code === 200 && setSysJobGroup(res.data));
        getDicts('sys_common_status').then(res => res.code === 200 && setSysCommonStatus(res.data));
        loadJobGroupOptions();
        
        // Reset form with default values if provided
        form.setFieldsValue({
          jobName: defaultJobName,
          jobGroup: defaultJobGroup
        });
        
        setQueryParams(prev => ({
          ...prev,
          jobName: defaultJobName,
          jobGroup: defaultJobGroup,
          pageNum: 1
        }));
      }
    }, [visible, defaultJobName, defaultJobGroup]);
  
    useEffect(() => {
      if (visible) {
        fetchData();
      }
    }, [queryParams, visible]);
  
    const fetchData = async () => {
      setLoading(true);
      try {
        const { createTime, ...params } = queryParams;
        if (createTime) {
          params['params[beginTime]'] = createTime[0].format('YYYY-MM-DD HH:mm:ss');
          params['params[endTime]'] = createTime[1].format('YYYY-MM-DD HH:mm:ss');
        }
        const res = await listJobLog(params);
        if (res.code === 200) {
          setData(res.data.rows);
          setTotal(res.data.total);
        }
      } catch (_error) {
        message.error('获取调度日志失败');
      } finally {
        setLoading(false);
      }
    };
  
    const handleSearch = () => {
      form.validateFields().then(values => {
        const { startTimeRange, endTimeRange, ...rest } = values;
        setQueryParams({
          ...queryParams,
          ...rest,
          startTimeStart: startTimeRange ? startTimeRange[0].format('YYYY-MM-DD HH:mm:ss') : undefined,
          startTimeEnd: startTimeRange ? startTimeRange[1].format('YYYY-MM-DD HH:mm:ss') : undefined,
          endTimeStart: endTimeRange ? endTimeRange[0].format('YYYY-MM-DD HH:mm:ss') : undefined,
          endTimeEnd: endTimeRange ? endTimeRange[1].format('YYYY-MM-DD HH:mm:ss') : undefined,
          pageNum: 1
        });
      });
    };
  
    const handleReset = () => {
      form.resetFields();
      setQueryParams({
        pageNum: 1,
        pageSize: 10,
        jobName: undefined,
        jobGroup: undefined,
        status: undefined,
        startTimeStart: undefined,
        startTimeEnd: undefined,
        endTimeStart: undefined,
        endTimeEnd: undefined
      });
    };
  
    const handleDelete = async (ids) => {
      try {
        const res = await delJobLog(ids);
        if (res.code === 200) {
          message.success('删除成功');
          fetchData();
          setSelectedRowKeys([]);
        }
      } catch (_error) {
        message.error('删除失败');
      }
    };
  
    const handleClean = async () => {
      try {
        const res = await cleanJobLog();
        if (res.code === 200) {
          message.success('清空成功');
          fetchData();
        }
      } catch (_error) {
        message.error('清空失败');
      }
    };
  
    const handleExport = async () => {
      try {
        const { createTime, ...params } = queryParams;
        if (createTime) {
          params['params[beginTime]'] = createTime[0].format('YYYY-MM-DD HH:mm:ss');
          params['params[endTime]'] = createTime[1].format('YYYY-MM-DD HH:mm:ss');
        }
        const res = await exportJobLog(params);
        const url = window.URL.createObjectURL(new Blob([res]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `job_log_${new Date().getTime()}.xlsx`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        message.success('导出成功');
      } catch (_error) {
        message.error('导出失败');
      }
    };
    
    const showDetail = (record) => {
        setCurrentLog(record);
        setDetailOpen(true);
    };
  
    const columns = [
      {
        title: '日志编号',
        dataIndex: 'jobLogId',
        key: 'jobLogId',
        width: 100,
      },
      {
        title: '任务名称',
        dataIndex: 'jobName',
        key: 'jobName',
        width: 150,
        ellipsis: true,
      },
      {
        title: '任务分组',
        dataIndex: 'jobGroup',
        key: 'jobGroup',
        width: 100,
        render: (text) => {
          const dict = sysJobGroup.find(d => d.dictValue === text);
          return dict ? <Tag>{dict.dictLabel}</Tag> : text;
        }
      },
      {
        title: '调用目标',
        dataIndex: 'invokeTarget',
        key: 'invokeTarget',
        width: 250,
        ellipsis: true,
      },
      {
        title: '日志信息',
        dataIndex: 'jobMessage',
        key: 'jobMessage',
        width: 200,
        ellipsis: true,
      },
      {
        title: '触发类型',
        dataIndex: 'triggerType',
        key: 'triggerType',
        width: 100,
        render: (triggerType) => {
          const typeMap = {
            '0': <Tag color="blue">定时触发</Tag>,
            '1': <Tag color="orange">手动触发</Tag>
          };
          return typeMap[triggerType] || triggerType;
        }
      },
      {
        title: '执行状态',
        dataIndex: 'status',
        key: 'status',
        width: 100,
        render: (status) => {
           const dict = sysCommonStatus.find(d => d.dictValue === status);
           return dict ? (
               <Tag color={status === '0' ? 'success' : 'error'}>{dict.dictLabel}</Tag>
           ) : status;
        }
      },
      {
        title: '开始时间',
        dataIndex: 'startTime',
        key: 'startTime',
        width: 160,
      },
      {
        title: '结束时间',
        dataIndex: 'endTime',
        key: 'endTime',
        width: 160,
      },
      {
        title: '异常信息',
        dataIndex: 'exceptionInfo',
        key: 'exceptionInfo',
        width: 200,
        ellipsis: true,
      },
      {
        title: '操作',
        key: 'action',
        width: 180,
        fixed: 'right',
        render: (_, record) => (
          <Space size="middle">
            <Tooltip title="详情">
              <Button 
                  type="text" 
                  icon={<EyeOutlined />} 
                  onClick={() => showDetail(record)}
                  size="small"
              />
            </Tooltip>
          </Space>
        ),
      },
    ];
  
    return (
      <Modal
        title="调度日志"
        open={visible}
        onCancel={onCancel}
        width="90%"
        footer={null}
        destroyOnClose
        style={{ top: 20 }}
      >
        <div className="table-search" style={{ marginBottom: 16 }}>
          <Form form={form} layout="inline" component="div" labelCol={{ span: 6 }} wrapperCol={{ span: 18 }} style={{ width: '100%' }}>
            <Row gutter={[24, 16]} style={{ width: '100%' }}>
              <Col span={6}>
                <Form.Item name="jobName" label="任务名称">
                  <Input placeholder="请输入任务名称" allowClear />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item name="jobGroup" label="任务组名">
                   <AutoComplete
                      placeholder="请选择或输入任务组名"
                      allowClear
                      options={jobGroupOptions}
                      onSearch={handleJobGroupSearch}
                      filterOption={false}
                   />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item name="status" label="执行状态">
                   <Select placeholder="请选择" allowClear>
                      {sysCommonStatus.map(dict => (
                          <Option key={dict.dictValue} value={dict.dictValue}>{dict.dictLabel}</Option>
                      ))}
                   </Select>
                </Form.Item>
              </Col>
              {expand && (
                <>
                  <Col span={6}>
                    <Form.Item name="startTimeRange" label="开始时间">
                       <DatePicker.RangePicker 
                         style={{ width: '100%' }} 
                         showTime 
                         format="YYYY-MM-DD HH:mm:ss"
                         locale={zhCN}
                         placeholder={['开始时间起', '开始时间止']}
                       />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item name="endTimeRange" label="结束时间">
                       <DatePicker.RangePicker 
                         style={{ width: '100%' }} 
                         showTime 
                         format="YYYY-MM-DD HH:mm:ss"
                         locale={zhCN}
                         placeholder={['结束时间起', '结束时间止']}
                       />
                    </Form.Item>
                  </Col>
                </>
              )}
              <Col span={24} style={{ textAlign: 'right', marginTop: '8px' }}>
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
        </div>
  
        <div className="table-toolbar">
          <Space size="large">
            <Popconfirm
               title="确定删除选中日志吗？"
               onConfirm={() => handleDelete(selectedRowKeys)}
               disabled={selectedRowKeys.length === 0}
            >
               <Button danger icon={<DeleteOutlined />} disabled={selectedRowKeys.length === 0}>删除</Button>
            </Popconfirm>
            <Popconfirm
               title="确定清空所有调度日志吗？"
               onConfirm={handleClean}
            >
               <Button danger icon={<DeleteOutlined />}>清空</Button>
            </Popconfirm>
            <Button icon={<DownloadOutlined />} onClick={handleExport}>导出</Button>
          </Space>
          <div style={{ flex: 1 }}></div>
          <Space size="large">
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
          columns={columns}
          dataSource={data}
          rowKey="jobLogId"
          loading={loading}
          size={tableSize}
          scroll={{ x: 1060 }}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys
          }}
          pagination={{
            current: queryParams.pageNum,
            pageSize: queryParams.pageSize,
            total: total,
            showTotal: (total, range) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
                setQueryParams(prev => ({ ...prev, pageNum: page, pageSize }));
            },
            position: ['bottomRight'],
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100']
          }}
        />
        
        <Modal
            title="调度日志详情"
            open={detailOpen}
            onCancel={() => setDetailOpen(false)}
            width={700}
            centered
            footer={<Button onClick={() => setDetailOpen(false)}>关闭</Button>}
        >
            <Descriptions column={2} bordered size="small" layout="vertical">
                <Descriptions.Item label="日志序号">{currentLog.jobLogId}</Descriptions.Item>
                <Descriptions.Item label="任务名称">{currentLog.jobName}</Descriptions.Item>
                <Descriptions.Item label="任务分组">{currentLog.jobGroup}</Descriptions.Item>
                <Descriptions.Item label="开始时间">{currentLog.startTime}</Descriptions.Item>
                <Descriptions.Item label="结束时间">{currentLog.endTime}</Descriptions.Item>
                <Descriptions.Item label="调用方法">{currentLog.invokeTarget}</Descriptions.Item>
                <Descriptions.Item label="日志信息" span={2}>
                    <div style={{ whiteSpace: 'pre-wrap', maxHeight: '300px', overflowY: 'auto' }}>
                        {currentLog.jobMessage}
                    </div>
                </Descriptions.Item>
                <Descriptions.Item label="执行状态">
                    <Tag color={currentLog.status === '0' ? 'success' : 'error'}>
                        {currentLog.status === '0' ? '正常' : '失败'}
                    </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="异常信息" span={2}>
                    <Input.TextArea 
                        value={currentLog.exceptionInfo} 
                        readOnly 
                        rows={8}
                        style={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}
                    />
                </Descriptions.Item>
            </Descriptions>
        </Modal>
      </Modal>
    );
};

const Job = () => {
  const [form] = Form.useForm();
  const jobType = Form.useWatch('jobType', form);
  const [searchForm] = Form.useForm();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [queryParams, setQueryParams] = useState({ pageNum: 1, pageSize: 10 });
  const [sysJobGroup, setSysJobGroup] = useState([]);
  const [jobGroupOptions, setJobGroupOptions] = useState([]);
  const [sysJobStatus, setSysJobStatus] = useState([]);
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [detailOpen, setDetailOpen] = useState(false);
  const [currentJob, setCurrentJob] = useState({});
  const [logVisible, setLogVisible] = useState(false);
  const [logParams, setLogParams] = useState({});
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [tableSize, setTableSize] = useState('large');
  
  // HTTP接口配置状态
  const [httpPath, setHttpPath] = useState('');
  const [httpIps, setHttpIps] = useState(['']);
  
  // 脚本配置状态
  const [scriptName, setScriptName] = useState('');
  const [scriptType, setScriptType] = useState('');
  const [scriptContent, setScriptContent] = useState('');
  
  // 基础列配置
  const [columnWidths, setColumnWidths] = useState({
    jobId: 100,
    jobName: 200,
    jobType: 120,
    jobGroup: 120,
    invokeTarget: 250,
    cronExpression: 150,
    loadBalanceStrategy: 120,
    status: 80,
    createBy: 100,
    createTime: 160,
    updateBy: 100,
    updateTime: 160,
    remark: 400,
    action: 280
  });

  const columns = useMemo(() => [
    { title: '任务编号', dataIndex: 'jobId', align: 'center', width: columnWidths.jobId },
    { title: '任务名称', dataIndex: 'jobName', align: 'center', width: columnWidths.jobName, ellipsis: true },
    { 
      title: '任务类型', 
      dataIndex: 'jobType', 
      align: 'center', 
      width: columnWidths.jobType,
      render: (v) => {
        const typeMap = {
          1: <Tag color="green">内置方法</Tag>,
          2: <Tag color="blue">HTTP接口</Tag>,
          3: <Tag color="orange">脚本</Tag>
        };
        return typeMap[v] || v;
      },
      filters: [
        { text: '内置方法', value: 1 },
        { text: 'HTTP接口', value: 2 },
        { text: '脚本', value: 3 },
      ],
      onFilter: (value, record) => Number(record.jobType) === Number(value),
    },
    { title: '任务组名', dataIndex: 'jobGroup', align: 'center', width: columnWidths.jobGroup, render: (v) => sysJobGroup.find(d => d.dictValue === v)?.dictLabel || v },
    { title: '调用目标', dataIndex: 'invokeTarget', align: 'center', width: columnWidths.invokeTarget, ellipsis: true },
    { title: 'Cron表达式', dataIndex: 'cronExpression', align: 'center', width: columnWidths.cronExpression },
    { 
      title: '负载均衡策略', 
      dataIndex: 'loadBalanceStrategy', 
      align: 'center', 
      width: 120,
      render: (v) => {
        if (!v) return '-';
        const strategyMap = {
          'roundRobin': '轮询',
          'random': '随机',
          'weightedRoundRobin': '加权轮询',
          'weightedRandom': '加权随机',
          'leastConnections': '最少连接',
          'fastestResponse': '最快响应',
          'consistentHash': '一致性哈希',
          'zoneAware': '区域感知'
        };
        return strategyMap[v] || v;
      }
    },
    { title: '状态', dataIndex: 'status', align: 'center', width: columnWidths.status, render: (v, r) => <Switch checked={v === '0'} onChange={() => handleStatusChange(r)} /> },
    { title: '创建人', dataIndex: 'createBy', align: 'center', width: columnWidths.createBy },
    { title: '创建时间', dataIndex: 'createTime', align: 'center', width: columnWidths.createTime },
    { title: '更新人', dataIndex: 'updateBy', align: 'center', width: columnWidths.updateBy },
    { title: '更新时间', dataIndex: 'updateTime', align: 'center', width: columnWidths.updateTime },
    { title: '备注', dataIndex: 'remark', align: 'center', width: columnWidths.remark, ellipsis: true },
    {
      title: '操作',
      align: 'center',
      width: columnWidths.action,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" icon={<PlayCircleOutlined />} onClick={() => handleRun(r)} style={{ padding: '4px 8px' }}>执行一次</Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleUpdate(r)} style={{ padding: '4px 8px' }}>修改</Button>
          <Button type="link" icon={<EyeOutlined />} onClick={() => { setCurrentJob(r); setDetailOpen(true); }}>详情</Button>
          <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(r.jobId)}><Button type="link" danger icon={<DeleteOutlined />} style={{ padding: '4px 8px' }}>删除</Button></Popconfirm>
        </Space>
      ),
    },
  ], [sysJobGroup, columnWidths]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await listJob(queryParams);
      if (res.code === 200) {
        setData(res.data.rows);
        setTotal(res.data.total);
      }
     } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    getDicts('sys_job_group').then(res => res.code === 200 && setSysJobGroup(res.data));
    getDicts('sys_job_status').then(res => res.code === 200 && setSysJobStatus(res.data));
    loadJobGroupOptions();
  }, [queryParams]);

  const loadJobGroupOptions = async () => {
    try {
      const res = await getJobGroups('');
      if (res.code === 200) {
        setJobGroupOptions(res.data.map(group => ({
          value: group,
          label: group
        })));
      }
    } catch (error) {
      console.error('加载任务组名失败:', error);
    }
  };

  const handleJobGroupSearch = async (value) => {
    try {
      const res = await getJobGroups(value);
      if (res.code === 200) {
        setJobGroupOptions(res.data.map(group => ({
          value: group,
          label: group
        })));
      }
    } catch (error) {
      console.error('搜索任务组名失败:', error);
    }
  };

  const handleAdd = () => {
    form.resetFields();
    setHttpPath('');
    setHttpIps(['']);
    setScriptName('');
    setScriptType('');
    setScriptContent('');
    form.setFieldsValue({ 
      jobType: 1, 
      jobGroup: 'DEFAULT', 
      concurrent: '1', 
      status: '0', 
      misfirePolicy: '3' 
    });
    setTitle('新增任务');
    setOpen(true);
  };

  const handleUpdate = async (row) => {
    form.resetFields();
    const res = await getJob(row.jobId || selectedRowKeys[0]);
    if (res.code === 200) {
      // 解析httpUrl为path和ips
      let path = '';
      let ips = [''];
      if (res.data?.httpUrl) {
        try {
          const urls = res.data.httpUrl.split(',');
          if (urls.length > 0) {
            // 从第一个URL中提取路径
            const firstUrl = urls[0].trim();
            const urlObj = new URL(firstUrl);
            path = urlObj.pathname;
            
            // 提取所有IP:port
            ips = urls.map(urlItem => {
              const url = urlItem.trim();
              const urlObj = new URL(url);
              return `${urlObj.hostname}:${urlObj.port}`;
            });
          }
        } catch (error) {
          console.error('URL解析错误:', error);
          path = '';
          ips = [''];
        }
      }
      
      setHttpPath(path);
      setHttpIps(ips);
      
      // 设置脚本相关状态
      setScriptName(res.data?.scriptName || '');
      setScriptType(res.data?.scriptType || '');
      setScriptContent(res.data?.scriptContent || '');
      
      form.setFieldsValue({
        ...res.data,
        jobType: res.data?.jobType != null ? Number(res.data.jobType) : 1,
        cronExpression: res.data.cronExpression || '0 0 12 * * ?',
        scriptName: res.data?.scriptName || '',
        scriptType: res.data?.scriptType || '',
        scriptContent: res.data?.scriptContent || '',
      });
      setTitle('修改任务');
      setOpen(true);
    }
  };

  const handleDelete = async (ids) => {
    await delJob(ids);
    message.success('删除成功');
    fetchData();
  };

  const handleStatusChange = async (row) => {
    const status = row.status === '0' ? '1' : '0';
    await changeJobStatus(row.jobId, status);
    message.success('操作成功');
    fetchData();
  };

  const handleRun = async (row) => {
    const res = await runJob(row.jobId);
    if (res?.code === 200) {
      message.success('执行成功');
    } else {
      message.error(res?.msg || '执行失败');
    }
  };

  const submitForm = async () => {
    const values = await form.validateFields();
    
    // 如果是HTTP接口类型，拼接httpUrl
    if (values.jobType === 2) {
      const fullUrls = httpIps
        .filter(ip => ip && ip.trim() !== '')
        .map(ip => {
          const trimmedIp = ip.trim();
          // 判断是否包含协议
          const hasProtocol = trimmedIp.includes('://');
          let protocol = 'http';
          let hostname = trimmedIp;
          
          if (hasProtocol) {
            const urlObj = new URL(trimmedIp);
            protocol = urlObj.protocol.replace(':', '');
            hostname = urlObj.hostname;
            // 如果URL中包含端口，保留端口
            if (urlObj.port) {
              hostname = `${urlObj.hostname}:${urlObj.port}`;
            }
          } else if (trimmedIp.includes(':')) {
            hostname = trimmedIp;
          }
          
          return `${protocol}://${hostname}${httpPath}`;
        })
        .join(',');
      
      values.httpUrl = fullUrls;
    }
    
    // 如果是脚本类型，设置脚本相关字段
    if (values.jobType === 3) {
      values.scriptName = values.scriptName;
      values.scriptType = values.scriptType;
      values.scriptContent = values.scriptContent;
    }
    
    if (values.jobId) {
      const res = await updateJob(values);
      if (res.code === 200) {
        message.success('修改成功');
        setOpen(false);
        fetchData();
      }
    } else {
      const res = await addJob(values);
      if (res.code === 200) {
        message.success('新增成功');
        setOpen(false);
        fetchData();
      }
    }
  };

  const handleExport = () => {
    exportJob(queryParams).then(res => {
        const url = window.URL.createObjectURL(new Blob([res]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `job_${new Date().getTime()}.xlsx`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    });
  };

  const handleResize = (key) => (e, { size }) => {
    setColumnWidths((prev) => ({
      ...prev,
      [key]: size.width
    }));
  };

  const resizableColumns = useMemo(() => columns.map((col) => ({
    ...col,
    onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column.dataIndex || 'action'),
    }),
  })), [columns]);

  return (
    <div className="app-container">
      <Card bordered={false} style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline" onFinish={(v) => setQueryParams({ ...queryParams, ...v, pageNum: 1 })}>
          <Form.Item name="jobId" label="任务编号"><Input placeholder="请输入任务编号" allowClear /></Form.Item>
          <Form.Item name="jobName" label="任务名称"><Input placeholder="请输入" allowClear /></Form.Item>
          <Form.Item name="jobGroup" label="任务组名">
            <AutoComplete
              placeholder="请选择或输入任务组名"
              allowClear
              style={{ width: 150 }}
              options={jobGroupOptions}
              onSearch={handleJobGroupSearch}
              filterOption={false}
            />
          </Form.Item>
          <Form.Item name="jobType" label="任务类型">
            <Select placeholder="请选择" allowClear style={{ width: 150 }}>
                <Option value={1}>内置方法</Option>
                <Option value={2}>HTTP接口</Option>
                <Option value={3}>脚本</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status" label="任务状态">
            <Select placeholder="请选择" allowClear style={{ width: 150 }}>
                {sysJobStatus.map(d => <Option key={d.dictValue} value={d.dictValue}>{d.dictLabel}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item><Space><Button type="primary" icon={<SearchOutlined />} onClick={() => searchForm.submit()}>搜索</Button><Button icon={<ReloadOutlined />} onClick={() => { searchForm.resetFields(); searchForm.submit(); }}>重置</Button></Space></Form.Item>
        </Form>
      </Card>

      <Card bordered={false}>
        <div className="table-toolbar">
          <Space size="large">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
            <Button danger icon={<DeleteOutlined />} disabled={!selectedRowKeys.length} onClick={() => handleDelete(selectedRowKeys.join(','))}>删除</Button>
            <Button icon={<FileTextOutlined />} onClick={() => { setLogParams({}); setLogVisible(true); }}>调度日志</Button>
            <Button icon={<ExportOutlined />} onClick={handleExport}>导出</Button>
          </Space>
          <div style={{ flex: 1 }}></div>
          <Space size="large">
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
          dataSource={data} 
          columns={resizableColumns} 
          components={{
            header: {
              cell: ResizableTitle,
            },
          }}
          rowKey="jobId" 
          loading={loading} 
          size={tableSize} 
          scroll={{ x: 'max-content' }}
          rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }} 
          pagination={{ 
            current: queryParams.pageNum, 
            pageSize: queryParams.pageSize, 
            total, 
            showTotal: (total, range) => `共 ${total} 条`, 
            onChange: (pageNum, pageSize) => setQueryParams({ ...queryParams, pageNum, pageSize }),
            position: ['bottomRight'],
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100']
          }} 
        />
      </Card>

      <Modal title={title} open={open} onOk={submitForm} onCancel={() => setOpen(false)} width={700} centered destroyOnClose>
          <Form form={form} layout="vertical">
              <Form.Item name="jobId" hidden><Input /></Form.Item>
              <Row gutter={16}>
                  <Col span={12}><Form.Item name="jobName" label="任务名称" rules={[{ required: true }]}><Input /></Form.Item></Col>
                  <Col span={12}><Form.Item name="jobGroup" label="任务分组" rules={[{ required: true }]}><AutoComplete placeholder="请选择或输入任务分组" allowClear options={jobGroupOptions} onSearch={handleJobGroupSearch} filterOption={false} /></Form.Item></Col>
              </Row>
              <Form.Item name="jobType" label="任务类型" rules={[{ required: true }]}>
                  <Radio.Group>
                      <Radio value={1}>内置方法</Radio>
                      <Radio value={2}>HTTP接口</Radio>
                      <Radio value={3}>脚本</Radio>
                  </Radio.Group>
              </Form.Item>
              
              {jobType === 1 && (
                  <Form.Item name="methodName" label="内置方法" rules={[{ required: true, message: '请输入内置方法全限定名' }]}>
                      <Input placeholder="示例: com.xxx.service.JobService.runTask" />
                  </Form.Item>
              )}
              
              {jobType === 2 && (
                  <>
                      <Row gutter={16}>
                          <Col span={18}>
                              <Form.Item label="接口路径" rules={[{ required: true, message: '请输入接口路径' }]}>
                                  <Input placeholder="/api/test/{jobId}" value={httpPath} onChange={(e) => setHttpPath(e.target.value)} />
                              </Form.Item>
                          </Col>
                          <Col span={6}>
                              <Form.Item name="httpMethod" label="请求方式" rules={[{ required: true }]}>
                                  <Select>
                                      <Option value="GET">GET</Option>
                                      <Option value="POST">POST</Option>
                                      <Option value="PUT">PUT</Option>
                                      <Option value="DELETE">DELETE</Option>
                                  </Select>
                              </Form.Item>
                          </Col>
                      </Row>
                      <Form.Item label="服务器IP">
                          <div style={{ width: '100%' }}>
                              {httpIps.map((ip, index) => (
                                  <div key={index} style={{ display: 'flex', marginBottom: '8px', alignItems: 'center' }}>
                                      <Input
                                          style={{ flex: 1, marginRight: '8px' }}
                                          placeholder="例如：localhost:8080"
                                          value={ip}
                                          onChange={(e) => {
                                              const newIps = [...httpIps];
                                              newIps[index] = e.target.value;
                                              setHttpIps(newIps);
                                          }}
                                      />
                                      {httpIps.length > 1 && (
                                          <Button 
                                              type="text" 
                                              danger 
                                              icon={<MinusCircleOutlined />} 
                                              onClick={() => {
                                                  const newIps = httpIps.filter((_, i) => i !== index);
                                                  setHttpIps(newIps);
                                              }}
                                          />
                                      )}
                                  </div>
                              ))}
                              <Button 
                                  type="dashed" 
                                  icon={<PlusCircleOutlined />} 
                                  onClick={() => setHttpIps([...httpIps, ''])}
                                  style={{ width: '100%' }}
                              >
                                  添加服务器IP
                              </Button>
                          </div>
                      </Form.Item>
                      <Row gutter={16}>
                          <Col span={12}>
                              <Form.Item name="loadBalanceStrategy" label="负载均衡策略" initialValue="roundRobin" rules={[{ required: true, message: '请选择负载均衡策略' }]}>
                                  <Select>
                                      <Option value="roundRobin">轮询</Option>
                                      <Option value="random">随机</Option>
                                      <Option value="weightedRoundRobin">加权轮询</Option>
                                      <Option value="weightedRandom">加权随机</Option>
                                      <Option value="leastConnections">最少连接</Option>
                                      <Option value="fastestResponse">最快响应</Option>
                                      <Option value="consistentHash">一致性哈希</Option>
                                      <Option value="zoneAware">区域感知</Option>
                                  </Select>
                              </Form.Item>
                          </Col>
                      </Row>
                      <Form.Item name="httpHeaders" label="请求头 (JSON)" rules={[{
                        validator: (_, value) => {
                          if (!value) return Promise.resolve();
                          try {
                            JSON.parse(value);
                            return Promise.resolve();
                          } catch (_e) {
                            return Promise.reject(new Error('请输入合法的 JSON 格式'));
                          }
                        }
                      }]}>
                          <Input.TextArea rows={2} placeholder='示例: {"Content-Type": "application/json"}' />
                      </Form.Item>
                      <Form.Item name="httpBody" label="请求体 (JSON 模板)" help="支持占位符: {jobId}, {jobName}, {jobGroup}">
                          <ControlledEditor />
                      </Form.Item>
                  </>
              )}
              
              {jobType === 3 && (
                  <>
                      <Form.Item name="scriptName" label="脚本名称" rules={[{ required: true, message: '请输入脚本名称' }]}>
                          <Input placeholder="例如：数据备份脚本" value={scriptName} onChange={(e) => setScriptName(e.target.value)} />
                      </Form.Item>
                      <Form.Item name="scriptType" label="脚本类型" rules={[{ required: true, message: '请选择脚本类型' }]}>
                          <Select placeholder="请选择脚本类型" value={scriptType} onChange={(value) => setScriptType(value)}>
                              <Option value="python">Python脚本</Option>
                              <Option value="shell">Shell脚本</Option>
                              <Option value="cmd">CMD脚本</Option>
                              <Option value="powershell">PowerShell脚本</Option>
                              <Option value="sql">SQL脚本</Option>
                          </Select>
                      </Form.Item>
                      <Form.Item name="scriptContent" label="脚本内容" rules={[{ required: true, message: '请输入脚本内容' }]}>
                          <Input.TextArea 
                              rows={10} 
                              placeholder={`请输入${scriptType || '脚本'}内容...`}
                              value={scriptContent} 
                              onChange={(e) => setScriptContent(e.target.value)}
                              style={{ fontFamily: 'monospace' }}
                          />
                      </Form.Item>
                  </>
              )}
              <Form.Item 
                name="cronExpression" 
                label="Cron表达式" 
                validateTrigger="onBlur"
                rules={[
                  { required: true, message: '请输入Cron表达式' },
                  {
                    validator: (_, value) => {
                      if (!value) return Promise.resolve();
                      const parts = value.trim().split(/\s+/);
                      if (parts.length < 6 || parts.length > 7) {
                        return Promise.reject(new Error('Cron表达式格式不正确，必须包含6或7个部分'));
                      }
                      return Promise.resolve();
                    }
                  }
                ]}
              >
                <CronGenerator />
              </Form.Item>
              <Form.Item name="misfirePolicy" label="MISFIRE策略"><Radio.Group><Radio value="1">立即执行</Radio><Radio value="2">执行一次</Radio><Radio value="3">放弃执行</Radio></Radio.Group></Form.Item>
              <Row gutter={16}>
                  <Col span={12}><Form.Item name="concurrent" label="并发"><Radio.Group><Radio value="0">允许</Radio><Radio value="1">禁止</Radio></Radio.Group></Form.Item></Col>
                  <Col span={12}><Form.Item name="status" label="状态"><Radio.Group>{sysJobStatus.map(d => <Radio key={d.dictValue} value={d.dictValue}>{d.dictLabel}</Radio>)}</Radio.Group></Form.Item></Col>
              </Row>
              <Form.Item name="remark" label="备注"><Input.TextArea rows={3} placeholder="请输入备注" /></Form.Item>
          </Form>
      </Modal>
      
      <JobLog visible={logVisible} onCancel={() => setLogVisible(false)} {...logParams} />

      <Modal 
        title="任务详情" 
        open={detailOpen} 
        onCancel={() => setDetailOpen(false)} 
        width={700}
        centered
        footer={<Button onClick={() => setDetailOpen(false)}>关闭</Button>}
      >
          <Descriptions column={2} bordered size="small" layout="vertical">
               <Descriptions.Item label="任务名称">{currentJob.jobName}</Descriptions.Item>
               <Descriptions.Item label="任务分组">{sysJobGroup.find(d => d.dictValue === currentJob.jobGroup)?.dictLabel}</Descriptions.Item>
                <Descriptions.Item label="任务类型">
                    {Number(currentJob.jobType) === 1 ? <Tag color="green">内置方法</Tag> :
                     Number(currentJob.jobType) === 2 ? <Tag color="blue">HTTP接口</Tag> :
                     Number(currentJob.jobType) === 3 ? <Tag color="orange">脚本</Tag> : currentJob.jobType}
                </Descriptions.Item>
               <Descriptions.Item label="Cron表达式">{currentJob.cronExpression}</Descriptions.Item>
               
                {Number(currentJob.jobType) === 1 && (
                   <Descriptions.Item label="内置方法" span={2}>{currentJob.methodName}</Descriptions.Item>
               )}
               
                {Number(currentJob.jobType) === 2 && (
                   <>
                       <Descriptions.Item label="接口 URL" span={2}>{currentJob.httpUrl}</Descriptions.Item>
                       <Descriptions.Item label="请求方式">{currentJob.httpMethod}</Descriptions.Item>
                       <Descriptions.Item label="负载均衡策略">
                           {currentJob.loadBalanceStrategy === 'roundRobin' ? '轮询' :
                            currentJob.loadBalanceStrategy === 'random' ? '随机' :
                            currentJob.loadBalanceStrategy === 'weightedRoundRobin' ? '加权轮询' :
                            currentJob.loadBalanceStrategy === 'weightedRandom' ? '加权随机' :
                            currentJob.loadBalanceStrategy === 'leastConnections' ? '最少连接' :
                            currentJob.loadBalanceStrategy === 'fastestResponse' ? '最快响应' :
                            currentJob.loadBalanceStrategy === 'consistentHash' ? '一致性哈希' :
                            currentJob.loadBalanceStrategy === 'zoneAware' ? '区域感知' : currentJob.loadBalanceStrategy}
                       </Descriptions.Item>
                       <Descriptions.Item label="请求头" span={2}>
                           <pre style={{ margin: 0, fontSize: '12px', background: '#f5f5f5', padding: '8px' }}>
                               {currentJob.httpHeaders}
                           </pre>
                       </Descriptions.Item>
                       <Descriptions.Item label="请求体" span={2}>
                           <pre style={{ margin: 0, fontSize: '12px', background: '#f5f5f5', padding: '8px', maxHeight: '150px', overflow: 'auto' }}>
                               {currentJob.httpBody}
                           </pre>
                       </Descriptions.Item>
                   </>
               )}
               
               {Number(currentJob.jobType) === 3 && (
                   <>
                       <Descriptions.Item label="脚本名称" span={2}>{currentJob.scriptName}</Descriptions.Item>
                       <Descriptions.Item label="脚本类型">
                           {currentJob.scriptType === 'python' ? 'Python脚本' :
                            currentJob.scriptType === 'shell' ? 'Shell脚本' :
                            currentJob.scriptType === 'cmd' ? 'CMD脚本' :
                            currentJob.scriptType === 'powershell' ? 'PowerShell脚本' :
                            currentJob.scriptType === 'sql' ? 'SQL脚本' : currentJob.scriptType}
                       </Descriptions.Item>
                       <Descriptions.Item label="脚本内容" span={2}>
                           <pre style={{ margin: 0, fontSize: '12px', background: '#f5f5f5', padding: '8px', maxHeight: '300px', overflow: 'auto', fontFamily: 'monospace' }}>
                               {currentJob.scriptContent}
                           </pre>
                       </Descriptions.Item>
                   </>
               )}

               <Descriptions.Item label="MISFIRE策略" span={2}>
                   {currentJob.misfirePolicy === '1' ? '立即执行' : currentJob.misfirePolicy === '2' ? '执行一次' : '放弃执行'}
               </Descriptions.Item>
              <Descriptions.Item label="并发执行">
                  <Tag color={currentJob.concurrent === '0' ? 'blue' : 'orange'}>
                      {currentJob.concurrent === '0' ? '允许' : '禁止'}
                  </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="任务状态">
                  <Tag color={currentJob.status === '0' ? 'success' : 'error'}>
                      {sysJobStatus.find(d => d.dictValue === currentJob.status)?.dictLabel || '未知'}
                  </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">{currentJob.createTime}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{currentJob.updateTime}</Descriptions.Item>
              <Descriptions.Item label="创建人">{currentJob.createBy}</Descriptions.Item>
              <Descriptions.Item label="更新人">{currentJob.updateBy}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>{currentJob.remark}</Descriptions.Item>
          </Descriptions>
      </Modal>
    </div>
  );
};

export default Job;