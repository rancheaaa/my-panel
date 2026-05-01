import React, { useRef, useEffect } from 'react';
import { Card, Empty } from 'antd';
import * as echarts from 'echarts';

const QueueTrendChart = ({ data = [], title = '队列深度趋势' }) => {
  const chartRef = useRef(null);
  const chartInstance = useRef(null);

  useEffect(() => {
    if (!chartRef.current) return;
    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current);
    }
    if (data.length === 0) {
      chartInstance.current.setOption({
        title: { text: title, left: 'center', textStyle: { fontSize: 14 } },
        graphic: { type: 'text', left: 'center', top: 'middle', style: { text: '暂无数据', fontSize: 14, fill: '#999' } }
      });
      return;
    }

    const times = data.map(d => d.snapshotTime || '');
    const sendDepths = data.map(d => d.sendQueueDepth || 0);
    const retryDepths = data.map(d => d.retryQueueDepth || 0);
    const processingRates = data.map(d => d.processingRatePerSec || 0);

    chartInstance.current.setOption({
      title: { text: title, left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      legend: { data: ['发送队列', '重试队列', '处理速率'], bottom: 0 },
      grid: { left: 60, right: 60, top: 40, bottom: 40 },
      xAxis: { type: 'category', data: times, axisLabel: { fontSize: 10, rotate: 30 } },
      yAxis: [
        { type: 'value', name: '队列深度', position: 'left' },
        { type: 'value', name: '速率(文件/秒)', position: 'right' }
      ],
      series: [
        { name: '发送队列', type: 'line', data: sendDepths, smooth: true, itemStyle: { color: '#1890ff' } },
        { name: '重试队列', type: 'line', data: retryDepths, smooth: true, itemStyle: { color: '#faad14' } },
        { name: '处理速率', type: 'line', yAxisIndex: 1, data: processingRates, smooth: true, itemStyle: { color: '#52c41a' } }
      ]
    });

    const handleResize = () => chartInstance.current?.resize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [data, title]);

  useEffect(() => {
    return () => { chartInstance.current?.dispose(); };
  }, []);

  return (
    <Card size="small">
      <div ref={chartRef} style={{ width: '100%', height: 320 }} />
    </Card>
  );
};

export default QueueTrendChart;
