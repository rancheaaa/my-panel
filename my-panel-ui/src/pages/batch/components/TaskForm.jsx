import React, { useState, useEffect } from 'react';
import { Form, Input, Select, Button, InputNumber, Switch, Card, Row, Col, Tooltip, Tag, Space, message } from 'antd';
import {
  FileTextOutlined,
  CloudServerOutlined,
  ClusterOutlined,
  FilterOutlined,
  ScheduleOutlined,
  ReloadOutlined,
  ToolOutlined,
  SendOutlined,
  InfoCircleOutlined,
  CheckCircleOutlined,
  QuestionCircleOutlined,
  PlusOutlined,
  MinusCircleOutlined,
  SearchOutlined,
  ClockCircleOutlined,
  LoadingOutlined
} from '@ant-design/icons';
import { listAgentRegistry } from '../../../api/agent';
import { batchApi } from '../../../api/batch';

const { TextArea } = Input;

const sectionStyle = {
  borderRadius: 10,
  marginBottom: 12,
  border: '1px solid #f0f0f0',
  overflow: 'hidden'
};

const headerStyle = (color) => ({
  background: `linear-gradient(135deg, ${color}11 0%, ${color}22 100%)`,
  padding: '10px 18px',
  borderBottom: 'none',
  display: 'flex',
  alignItems: 'center',
  gap: 8
});

const titleStyle = {
  margin: 0,
  fontSize: 13.5,
  fontWeight: 600,
  color: '#1f1f1f'
};

const iconStyle = (color) => ({
  fontSize: 16,
  color: color,
  background: `${color}18`,
  padding: 6,
  borderRadius: 6,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center'
});

const formItemStyle = {
  marginBottom: 12
};

const inputStyle = {
  borderRadius: 6,
  height: 34
};

