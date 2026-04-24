import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as echarts from 'echarts';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Switch,
  Table,
  Tag,
  notification
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  ExperimentOutlined,
  ApiOutlined,
  DatabaseOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  SettingOutlined
} from '@ant-design/icons';
import {
  getAlertEvents,
  getAlertRules,
  getDashboardData,
  getDashboardTrend,
  deleteAlertRule,
  saveAlertRule,
  updateAlertEventStatus
} from '../../../api/monitor/dashboard';
import './index.scss';

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

const TIME_RANGE_OPTIONS = [
  { label: '近1分钟', value: '1m' },
  { label: '近5分钟', value: '5m' },
  { label: '近15分钟', value: '15m' },
  { label: '近1小时', value: '1h' },
  { label: '近6小时', value: '6h' },
  { label: '近1天', value: '1d' },
  { label: '近3天', value: '3d' },
  { label: '近7天', value: '7d' }
];

const GRANULARITY_OPTIONS = [
  { label: '1分钟', value: '1m' },
  { label: '5分钟', value: '5m' },
  { label: '15分钟', value: '15m' },
  { label: '1小时', value: '1h' },
  { label: '1天', value: '1d' }
];

const REFRESH_INTERVAL_OPTIONS = [
  { label: '10秒', value: 10000 },
  { label: '30秒', value: 30000 },
  { label: '1分钟', value: 60000 },
  { label: '5分钟', value: 300000 }
];

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

const CHART_COLORS = ['#1677ff', '#13c2c2', '#722ed1', '#eb2f96', '#fa8c16', '#2f54eb', '#52c41a', '#f5222d'];

const SCOPE_LABEL = {
  eden: '伊甸园区',
  survivor: '幸存区',
  young: '新生代',
  old: '老年代',
  metaspace: '元空间',
  general: '堆'
};

const BYTES_METRICS = [
  'heap_heap_used_bytes', 'heap_committed_bytes', 'heap_max_bytes',
  'pool_used_bytes', 'pool_committed_bytes',
  'process_rss_bytes', 'os_total_memory_bytes', 'os_available_memory_bytes'
];

const ALERT_EVENT_STATUS = [
  { label: '待处理', value: 'open', color: 'red' },
  { label: '已解决', value: 'resolved', color: 'green' },
  { label: '忽略', value: 'ignored', color: 'default' }
];

const BYTES_UNITS = [
  { label: 'B', value: 1 },
  { label: 'KB', value: 1024 },
  { label: 'MB', value: 1024 * 1024 },
  { label: 'GB', value: 1024 * 1024 * 1024 }
];

const formatBytes = (bytes, unitValue) => {
  if (bytes == null || isNaN(bytes)) return '0 B';
  return (bytes / unitValue).toFixed(2) + ' ' + BYTES_UNITS.find(u => u.value === unitValue)?.label || 'B';
};

const getPreferredUnit = (metricName) => {
  return BYTES_METRICS.includes(metricName) ? 'MB' : null;
};

const parseRangeToMillis = (range) => {
  const unit = range.slice(-1);
  const n = Number(range.slice(0, -1));
  if (unit === 'm') return n * 60 * 1000;
  if (unit === 'h') return n * 60 * 60 * 1000;
  return n * 24 * 60 * 60 * 1000;
};

const getGranularityByRange = (range) => {
  if (range === '1m' || range === '5m' || range === '15m') return '1m';
  if (range === '1h') return '5m';
  if (range === '6h') return '15m';
  if (range === '1d') return '1h';
  return '1d';
};

