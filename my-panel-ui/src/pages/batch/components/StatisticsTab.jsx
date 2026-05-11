import React from 'react';
import { Card, Row, Col, Statistic, Progress, Tag, Tooltip, Space, Button } from 'antd';
import {
  FileOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  StopOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  SyncOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  MinusOutlined,
  CloudServerOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
  DashboardOutlined,
  LineChartOutlined
} from '@ant-design/icons';
import { usePolling } from '../hooks/usePolling';
import * as batchApi from '../../../api/batch';

const statusConfig = [
  { key: 'total', label: '总任务数', icon: <FileOutlined />, color: '#1890ff', bg: '#e6f7ff', border: '#91d5ff' },
  { key: 'running', label: '运行中', icon: <PlayCircleOutlined />, color: '#52c41a', bg: '#f6ffed', border: '#b7eb8f' },
  { key: 'paused', label: '已暂停', icon: <PauseCircleOutlined />, color: '#faad14', bg: '#fffbe6', border: '#ffe58f' },
  { key: 'stopped', label: '已停止', icon: <StopOutlined />, color: '#ff4d4f', bg: '#fff2f0', border: '#ffa39e' },
  { key: 'completed', label: '已完成', icon: <CheckCircleOutlined />, color: '#722ed1', bg: '#f9f0ff', border: '#d3adf7' },
  { key: 'error', label: '异常', icon: <ExclamationCircleOutlined />, color: '#cf1322', bg: '#fff1f0', border: '#ffa39e' }
];