const TaskForm = ({ onSubmit, initialValues = {}, loading = false, mode = 'create' }) => {
  const [form] = Form.useForm();
  const [agentList, setAgentList] = useState([]);
  const [transferMode, setTransferMode] = useState('ONE_TO_ONE');
  const [sourceDirStatus, setSourceDirStatus] = useState(null);
  const [targetDirStatuses, setTargetDirStatuses] = useState({});
  const isDisabled = mode === 'detail';
  const isCreate = mode === 'create';

  const checkDirectory = async (agentId, dirPath, type, index = null) => {
    if (!agentId || !dirPath) {
      if (type === 'source') {
        setSourceDirStatus(null);
      } else {
        setTargetDirStatuses(prev => {
          const next = { ...prev };
          delete next[index];
          return next;
        });
      }
      return;
    }

    try {
      const res = await batchApi.checkDirectory(agentId, dirPath);
      if (res.data) {
        const status = res.data.status;
        if (type === 'source') {
          setSourceDirStatus(status);
        } else {
          setTargetDirStatuses(prev => ({ ...prev, [index]: status }));
        }
      }
    } catch (error) {
      console.error('检查目录失败:', error);
      if (type === 'source') {
        setSourceDirStatus('unknown');
      } else {
        setTargetDirStatuses(prev => ({ ...prev, [index]: 'unknown' }));
      }
    }
  };

  const renderDirStatusTag = (status) => {
    if (!status) return null;

    if (status === 'exists') {
      return <Tag color="success" style={{ marginLeft: 8, fontSize: 11 }}>目录存在</Tag>;
    }
    if (status === 'not_exists') {
      return <Tag color="error" style={{ marginLeft: 8, fontSize: 11 }}>目录不存在</Tag>;
    }
    return <Tag color="default" style={{ marginLeft: 8, fontSize: 11 }}>未知</Tag>;
  };

  const handleTargetsChange = (targets) => {
    const count = targets?.filter(t => t && t.agentId !== undefined).length || 0;
    const mode = count <= 1 ? 'ONE_TO_ONE' : 'ONE_TO_MANY';
    setTransferMode(mode);
    form.setFieldsValue({ transferMode: mode });
  };

  useEffect(() => {
    if (initialValues && Object.keys(initialValues).length > 0) {
      const transformed = { ...initialValues };
      if (initialValues.includePatterns && typeof initialValues.includePatterns === 'string') {
        try { transformed.includePatterns = JSON.parse(initialValues.includePatterns); } catch (e) { transformed.includePatterns = []; }
      }
      if (initialValues.excludePatterns && typeof initialValues.excludePatterns === 'string') {
        try { transformed.excludePatterns = JSON.parse(initialValues.excludePatterns); } catch (e) { transformed.excludePatterns = []; }
      }
      if (initialValues.retryEnabled !== undefined && typeof initialValues.retryEnabled !== 'boolean') {
        transformed.retryEnabled = initialValues.retryEnabled === 1;
      }
      if (initialValues.preserveDirStructure !== undefined && typeof initialValues.preserveDirStructure !== 'boolean') {
        transformed.preserveDirStructure = initialValues.preserveDirStructure === 1;
      }
      if (initialValues.targetAgentIds) {
        const targetAgentIds = Array.isArray(initialValues.targetAgentIds)
          ? initialValues.targetAgentIds
          : (() => { try { return JSON.parse(initialValues.targetAgentIds); } catch (e) { return []; } })();
        const targetDirs = (initialValues.targetDirs || '').split(';').filter(Boolean);
        transformed.targets = targetAgentIds.map((id, idx) => ({
          agentId: id,
          dir: targetDirs[idx] || ''
        }));
      }
      form.setFieldsValue(transformed);
      if (transformed.transferMode) {
        setTransferMode(transformed.transferMode);
      }
    }
  }, [initialValues]);

  const loadAgentList = async () => {
    try {
      const res = await listAgentRegistry({ pageNum: 1, pageSize: 1000 });
      if (res.data?.rows) {
        setAgentList(res.data.rows);
      }
    } catch (error) {
      console.error('加载Agent列表失败:', error);
    }
  };

  useEffect(() => {
    loadAgentList();
  }, []);

  const onFinish = (values) => {
    console.log('📝 表单提交 - 原始值:', values);
    console.log('📋 targets 数组:', values.targets);

    const targetAgentIds = values.targets?.map(t => t.agentId).filter(Boolean) || [];
    const targetDirs = values.targets?.map(t => t.dir).filter(Boolean).join(';') || '';

    const sourceAgent = agentList.find(a => a.id === values.sourceAgentId);
    const sourceAgentName = sourceAgent?.nodeName || (sourceAgent?.agentIp && sourceAgent?.agentPort ? `${sourceAgent.agentIp}:${sourceAgent.agentPort}` : '');

    const targetAgentNames = values.targets?.map(t => {
      const agent = agentList.find(a => a.id === t.agentId);
      return agent?.nodeName || (agent?.agentIp && agent?.agentPort ? `${agent.agentIp}:${agent.agentPort}` : '');
    }).filter(Boolean) || [];

    console.log('✅ 提取的 sourceAgentName:', sourceAgentName);
    console.log('✅ 提取的 targetAgentIds:', targetAgentIds);
    console.log('✅ 提取的 targetAgentNames:', targetAgentNames);
    console.log('✅ 提取的 targetDirs:', targetDirs);

    const data = {
      ...values,
      transferMode: transferMode,
      includePatterns: values.includePatterns || [],
      excludePatterns: values.excludePatterns || [],
      sourceAgentName,
      targetAgentIds,
      targetAgentNames,
      targetDirs,
      routingConfig: values.routingStrategy === 'REGION_BASED' ? (values.routingConfig || null) : null,
      retryEnabled: values.retryEnabled ? 1 : 0,
      preserveDirStructure: values.preserveDirStructure ? 1 : 0
    };

    console.log('📤 最终提交到后端的数据:', data);
    onSubmit(data);
  };

  const onFinishFailed = ({ values, errorFields, outOfDate }) => {
    const fieldLabels = {
      taskName: '任务名称',
      sourceAgentId: '源节点',
      sourceDir: '源目录',
      scanCronExpression: '执行频率(Cron)',
      targets: '目标节点',
      includePatterns: '文件匹配规则(包含/排除模式)',
      excludePatterns: '文件匹配规则(包含/排除模式)',
      routingConfig: '区域路由配置',
      backupDir: '备份目录'
    };
    const missingFields = errorFields.map(err => {
      const name = Array.isArray(err.name) ? err.name.join('.') : err.name;
      if (name.startsWith('targets.')) {
        const idx = name.match(/targets\.(\d+)/)?.[1];
        const field = name.match(/targets\.\d+\.(.+)/)?.[1];
        return `目标节点${idx ? '[' + (parseInt(idx) + 1) + ']' : ''}的${fieldLabels[field] || field}`;
      }
      return fieldLabels[name] || name;
    });
    const dedupedFields = [...new Set(missingFields)];
    message.error({
      content: (
        <div>
          <div style={{ fontWeight: 600, marginBottom: 4 }}>以下必填字段未填写：</div>
          <div>{dedupedFields.map((f, i) => <div key={i}>{i + 1}. {f}</div>)}</div>
        </div>
      ),
      duration: 6
    });
    const firstErrorField = errorFields[0]?.name;
    if (firstErrorField) {
      form.scrollToField(firstErrorField, { scrollBehavior: 'smooth', block: 'center' });
    }
  };

  return (
    <div style={{ width: '100%' }}>
        <Form
          form={form}
          layout="vertical"
          initialValues={initialValues}
          onFinish={onFinish}
          onFinishFailed={onFinishFailed}
          requiredMark={false}
          size="middle"
          style={{ marginTop: 4 }}
          onValuesChange={(changedValues, allValues) => {
            if (changedValues.targets !== undefined) {
              handleTargetsChange(allValues.targets);
            }
          }}
        >
        {/* 第一行：基本信息 */}
        <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
          <div style={headerStyle('#1890ff')}>
            <span style={iconStyle('#1890ff')}><FileTextOutlined /></span>
            <span style={titleStyle}>基本信息</span>
            <Tag color="blue" style={{ marginLeft: 'auto', fontSize: 11 }}>必填</Tag>
          </div>
          <Form.Item label={<span>任务名称 <span style={{ color: '#ff4d4f' }}>*</span></span>} name="taskName" rules={[{ required: true }]} style={formItemStyle}>
            <Input placeholder="例如: 生产环境日志文件备份任务" maxLength={100} showCount prefix={<FileTextOutlined style={{ color: '#bfbfbf' }} />} style={inputStyle} disabled={isDisabled} />
          </Form.Item>
          <Form.Item label="任务描述" name="taskDescription" style={formItemStyle}>
            <TextArea rows={2} placeholder="简要描述此任务的用途和注意事项..." maxLength={500} showCount style={{ borderRadius: 6 }} disabled={isDisabled} />
          </Form.Item>
        </Card>

        {/* 第二行：源节点（独立一行，全宽） */}
        <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
          <div style={headerStyle('#52c41a')}>
            <span style={iconStyle('#52c41a')}><CloudServerOutlined /></span>
            <span style={titleStyle}>源节点</span>
            <Tooltip title="文件来源"><InfoCircleOutlined style={{ marginLeft: 4, color: '#8c8c8c', cursor: 'pointer', fontSize: 13 }} /></Tooltip>
            <Tag color="green" style={{ marginLeft: 'auto', fontSize: 11 }}>必填</Tag>
          </div>
          <div style={{
            display: 'flex', gap: 8, alignItems: 'flex-start',
            padding: 12, borderRadius: 6, border: '1px solid #f0f8f0',
            background: '#f6ffed'
          }}
          onMouseEnter={(e) => e.currentTarget.style.background = '#f0fff0'}
          onMouseLeave={(e) => e.currentTarget.style.background = '#f6ffed'}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 11, color: '#8c8c8c', marginBottom: 2 }}>源节点</div>
              <Form.Item name="sourceAgentId" rules={[{ required: true, message: '请选择源节点' }]} noStyle>
                <Select
                  showSearch
                  placeholder="搜索并选择源节点 (例如: root@172.17.0.1:7777)"
                  optionFilterProp="label"
                  filterOption={(input, option) =>
                    (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                  }
                  size="small"
                  style={{ width: '100%', borderRadius: 5, height: 30 }}
                  disabled={isDisabled}
                >
                  {agentList.map(agent => (
                    <Select.Option key={agent.id} value={agent.id} label={agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}>
                      <Space size="small">
                        <CloudServerOutlined style={{ color: '#52c41a', fontSize: 12 }} />
                        <span style={{ fontWeight: 500 }}>{agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}</span>
                        {agent.nodeStatus === 1 && <Tag color="green" style={{ fontSize: 10 }}>在线</Tag>}
                        {agent.nodeStatus === 0 && <Tag color="red" style={{ fontSize: 10 }}>离线</Tag>}
                        {agent.osType && <Tag color="blue" style={{ fontSize: 10 }}>{agent.osType}</Tag>}
                      </Space>
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 11, color: '#8c8c8c', marginBottom: 2 }}>源目录</div>
              <Form.Item name="sourceDir" rules={[{ required: true, message: '请输入源目录' }]} noStyle>
                <Input
                  placeholder="/var/log/app"
                  size="small"
                  style={{ borderRadius: 5, height: 30 }}
                  prefix={<FilterOutlined style={{ color: '#52c41a', fontSize: 12 }} />}
                  disabled={isDisabled}
                  onBlur={(e) => {
                    const sourceAgentId = form.getFieldValue('sourceAgentId');
                    checkDirectory(sourceAgentId, e.target.value, 'source');
                  }}
                />
              </Form.Item>
              {renderDirStatusTag(sourceDirStatus)}
            </div>
          </div>
        </Card>

        {/* 第三行：目标节点（独立一行，全宽） */}
        <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
          <div style={headerStyle('#722ed1')}>
            <span style={iconStyle('#722ed1')}><ClusterOutlined /></span>
            <span style={titleStyle}>目标节点</span>
            <Tooltip title="每个目标包含Agent ID与接收目录"><InfoCircleOutlined style={{ marginLeft: 4, color: '#8c8c8c', cursor: 'pointer', fontSize: 13 }} /></Tooltip>
            <Tag color="purple" style={{ marginLeft: 'auto', fontSize: 11 }}>必填</Tag>
          </div>
          <Form.List name="targets" initialValue={[{ agentId: undefined, dir: undefined }]}>
            {(fields, { add, remove }) => (
              <div>
                {fields.map(({ key, name, ...restField }) => (
                  <div key={key} style={{
                    display: 'flex', gap: 8, alignItems: 'flex-start',
                    marginBottom: fields.length > 1 ? 8 : 0,
                    padding: 8, borderRadius: 6, border: '1px solid #f0f0f8',
                    background: key % 2 === 0 ? '#fafafe' : '#fff'
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.background = '#f0f0ff'}
                  onMouseLeave={(e) => e.currentTarget.style.background = key % 2 === 0 ? '#fafafe' : '#fff'}
                  >
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 11, color: '#aaa', marginBottom: 2 }}>目标节点</div>
                      <Form.Item {...restField} name={[name, 'agentId']} rules={[{ required: true, message: '请选择目标节点' }]} noStyle>
                        <Select
                          showSearch
                          placeholder="搜索并选择目标节点 (例如: root@172.17.0.1:7777)"
                          optionFilterProp="label"
                          filterOption={(input, option) =>
                            (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                          }
                          size="small"
                          style={{ width: '100%', borderRadius: 5, height: 30 }}
                          disabled={isDisabled}
                        >
                          {agentList.map(agent => (
                            <Select.Option key={agent.id} value={agent.id} label={agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}>
                              <Space size="small">
                                <CloudServerOutlined style={{ color: '#722ed1', fontSize: 12 }} />
                                <span style={{ fontWeight: 500 }}>{agent.nodeName || `${agent.agentIp}:${agent.agentPort}`}</span>
                                {agent.nodeStatus === 1 && <Tag color="green" style={{ fontSize: 10 }}>在线</Tag>}
                                {agent.nodeStatus === 0 && <Tag color="red" style={{ fontSize: 10 }}>离线</Tag>}
                                {agent.osType && <Tag color="blue" style={{ fontSize: 10 }}>{agent.osType}</Tag>}
                              </Space>
                            </Select.Option>
                          ))}
                        </Select>
                      </Form.Item>
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 11, color: '#aaa', marginBottom: 2 }}>接收目录</div>
                      <Form.Item {...restField} name={[name, 'dir']} rules={[{ required: true, message: '请输入接收目录' }]} noStyle>
                        <Input
                          placeholder="/backup/node-02/logs"
                          size="small"
                          style={{ borderRadius: 5, height: 30 }}
                          prefix={<FilterOutlined style={{ color: '#722ed1', fontSize: 12 }} />}
                          disabled={isDisabled}
                          onBlur={(e) => {
                            const targets = form.getFieldValue('targets');
                            const targetAgentId = targets?.[name]?.agentId;
                            checkDirectory(targetAgentId, e.target.value, 'target', key);
                          }}
                        />
                      </Form.Item>
                      {renderDirStatusTag(targetDirStatuses[key])}
                    </div>
                    {fields.length > 1 && !isDisabled && (
                      <div style={{ paddingTop: 15, flexShrink: 0, paddingLeft: 2 }}>
                        <MinusCircleOutlined onClick={() => remove(name)} style={{ fontSize: 16, color: '#ff4d4f', cursor: 'pointer' }} />
                      </div>
                    )}
                  </div>
                ))}
                {!isDisabled && (
                  <Button type="dashed" onClick={() => add({ agentId: undefined, dir: undefined })} block icon={<PlusOutlined />} size="small"
                    style={{ borderRadius: 6, borderColor: '#722ed1', color: '#722ed1', height: 32, marginTop: 8 }}>
                    添加目标节点
                  </Button>
                )}
              </div>
            )}
          </Form.List>
        </Card>

        {/* 第四行：传输策略 + 重试策略（各占50%） */}
        <Row gutter={16}>
          <Col span={12}>
            <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
              <div style={headerStyle('#fa8c16')}>
                <span style={iconStyle('#fa8c16')}><ToolOutlined /></span>
                <span style={titleStyle}>传输策略</span>
                <Tag color="orange" style={{ marginLeft: 'auto', fontSize: 11 }}>必填</Tag>
              </div>
              <Row gutter={12}>
                <Col span={12}><Form.Item label="传输模式" name="transferMode" initialValue={transferMode} style={{ ...formItemStyle, marginBottom: 10 }}>
                  <Select size="small" disabled>
                    <Select.Option value="ONE_TO_ONE">一对一</Select.Option>
                    <Select.Option value="ONE_TO_MANY">一对多</Select.Option>
                  </Select>
                </Form.Item></Col>
                <Col span={12}><Form.Item label="路由策略" name="routingStrategy" initialValue="ROUND_ROBIN" style={{ ...formItemStyle, marginBottom: 10 }}>
                  <Select size="small" disabled={isDisabled}>
                    <Select.Option value="ROUND_ROBIN"><Tag color="blue" style={{ fontSize: 11, marginRight: 0 }}>轮询</Tag></Select.Option>
                    <Select.Option value="RANDOM"><Tag color="geekblue" style={{ fontSize: 11, marginRight: 0 }}>随机</Tag></Select.Option>
                    <Select.Option value="REGION_BASED"><Tag color="purple" style={{ fontSize: 11, marginRight: 0 }}>区域</Tag></Select.Option>
                    <Select.Option value="BROADCAST"><Tag color="orange" style={{ fontSize: 11, marginRight: 0 }}>广播</Tag></Select.Option>
                  </Select>
                </Form.Item></Col>
              </Row>
              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.routingStrategy !== cur.routingStrategy}>
                {({ getFieldValue }) => {
                  const strategy = getFieldValue('routingStrategy');
                  if (strategy === 'REGION_BASED') {
                    return (
                      <div style={{
                        background: '#f9f0ff',
                        border: '1px solid #d3adf7',
                        borderRadius: 6,
                        padding: '12px 14px',
                        marginBottom: 10
                      }}>
                        <Form.Item
                          label={<span>区域路由配置 <span style={{ color: '#ff4d4f' }}>*</span></span>}
                          name="routingConfig"
                          rules={[{ required: true, message: '区域路由策略必填' }]}
                          style={{ marginBottom: 4 }}
                        >
                          <TextArea
                            rows={4}
                            placeholder='{"regionMapping": {"region-a": ["agent-001", "agent-002"], "region-b": ["agent-003", "agent-004"], "region-c": ["agent-005"]}, "sourceRegion": "region-a", "defaultRegion": "region-a", "fallbackStrategy": "BROADCAST"}'
                            maxLength={2000}
                            showCount
                            disabled={isDisabled}
                            style={{ borderRadius: 6, fontFamily: 'Monaco, Consolas, monospace', fontSize: 12 }}
                          />
                        </Form.Item>
                        <div style={{ fontSize: 11.5, color: '#8c8c8c', display: 'flex', alignItems: 'center' }}>
                          <InfoCircleOutlined style={{ marginRight: 4, flexShrink: 0 }} />
                          <span>JSON格式，定义区域与目标节点的映射关系</span>
                        </div>
                      </div>
                    );
                  }
                  return null;
                }}
              </Form.Item>
              <Form.Item label="保持目录结构" name="preserveDirStructure" valuePropName="checked" initialValue={true} style={{ ...formItemStyle, marginBottom: 0 }}>
                <Switch checkedChildren="保持" unCheckedChildren="扁平" size="small" disabled={isDisabled} />
              </Form.Item>
            </Card>
          </Col>
          <Col span={12}>
            <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
              <div style={headerStyle('#fa541c')}>
                <span style={iconStyle('#fa541c')}><ReloadOutlined /></span>
                <span style={titleStyle}>重试策略</span>
                <Tag color="default" style={{ marginLeft: 'auto', fontSize: 11 }}>选填</Tag>
                <Form.Item name="retryEnabled" valuePropName="checked" initialValue={true} noStyle style={{ marginLeft: 8, marginBottom: 0 }}>
                  <Switch size="small" checkedChildren="开" unCheckedChildren="关" disabled={isDisabled} />
                </Form.Item>
              </div>
              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.retryEnabled !== cur.retryEnabled}>
                {({ getFieldValue }) => {
                  const retryEnabled = getFieldValue('retryEnabled');
                  if (!retryEnabled) {
                    return (
                      <div style={{
                        textAlign: 'center',
                        padding: '20px 0',
                        color: '#bfbfbf',
                        fontSize: 13,
                        background: '#fafafa',
                        borderRadius: 6,
                        border: '1px dashed #d9d9d9'
                      }}>
                        <ReloadOutlined style={{ marginRight: 6, fontSize: 14 }} />
                        重试功能已关闭
                      </div>
                    );
                  }
                  return (
                    <>
                      <Row gutter={10}>
                        <Col span={8}><Form.Item label="最大次数" name="maxRetryCount" initialValue={100} style={{ ...formItemStyle, marginBottom: 8 }}>
                          <InputNumber min={0} max={999999999} size="small" style={{ width: '100%' }} disabled={isDisabled} />
                        </Form.Item></Col>
                        <Col span={8}><Form.Item label="间隔(分)" name="retryIntervalMin" initialValue={1} style={{ ...formItemStyle, marginBottom: 8 }}>
                          <InputNumber min={1} max={1440} size="small" style={{ width: '100%' }} disabled={isDisabled} />
                        </Form.Item></Col>
                        <Col span={8}><Form.Item label="保留天数" name="retryMaxDays" initialValue={3} style={{ ...formItemStyle, marginBottom: 8 }}>
                          <InputNumber min={1} max={365} size="small" style={{ width: '100%' }} disabled={isDisabled} />
                        </Form.Item></Col>
                      </Row>
                      <Form.Item label="退避方式" name="retryBackoffType" initialValue="EXPONENTIAL" style={{ ...formItemStyle, marginBottom: 0 }}>
                        <Select size="small" disabled={isDisabled}><Select.Option value="LINEAR">线性</Select.Option><Select.Option value="EXPONENTIAL">指数</Select.Option></Select>
                      </Form.Item>
                    </>
                  );
                }}
              </Form.Item>
            </Card>
          </Col>
        </Row>

        {/* 第五行：文件匹配规则（独立一行，全宽） */}
        <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
          <div style={headerStyle('#13c2c2')}>
            <span style={iconStyle('#13c2c2')}><FilterOutlined /></span>
            <span style={titleStyle}>文件匹配规则</span>
            <Tag color="cyan" style={{ marginLeft: 'auto', fontSize: 11 }}>必填</Tag>
          </div>
          <Row gutter={16}>
            <Col span={10}>
              <Form.Item
                label={<span>包含模式 <span style={{ color: '#ff4d4f' }}>*</span></span>}
                name="includePatterns"
                rules={[
                  {
                    validator: (_, value) => {
                      const excludePatterns = form.getFieldValue('excludePatterns');
                      if ((!value || value.length === 0) && (!excludePatterns || excludePatterns.length === 0)) {
                        return Promise.reject(new Error('包含模式和排除模式至少填写一项'));
                      }
                      return Promise.resolve();
                    }
                  }
                ]}
                style={formItemStyle}
              >
                <Select mode="tags" placeholder="例如: *.log, *.txt" tokenSeparators={[',']} style={{ width: '100%', borderRadius: 6 }} disabled={isDisabled} />
              </Form.Item>
              <div style={{ fontSize: 11.5, color: '#8c8c8c', marginTop: -6, marginBottom: 4 }}>
                <InfoCircleOutlined style={{ marginRight: 4 }} />
                匹配的文件才会被传输，支持通配符 * 和 ?
              </div>
            </Col>
            <Col span={10}>
              <Form.Item
                label={<span>排除模式 <span style={{ color: '#ff4d4f' }}>*</span></span>}
                name="excludePatterns"
                rules={[
                  {
                    validator: (_, value) => {
                      const includePatterns = form.getFieldValue('includePatterns');
                      if ((!value || value.length === 0) && (!includePatterns || includePatterns.length === 0)) {
                        return Promise.reject(new Error('包含模式和排除模式至少填写一项'));
                      }
                      return Promise.resolve();
                    }
                  }
                ]}
                style={formItemStyle}
              >
                <Select mode="tags" placeholder="例如: temp*, *.bak" tokenSeparators={[',']} style={{ width: '100%', borderRadius: 6 }} disabled={isDisabled} />
              </Form.Item>
              <div style={{ fontSize: 11.5, color: '#8c8c8c', marginTop: -6, marginBottom: 4 }}>
                <InfoCircleOutlined style={{ marginRight: 4 }} />
                排除的文件不会被传输，优先级高于包含模式
              </div>
            </Col>
            <Col span={4}>
              <Form.Item label="最大扫描数" name="maxScanFiles" initialValue={1000} style={formItemStyle}>
                <InputNumber min={1} max={100000} style={{ width: '100%', height: 34, borderRadius: 6 }} addonAfter="个" disabled={isDisabled} />
              </Form.Item>
            </Col>
          </Row>
          <div style={{ fontSize: 12, color: '#faad14', marginTop: 12, padding: '8px 12px', background: '#fffbe6', borderRadius: 6, border: '1px solid #ffe58f' }}>
            <InfoCircleOutlined style={{ marginRight: 6 }} />
            通配符规则：<span style={{ fontWeight: 500 }}>包含模式</span> 或 <span style={{ fontWeight: 500 }}>排除模式</span> 至少填写一项
          </div>
        </Card>

        {/* 第六行：定时调度（独立一行，全宽，必填） */}
        <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
          <div style={headerStyle('#eb2f96')}>
            <span style={iconStyle('#eb2f96')}><ScheduleOutlined /></span>
            <span style={titleStyle}>定时调度</span>
            <Tag color="magenta" style={{ marginLeft: 'auto', fontSize: 11 }}>必填</Tag>
          </div>
          <Form.Item
            label={<span>执行频率 <span style={{ color: '#ff4d4f' }}>*</span></span>}
            name="scanCronExpression"
            rules={[{ required: true, message: '请选择执行频率' }]}
            style={{ ...formItemStyle, marginBottom: 8 }}
          >
            <Select
              placeholder="请选择执行频率"
              style={inputStyle}
              optionLabelProp="label"
              disabled={isDisabled}
            >
              <Select.OptGroup label="常用间隔">
                <Select.Option value="0 */1 * * * ?" label={<Space><ClockCircleOutlined />每隔 1 分钟</Space>}>
                  <div><strong>每隔 1 分钟</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 */1 * * * ?</div>
                </Select.Option>
                <Select.Option value="0 */5 * * * ?" label={<Space><ClockCircleOutlined />每隔 5 分钟</Space>}>
                  <div><strong>每隔 5 分钟</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 */5 * * * ?</div>
                </Select.Option>
                <Select.Option value="0 */10 * * * ?" label={<Space><ClockCircleOutlined />每隔 10 分钟</Space>}>
                  <div><strong>每隔 10 分钟</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 */10 * * * ?</div>
                </Select.Option>
                <Select.Option value="0 */30 * * * ?" label={<Space><ClockCircleOutlined />每隔 30 分钟</Space>}>
                  <div><strong>每隔 30 分钟</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 */30 * * * ?</div>
                </Select.Option>
                <Select.Option value="0 0 */1 * * ?" label={<Space><ClockCircleOutlined />每隔 1 小时</Space>}>
                  <div><strong>每隔 1 小时</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 0 */1 * * ?</div>
                </Select.Option>
              </Select.OptGroup>
              <Select.OptGroup label="每日定时">
                <Select.Option value="0 0 0 * * ?" label={<Space><ClockCircleOutlined />每天 00:00</Space>}>
                  <div><strong>每天 00:00 (午夜)</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 0 0 * * ?</div>
                </Select.Option>
                <Select.Option value="0 0 2 * * ?" label={<Space><ClockCircleOutlined />每天 02:00</Space>}>
                  <div><strong>每天 02:00 (凌晨)</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 0 2 * * ?</div>
                </Select.Option>
                <Select.Option value="0 0 12 * * ?" label={<Space><ClockCircleOutlined />每天 12:00</Space>}>
                  <div><strong>每天 12:00 (中午)</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 0 12 * * ?</div>
                </Select.Option>
                <Select.Option value="0 0 18 * * ?" label={<Space><ClockCircleOutlined />每天 18:00</Space>}>
                  <div><strong>每天 18:00 (傍晚)</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 0 18 * * ?</div>
                </Select.Option>
              </Select.OptGroup>
              <Select.OptGroup label="每周定时">
                <Select.Option value="0 0 2 ? * MON" label={<Space><ClockCircleOutlined />每周一 02:00</Space>}>
                  <div><strong>每周一 02:00</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 0 2 ? * MON</div>
                </Select.Option>
                <Select.Option value="0 0 2 ? * SUN" label={<Space><ClockCircleOutlined />每周日 02:00</Space>}>
                  <div><strong>每周日 02:00</strong></div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>0 0 2 ? * SUN</div>
                </Select.Option>
              </Select.OptGroup>
            </Select>
          </Form.Item>
          <div style={{ padding: '6px 10px', background: '#fff0f6', borderRadius: 4, border: '1px solid #ffadd2' }}>
            <Tooltip title="选择预设频率后自动生成Cron表达式">
              <span style={{ fontSize: 11.5, color: '#c41d7f' }}>
                <InfoCircleOutlined style={{ marginRight: 4 }} />
                请选择任务的执行频率，支持常用间隔、每日定时、每周定时等预设选项
              </span>
            </Tooltip>
          </div>
        </Card>

        {/* 第七行：传输后操作（全宽） */}
        <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
          <div style={headerStyle('#2f54eb')}>
            <span style={iconStyle('#2f54eb')}><SendOutlined /></span>
            <span style={titleStyle}>传输后操作</span>
            <Tag color="blue" style={{ marginLeft: 'auto', fontSize: 11 }}>必填</Tag>
          </div>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="成功后操作" name="postTransferAction" initialValue="NONE" style={formItemStyle}>
                <Select size="small" style={inputStyle} disabled={isDisabled}>
                  <Select.Option value="NONE">无操作</Select.Option>
                  <Select.Option value="DELETE"><span style={{ color: '#ff4d4f' }}>删除源文件</span></Select.Option>
                  <Select.Option value="BACKUP">备份源文件</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.postTransferAction !== cur.postTransferAction}>
                {({ getFieldValue }) =>
                  getFieldValue('postTransferAction') === 'BACKUP' && (
                    <div style={{ background: '#fff7e6', border: '1px solid #ffd591', borderRadius: 6, padding: '10px 14px' }}>
                      <Row gutter={12}>
                        <Col span={12}><Form.Item label="备份目录" name="backupDir" rules={[{ required: true }]} style={{ marginBottom: 0 }}>
                          <Input placeholder="/backup/archive" size="small" disabled={isDisabled} />
                        </Form.Item></Col>
                        <Col span={12}><Form.Item label="备份模式" name="backupMode" initialValue="COPY" style={{ marginBottom: 0 }}>
                          <Select size="small" style={{ width: '100%' }} disabled={isDisabled}><Select.Option value="COPY">复制</Select.Option><Select.Option value="MOVE">移动</Select.Option></Select>
                        </Form.Item></Col>
                      </Row>
                    </div>
                  )
                }
              </Form.Item>
            </Col>
          </Row>
        </Card>

        {/* 提交按钮 */}
        {!isDisabled && (
          <div style={{ textAlign: 'center', paddingTop: 16, paddingBottom: 4 }}>
            <Button type="primary" htmlType="submit" loading={loading} size="large" icon={<CheckCircleOutlined />}
              style={{ minWidth: 180, height: 42, borderRadius: 8, fontSize: 15, fontWeight: 600, boxShadow: '0 4px 14px rgba(24,144,255,.35)' }}
              onMouseEnter={(e) => e.currentTarget.style.transform = 'translateY(-2px)'}
              onMouseLeave={(e) => e.currentTarget.style.transform = 'translateY(0)'}
            >
              {isCreate ? '创建任务' : '保存修改'}
            </Button>
          </div>
        )}
      </Form>
    </div>
  );
};

export default TaskForm;
