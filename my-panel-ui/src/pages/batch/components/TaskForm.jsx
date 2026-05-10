import React from 'react';
import { Form, Input, Select, Button, InputNumber, Switch, Card, Collapse, Divider } from 'antd';

const { TextArea } = Input;
const { Panel } = Collapse;

const TaskForm = ({ onSubmit, initialValues = {}, loading = false }) => {
  const [form] = Form.useForm();

  const onFinish = (values) => {
    const data = {
      ...values,
      includePatterns: JSON.stringify(values.includePatterns || []),
      excludePatterns: JSON.stringify(values.excludePatterns || []),
      targetAgentIds: JSON.stringify(values.targetAgentIds || []),
      targetDirs: values.targetDirs?.split('\n').filter(Boolean).join(';') || '',
      retryEnabled: values.retryEnabled ? 1 : 0,
      preserveDirStructure: values.preserveDirStructure ? 1 : 0
    };
    onSubmit(data);
  };

  return (
    <Form form={form} layout="vertical" initialValues={initialValues} onFinish={onFinish}>
      <Collapse defaultActiveKey={['basic', 'source', 'target', 'file', 'schedule', 'retry', 'post', 'advanced']}>

        {/* 基本信息 */}
        <Panel header="基本信息" key="basic">
          <Form.Item label="任务名称" name="taskName" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="例如: 日志文件备份任务" maxLength={100} />
          </Form.Item>
          <Form.Item label="任务描述" name="taskDescription">
            <TextArea rows={3} placeholder="输入任务描述信息（可选）" maxLength={500} />
          </Form.Item>
        </Panel>

        {/* 源节点配置 */}
        <Panel header="源节点配置" key="source">
          <Form.Item label="源Agent ID" name="sourceAgentId" rules={[{ required: true, message: '请输入源Agent ID' }]}>
            <Input placeholder="例如: agent-001" />
          </Form.Item>
          <Form.Item label="源目录绝对路径" name="sourceDir" rules={[{ required: true, message: '请输入源目录路径' }]}>
            <Input placeholder="例如: /var/log/app 或 D:\logs\app" />
          </Form.Item>
        </Panel>

        {/* 目标节点配置 */}
        <Panel header="目标节点配置" key="target">
          <Form.Item label="目标Agent ID列表" name="targetAgentIds" rules={[{ required: true, message: '请至少选择一个目标Agent' }]}>
            <Select mode="tags" placeholder="输入或选择目标Agent ID，按回车添加" />
          </Form.Item>
          <Form.Item label="目标目录(每行一个)" name="targetDirs" rules={[{ required: true, message: '请输入至少一个目标目录' }]}>
            <TextArea rows={4} placeholder={"每行一个目标目录\n例如:\n/backup/app-001/logs\n/backup/app-002/logs"} />
          </Form.Item>
          <Form.Item label="传输模式" name="transferMode" initialValue="ONE_TO_MANY">
            <Select>
              <Select.Option value="ONE_TO_ONE">一对一（每个目标独立）</Select.Option>
              <Select.Option value="ONE_TO_MANY">一对多（广播到所有目标）</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="路由策略" name="routingStrategy" initialValue="ROUND_ROBIN">
            <Select>
              <Select.Option value="ROUND_ROBIN">轮询</Select.Option>
              <Select.Option value="RANDOM">随机</Select.Option>
              <Select.Option value="REGION_BASED">区域优先</Select.Option>
              <Select.Option value="BROADCAST">广播</Select.Option>
            </Select>
          </Form.Item>
        </Panel>

        {/* 文件匹配规则 */}
        <Panel header="文件匹配规则" key="file">
          <Form.Item label="包含模式(通配符)" name="includePatterns">
            <Select mode="tags" placeholder="输入文件匹配模式，如 *.log, *.txt" tokenSeparators={[',']} />
          </Form.Item>
          <Form.Item label="排除模式(通配符)" name="excludePatterns">
            <Select mode="tags" placeholder="输入排除模式，如 temp*, *.tmp" tokenSeparators={[',']} />
          </Form.Item>
          <Form.Item label="单次最大扫描文件数" name="maxScanFiles" initialValue={1000}>
            <InputNumber min={1} max={100000} style={{ width: '100%' }} placeholder="限制单次扫描的文件数量上限" />
          </Form.Item>
          <Form.Item label="保持原始目录结构" name="preserveDirStructure" valuePropName="checked" initialValue={true}>
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>
        </Panel>

        {/* 定时调度 */}
        <Panel header="定时调度" key="schedule">
          <Form.Item label="Cron表达式" name="scanCronExpression">
            <Input placeholder="例如: 0 0 2 * * ? (每天凌晨2点) 或留空表示手动触发" />
          </Form.Item>
        </Panel>

        {/* 重试策略 */}
        <Panel header="重试策略" key="retry">
          <Form.Item label="启用自动重试" name="retryEnabled" valuePropName="checked" initialValue={true}>
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
          <Form.Item label="最大重试次数" name="maxRetryCount" initialValue={3}>
            <InputNumber min={0} max={10} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="首次重试间隔(分钟)" name="retryIntervalMin" initialValue={5}>
            <InputNumber min={1} max={60} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="重试保留天数" name="retryMaxDays" initialValue={7}>
            <InputNumber min={1} max={30} style={{ width: '100%' }} placeholder="失败记录保留天数" />
          </Form.Item>
          <Form.Item label="退避策略" name="retryBackoffType" initialValue="EXPONENTIAL">
            <Select>
              <Select.Option value="LINEAR">线性退避 (固定间隔)</Select.Option>
              <Select.Option value="EXPONENTIAL">指数退避 (间隔递增)</Select.Option>
            </Select>
          </Form.Item>
        </Panel>

        {/* 传输后操作 */}
        <Panel header="传输后操作" key="post">
          <Form.Item label="传输成功后的操作" name="postTransferAction" initialValue="NONE">
            <Select>
              <Select.Option value="NONE">无操作</Select.Option>
              <Select.Option value="DELETE">删除源文件</Select.Option>
              <Select.Option value="BACKUP">备份源文件</Select.Option>
            </Select>
          </Form.Item>
          {(
            <Form.Item noStyle shouldUpdate={(prev, cur) => prev.postTransferAction !== cur.postTransferAction}>
              {({ getFieldValue }) =>
                getFieldValue('postTransferAction') === 'BACKUP' && (
                  <>
                    <Form.Item label="备份目录" name="backupDir" rules={[{ required: true, message: '请输入备份目录' }]}>
                      <Input placeholder="例如: /backup/archive" />
                    </Form.Item>
                    <Form.Item label="备份模式" name="backupMode" initialValue="COPY">
                      <Select>
                        <Select.Option value="COPY">复制</Select.Option>
                        <Select.Option value="MOVE">移动</Select.Option>
                      </Select>
                    </Form.Item>
                  </>
                )
              }
            </Form.Item>
          )}
        </Panel>

      </Collapse>

      <Divider />

      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block size="large">
          提交创建
        </Button>
      </Form.Item>
    </Form>
  );
};

export default TaskForm;
