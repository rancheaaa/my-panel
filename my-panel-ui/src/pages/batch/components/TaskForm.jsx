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
  borderRadius: 12,
  marginBottom: 20,
  border: '1px solid #f0f0f0',
  transition: 'all 0.3s ease',
  overflow: 'hidden'
};

const headerStyle = (icon, color) => ({
  background: `linear-gradient(135deg, ${color}11 0%, ${color}22 100%)`,
  padding: '16px 24px',
  borderBottom: 'none',
  display: 'flex',
  alignItems: 'center',
  gap: 10
});

const titleStyle = {
  margin: 0,
  fontSize: 15,
  fontWeight: 600,
  color: '#1f1f1f',
  letterSpacing: '-0.2px'
};

const iconStyle = (color) => ({
  fontSize: 18,
  color: color,
  background: `${color}18`,
  padding: 8,
  borderRadius: 8,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center'
});

const formItemStyle = {
  marginBottom: 18
};

const inputStyle = {
  borderRadius: 8,
  height: 40
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
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <Form
        form={form}
        layout="vertical"
        initialValues={initialValues}
        onFinish={onFinish}
        requiredMark={false}
        size="large"
        style={{ marginTop: 8 }}
      >
        {/* 基本信息 */}
        <Card style={sectionStyle} styles={{ body: { padding: '24px' }}}>
          <div style={headerStyle('#1890ff', FileTextOutlined)}>
            <span style={iconStyle('#1890ff')}>
              <FileTextOutlined />
            </span>
            <span style={titleStyle}>基本信息</span>
            <Tag color="blue" style={{ marginLeft: 'auto', fontSize: 12 }}>必填</Tag>
          </div>
          <Row gutter={24}>
            <Col span={16}>
              <Form.Item
                label={<span>任务名称 <span style={{ color: '#ff4d4f' }}>*</span></span>}
                name="taskName"
                rules={[{ required: true, message: '请输入任务名称' }]}
                style={formItemStyle}
              >
                <Input
                  placeholder="例如: 生产环境日志文件备份任务"
                  maxLength={100}
                  showCount
                  prefix={<FileTextOutlined style={{ color: '#bfbfbf' }} />}
                  style={inputStyle}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="任务描述" name="taskDescription" style={formItemStyle}>
            <TextArea
              rows={3}
              placeholder="简要描述此任务的用途和注意事项..."
              maxLength={500}
              showCount
              style={{ borderRadius: 8 }}
            />
          </Form.Item>
        </Card>

        {/* 源节点 & 目标节点 - 并排布局 */}
        <Row gutter={20}>
          <Col span={12}>
            <Card style={sectionStyle} styles={{ body: { padding: '24px' }}}>
              <div style={headerStyle('#52c41a', CloudServerOutlined)}>
                <span style={iconStyle('#52c41a')}>
                  <CloudServerOutlined />
                </span>
                <span style={titleStyle}>源节点配置</span>
                <Tooltip title="文件来源">
                  <InfoCircleOutlined style={{ marginLeft: 6, color: '#8c8c8c', cursor: 'pointer' }} />
                </Tooltip>
              </div>
              <Form.Item
                label={<><CloudServerOutlined style={{ marginRight: 6 }} />源 Agent ID<span style={{ color: '#ff4d4f' }}>*</span></>}
                name="sourceAgentId"
                rules={[{ required: true, message: '请输入源Agent ID' }]}
                style={formItemStyle}
              >
                <Input placeholder="agent-001" style={inputStyle} />
              </Form.Item>
              <Form.Item
                label={<><FilterOutlined style={{ marginRight: 6 }} />源目录路径<span style={{ color: '#ff4d4f' }}>*</span></>}
                name="sourceDir"
                rules={[{ required: true, message: '请输入源目录路径' }]}
                style={formItemStyle}
              >
                <Input placeholder="/var/log/app 或 D:\logs\app" style={inputStyle} addonBefore={<QuestionCircleOutlined />} />
              </Form.Item>
            </Card>
          </Col>
          <Col span={12}>
            <Card style={sectionStyle} styles={{ body: { padding: '24px' }}}>
              <div style={headerStyle('#722ed1', ClusterOutlined)}>
                <span style={iconStyle('#722ed1')}>
                  <ClusterOutlined />
                </span>
                <span style={titleStyle}>目标节点配置</span>
                <Tooltip title="每个目标节点包含Agent ID和对应的接收目录">
                  <InfoCircleOutlined style={{ marginLeft: 6, color: '#8c8c8c', cursor: 'pointer' }} />
                </Tooltip>
              </div>

              <Form.List name="targets" initialValue={[{ agentId: '', dir: '' }]}>
                {(fields, { add, remove }) => (
                  <div>
                    {fields.map(({ key, name, ...restField }) => (
                      <div
                        key={key}
                        style={{
                          display: 'flex',
                          gap: 10,
                          alignItems: 'flex-start',
                          marginBottom: fields.length > 1 ? 12 : 0,
                          padding: 12,
                          background: key % 2 === 0 ? '#fafafe' : '#ffffff',
                          borderRadius: 8,
                          border: '1px solid #f0f0f8'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.background = '#f0f0ff'}
                        onMouseLeave={(e) => e.currentTarget.style.background = key % 2 === 0 ? '#fafafe' : '#ffffff'}
                      >
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>Agent ID</div>
                          <Form.Item
                            {...restField}
                            name={[name, 'agentId']}
                            rules={[{ required: true, message: '' }]}
                            noStyle
                          >
                            <Input
                              placeholder="agent-002"
                              size="middle"
                              style={{ borderRadius: 6, height: 36 }}
                              prefix={<CloudServerOutlined style={{ color: '#722ed1', fontSize: 13 }} />}
                            />
                          </Form.Item>
                        </div>
                        <div
                          style={{
                            width: 28,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: '#bfbfbf',
                            paddingTop: 22,
                            flexShrink: 0,
                            fontSize: 16
                          }}
                        >
                          →
                        </div>
                        <div style={{ flex: 2 }}>
                          <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4, fontWeight: 500 }}>接收目录</div>
                          <Form.Item
                            {...restField}
                            name={[name, 'dir']}
                            rules={[{ required: true, message: '' }]}
                            noStyle
                          >
                            <Input
                              placeholder="/backup/node-02/logs"
                              size="middle"
                              style={{ borderRadius: 6, height: 36 }}
                              prefix={<FilterOutlined style={{ color: '#722ed1', fontSize: 13 }} />}
                            />
                          </Form.Item>
                        </div>
                        {fields.length > 1 && (
                          <div style={{ paddingTop: 20, flexShrink: 0, paddingLeft: 4 }}>
                            <MinusCircleOutlined
                              onClick={() => remove(name)}
                              style={{ fontSize: 18, color: '#ff4d4f', cursor: 'pointer' }}
                            />
                          </div>
                        )}
                      </div>
                    ))}
                    <Button
                      type="dashed"
                      onClick={() => add({ agentId: '', dir: '' })}
                      block
                      icon={<PlusOutlined />}
                      style={{
                        borderRadius: 8,
                        borderStyle: 'dashed',
                        borderColor: '#722ed1',
                        color: '#722ed1',
                        height: 38,
                        marginTop: 12
                      }}
                    >
                      添加目标节点
                    </Button>
                  </div>
                )}
              </Form.List>
            </Card>
          </Col>
        </Row>

        {/* 传输策略 */}
        <Card style={sectionStyle} styles={{ body: { padding: '24px' }}}>
          <div style={headerStyle('#fa8c16', ToolOutlined)}>
            <span style={iconStyle('#fa8c16')}>
              <ToolOutlined />
            </span>
            <span style={titleStyle}>传输策略</span>
          </div>
          <Row gutter={24}>
            <Col span={8}>
              <Form.Item label="传输模式" name="transferMode" initialValue="ONE_TO_MANY" style={formItemStyle}>
                <Select style={inputStyle}>
                  <Select.Option value="ONE_TO_ONE">一对一独立</Select.Option>
                  <Select.Option value="ONE_TO_MANY">一对多广播</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="路由策略" name="routingStrategy" initialValue="ROUND_ROBIN" style={formItemStyle}>
                <Select style={inputStyle}>
                  <Select.Option value="ROUND_ROBIN"><Tag color="blue">轮询</Tag></Select.Option>
                  <Select.Option value="RANDOM"><Tag color="geekblue">随机</Tag></Select.Option>
                  <Select.Option value="REGION_BASED"><Tag color="purple">区域优先</Tag></Select.Option>
                  <Select.Option value="BROADCAST"><Tag color="orange">广播</Tag></Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="保持目录结构" name="preserveDirStructure" valuePropName="checked" initialValue={true} style={{ ...formItemStyle, paddingTop: 7 }}>
                <Switch checkedChildren="保持" unCheckedChildren="扁平" />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        {/* 文件匹配规则 */}
        <Card style={sectionStyle} styles={{ body: { padding: '24px' }}}>
          <div style={headerStyle('#13c2c2', FilterOutlined)}>
            <span style={iconStyle('#13c2c2')}>
              <FilterOutlined />
            </span>
            <span style={titleStyle}>文件匹配规则</span>
          </div>
          <Row gutter={24}>
            <Col span={12}>
              <Form.Item label="包含模式" name="includePatterns" style={formItemStyle}>
                <Select mode="tags" placeholder="*.log, *.txt" tokenSeparators={[',']} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="排除模式" name="excludePatterns" style={formItemStyle}>
                <Select mode="tags" placeholder="temp*, *.tmp" tokenSeparators={[',']} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={24}>
            <Col span={12}>
              <Form.Item label="最大扫描文件数" name="maxScanFiles" initialValue={1000} style={formItemStyle}>
                <InputNumber min={1} max={100000} style={{ width: '100%', height: 40 }} addonAfter="个" />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        {/* 定时调度 & 重试 - 并排布局 */}
        <Row gutter={20}>
          <Col span={12}>
            <Card style={sectionStyle} styles={{ body: { padding: '24px' }}}>
              <div style={headerStyle('#eb2f96', ScheduleOutlined)}>
                <span style={iconStyle('#eb2f96')}>
                  <ScheduleOutlined />
                </span>
                <span style={titleStyle}>定时调度</span>
                <Tag color="magenta" style={{ marginLeft: 'auto', fontSize: 12 }}>可选</Tag>
              </div>
              <Form.Item label="Cron 表达式" name="scanCronExpression" style={formItemStyle}>
                <Input
                  placeholder="0 0 2 * * ? (每天凌晨2点)"
                  style={inputStyle}
                  suffix={
                    <Tooltip title="留空则仅手动触发">
                      <QuestionCircleOutlined style={{ color: '#bfbfbf' }} />
                    </Tooltip>
                  }
                />
              </Form.Item>
            </Card>
          </Col>
          <Col span={12}>
            <Card style={sectionStyle} styles={{ body: { padding: '24px' }}}>
              <div style={headerStyle('#fa541c', ReloadOutlined)}>
                <span style={iconStyle('#fa541c')}>
                  <ReloadOutlined />
                </span>
                <span style={titleStyle}>重试策略</span>
                <Form.Item name="retryEnabled" valuePropName="checked" initialValue={true} noStyle style={{ marginLeft: 'auto', marginBottom: 0 }}>
                  <Switch size="small" checkedChildren="开" unCheckedChildren="关" />
                </Form.Item>
              </div>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item label="最大次数" name="maxRetryCount" initialValue={3} style={{ ...formItemStyle, marginBottom: 14 }}>
                    <InputNumber min={0} max={10} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item label="间隔(分)" name="retryIntervalMin" initialValue={5} style={{ ...formItemStyle, marginBottom: 14 }}>
                    <InputNumber min={1} max={60} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item label="保留天数" name="retryMaxDays" initialValue={7} style={{ ...formItemStyle, marginBottom: 14 }}>
                    <InputNumber min={1} max={30} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item label="退避方式" name="retryBackoffType" initialValue="EXPONENTIAL" style={{ ...formItemStyle, marginBottom: 14 }}>
                    <Select size="middle" style={{ width: '100%' }}>
                      <Select.Option value="LINEAR">线性</Select.Option>
                      <Select.Option value="EXPONENTIAL">指数</Select.Option>
                    </Select>
                  </Form.Item>
                </Col>
              </Row>
            </Card>
          </Col>
        </Row>

        {/* 传输后操作 */}
        <Card style={sectionStyle} styles={{ body: { padding: '24px' }}}>
          <div style={headerStyle('#2f54eb', SendOutlined)}>
            <span style={iconStyle('#2f54eb')}>
              <SendOutlined />
            </span>
            <span style={titleStyle}>传输后操作</span>
          </div>
          <Row gutter={24}>
            <Col span={8}>
              <Form.Item label="成功后的操作" name="postTransferAction" initialValue="NONE" style={formItemStyle}>
                <Select style={inputStyle}>
                  <Select.Option value="NONE">无操作</Select.Option>
                  <Select.Option value="DELETE"><span style={{ color: '#ff4d4f' }}>删除源文件</span></Select.Option>
                  <Select.Option value="BACKUP">备份源文件</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.postTransferAction !== cur.postTransferAction}>
            {({ getFieldValue }) =>
              getFieldValue('postTransferAction') === 'BACKUP' && (
                <Card
                  size="small"
                  style={{
                    background: '#fff7e6',
                    border: '1px solid #ffd591',
                    borderRadius: 8,
                    marginTop: 12
                  }}
                >
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item label="备份目录" name="backupDir" rules={[{ required: true, message: '' }]} style={{ marginBottom: 8 }}>
                        <Input placeholder="/backup/archive" size="middle" />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item label="备份模式" name="backupMode" initialValue="COPY" style={{ marginBottom: 8 }}>
                        <Select size="middle" style={{ width: '100%' }}>
                          <Select.Option value="COPY">复制</Select.Option>
                          <Select.Option value="MOVE">移动</Select.Option>
                        </Select>
                      </Form.Item>
                    </Col>
                  </Row>
                </Card>
              )
            }
          </Form.Item>
        </Card>

        {/* 提交按钮区域 */}
        <div style={{
          textAlign: 'center',
          paddingTop: 28,
          paddingBottom: 8
        }}>
          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            size="large"
            icon={<CheckCircleOutlined />}
            style={{
              minWidth: 200,
              height: 48,
              borderRadius: 10,
              fontSize: 16,
              fontWeight: 600,
              boxShadow: '0 4px 14px rgba(24, 144, 255, 0.35)',
              transition: 'all 0.3s ease'
            }}
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
