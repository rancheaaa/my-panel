import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as echarts from 'echarts';
import {
  Alert,
  Card,
  Col,
  Empty,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Switch,
  Table,
  Tag
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import {
  getAlertEvents,
  getDashboardData,
  getDashboardTrend
} from '../../../api/monitor/dashboard';
import './index.scss';

const CATEGORY_METRICS = {
  heap: [
    'heap_usage_pct', 'heap_heap_used_bytes', 'heap_committed_bytes', 'heap_max_bytes',
    'heap_non_heap_used_bytes', 'heap_non_heap_committed_bytes',
    'pool_usage_pct'
  ],
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
  gc: 'GC',
  thread: '线程',
  cpu: 'CPU',
  system_load: '系统负载',
  db_pool: '连接池',
  class_loading: '类加载'
};

const METRIC_META = {
  heap_usage_pct: { name: '堆使用率', unit: '%' },
  heap_heap_used_bytes: { name: '堆内已使用', unit: 'bytes' },
  heap_committed_bytes: { name: '堆已提交', unit: 'bytes' },
  heap_max_bytes: { name: '堆最大可用', unit: 'bytes' },
  heap_non_heap_used_bytes: { name: '堆外已使用', unit: 'bytes' },
  heap_non_heap_committed_bytes: { name: '堆外已提交', unit: 'bytes' },
  pool_usage_pct: { name: '内存池使用率', unit: '%' },
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

const BYTES_METRICS = [
  'heap_heap_used_bytes', 'heap_committed_bytes', 'heap_max_bytes',
  'heap_non_heap_used_bytes', 'heap_non_heap_committed_bytes',
  'pool_used_bytes', 'pool_committed_bytes', 'pool_max_bytes'
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
        return formatBytes(value, bytesUnit);
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
              return formatBytes(value, bytesUnit);
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
          name: item.metricScope ? `${item.metricScope}` : (item.tagName || '总览'),
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
  const [range, setRange] = useState('1h');
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshInterval, setRefreshInterval] = useState(60000);
  const [selectedCategories, setSelectedCategories] = useState(Object.keys(CATEGORY_METRICS));
  const [overview, setOverview] = useState(null);
  const [trendMap, setTrendMap] = useState({});
  const [alertEvents, setAlertEvents] = useState([]);
  const [bytesUnit, setBytesUnit] = useState(1024 * 1024);

  const doFetch = async (silent = false) => {
    if (!silent) setLoading(true);
    setRefreshing(true);
    const end = new Date();
    const begin = new Date(end.getTime() - parseRangeToMillis(range));
    const granularity = getGranularityByRange(range);
    try {
      const [overviewRes, alertRes, ...trendResponses] = await Promise.all([
        getDashboardData(),
        getAlertEvents({ range, limit: 50 }),
        ...selectedCategories.map((category) =>
          getDashboardTrend({
            category,
            metricNames: CATEGORY_METRICS[category],
            granularity,
            beginTime: begin,
            endTime: end
          })
        )
      ]);

      if (overviewRes.code === 200) {
        setOverview(overviewRes.data || {});
      }
      if (alertRes.code === 200) {
        setAlertEvents(alertRes.data || []);
      }

      const nextTrendMap = {};
      selectedCategories.forEach((category, idx) => {
        const one = trendResponses[idx];
        nextTrendMap[category] = one?.code === 200 ? (one.data?.series || []) : [];
      });
      setTrendMap(nextTrendMap);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    doFetch();
    if (!autoRefresh) {
      return undefined;
    }
    const timer = setInterval(() => doFetch(true), refreshInterval);
    return () => clearInterval(timer);
  }, [range, selectedCategories, autoRefresh, refreshInterval]);

  const stats = overview?.basicStats || {};
  const alertSummary = overview?.alertSummary || {};
  const metricCharts = useMemo(() => {
    const grouped = {};
    selectedCategories.forEach((category) => {
      const seriesList = trendMap[category] || [];
      seriesList.forEach((series) => {
        const metricName = series.metricName;
        const key = `${category}|${metricName}`;
        if (!grouped[key]) {
          const meta = METRIC_META[metricName] || {};
          grouped[key] = {
            key,
            category,
            categoryLabel: CATEGORY_LABEL[category] || category,
            metricName,
            title: meta.name || metricName,
            unit: meta.unit || series.metricUnit || '',
            series: []
          };
        }
        grouped[key].series.push(series);
      });
    });
    return Object.values(grouped);
  }, [selectedCategories, trendMap]);

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
    { title: '详情', dataIndex: 'detail', key: 'detail' }
  ]), []);

  if (loading) {
    return <Spin style={{ width: '100%', marginTop: 100 }} />;
  }

  return (
    <div className="app-container monitor-dashboard-page">
      <Card className="dashboard-toolbar-card" style={{ marginBottom: 16 }}>
        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space wrap>
            <div className="toolbar-item-group">
              <span className="toolbar-item-label required">时间范围</span>
              <Select
                value={range}
                options={TIME_RANGE_OPTIONS}
                className="dashboard-select dashboard-select-single"
                style={{ width: 140 }}
                onChange={setRange}
              />
            </div>
            <div className="toolbar-item-group">
              <span className="toolbar-item-label">自动刷新</span>
              <Switch
                checked={autoRefresh}
                onChange={setAutoRefresh}
                className="toolbar-refresh-switch"
              />
            </div>
            <div className="toolbar-item-group">
              <span className="toolbar-item-label">刷新间隔</span>
              <Select
                value={refreshInterval}
                options={REFRESH_INTERVAL_OPTIONS}
                className="dashboard-select dashboard-select-single"
                style={{ width: 140 }}
                disabled={!autoRefresh}
                onChange={setRefreshInterval}
              />
            </div>
            <Select
              mode="multiple"
              value={selectedCategories}
              options={Object.keys(CATEGORY_METRICS).map((item) => ({ label: item, value: item }))}
              className="dashboard-select dashboard-select-multiple"
              style={{ minWidth: 360 }}
              onChange={setSelectedCategories}
              placeholder="选择指标类型"
            />
          </Space>
          <a onClick={() => doFetch(true)}>
            <ReloadOutlined spin={refreshing} /> 刷新
          </a>
        </Space>
      </Card>

      <Row gutter={16}>
        <Col span={4}><Card className="summary-card"><Statistic title="CPU使用率" value={Number(stats.cpuUsage || 0).toFixed(2)} suffix="%" /></Card></Col>
        <Col span={4}><Card className="summary-card"><Statistic title="CPU核心数" value={Number(stats.cpuCore || 0)} /></Card></Col>
        <Col span={4}><Card className="summary-card"><Statistic title="堆使用率" value={Number(stats.heapUsage || 0).toFixed(2)} suffix="%" /></Card></Col>
        <Col span={4}><Card className="summary-card"><Statistic title="线程总数" value={Number(stats.threadTotal || 0)} /></Card></Col>
        <Col span={4}><Card className="summary-card"><Statistic title="虚拟线程" value={Number(stats.virtualThreadTotal || 0)} /></Card></Col>
        <Col span={4}><Card className="summary-card"><Statistic title="1分钟负载" value={Number(stats.load1m || 0).toFixed(2)} /></Card></Col>
      </Row>

      <Alert
        style={{ marginTop: 16, marginBottom: 16 }}
        message={`告警汇总：critical ${alertSummary.critical || 0}，warning ${alertSummary.warning || 0}，总计 ${alertSummary.total || 0}`}
        type={(alertSummary.critical || 0) > 0 ? 'error' : (alertSummary.warning || 0) > 0 ? 'warning' : 'success'}
        showIcon
      />

      <Row gutter={[16, 0]}>
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

      <Card className="alert-table-card" title="告警事件">
        <Table
          rowKey="id"
          columns={alertColumns}
          dataSource={alertEvents}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 1100 }}
        />
      </Card>

      <Card className="hint-card" title="展示能力说明" style={{ marginTop: 16 }}>
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
