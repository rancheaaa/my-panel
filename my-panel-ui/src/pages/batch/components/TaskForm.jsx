import React from 'react';
import { Form, Input, Select, Button, InputNumber, Switch, Card, Row, Col, Tooltip, Tag, Space } from 'antd';
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
  MinusCircleOutlined
} from '@ant-design/icons';

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

const TaskForm = ({ onSubmit, initialValues = {}, loading = false }) => {
  const [form] = Form.useForm();

  const onFinish = (values) => {
    const data = {
      ...values,
      includePatterns: JSON.stringify(values.includePatterns || []),
      excludePatterns: JSON.stringify(values.excludePatterns || []),
      targetAgentIds: values.targets?.map(t => t.agentId).filter(Boolean),
      targetDirs: values.targets?.map(t => t.dir).filter(Boolean).join(';') || '',
      retryEnabled: values.retryEnabled ? 1 : 0,
      preserveDirStructure: values.preserveDirStructure ? 1 : 0
    };
    onSubmit(data);
  };

  return (
    <div style={{ width: '100%' }}>
      <Form
        form={form}
        layout="vertical"
        initialValues={initialValues}
        onFinish={onFinish}
        requiredMark={false}
        size="middle"
        style={{ marginTop: 4 }}
      >
        {/* 第一行：基本信息 */}
        <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
          <div style={headerStyle('#1890ff')}>
            <span style={iconStyle('#1890ff')}><FileTextOutlined /></span>
            <span style={titleStyle}>基本信息</span>
            <Tag color="blue" style={{ marginLeft: 'auto', fontSize: 11 }}>必填</Tag>
          </div>
          <Form.Item label={<span>任务名称 <span style={{ color: '#ff4d4f' }}>*</span></span>} name="taskName" rules={[{ required: true }]} style={formItemStyle}>
            <Input placeholder="例如: 生产环境日志文件备份任务" maxLength={100} showCount prefix={<FileTextOutlined style={{ color: '#bfbfbf' }} />} style={inputStyle} />
          </Form.Item>
          <Form.Item label="任务描述" name="taskDescription" style={formItemStyle}>
            <TextArea rows={2} placeholder="简要描述此任务的用途和注意事项..." maxLength={500} showCount style={{ borderRadius: 6 }} />
          </Form.Item>
        </Card>

        {/* 第二行：源节点 + 目标节点 并排 */}
        <Row gutter={16}>
          <Col span={8}>
            <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
              <div style={headerStyle('#52c41a')}>
                <span style={iconStyle('#52c41a')}><CloudServerOutlined /></span>
                <span style={titleStyle}>源节点</span>
                <Tooltip title="文件来源"><InfoCircleOutlined style={{ marginLeft: 4, color: '#8c8c8c', cursor: 'pointer', fontSize: 13 }} /></Tooltip>
              </div>
              <Form.Item label="Agent ID" name="sourceAgentId" rules={[{ required: true }]} style={formItemStyle}>
                <Input placeholder="agent-001" style={inputStyle} prefix={<CloudServerOutlined style={{ color: '#52c41a', fontSize: 13 }} />} />
              </Form.Item>
              <Form.Item label="源目录" name="sourceDir" rules={[{ required: true }]} style={formItemStyle}>
                <Input placeholder="/var/log/app" style={inputStyle} prefix={<FilterOutlined style={{ color: '#52c41a', fontSize: 13 }} />} />
              </Form.Item>
            </Card>
          </Col>
          <Col span={16}>
            <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
              <div style={headerStyle('#722ed1')}>
                <span style={iconStyle('#722ed1')}><ClusterOutlined /></span>
                <span style={titleStyle}>目标节点</span>
                <Tooltip title="每个目标包含Agent ID与接收目录"><InfoCircleOutlined style={{ marginLeft: 4, color: '#8c8c8c', cursor: 'pointer', fontSize: 13 }} /></Tooltip>
              </div>
              <Form.List name="targets" initialValue={[{ agentId: '', dir: '' }]}>
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
                          <div style={{ fontSize: 11, color: '#aaa', marginBottom: 2 }}>Agent ID</div>
                          <Form.Item {...restField} name={[name, 'agentId']} rules={[{ required: true }]} noStyle>
                            <Input placeholder="agent-002" size="small" style={{ borderRadius: 5, height: 30 }} prefix={<CloudServerOutlined style={{ color: '#722ed1', fontSize: 12 }} />} />
                          </Form.Item>
                        </div>
                        <div style={{ width: 22, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#d9d9d9', paddingTop: 17, flexShrink: 0, fontSize: 14 }}>→</div>
                        <div style={{ flex: 2.5, minWidth: 0 }}>
                          <div style={{ fontSize: 11, color: '#aaa', marginBottom: 2 }}>接收目录</div>
                          <Form.Item {...restField} name={[name, 'dir']} rules={[{ required: true }]} noStyle>
                            <Input placeholder="/backup/node-02/logs" size="small" style={{ borderRadius: 5, height: 30 }} prefix={<FilterOutlined style={{ color: '#722ed1', fontSize: 12 }} />} />
                          </Form.Item>
                        </div>
                        {fields.length > 1 && (
                          <div style={{ paddingTop: 15, flexShrink: 0, paddingLeft: 2 }}>
                            <MinusCircleOutlined onClick={() => remove(name)} style={{ fontSize: 16, color: '#ff4d4f', cursor: 'pointer' }} />
                          </div>
                        )}
                      </div>
                    ))}
                    <Button type="dashed" onClick={() => add({ agentId: '', dir: '' })} block icon={<PlusOutlined />} size="small"
                      style={{ borderRadius: 6, borderColor: '#722ed1', color: '#722ed1', height: 32, marginTop: 8 }}>
                      添加目标节点
                    </Button>
                  </div>
                )}
              </Form.List>
            </Card>
          </Col>
        </Row>

        {/* 第三行：传输策略 + 文件匹配 规则 合并为一个宽行 */}
        <Row gutter={16}>
          <Col span={9}>
            <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
              <div style={headerStyle('#fa8c16')}>
                <span style={iconStyle('#fa8c16')}><ToolOutlined /></span>
                <span style={titleStyle}>传输策略</span>
              </div>
              <Row gutter={12}>
                <Col span={12}><Form.Item label="传输模式" name="transferMode" initialValue="ONE_TO_MANY" style={{ ...formItemStyle, marginBottom: 10 }}>
                  <Select size="small"><Select.Option value="ONE_TO_ONE">一对一</Select.Option><Select.Option value="ONE_TO_MANY">一对多</Select.Option></Select>
                </Form.Item></Col>
                <Col span={12}><Form.Item label="路由策略" name="routingStrategy" initialValue="ROUND_ROBIN" style={{ ...formItemStyle, marginBottom: 10 }}>
                  <Select size="small">
                    <Select.Option value="ROUND_ROBIN"><Tag color="blue" style={{ fontSize: 11, marginRight: 0 }}>轮询</Tag></Select.Option>
                    <Select.Option value="RANDOM"><Tag color="geekblue" style={{ fontSize: 11, marginRight: 0 }}>随机</Tag></Select.Option>
                    <Select.Option value="REGION_BASED"><Tag color="purple" style={{ fontSize: 11, marginRight: 0 }}>区域</Tag></Select.Option>
                    <Select.Option value="BROADCAST"><Tag color="orange" style={{ fontSize: 11, marginRight: 0 }}>广播</Tag></Select.Option>
                  </Select>
                </Form.Item></Col>
              </Row>
              <Form.Item label="保持目录结构" name="preserveDirStructure" valuePropName="checked" initialValue={true} style={{ ...formItemStyle, marginBottom: 0 }}>
                <Switch checkedChildren="保持" unCheckedChildren="扁平" size="small" />
              </Form.Item>
            </Card>
          </Col>
          <Col span={15}>
            <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
              <div style={headerStyle('#13c2c2')}>
                <span style={iconStyle('#13c2c2')}><FilterOutlined /></span>
                <span style={titleStyle}>文件匹配规则</span>
              </div>
              <Row gutter={12}>
                <Col span={8}><Form.Item label="包含模式" name="includePatterns" style={{ ...formItemStyle, marginBottom: 10 }}>
                  <Select mode="tags" size="small" placeholder="*.log" tokenSeparators={[',']} style={{ width: '100%' }} />
                </Form.Item></Col>
                <Col span={8}><Form.Item label="排除模式" name="excludePatterns" style={{ ...formItemStyle, marginBottom: 10 }}>
                  <Select mode="tags" size="small" placeholder="temp*" tokenSeparators={[',']} style={{ width: '100%' }} />
                </Form.Item></Col>
                <Col span={8}><Form.Item label="最大扫描数" name="maxScanFiles" initialValue={1000} style={{ ...formItemStyle, marginBottom: 10 }}>
                  <InputNumber min={1} max={100000} size="small" style={{ width: '100%', height: 30 }} addonAfter="个" />
                </Form.Item></Col>
              </Row>
            </Card>
          </Col>
        </Row>

        {/* 第四行：定时调度 + 重试 + 传输后操作 三栏并列 */}
        <Row gutter={16}>
          <Col span={8}>
            <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
              <div style={headerStyle('#eb2f96')}>
                <span style={iconStyle('#eb2f96')}><ScheduleOutlined /></span>
                <span style={titleStyle}>定时调度</span>
                <Tag color="magenta" style={{ marginLeft: 'auto', fontSize: 11 }}>可选</Tag>
              </div>
              <Form.Item label="Cron 表达式" name="scanCronExpression" style={{ ...formItemStyle, marginBottom: 0 }}>
                <Input placeholder="0 0 2 * * ?" style={inputStyle}
                  suffix={<Tooltip title="留空则手动触发"><QuestionCircleOutlined style={{ color: '#bfbfbf', fontSize: 13 }} /></Tooltip>} />
              </Form.Item>
            </Card>
          </Col>
          <Col span={10}>
            <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
              <div style={headerStyle('#fa541c')}>
                <span style={iconStyle('#fa541c')}><ReloadOutlined /></span>
                <span style={titleStyle}>重试策略</span>
                <Form.Item name="retryEnabled" valuePropName="checked" initialValue={true} noStyle style={{ marginLeft: 'auto', marginBottom: 0 }}>
                  <Switch size="small" checkedChildren="开" unCheckedChildren="关" />
                </Form.Item>
              </div>
              <Row gutter={10}>
                <Col span={8}><Form.Item label="最大次数" name="maxRetryCount" initialValue={3} style={{ ...formItemStyle, marginBottom: 8 }}>
                  <InputNumber min={0} max={10} size="small" style={{ width: '100%' }} />
                </Form.Item></Col>
                <Col span={8}><Form.Item label="间隔(分)" name="retryIntervalMin" initialValue={5} style={{ ...formItemStyle, marginBottom: 8 }}>
                  <InputNumber min={1} max={60} size="small" style={{ width: '100%' }} />
                </Form.Item></Col>
                <Col span={8}><Form.Item label="保留天数" name="retryMaxDays" initialValue={7} style={{ ...formItemStyle, marginBottom: 8 }}>
                  <InputNumber min={1} max={30} size="small" style={{ width: '100%' }} />
                </Form.Item></Col>
              </Row>
              <Form.Item label="退避方式" name="retryBackoffType" initialValue="EXPONENTIAL" style={{ ...formItemStyle, marginBottom: 0 }}>
                <Select size="small"><Select.Option value="LINEAR">线性</Select.Option><Select.Option value="EXPONENTIAL">指数</Select.Option></Select>
              </Form.Item>
            </Card>
          </Col>
          <Col span={6}>
            <Card style={sectionStyle} styles={{ body: { padding: '16px 18px' }}}>
              <div style={headerStyle('#2f54eb')}>
                <span style={iconStyle('#2f54eb')}><SendOutlined /></span>
                <span style={titleStyle}>传输后操作</span>
              </div>
              <Form.Item label="成功后操作" name="postTransferAction" initialValue="NONE" style={formItemStyle}>
                <Select size="small" style={inputStyle}>
                  <Select.Option value="NONE">无操作</Select.Option>
                  <Select.Option value="DELETE"><span style={{ color: '#ff4d4f' }}>删除源文件</span></Select.Option>
                  <Select.Option value="BACKUP">备份源文件</Select.Option>
                </Select>
              </Form.Item>
              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.postTransferAction !== cur.postTransferAction}>
                {({ getFieldValue }) =>
                  getFieldValue('postTransferAction') === 'BACKUP' && (
                    <div style={{ background: '#fff7e6', border: '1px solid #ffd591', borderRadius: 6, padding: '8px 10px', marginTop: 4 }}>
                      <Form.Item label="备份目录" name="backupDir" rules={[{ required: true }]} style={{ marginBottom: 6 }}>
                        <Input placeholder="/backup/archive" size="small" />
                      </Form.Item>
                      <Form.Item label="备份模式" name="backupMode" initialValue="COPY" style={{ marginBottom: 0 }}>
                        <Select size="small" style={{ width: '100%' }}><Select.Option value="COPY">复制</Select.Option><Select.Option value="MOVE">移动</Select.Option></Select>
                      </Form.Item>
                    </div>
                  )
                }
              </Form.Item>
            </Card>
          </Col>
        </Row>

        {/* 提交按钮 */}
        <div style={{ textAlign: 'center', paddingTop: 16, paddingBottom: 4 }}>
          <Button type="primary" htmlType="submit" loading={loading} size="large" icon={<CheckCircleOutlined />}
            style={{ minWidth: 180, height: 42, borderRadius: 8, fontSize: 15, fontWeight: 600, boxShadow: '0 4px 14px rgba(24,144,255,.35)' }}
            onMouseEnter={(e) => e.currentTarget.style.transform = 'translateY(-2px)'}
            onMouseLeave={(e) => e.currentTarget.style.transform = 'translateY(0)'}
          >
            创建任务
          </Button>
        </div>
      </Form>
    </div>
  );
};

export default TaskForm;
