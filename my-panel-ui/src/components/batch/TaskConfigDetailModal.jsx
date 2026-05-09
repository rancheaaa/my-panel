import React, { useEffect, useState } from 'react';
import { Modal, Form, Input, Select, Row, Col, InputNumber, Switch, Button, Space, Card, Tooltip } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';

const parseJsonSafe = (str) => {
  if (!str) return null;
  try { return JSON.parse(str); } catch { return str; }
};

const agentLabel = (a) => `${a.appId || 'unknown'}@${a.agentIp}`;

const agentFilterOption = (input, option) => {
  const label = option.label || '';
  const lower = input.toLowerCase();
  return label.toLowerCase().includes(lower);
};

const TaskConfigDetailModal = ({ visible, onCancel, task, agents = [] }) => {
  const [form] = Form.useForm();
  const [targetEntries, setTargetEntries] = useState([]);

  const transferMode = Form.useWatch('transferMode', form);
  const postTransferAction = Form.useWatch('postTransferAction', form);
  const routingStrategy = Form.useWatch('routingStrategy', form);

  useEffect(() => {
    if (visible && task) {
      let includeStr = '';
      let excludeStr = '';
      try {
        const inc = parseJsonSafe(task.includePatterns);
        if (Array.isArray(inc)) includeStr = inc.join(', ');
        const exc = parseJsonSafe(task.excludePatterns);
        if (Array.isArray(exc)) excludeStr = exc.join(', ');
      } catch(e) {}

      form.setFieldsValue({
        taskName: task.taskName,
        taskDescription: task.taskDescription,
        sourceAgentId: task.sourceAgentId,
        sourceDir: task.sourceDir,
        includePatternsStr: includeStr,
        excludePatternsStr: excludeStr,
        transferMode: task.transferMode,
        routingStrategy: task.routingStrategy,
        routingConfig: parseJsonSafe(task.routingConfig),
        postTransferAction: task.postTransferAction,
        backupDir: task.backupDir,
        backupMode: task.backupMode,
        scanFrequencySec: task.scanFrequencySec,
        maxScanFiles: task.maxScanFiles,
        maxBandwidthKbS: task.maxBandwidthKbS,
        preserveDirStructure: task.preserveDirStructure === 1,
        retryEnabled: task.retryEnabled === 1,
        retryMaxDays: task.retryMaxDays,
        retryIntervalMin: task.retryIntervalMin,
      });

      // 初始化 targetEntries
      let entries = [];
      try {
        const targetAgents = parseJsonSafe(task.targetAgents) || [];
        const targetDirsStr = task.targetDirs || '';
        const targetDirsArr = targetDirsStr.split(';').filter(Boolean);
        if (Array.isArray(targetAgents) && targetAgents.length > 0) {
           entries = targetAgents.map((agentId, index) => ({
             agentId,
             targetDir: targetDirsArr[index] || '',
             key: Date.now() + index
           }));
        } else if (targetDirsArr.length > 0) {
           entries = targetDirsArr.map((dir, index) => ({
             agentId: '',
             targetDir: dir,
             key: Date.now() + index
           }));
        }
      } catch(e) {}
      setTargetEntries(entries);
    }
  }, [visible, task, form]);

  return (
    <Modal title="任务配置详情 (模板)" open={visible} onCancel={onCancel} 
      footer={[<Button key="close" onClick={onCancel}>关闭</Button>]} width={780} destroyOnClose>
      <Form form={form} layout="vertical" disabled>
        <Row gutter={16}>
          <Col span={16}><Form.Item name="taskName" label="任务名称">
            <Input />
          </Form.Item></Col>
          <Col span={8}><Form.Item name="taskDescription" label="任务描述">
            <Input />
          </Form.Item></Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}><Form.Item name="sourceAgentId" label="发送节点">
            <Select showSearch filterOption={agentFilterOption}>
              {agents.map(a => (
                <Select.Option key={a.id} value={a.id} label={agentLabel(a)}>
                  {agentLabel(a)}
                </Select.Option>
              ))}
            </Select>
          </Form.Item></Col>
          <Col span={12}><Form.Item name="sourceDir" label="发送目录">
            <Input />
          </Form.Item></Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}><Form.Item name="includePatternsStr" label="包含模式(Glob, 逗号分隔)">
            <Input />
          </Form.Item></Col>
          <Col span={12}><Form.Item name="excludePatternsStr" label="排除模式(Glob, 逗号分隔)">
            <Input />
          </Form.Item></Col>
        </Row>

        <Card title="目标节点配置" size="small" style={{ marginBottom: 16 }}>
          {targetEntries.length === 0 && (
            <div style={{ textAlign: 'center', padding: 20, color: '#999' }}>
              无目标节点配置
            </div>
          )}
          {targetEntries.map((entry) => (
            <Row gutter={8} key={entry.key} style={{ marginBottom: 8 }} align="middle">
              <Col span={10}>
                <Select showSearch filterOption={agentFilterOption} value={entry.agentId || undefined} style={{ width: '100%' }}>
                  {agents.map(a => (
                    <Select.Option key={a.id} value={a.id} label={agentLabel(a)}>
                      {agentLabel(a)}
                    </Select.Option>
                  ))}
                </Select>
              </Col>
              <Col span={14}>
                <Input value={entry.targetDir} />
              </Col>
            </Row>
          ))}
        </Card>

        <Row gutter={16}>
          <Col span={8}><Form.Item name="transferMode" label="传输模式">
            <Select>
              <Select.Option value="ONE_TO_ONE">一对一 (1:1)</Select.Option>
              <Select.Option value="ONE_TO_MANY">一对多 (1:N)</Select.Option>
            </Select>
          </Form.Item></Col>
          <Col span={8}><Form.Item name="routingStrategy" label="路由策略">
            <Select>
              <Select.Option value="BROADCAST">广播</Select.Option>
              <Select.Option value="SINGLE">单机粘性</Select.Option>
              <Select.Option value="ROUND_ROBIN">轮询</Select.Option>
              <Select.Option value="REGION_BASED">区域路由</Select.Option>
              <Select.Option value="RANDOM">随机</Select.Option>
            </Select>
          </Form.Item></Col>
          <Col span={8}><Form.Item name="maxBandwidthKbS" label="带宽限制(KB/s)">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item></Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}><Form.Item name="postTransferAction" label="传输后操作">
            <Select>
              <Select.Option value="NONE">无操作</Select.Option>
              <Select.Option value="DELETE">删除源文件</Select.Option>
              <Select.Option value="BACKUP">备份</Select.Option>
            </Select>
          </Form.Item></Col>
          {postTransferAction === 'BACKUP' && (
            <>
              <Col span={8}><Form.Item name="backupDir" label="备份目录">
                <Input />
              </Form.Item></Col>
              <Col span={8}><Form.Item name="backupMode" label="备份模式">
                <Select><Select.Option value="COPY">复制</Select.Option><Select.Option value="MOVE">移动</Select.Option></Select>
              </Form.Item></Col>
            </>
          )}
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item
              label={<span>扫描间隔(秒) <Tooltip title="系统自动转换为Cron表达式供Agent定时扫描"><QuestionCircleOutlined /></Tooltip></span>}
              name="scanFrequencySec">
              <InputNumber style={{ width: '100%' }} addonAfter="秒" />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}><Form.Item name="maxScanFiles" label="最大扫描数"><InputNumber style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="retryEnabled" label="自动重试" valuePropName="checked"><Switch /></Form.Item></Col>
          <Col span={8}><Form.Item name="preserveDirStructure" label="保持目录结构" valuePropName="checked"><Switch /></Form.Item></Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}><Form.Item name="retryMaxDays" label="重试保留天数"><InputNumber style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={12}><Form.Item name="retryIntervalMin" label="重试间隔(分钟)"><InputNumber style={{ width: '100%' }} /></Form.Item></Col>
        </Row>
      </Form>
    </Modal>
  );
};

export default TaskConfigDetailModal;