const EChartLineCard = ({ title, unit, categoryLabel, series = [], chartIndex = 0, bytesUnit = 1, onBytesUnitChange, metricName }) => {
  const containerRef = useRef(null);
  const chartRef = useRef(null);
  const isBytesMetric = BYTES_METRICS.includes(metricName);

  useEffect(() => {
    if (!containerRef.current) return;
    if (!chartRef.current) {
      chartRef.current = echarts.init(containerRef.current);
    }
    const chart = chartRef.current;
    const baseColor = CHART_COLORS[chartIndex % CHART_COLORS.length];
    const displayUnit = isBytesMetric ? (BYTES_UNITS.find(u => u.value === bytesUnit)?.label || 'B') : unit;
    const valueFormatter = (value) => {
      if (isBytesMetric) {
        return `${Number(value).toFixed(2)} ${displayUnit}`;
      }
      return `${Number(value).toFixed(2)}${displayUnit ? ` ${displayUnit}` : ''}`;
    };
    const option = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(17, 24, 39, 0.92)',
        borderColor: 'rgba(59, 130, 246, 0.4)',
        textStyle: { color: '#dbeafe' },
        valueFormatter
      },
      legend: {
        type: 'scroll',
        top: 0,
        textStyle: { color: '#4b5563' }
      },
      grid: { left: 56, right: isBytesMetric ? 100 : 30, top: 44, bottom: 64 },
      xAxis: {
        type: 'time',
        axisLine: { lineStyle: { color: '#94a3b8' } },
        splitLine: { show: false },
        axisLabel: { color: '#64748b' }
      },
      yAxis: {
        type: 'value',
        scale: true,
        axisLine: { lineStyle: { color: '#94a3b8' } },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.25)', type: 'dashed' } },
        axisLabel: {
          color: '#64748b',
          formatter: (value) => {
            if (isBytesMetric) {
              return `${Number(value).toFixed(2)} ${displayUnit}`;
            }
            return `${Number(value).toFixed(0)}${displayUnit ? ` ${displayUnit}` : ''}`;
          }
        }
      },
      dataZoom: [
        { type: 'inside', xAxisIndex: 0, filterMode: 'none', zoomOnMouseWheel: true, moveOnMouseMove: true },
        {
          type: 'slider',
          xAxisIndex: 0,
          filterMode: 'none',
          bottom: 16,
          height: 20,
          borderColor: 'rgba(148, 163, 184, 0.2)',
          backgroundColor: 'rgba(148, 163, 184, 0.08)',
          fillerColor: 'rgba(22, 119, 255, 0.24)'
        }
      ],
      series: series.map((item, index) => {
        const color = CHART_COLORS[(chartIndex + index) % CHART_COLORS.length] || baseColor;
        const processedData = (item.points || []).map((point) => {
          const scaledValue = isBytesMetric ? point.value / bytesUnit : point.value;
          return [point.time, scaledValue];
        });
        return {
          type: 'line',
          name: item.metricScope ? (SCOPE_LABEL[item.metricScope] || item.metricScope) + ' - ' + (METRIC_META[item.metricName]?.name || item.metricName) : (METRIC_META[item.metricName]?.name || item.tagName || '总览'),
          showSymbol: false,
          smooth: true,
          symbol: 'circle',
          lineStyle: { width: 2.5, color },
          itemStyle: { color },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: `${color}66` },
              { offset: 1, color: `${color}08` }
            ])
          },
          emphasis: { focus: 'series' },
          data: processedData
        };
      })
    };
    chart.setOption(option, true);
    const onResize = () => chart.resize();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [series, bytesUnit]);

  useEffect(() => () => {
    if (chartRef.current) {
      chartRef.current.dispose();
      chartRef.current = null;
    }
  }, []);

  return (
    <Card
      className="metric-chart-card"
      title={
        <div className="metric-chart-title">
          <span>{title}</span>
          <Tag color="blue">{categoryLabel}</Tag>
        </div>
      }
      extra={
        isBytesMetric ? (
          <Select
            size="small"
            value={bytesUnit}
            onChange={onBytesUnitChange}
            options={BYTES_UNITS}
            style={{ width: 70 }}
          />
        ) : null
      }
      style={{ marginBottom: 16 }}
    >
      {series.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
      ) : (
        <div ref={containerRef} style={{ height: 300 }} />
      )}
    </Card>
  );
};

