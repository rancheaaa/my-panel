import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as echarts from 'echarts';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Progress,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Switch,
  Tag,
  notification
} from 'antd';
import {
  ReloadOutlined,
  ExperimentOutlined,
  ApiOutlined,
  DatabaseOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  SettingOutlined
} from '@ant-design/icons';
import {
  getDashboardData,
  getDashboardTrend,
  getServiceInstances
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
  if (range === '1m') return '10s';
  if (range === '5m') return '30s';
  if (range === '15m') return '1m';
  if (range === '1h') return '5m';
  if (range === '6h') return '15m';
  if (range === '1d') return '1h';
  return '1d';
};

const EChartLineCard = ({ title, unit, categoryLabel, series = [], chartIndex = 0, bytesUnit = 1, onBytesUnitChange, metricName, timeRange }) => {
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
    
    let xAxisMin = null;
    let xAxisMax = null;
    if (series && series.length > 0) {
      const allPoints = series.flatMap(s => s.points || []);
      if (allPoints.length > 0) {
        const timestamps = allPoints.map(p => {
          if (typeof p.time === 'string') return new Date(p.time.replace(' ', 'T')).getTime();
          if (p.time instanceof Date) return p.time.getTime();
          return Number(p.time);
        }).filter(t => !isNaN(t));
        if (timestamps.length > 0) {
          xAxisMin = Math.min(...timestamps);
          xAxisMax = Math.max(...timestamps);
        }
      }
    }
    
    const rangeMs = (timeRange?.endTime && timeRange?.beginTime) ? (timeRange.endTime - timeRange.beginTime) : 0;
    const padding = rangeMs * 0.02;
    
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
        min: xAxisMin ? xAxisMin - padding : null,
        max: xAxisMax ? xAxisMax + padding : null,
        axisLine: { lineStyle: { color: '#94a3b8' } },
        splitLine: { show: false },
        axisLabel: { 
          color: '#64748b',
          formatter: (value) => {
            const date = new Date(value);
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            const seconds = String(date.getSeconds()).padStart(2, '0');
            if (timeRange && timeRange.endTime && timeRange.beginTime) {
              const rangeMs = timeRange.endTime - timeRange.beginTime;
              if (rangeMs <= 5 * 60 * 1000) {
                return `${hours}:${minutes}:${seconds}`;
              }
            }
            return `${hours}:${minutes}`;
          }
        }
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
        { 
          type: 'inside', 
          xAxisIndex: 0, 
          filterMode: 'none', 
          zoomOnMouseWheel: true, 
          moveOnMouseMove: true,
          start: 0,
          end: 100
        },
        {
          type: 'slider',
          xAxisIndex: 0,
          filterMode: 'none',
          bottom: 16,
          height: 20,
          borderColor: 'rgba(148, 163, 184, 0.2)',
          backgroundColor: 'rgba(148, 163, 184, 0.08)',
          fillerColor: 'rgba(22, 119, 255, 0.24)',
          start: 0,
          end: 100
        }
      ],
      series: series.map((item, index) => {
        const color = CHART_COLORS[(chartIndex + index) % CHART_COLORS.length] || baseColor;
        const processedData = (item.points || []).map((point) => {
          const scaledValue = isBytesMetric ? point.value / bytesUnit : point.value;
          let timestamp;
          if (typeof point.time === 'string') {
            const timeStr = point.time.replace(' ', 'T');
            timestamp = new Date(timeStr).getTime();
            if (isNaN(timestamp)) {
              console.warn('[Dashboard] 无法解析时间:', point.time);
              timestamp = Date.now();
            }
          } else if (point.time instanceof Date) {
            timestamp = point.time.getTime();
          } else {
            timestamp = Number(point.time) || Date.now();
          }
          return [timestamp, scaledValue];
        }).sort((a, b) => a[0] - b[0]);
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
  }, [series, bytesUnit, timeRange]);

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
  const [bytesUnit, setBytesUnit] = useState(1024 * 1024);
  const [currentTimeRange, setCurrentTimeRange] = useState({ beginTime: null, endTime: null });
  const [serviceInstances, setServiceInstances] = useState([]);
  const [selectedServiceId, setSelectedServiceId] = useState('');
  const [selectedInstance, setSelectedInstance] = useState('');

  const fetchServiceInstances = async () => {
    try {
      const res = await getServiceInstances();
      if (res.code === 200 && res.data && res.data.length > 0) {
        setServiceInstances(res.data);
        if (!selectedInstance && res.data.length > 0) {
          const firstInstance = res.data[0];
          setSelectedServiceId(firstInstance.serviceId);
          setSelectedInstance(`${firstInstance.serviceId}@${firstInstance.serviceIpPort}`);
        }
      }
    } catch (e) {
      console.error('Failed to fetch service instances:', e);
    }
  };

  const doFetch = async (silent = false) => {
    if (!silent) setLoading(true);
    setRefreshing(true);
    const end = new Date();
    const begin = new Date(end.getTime() - parseRangeToMillis(range));
    const granularity = getGranularityByRange(range);
    setCurrentTimeRange({ beginTime: begin.getTime(), endTime: end.getTime() });
    try {
      const [overviewRes, trendRes] = await Promise.all([
        getDashboardData(),
        getDashboardTrend({
          category: activeCategory,
          metricNames: CATEGORY_METRICS[activeCategory],
          granularity,
          beginTime: begin,
          endTime: end,
          serviceId: selectedServiceId || undefined
        })
      ]);

      if (overviewRes.code === 200) {
        setOverview(overviewRes.data || {});
      }

      setTrendMap({
        [activeCategory]: trendRes?.code === 200 ? (trendRes.data?.series || []) : []
      });
      
      if (trendRes?.code === 200 && trendRes.data) {
        console.log('[Dashboard] 趋势数据查询结果:', {
          category: trendRes.data.category,
          beginTime: trendRes.data.beginTime,
          endTime: trendRes.data.endTime,
          seriesCount: trendRes.data.series?.length || 0,
          sampleSeries: trendRes.data.series?.[0] ? {
            metricName: trendRes.data.series[0].metricName,
            pointsCount: trendRes.data.series[0].points?.length || 0,
            firstPoint: trendRes.data.series[0].points?.[0],
            lastPoint: trendRes.data.series[0].points?.[trendRes.data.series[0].points?.length - 1]
          } : null
        });
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchServiceInstances();
    doFetch();
    if (!autoRefresh) {
      return undefined;
    }
    const timer = setInterval(() => doFetch(true), refreshInterval);
    return () => clearInterval(timer);
  }, [range, activeCategory, autoRefresh, refreshInterval, selectedInstance]);

  const stats = overview?.basicStats || {};
  const alertSummary = overview?.alertSummary || {};

  const summaryCards = [
    {
      title: '进程内存占用',
      value: (Number(stats.processRss || 0) / (1024 * 1024)).toFixed(2),
      suffix: 'MB',
      percent: Math.min(Number(stats.processRss || 0) / (1024 * 1024 * 1024) * 100, 100),
      icon: <ExperimentOutlined />,
      color: '#6366f1',
      cardClass: 'rss-summary-card'
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
      title: 'CPU使用率',
      value: Number(stats.cpuUsage || 0).toFixed(2),
      suffix: '%',
      percent: Math.min(Number(stats.cpuUsage || 0), 100),
      icon: <ApiOutlined />,
      color: '#8b5cf6',
      cardClass: 'cpu-summary-card'
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
      let chartKey;
      if (isPoolBytes) {
        chartKey = `${activeCategory}|pool_bytes|${scope}`;
        if (!grouped[chartKey]) {
          grouped[chartKey] = { key: chartKey, category: activeCategory, categoryLabel: CATEGORY_LABEL[activeCategory] || activeCategory, metricName, title: (SCOPE_LABEL[scope] || scope) + ' - 分区内存', unit: 'bytes', series: [] };
        }
      } else if (isHeapBytes) {
        chartKey = `${activeCategory}|heap_bytes`;
        if (!grouped[chartKey]) {
          grouped[chartKey] = { key: chartKey, category: activeCategory, categoryLabel: CATEGORY_LABEL[activeCategory] || activeCategory, metricName, title: '堆内存使用', unit: 'bytes', series: [] };
        }
      } else {
        chartKey = `${activeCategory}|${metricName}|${scope}`;
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
            {serviceInstances.length > 0 && (
              <div className="toolbar-item-group" style={{ marginRight: 8 }}>
                <span className="toolbar-item-label">服务实例</span>
                <Select
                  value={selectedInstance}
                  className="dashboard-select dashboard-select-single"
                  style={{ width: 280, marginLeft: 6 }}
                  onChange={(value) => {
                    setSelectedInstance(value);
                    setSelectedServiceId(value.split('@')[0]);
                  }}
                  options={serviceInstances.map(instance => ({
                    value: `${instance.serviceId}@${instance.serviceIpPort}`,
                    label: `${instance.serviceId} @ ${instance.serviceIpPort}`
                  }))}
                  allowClear
                  onClear={() => {
                    setSelectedInstance('');
                    setSelectedServiceId('');
                  }}
                />
              </div>
            )}
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
              timeRange={currentTimeRange}
            />
          </Col>
        ))}
      </Row>

    </div>
  );
};

export default ServiceDashboard;
