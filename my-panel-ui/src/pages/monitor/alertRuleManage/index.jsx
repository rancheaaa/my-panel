import React, { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Switch,
  notification
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  AlertOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import {
  getAlertEvents,
  getAlertRules,
  deleteAlertRule,
  saveAlertRule,
  updateAlertEventStatus
} from '../../../api/monitor/alertRule';

const CATEGORY_METRICS = {
  heap: [
    'heap_usage_pct', 'heap_heap_used_bytes', 'heap_committed_bytes', 'heap_max_bytes',
    'pool_used_bytes', 'pool_committed_bytes', 'pool_usage_pct'
  ],
  process_memory: ['process_rss_bytes', 'os_total_memory_bytes', 'os_available_memory_bytes', 'os_used_pct', 'os_available_pct'],
  gc: ['gc_count_total', 'gc_time_total_ms', 'gc_avg_pause_ms', 'gc_max_pause_ms'],
  thread: ['thread_total', 'thread_virtual_total', 'thread_blocked', 'thread_waiting'],
  cpu: ['cpu_usage_pct', 'cpu_process_usage_pct', 'cpu_user_time_pct', 'cpu_system_time_pct', 'cpu_idle_time_pct'],
  system_load: ['load_avg_1m', 'load_avg_5m', 'load_avg_15m', 'run_queue_length', 'os_process_count', 'os_thread_count'],
  db_pool: ['pool_active_connections', 'pool_idle_connections', 'pool_pending_threads', 'pool_wait_millis_avg'],
  class_loading: ['class_loaded_count', 'class_total_loaded', 'class_unloaded_count']
};

const CATEGORY_LABEL = {
  heap: '堆内存',
  process_memory: '内存',
  gc: 'GC',
  thread: '线程',
  cpu: 'CPU',
  system_load: '系统负载',
  db_pool: '连接池',
  class_loading: '类加载'
};

const METRIC_META = {
  heap_usage_pct: { name: '堆使用率', unit: '%' },
  heap_heap_used_bytes: { name: '堆已使用', unit: 'bytes' },
  heap_committed_bytes: { name: '堆已提交', unit: 'bytes' },
  heap_max_bytes: { name: '堆最大可用', unit: 'bytes' },
  pool_used_bytes: { name: '分区已使用', unit: 'bytes' },
  pool_committed_bytes: { name: '分区已提交', unit: 'bytes' },
  pool_usage_pct: { name: '分区使用率', unit: '%' },
  process_rss_bytes: { name: '进程内存占用', unit: 'bytes' },
  os_total_memory_bytes: { name: 'OS总内存', unit: 'bytes' },
  os_available_memory_bytes: { name: 'OS可用内存', unit: 'bytes' },
  os_used_pct: { name: 'OS已用率', unit: '%' },
  os_available_pct: { name: 'OS可用率', unit: '%' },
  gc_count_total: { name: 'GC次数', unit: 'count' },
  gc_time_total_ms: { name: 'GC总耗时', unit: 'ms' },
  gc_avg_pause_ms: { name: 'GC平均耗时', unit: 'ms' },
  gc_max_pause_ms: { name: 'GC最大耗时', unit: 'ms' },
  thread_total: { name: '线程总数', unit: 'count' },
  thread_virtual_total: { name: '虚拟线程总数', unit: 'count' },
  thread_blocked: { name: '阻塞线程数', unit: 'count' },
  thread_waiting: { name: '等待线程数', unit: 'count' },
  cpu_usage_pct: { name: 'CPU使用率', unit: '%' },
  cpu_process_usage_pct: { name: '进程CPU使用率', unit: '%' },
  cpu_user_time_pct: { name: '用户态CPU占比', unit: '%' },
  cpu_system_time_pct: { name: '系统态CPU占比', unit: '%' },
  cpu_idle_time_pct: { name: '空闲CPU占比', unit: '%' },
  load_avg_1m: { name: '1分钟负载', unit: '' },
  load_avg_5m: { name: '5分钟负载', unit: '' },
  load_avg_15m: { name: '15分钟负载', unit: '' },
  run_queue_length: { name: '运行队列长度', unit: 'count' },
  os_process_count: { name: '进程总数', unit: 'count' },
  os_thread_count: { name: '系统线程数', unit: 'count' },
  pool_active_connections: { name: '活跃连接数', unit: 'count' },
  pool_idle_connections: { name: '空闲连接数', unit: 'count' },
  pool_pending_threads: { name: '等待连接数', unit: 'count' },
  pool_wait_millis_avg: { name: '连接等待耗时', unit: 'ms' },
  class_loaded_count: { name: '已加载类数量', unit: 'count' },
  class_total_loaded: { name: '累计加载类数量', unit: 'count' },
  class_unloaded_count: { name: '已卸载类数量', unit: 'count' }
};

const SCOPE_LABEL = {
  eden: '伊甸园区',
  survivor: '幸存区',
  young: '新生代',
  old: '老年代',
  metaspace: '元空间',
  general: '堆'
};

const ALERT_EVENT_STATUS = [
  { label: '待处理', value: 'open', color: 'red' },
  { label: '已解决', value: 'resolved', color: 'green' },
  { label: '忽略', value: 'ignored', color: 'default' }
];

const AlertRuleManagement = () => {
  const [alertRules, setAlertRules] = useState([]);
  const [alertEvents, setAlertEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [eventsLoading, setEventsLoading] = useState(false);
  const [ruleModalOpen, setRuleModalOpen] = useState(false);
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [editingRule, setEditingRule] = useState(null);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchAlertRules();
  }, []);

  const fetchAlertRules = async () => {
    setLoading(true);
    try {
      const res = await getAlertRules();
      if (res.code === 200) {
        setAlertRules(res.data || []);
      }
    } catch (e) {
      console.error('Failed to fetch alert rules:', e);
      notification.error({ message: '获取报警规则失败' });
    } finally {
      setLoading(false);
    }
  };

  const fetchAlertEvents = async () => {
    setEventsLoading(true);
    try {
      const res = await getAlertEvents({ range: '24h', limit: 200 });
      if (res.code === 200) {
        setAlertEvents(res.data || []);
      }
    } catch (e) {
      console.error('Failed to fetch alert events:', e);
      notification.error({ message: '获取告警事件失败' });
    } finally {
      setEventsLoading(false);
    }
  };

  const handleAddRule = () => {
    setEditingRule(null);
    form.resetFields();
    setRuleModalOpen(true);
  };

  const handleEditRule = (record) => {
    setEditingRule(record);
    form.setFieldsValue({
      ruleName: record.ruleName,
      metricCategory: record.metricCategory,
      metricName: record.metricName,
      metricScope: record.metricScope || '',
      operator: record.operator,
      thresholdValue: record.thresholdValue,
      durationSeconds: record.durationSeconds,
      severity: record.severity,
      enabled: record.enabled === '1',
      description: record.description || ''
    });
    setRuleModalOpen(true);
  };

  const handleDeleteRule = async (id) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这条报警规则吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAlertRule(id);
          notification.success({ message: '删除成功' });
          fetchAlertRules();
        } catch (e) {
          notification.error({ message: '删除失败', description: e.message });
        }
      }
    });
  };

  const handleSaveRule = async () => {
    try {
      const values = await form.validateFields();
      const data = {
        ...values,
        enabled: values.enabled ? '1' : '0'
      };
      if (editingRule?.id) {
        data.id = editingRule.id;
      }
      await saveAlertRule(data);
      notification.success({ message: editingRule ? '更新成功' : '创建成功' });
      setRuleModalOpen(false);
      fetchAlertRules();
    } catch (e) {
      if (e.errorFields) return;
      notification.error({ message: '保存失败', description: e.message });
    }
  };

  const handleUpdateEventStatus = async (id, status) => {
    try {
      await updateAlertEventStatus(id, status);
      setAlertEvents(prev => prev.map(event =>
        event.id === id ? { ...event, status } : event
      ));
      notification.success({ message: '状态更新成功' });
    } catch (e) {
      notification.error({ message: '状态更新失败', description: e.message });
    }
  };

  const showEventDrawer = () => {
    setDrawerVisible(true);
    fetchAlertEvents();
  };

  const ruleColumns = useMemo(() => ([
    { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 160 },
    {
      title: '指标分类',
      dataIndex: 'metricCategory',
      key: 'metricCategory',
      width: 100,
      render: (value) => CATEGORY_LABEL[value] || value
    },
    { title: '指标名称', dataIndex: 'metricName', key: 'metricName', width: 180,
      render: (value) => {
        const meta = METRIC_META[value];
        return meta ? meta.name : value;
      }
    },
    { title: '作用域', dataIndex: 'metricScope', key: 'metricScope', width: 100,
      render: (value) => value ? (SCOPE_LABEL[value] || value) : '-'
    },
    { title: '操作符', dataIndex: 'operator', key: 'operator', width: 80 },
    { title: '阈值', dataIndex: 'thresholdValue', key: 'thresholdValue', width: 100 },
    { title: '持续时间(秒)', dataIndex: 'durationSeconds', key: 'durationSeconds', width: 120 },
    {
      title: '严重级别',
      dataIndex: 'severity',
      key: 'severity',
      width: 100,
      render: (value) => value === 'critical' ? <Tag color="red">严重</Tag> : <Tag color="gold">警告</Tag>
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (value) => value === '1' ? <Tag color="green">启用</Tag> : <Tag color="default">禁用</Tag>
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEditRule(record)}>
            编辑
          </Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteRule(record.id)}>
            删除
          </Button>
        </Space>
      )
    }
  ]), []);

  const alertColumns = useMemo(() => ([
    { title: '时间', dataIndex: 'triggerTime', key: 'triggerTime', width: 180 },
    {
      title: '级别',
      dataIndex: 'severity',
      key: 'severity',
      width: 90,
      render: (value) => value === 'critical' ? <Tag color="red">critical</Tag> : <Tag color="gold">warning</Tag>
    },
    { title: '规则', dataIndex: 'ruleName', key: 'ruleName', width: 160 },
    { title: '指标', dataIndex: 'metricName', key: 'metricName', width: 140 },
    { title: '当前值', dataIndex: 'observedValue', key: 'observedValue', width: 100 },
    { title: '阈值', dataIndex: 'thresholdValue', key: 'thresholdValue', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value, record) => (
        <Select
          size="small"
          value={value || 'open'}
          style={{ width: 100 }}
          onChange={(newStatus) => handleUpdateEventStatus(record.id, newStatus)}
        >
          {ALERT_EVENT_STATUS.map(item => (
            <Select.Option key={item.value} value={item.value}>
              <Tag color={item.color} style={{ marginRight: 4 }}>{item.label}</Tag>
            </Select.Option>
          ))}
        </Select>
      )
    },
    { title: '详情', dataIndex: 'detail', key: 'detail', ellipsis: true }
  ]), []);

  return (
    <div className="app-container">
      <Card
        title="报警规则管理"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchAlertRules} loading={loading}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAddRule}>
              新增规则
            </Button>
            <Button
              type="primary"
              danger
              ghost
              icon={<AlertOutlined />}
              onClick={showEventDrawer}
            >
              告警事件
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={ruleColumns}
          dataSource={alertRules}
          loading={loading}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 1400 }}
          locale={{ emptyText: <Empty description="暂无报警规则，点击上方按钮新增" /> }}
        />
      </Card>

      <Modal
        title={editingRule ? '编辑报警规则' : '新增报警规则'}
        open={ruleModalOpen}
        onOk={handleSaveRule}
        onCancel={() => setRuleModalOpen(false)}
        width={700}
        destroyOnClose
        okText="保存"
        cancelText="取消"
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            enabled: true,
            durationSeconds: 60,
            severity: 'warning'
          }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="规则名称"
                name="ruleName"
                rules={[{ required: true, message: '请输入规则名称' }]}
              >
                <Input placeholder="例如：堆内存使用率过高" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="指标分类"
                name="metricCategory"
                rules={[{ required: true, message: '请选择指标分类' }]}
              >
                <Select placeholder="请选择指标分类">
                  {Object.keys(CATEGORY_METRICS).map(key => (
                    <Select.Option key={key} value={key}>{CATEGORY_LABEL[key]}</Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="指标名称"
                name="metricName"
                rules={[{ required: true, message: '请输入指标名称' }]}
              >
                <Input placeholder="例如：heap_usage_pct" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="作用域"
                name="metricScope"
              >
                <Input placeholder="可选，例如：young、old、eden等" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                label="操作符"
                name="operator"
                rules={[{ required: true, message: '请选择操作符' }]}
              >
                <Select placeholder="请选择操作符">
                  <Select.Option value="gt">大于 (&gt;)</Select.Option>
                  <Select.Option value="gte">大于等于 (≥)</Select.Option>
                  <Select.Option value="lt">小于 (&lt;)</Select.Option>
                  <Select.Option value="lte">小于等于 (≤)</Select.Option>
                  <Select.Option value="eq">等于 (=)</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                label="阈值"
                name="thresholdValue"
                rules={[{ required: true, message: '请输入阈值' }]}
              >
                <InputNumber style={{ width: '100%' }} placeholder="阈值数值" min={0} step={0.1} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                label="持续时间(秒)"
                name="durationSeconds"
                rules={[{ required: true, message: '请输入持续时间' }]}
              >
                <InputNumber style={{ width: '100%' }} min={10} max={3600} step={10} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                label="严重级别"
                name="severity"
                rules={[{ required: true, message: '请选择严重级别' }]}
              >
                <Select placeholder="请选择严重级别">
                  <Select.Option value="warning">警告</Select.Option>
                  <Select.Option value="critical">严重</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                label="启用状态"
                name="enabled"
                valuePropName="checked"
              >
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            label="描述"
            name="description"
          >
            <Input.TextArea rows={3} placeholder="规则描述信息（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <Space>
            <AlertOutlined />
            告警事件管理
          </Space>
        }
        placement="right"
        width={800}
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
        destroyOnClose
      >
        <Table
          rowKey="id"
          columns={alertColumns}
          dataSource={alertEvents}
          loading={eventsLoading}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 1100 }}
          size="small"
        />
      </Drawer>
    </div>
  );
};

export default AlertRuleManagement;