const ServiceDashboard = () => {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const getStoredValue = (key, defaultValue) => {
    try {
      const stored = localStorage.getItem(key);
      return stored !== null ? JSON.parse(stored) : defaultValue;
    } catch (e) {
      return defaultValue;
    }
  };

  const STORAGE_KEYS = {
    RANGE: 'dashboard_range',
    AUTO_REFRESH: 'dashboard_auto_refresh',
    REFRESH_INTERVAL: 'dashboard_refresh_interval'
  };

  const [range, setRangeState] = useState(() => getStoredValue(STORAGE_KEYS.RANGE, '1h'));
  const [autoRefresh, setAutoRefreshState] = useState(() => getStoredValue(STORAGE_KEYS.AUTO_REFRESH, true));
  const [refreshInterval, setRefreshIntervalState] = useState(() => getStoredValue(STORAGE_KEYS.REFRESH_INTERVAL, 60000));

  const setRange = (value) => {
    setRangeState(value);
    localStorage.setItem(STORAGE_KEYS.RANGE, JSON.stringify(value));
  };

  const setAutoRefresh = (value) => {
    setAutoRefreshState(value);
    localStorage.setItem(STORAGE_KEYS.AUTO_REFRESH, JSON.stringify(value));
  };

  const setRefreshInterval = (value) => {
    setRefreshIntervalState(value);
    localStorage.setItem(STORAGE_KEYS.REFRESH_INTERVAL, JSON.stringify(value));
  };
  const [activeCategory, setActiveCategory] = useState('heap');
  const [overview, setOverview] = useState(null);
  const [trendMap, setTrendMap] = useState({});
  const [alertEvents, setAlertEvents] = useState([]);
  const [bytesUnit, setBytesUnit] = useState(1024 * 1024);
  const [alertRules, setAlertRules] = useState([]);
  const [ruleModalOpen, setRuleModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState(null);
  const [notifiedEventIds, setNotifiedEventIds] = useState(new Set());
  const [form] = Form.useForm();

  const doFetch = async (silent = false) => {
    if (!silent) setLoading(true);
    setRefreshing(true);
    const end = new Date();
    const begin = new Date(end.getTime() - parseRangeToMillis(range));
    const granularity = getGranularityByRange(range);
    try {
      const [overviewRes, alertRes, trendRes] = await Promise.all([
        getDashboardData(),
        getAlertEvents({ range: '24h', limit: 200 }),
        getDashboardTrend({
          category: activeCategory,
          metricNames: CATEGORY_METRICS[activeCategory],
          granularity,
          beginTime: begin,
          endTime: end
        })
      ]);

      if (overviewRes.code === 200) {
        setOverview(overviewRes.data || {});
      }
      if (alertRes.code === 200) {
        const events = alertRes.data || [];
        setAlertEvents(events);

        const pendingStatuses = ['open', '待处理', 'pending'];
        const newPendingEvents = events.filter(event =>
          !notifiedEventIds.has(event.id) && pendingStatuses.includes((event.status || '').toLowerCase())
        );

        if (newPendingEvents.length > 0) {
          newPendingEvents.forEach(event => {
            const isCritical = event.severity === 'critical';
            notification[isCritical ? 'error' : 'warning']({
              message: `${isCritical ? '严重告警' : '警告'}: ${event.ruleName}`,
              description: `指标: ${event.metricName} | 当前值: ${event.observedValue} | 阈值: ${event.thresholdValue}`,
              duration: 10,
              placement: 'topRight'
            });
          });

          const MAX_NOTIFIED_IDS = 1000;
          setNotifiedEventIds(prev => {
            const updated = new Set([...prev, ...newPendingEvents.map(e => e.id)]);
            if (updated.size > MAX_NOTIFIED_IDS) {
              const arr = Array.from(updated);
              return new Set(arr.slice(arr.length - MAX_NOTIFIED_IDS));
            }
            return updated;
          });
        }
      }

      setTrendMap({
        [activeCategory]: trendRes?.code === 200 ? (trendRes.data?.series || []) : []
      });
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    doFetch();
    fetchAlertRules();
    if (!autoRefresh) {
      return undefined;
    }
    const timer = setInterval(() => doFetch(true), refreshInterval);
    return () => clearInterval(timer);
  }, [range, activeCategory, autoRefresh, refreshInterval]);

  const fetchAlertRules = async () => {
    try {
      const res = await getAlertRules();
      if (res.code === 200) {
        setAlertRules(res.data || []);
      }
    } catch (e) {
      console.error('Failed to fetch alert rules:', e);
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
      if (editingRule) {
        data.id = editingRule.id;
      }
      await saveAlertRule(data);
      notification.success({ message: editingRule ? '更新成功' : '创建成功' });
      setRuleModalOpen(false);
      fetchAlertRules();
    } catch (e) {
      if (e.errorFields) {
        return;
      }
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
      doFetch(true);
    } catch (e) {
      notification.error({ message: '状态更新失败', description: e.message });
    }
  };

  const stats = overview?.basicStats || {};
  const alertSummary = overview?.alertSummary || {};

  const summaryCards = [
    {
      title: 'CPU使用率',
      value: Number(stats.cpuUsage || 0).toFixed(2),
      suffix: '%',
      percent: Math.min(Number(stats.cpuUsage || 0), 100),
      icon: <ExperimentOutlined />,
      color: '#6366f1',
      cardClass: 'cpu-summary-card'
    },
    {
      title: 'CPU核心数',
      value: Number(stats.cpuCore || 0).toString(),
      suffix: '',
      percent: 0,
      icon: <ApiOutlined />,
      color: '#8b5cf6',
      cardClass: 'core-summary-card'
    },
    {
      title: '堆使用率',
      value: Number(stats.heapUsage || 0).toFixed(2),
      suffix: '%',
      percent: Math.min(Number(stats.heapUsage || 0), 100),
      icon: <DatabaseOutlined />,
      color: '#10b981',
      cardClass: 'heap-summary-card'
    },
    {
      title: '线程总数',
      value: Number(stats.threadTotal || 0).toString(),
      suffix: '',
      percent: 0,
      icon: <TeamOutlined />,
      color: '#f59e0b',
      cardClass: 'thread-summary-card'
    },
    {
      title: '最近1分钟负载',
      value: Number(stats.load1m || 0).toFixed(2),
      suffix: '',
      percent: Math.min(Number(stats.load1m || 0) * 20, 100),
      icon: <ThunderboltOutlined />,
      color: '#ef4444',
      cardClass: 'load-summary-card'
    },
    {
      title: 'GC回收器',
      value: stats.gcCollector || '-',
      suffix: '',
      percent: 0,
      icon: <SettingOutlined />,
      color: '#06b6d4',
      cardClass: 'gc-summary-card'
    }
  ];

  const metricCharts = useMemo(() => {
    const grouped = {};
    const seriesList = trendMap[activeCategory] || [];
    seriesList.forEach((series) => {
      const metricName = series.metricName;
      const scope = series.metricScope || '';
      if (scope === 'code' || scope === 'general') {
        return;
      }
      const isPoolBytes = metricName === 'pool_used_bytes' || metricName === 'pool_committed_bytes';
      const isHeapBytes = metricName === 'heap_heap_used_bytes' || metricName === 'heap_committed_bytes';
      if (isPoolBytes) {
        var chartKey = `${activeCategory}|pool_bytes|${scope}`;
        if (!grouped[chartKey]) {
          grouped[chartKey] = { key: chartKey, category: activeCategory, categoryLabel: CATEGORY_LABEL[activeCategory] || activeCategory, metricName, title: (SCOPE_LABEL[scope] || scope) + ' - 分区内存', unit: 'bytes', series: [] };
        }
      } else if (isHeapBytes) {
        var chartKey = `${activeCategory}|heap_bytes`;
        if (!grouped[chartKey]) {
          grouped[chartKey] = { key: chartKey, category: activeCategory, categoryLabel: CATEGORY_LABEL[activeCategory] || activeCategory, metricName, title: '堆内存使用', unit: 'bytes', series: [] };
        }
      } else {
        var chartKey = `${activeCategory}|${metricName}|${scope}`;
        if (!grouped[chartKey]) {
          const meta = METRIC_META[metricName] || {};
          const scopeLabel = scope ? (SCOPE_LABEL[scope] || scope) + ' - ' : '';
          grouped[chartKey] = { key: chartKey, category: activeCategory, categoryLabel: CATEGORY_LABEL[activeCategory] || activeCategory, metricName, title: scopeLabel + (meta.name || metricName), unit: meta.unit || series.metricUnit || '', series: [] };
        }
      }
      grouped[chartKey].series.push(series);
    });
    return Object.values(grouped).sort((a, b) => {
      const ORDER = ['heap_bytes', 'heap_usage_pct', 'heap_max_bytes', 'pool_bytes|young', 'pool_usage_pct|young',
        'pool_bytes|eden', 'pool_bytes|survivor', 'pool_bytes|old', 'pool_usage_pct|old',
        'pool_bytes|metaspace', 'pool_usage_pct|metaspace'];
      const normalizeKey = (key) => {
        let k = key.replace(`${activeCategory}|`, '');
        if (k.endsWith('|')) k = k.slice(0, -1);
        return k;
      };
      const aKey = normalizeKey(a.key);
      const bKey = normalizeKey(b.key);
      return ORDER.indexOf(aKey) - ORDER.indexOf(bKey);
    });
  }, [activeCategory, trendMap]);

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
      render: (value, record) => {
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
    { title: '规则', dataIndex: 'ruleName', key: 'ruleName', width: 180 },
    { title: '指标', dataIndex: 'metricName', key: 'metricName', width: 160 },
    { title: '当前值', dataIndex: 'observedValue', key: 'observedValue', width: 120 },
    { title: '阈值', dataIndex: 'thresholdValue', key: 'thresholdValue', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
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
    { title: '详情', dataIndex: 'detail', key: 'detail' }
  ]), []);

  if (loading) {
    return <Spin style={{ width: '100%', marginTop: 100 }} />;
  }

  return (
    <div className="app-container monitor-dashboard-page">
      <Card className="dashboard-toolbar-card" style={{ marginBottom: 24, padding: '20px 24px' }}>
        <Space wrap size="large" style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space wrap size="large">
            <div className="toolbar-item-group" style={{ marginRight: 8 }}>
              <span className="toolbar-item-label required">时间范围</span>
              <Select
                value={range}
                options={TIME_RANGE_OPTIONS}
                className="dashboard-select dashboard-select-single"
                style={{ width: 140, marginLeft: 6 }}
                onChange={setRange}
              />
            </div>
            <div className="toolbar-item-group" style={{ marginRight: 16 }}>
              <span className="toolbar-item-label">自动刷新</span>
              <Switch
                checked={autoRefresh}
                onChange={setAutoRefresh}
                className="toolbar-refresh-switch"
                style={{ marginLeft: 6 }}
              />
            </div>
            <div className="toolbar-item-group" style={{ marginRight: 8 }}>
              <span className="toolbar-item-label">刷新间隔</span>
              <Select
                value={refreshInterval}
                options={REFRESH_INTERVAL_OPTIONS}
                className="dashboard-select dashboard-select-single"
                style={{ width: 140, marginLeft: 6 }}
                disabled={!autoRefresh}
                onChange={setRefreshInterval}
              />
            </div>
            <Radio.Group
              value={activeCategory}
              onChange={(e) => setActiveCategory(e.target.value)}
              optionType="button"
              buttonStyle="solid"
              size="middle"
              style={{ marginLeft: 12 }}
            >
              {Object.keys(CATEGORY_METRICS).map((key) => (
                <Radio.Button key={key} value={key}>
                  {CATEGORY_LABEL[key] || key}
                </Radio.Button>
              ))}
            </Radio.Group>
          </Space>
          <a onClick={() => doFetch(true)}>
            <ReloadOutlined spin={refreshing} /> 刷新
          </a>
        </Space>
      </Card>

      <Row gutter={[24, 0]}>
        {summaryCards.map((card, index) => (
          <Col span={4} key={index}>
            <Card className={`summary-card ${card.cardClass}`} hoverable>
              <div className="summary-content">
                <div className="summary-icon">
                  {card.icon}
                </div>
                <div className="summary-info">
                  <div className="summary-label">{card.title}</div>
                  <div className="summary-value" style={{ color: card.color }}>
                    {card.value}{card.suffix}
                  </div>
                </div>
              </div>
              {card.percent > 0 && (
                <Progress
                  percent={card.percent}
                  strokeColor={card.color}
                  strokeWidth={6}
                  showInfo={false}
                  className="summary-progress"
                />
              )}
            </Card>
          </Col>
        ))}
      </Row>

      <Alert
        style={{ marginTop: 24, marginBottom: 24 }}
        message={`告警汇总：critical ${alertSummary.critical || 0}，warning ${alertSummary.warning || 0}，总计 ${alertSummary.total || 0}`}
        type={(alertSummary.critical || 0) > 0 ? 'error' : (alertSummary.warning || 0) > 0 ? 'warning' : 'success'}
        showIcon
      />

      <Row gutter={[24, 24]}>
        {metricCharts.map((chart, index) => (
          <Col key={chart.key} xs={24} xl={12}>
            <EChartLineCard
              title={chart.title}
              unit={chart.unit}
              categoryLabel={chart.categoryLabel}
              series={chart.series}
              chartIndex={index}
              bytesUnit={bytesUnit}
              onBytesUnitChange={setBytesUnit}
              metricName={chart.metricName}
            />
          </Col>
        ))}
      </Row>

      <Card
        className="alert-rule-card"
        title="报警规则管理"
        style={{ marginTop: 24, marginBottom: 24 }}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAddRule}>
            新增规则
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={ruleColumns}
          dataSource={alertRules}
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

      <Card className="alert-table-card" title="告警事件" style={{ marginTop: 24, marginBottom: 24 }}>
        <Table
          rowKey="id"
          columns={alertColumns}
          dataSource={alertEvents}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 1300 }}
        />
      </Card>

      <Card className="hint-card" title="展示能力说明" style={{ marginTop: 24 }}>
        <div className="hint-list">
          <span>1. 当前已按“一个指标一个图表”展示趋势，便于快速对比定位。</span>
          <span>2. 图表支持缩放、平移、悬浮提示，支持多时间粒度与指标类型筛选。</span>
          <span>3. 采集间隔默认10秒，可通过参数 `sys.monitor.collectIntervalMs` 调整。</span>
          <span>4. 历史保留默认7天，可通过参数 `sys.monitor.retentionDays` 调整。</span>
        </div>
      </Card>
    </div>
  );
};

export default ServiceDashboard;
