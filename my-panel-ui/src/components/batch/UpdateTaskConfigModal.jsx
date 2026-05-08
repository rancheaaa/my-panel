import React, { useEffect, useState } from 'react';
import { Modal, Form, Input, Select, Row, Col, InputNumber, Switch, Button, Space, Card, Tooltip, Typography } from 'antd';
import { PlusOutlined, DeleteOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import RegionConfigEditor from './RegionConfigEditor';

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

const UpdateTaskConfigModal = ({ visible, onOk, onCancel, initialValues, agents = [] }) => {
  const [form] = Form.useForm();
  const [targetEntries, setTargetEntries] = useState([]);

  const transferMode = Form.useWatch('transferMode', form);
  const postTransferAction = Form.useWatch('postTransferAction', form);
  const routingStrategy = Form.useWatch('routingStrategy', form);

  useEffect(() => {
    if (transferMode === 'ONE_TO_ONE' && routingStrategy === 'BROADCAST') {
      form.setFieldValue('routingStrategy', 'SINGLE');
    }
  }, [transferMode, routingStrategy, form]);

  useEffect(() => {
    if (visible && initialValues) {
      let includeStr = '';
      let excludeStr = '';
      try {
        const inc = parseJsonSafe(initialValues.includePatterns);
        if (Array.isArray(inc)) includeStr = inc.join(', ');
        const exc = parseJsonSafe(initialValues.excludePatterns);
        if (Array.isArray(exc)) excludeStr = exc.join(', ');
      } catch(e) {}

      form.setFieldsValue({
        taskName: initialValues.taskName,
        taskDescription: initialValues.taskDescription,
        sourceAgentId: initialValues.sourceAgentId,
        sourceDir: initialValues.sourceDir,
        includePatternsStr: includeStr,
        excludePatternsStr: excludeStr,
        transferMode: initialValues.transferMode,
        routingStrategy: initialValues.routingStrategy,
        routingConfig: parseJsonSafe(initialValues.routingConfig),
        postTransferAction: initialValues.postTransferAction,
        backupDir: initialValues.backupDir,
        backupMode: initialValues.backupMode,
        scanFrequencySec: initialValues.scanFrequencySec,
        maxScanFiles: initialValues.maxScanFiles,
        maxBandwidthKbS: initialValues.maxBandwidthKbS,
        preserveDirStructure: initialValues.preserveDirStructure === 1,
        retryEnabled: initialValues.retryEnabled === 1,
        retryMaxDays: initialValues.retryMaxDays,
        retryIntervalMin: initialValues.retryIntervalMin,
      });

      // 初始化 targetEntries
      let entries = [];
      try {
        const targetAgents = parseJsonSafe(initialValues.targetAgents) || [];
        const targetDirsStr = initialValues.targetDirs || '';
        const targetDirsArr = targetDirsStr.split(';').filter(Boolean);
        // 如果 targetAgents 存在
        if (Array.isArray(targetAgents) && targetAgents.length > 0) {
           entries = targetAgents.map((agentId, index) => ({
             agentId,
             targetDir: targetDirsArr[index] || '',
             key: Date.now() + index
           }));
        } else if (targetDirsArr.length > 0) {
            // 兼容旧数据
           entries = targetDirsArr.map((dir, index) => ({
             agentId: '',
             targetDir: dir,
             key: Date.now() + index
           }));
        }
      } catch(e) {}
      setTargetEntries(entries);
    }
  }, [visible, initialValues, form]);

  const handleAddTarget = () => {
    setTargetEntries([...targetEntries, { agentId: '', targetDir: '', key: Date.now() }]);
  };

  const handleRemoveTarget = (key) => {
    setTargetEntries(targetEntries.filter(e => e.key !== key));
  };

  const handleTargetAgentChange = (key, agentId) => {
    setTargetEntries(targetEntries.map(e => e.key === key ? { ...e, agentId } : e));
  };

  const handleTargetDirChange = (key, targetDir) => {
    setTargetEntries(targetEntries.map(e => e.key === key ? { ...e, targetDir } : e));
  };

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      if (targetEntries.length === 0) {
        form.setFields([{ name: 'targetAgents', errors: ['请至少添加一个目标节点'] }]);
        return;
      }
      const agentIds = [];
      const dirs = [];
      for (const entry of targetEntries) {
        if (!entry.agentId) {
          form.setFields([{ name: 'targetAgents', errors: ['请为每个目标选择Agent'] }]);
          return;
        }
        if (!entry.targetDir) {
          form.setFields([{ name: 'targetAgents', errors: ['请为每个目标填写接收目录'] }]);
          return;
        }
        agentIds.push(entry.agentId);
        dirs.push(entry.targetDir);
      }
      values.targetAgents = agentIds;
      values.targetDirs = dirs.join(';');
      if (values.includePatternsStr) {
        values.includePatterns = values.includePatternsStr.split(',').map(s => s.trim()).filter(Boolean);
        delete values.includePatternsStr;
      }
      if (values.excludePatternsStr) {
        values.excludePatterns = values.excludePatternsStr.split(',').map(s => s.trim()).filter(Boolean);
        delete values.excludePatternsStr;
      }
      values.retryEnabled = values.retryEnabled !== false;
      values.preserveDirStructure = values.preserveDirStructure !== false;
      onOk(values);
    } catch (e) {
      // validation failed
    }
  };

  const onlineAgents = agents.filter(a => a.nodeStatus === 1);
  const selectedAgentIds = targetEntries.map(e => e.agentId).filter(Boolean);

  return (
    <Modal title="修改批量传输任务" open={visible} onOk={handleOk}
      onCancel={() => { form.resetFields(); setTargetEntries([]); onCancel(); }} width={780} destroyOnClose>
      <Form form={form} layout="vertical" initialValues={{
        transferMode: 'ONE_TO_MANY', routingStrategy: 'BROADCAST',
        postTransferAction: 'NONE', retryEnabled: true, preserveDirStructure: true,
        scanFrequencySec: 300, maxScanFiles: 10000, retryMaxDays: 7, retryIntervalMin: 30
      }}>
        <Row gutter={16}>
          <Col span={16}><Form.Item name="taskName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input maxLength={200} placeholder="输入任务名称" />
          </Form.Item></Col>
          <Col span={8}><Form.Item name="taskDescription" label="任务描述">
            <Input maxLength={500} placeholder="可选" />
          </Form.Item></Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}><Form.Item name="sourceAgentId" label="发送节点" rules={[{ required: true, message: '请选择发送节点' }]}>
            <Select showSearch filterOption={agentFilterOption} placeholder="输入appId或IP模糊搜索">
              {onlineAgents.map(a => (
                <Select.Option key={a.id} value={a.id} label={agentLabel(a)}>
                  {agentLabel(a)}
                </Select.Option>
              ))}
            </Select>
          </Form.Item></Col>
          <Col span={12}><Form.Item name="sourceDir" label="发送目录" rules={[{ required: true, message: '请输入发送目录' }]}>
            <Input placeholder="绝对路径, 如 /var/app/logs" />
          </Form.Item></Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}><Form.Item name="includePatternsStr" label="包含模式(Glob, 逗号分隔)">
            <Input placeholder="*.log, logs/**/*.gz" />
          </Form.Item></Col>
          <Col span={12}><Form.Item name="excludePatternsStr" label="排除模式(Glob, 逗号分隔)">
            <Input placeholder="*.tmp, temp/*" />
          </Form.Item></Col>
        </Row>

        <Card title="目标节点配置" size="small" style={{ marginBottom: 16 }}
          extra={<Button type="link" icon={<PlusOutlined />} onClick={handleAddTarget}>添加目标</Button>}>
          <Form.Item name="targetAgents" hidden>
            <Input />
          </Form.Item>
          {targetEntries.length === 0 && (
            <div style={{ textAlign: 'center', padding: 20, color: '#999' }}>
              点击"添加目标"按钮配置目标节点和接收目录
            </div>
          )}
          {targetEntries.map((entry) => (
            <Row gutter={8} key={entry.key} style={{ marginBottom: 8 }} align="middle">
              <Col span={10}>
                <Select showSearch filterOption={agentFilterOption} placeholder="输入appId或IP模糊搜索"
                  value={entry.agentId || undefined}
                  onChange={(v) => handleTargetAgentChange(entry.key, v)}
                  style={{ width: '100%' }}>
                  {onlineAgents
                    .filter(a => a.id === entry.agentId || !selectedAgentIds.includes(a.id))
                    .map(a => (
                      <Select.Option key={a.id} value={a.id} label={agentLabel(a)}>
                        {agentLabel(a)}
                      </Select.Option>
                    ))}
                </Select>
              </Col>
              <Col span={12}>
                <Input placeholder="接收目录绝对路径, 如 /data/backup"
                  value={entry.targetDir}
                  onChange={(e) => handleTargetDirChange(entry.key, e.target.value)} />
              </Col>
              <Col span={2}>
                <Button type="text" danger icon={<DeleteOutlined />}
                  onClick={() => handleRemoveTarget(entry.key)} />
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
              <Select.Option value="BROADCAST" disabled={transferMode === 'ONE_TO_ONE'}>广播</Select.Option>
              <Select.Option value="SINGLE">单机粘性</Select.Option>
              <Select.Option value="ROUND_ROBIN">轮询</Select.Option>
              <Select.Option value="REGION_BASED">区域路由</Select.Option>
              <Select.Option value="RANDOM">随机</Select.Option>
            </Select>
          </Form.Item></Col>
          <Col span={8}><Form.Item name="maxBandwidthKbS" label="带宽限制(KB/s)">
            <InputNumber min={1} placeholder="不限" style={{ width: '100%' }} />
          </Form.Item></Col>
        </Row>
        {routingStrategy === 'REGION_BASED' && (
          <Form.Item name="routingConfig" label="区域路由配置" rules={[{ required: true, message: '区域路由必须配置' }]}>
            <RegionConfigEditor />
          </Form.Item>
        )}
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
              <Col span={8}><Form.Item name="backupDir" label="备份目录" rules={[{ required: true, message: '备份必须指定目录' }]}>
                <Input placeholder="备份目录绝对路径" />
              </Form.Item></Col>
              <Col span={8}><Form.Item name="backupMode" label="备份模式" initialValue="COPY">
                <Select><Select.Option value="COPY">复制</Select.Option><Select.Option value="MOVE">移动</Select.Option></Select>
              </Form.Item></Col>
            </>
          )}
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item
              label={<span>扫描间隔(秒) <Tooltip title="系统将自动转换为Cron表达式供Agent定时扫描"><QuestionCircleOutlined /></Tooltip></span>}
              name="scanFrequencySec">
              <InputNumber min={60} max={86400} style={{ width: '100%' }} addonAfter="秒" />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}><Form.Item name="maxScanFiles" label="最大扫描数"><InputNumber min={100} max={100000} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="retryEnabled" label="自动重试" valuePropName="checked"><Switch /></Form.Item></Col>
          <Col span={8}><Form.Item name="preserveDirStructure" label="保持目录结构" valuePropName="checked"><Switch /></Form.Item></Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}><Form.Item name="retryMaxDays" label="重试保留天数"><InputNumber min={1} max={30} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={12}><Form.Item name="retryIntervalMin" label="重试间隔(分钟)"><InputNumber min={5} max={1440} style={{ width: '100%' }} /></Form.Item></Col>
        </Row>
      </Form>
    </Modal>
  );
};

export default UpdateTaskConfigModal;