const StatisticsTab = () => {
  const { data: statistics, stop, start } = usePolling(
    () => batchApi.getStatistics().then(res => res.data),
    5000
  );

  const total = statistics?.total || 0;
  const running = statistics?.running || 0;
  const paused = statistics?.paused || 0;
  const stopped = statistics?.stopped || 0;
  const completed = statistics?.completed || 0;
  const error = statistics?.error || 0;

  const activeTotal = running + paused + stopped + completed + error;
  const runningPct = activeTotal > 0 ? Math.round((running / activeTotal) * 100) : 0;

  return (
    <div>
      {/* 主卡片：总览大数字 */}
      <Card
        style={{
          borderRadius: 12,
          marginBottom: 16,
          background: `linear-gradient(135deg, #667eea11 0%, #764ba222 100%)`,
          border: '1px solid #f0f0f8'
        }}
        styles={{ body: { padding: '24px 28px' }}}
      >
        <Row align="middle">
          <Col flex="auto">
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{
                width: 52, height: 52, borderRadius: 14,
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                boxShadow: '0 4px 15px rgba(102,126,234,.35)'
              }}>
                <DashboardOutlined style={{ fontSize: 24, color: '#fff' }} />
              </div>
              <div>
                <div style={{ fontSize: 13, color: '#8c8c8c', fontWeight: 500 }}>批量传输任务总览</div>
                <div style={{ fontSize: 36, fontWeight: 700, color: '#1f1f1f', lineHeight: 1.1, letterSpacing: '-1px' }}>{total}</div>
              </div>
            </div>
          </Col>
          <Col>
            <Space size={20}>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 22, fontWeight: 700, color: '#52c41a' }}>{running}</div>
                <div style={{ fontSize: 11.5, color: '#8c8c8c' }}>运行中</div>
              </div>
              <div style={{ width: 1, height: 32, background: '#f0f0f0' }} />
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 22, fontWeight: 700, color: '#faad14' }}>{paused}</div>
                <div style={{ fontSize: 11.5, color: '#8c8c8c' }}>已暂停</div>
              </div>
              <div style={{ width: 1, height: 32, background: '#f0f0f0' }} />
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 22, fontWeight: 700, color: '#722ed1' }}>{completed}</div>
                <div style={{ fontSize: 11.5, color: '#8c8c8c' }}>已完成</div>
              </div>
            </Space>
          </Col>
        </Row>

        {/* 活跃度进度条 */}
        <div style={{ marginTop: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
            <span style={{ fontSize: 12, color: '#8c8c8c', fontWeight: 500 }}>活跃任务占比</span>
            <span style={{ fontSize: 12, fontWeight: 600, color: '#52c41a' }}>{runningPct}%</span>
          </div>
          <Progress
            percent={runningPct}
            strokeColor={{
              '0%': '#52c41a',
              '50%': '#1890ff',
              '100%': '#722ed1'
            }}
            trailColor="#f0f0f0"
            size="small"
            style={{ borderRadius: 10 }}
          />
        </div>
      </Card>

      {/* 状态卡片网格 */}
      <Row gutter={[14, 14]}>
        {statusConfig.filter(s => s.key !== 'total').map(cfg => {
          const value = statistics?.[cfg.key] || 0;
          const pct = activeTotal > 0 ? Math.round((value / activeTotal) * 100) : 0;
          const isZero = value === 0;

          return (
            <Col span={8} key={cfg.key}>
              <Card
                hoverable
                style={{
                  borderRadius: 10,
                  borderLeft: '4px solid ' + (isZero ? '#d9d9d9' : cfg.color),
                  transition: 'all 0.25s ease',
                  cursor: 'default'
                }}
                styles={{ body: { padding: '18px 20px' } }}
              >
                <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                  <div>
                    <div style={{
                      width: 38, height: 38, borderRadius: 10,
                      background: isZero ? '#f5f5f5' : cfg.bg,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      marginBottom: 10
                    }}>
                      <span style={{ color: isZero ? '#bfbfbf' : cfg.color, fontSize: 18 }}>{cfg.icon}</span>
                    </div>
                    <div style={{ fontSize: 28, fontWeight: 700, color: isZero ? '#bfbfbf' : cfg.color, lineHeight: 1.1 }}>{value}</div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>{cfg.label}</div>
                  </div>
                  {!isZero && (
                    <Tag
                      color={cfg.color}
                      style={{
                        borderRadius: 10,
                        fontSize: 12,
                        fontWeight: 600,
                        padding: '2px 10px',
                        lineHeight: '20px'
                      }}
                    >
                      {pct}%
                    </Tag>
                  )}
                </div>

                {/* 迷你进度条 */}
                {!isZero && (
                  <Progress
                    percent={pct}
                    showInfo={false}
                    strokeColor={cfg.color}
                    trailColor="#f5f5f5"
                    size="small"
                    style={{ marginTop: 10, borderRadius: 6 }}
                  />
                )}
              </Card>
            </Col>
          );
        })}
      </Row>

      {/* 底部信息栏 */}
      <Card
        size="small"
        style={{
          borderRadius: 10,
          marginTop: 14,
          background: '#fafafa',
          border: '1px solid #f0f0f0'
        }}
        styles={{ body: { padding: '12px 18px' }}}
      >
        <Row align="middle" justify="space-between">
          <Col>
            <Space size={16}>
              <span style={{ fontSize: 12, color: '#8c8c8c', display: 'flex', alignItems: 'center', gap: 5 }}>
                <SyncOutlined spin style={{ color: '#1890ff', fontSize: 12 }} />
                自动刷新中 · 每5秒
              </span>
              <span style={{ fontSize: 12, color: '#bfbfbf' }}>|</span>
              <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                <ClockCircleOutlined style={{ marginRight: 4, fontSize: 11 }} />
                最后更新: {new Date().toLocaleTimeString('zh-CN')}
              </span>
            </Space>
          </Col>
          <Col>
            <Space size={8}>
              <Tooltip title="暂停自动刷新">
                <Button size="small" shape="circle" icon={<PauseCircleOutlined />} onClick={stop}
                  style={{ borderRadius: 6 }} />
              </Tooltip>
              <Tooltip title="立即刷新">
                <Button size="small" type="primary" ghost shape="circle" icon={<ThunderboltOutlined />} onClick={start}
                  style={{ borderRadius: 6 }} />
              </Tooltip>
            </Space>
          </Col>
        </Row>
      </Card>
    </div>
  );
};

export default StatisticsTab;
