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
  LineChartOutlined,
  HddOutlined,
  CalendarOutlined,
  RocketOutlined,
  LoadingOutlined,
  CloseCircleOutlined
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

const taskStatusMap = {
  READY: { text: '就绪', color: '#1890ff', bg: '#e6f7ff' },
  RUNNING: { text: '运行中', color: '#52c41a', bg: '#f6ffed' },
  PAUSED: { text: '已暂停', color: '#faad14', bg: '#fffbe6' }
};

const subtaskStatusMap = {
  QUEUED: { text: '排队中', color: '#d9d9d9', bg: '#f5f5f5', icon: <ClockCircleOutlined /> },
  SENDING: { text: '传输中', color: '#1890ff', bg: '#e6f7ff', icon: <LoadingOutlined spin /> },
  COMPLETED: { text: '已完成', color: '#52c41a', bg: '#f6ffed', icon: <CheckCircleOutlined /> },
  FAILED: { text: '失败', color: '#ff4d4f', bg: '#fff2f0', icon: <CloseCircleOutlined /> },
  RETRYING: { text: '重试中', color: '#faad14', bg: '#fffbe6', icon: <SyncOutlined spin /> }
};

function formatBytes(bytes) {
  if (!bytes || bytes < 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  while (bytes >= 1024 && i < units.length - 1) {
    bytes /= 1024;
    i++;
  }
  return `${bytes.toFixed(2)} ${units[i]}`;
}

function formatSpeed(speed) {
  if (!speed || speed <= 0) return '0 B/s';
  return formatBytes(speed) + '/s';
}

const StatisticsTab = () => {
  const { data: fullData, stop, start } = usePolling(
    () => batchApi.getFullSummary().then(res => res.data),
    5000
  );

  const taskSummary = fullData?.taskSummary || {};
  const subtaskSummary = fullData?.subtaskSummary || {};

  const total = taskSummary.totalCount || 0;
  const running = taskSummary.runningCount || 0;
  const paused = taskSummary.pausedCount || 0;
  const ready = taskSummary.readyCount || 0;

  const activeTotal = running + paused + ready;
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
                <div style={{ fontSize: 22, fontWeight: 700, color: '#1890ff' }}>{ready}</div>
                <div style={{ fontSize: 11.5, color: '#8c8c8c' }}>就绪</div>
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

      {/* 传输任务状态分布 */}
      <Card
        title={
          <Space>
            <CloudServerOutlined style={{ color: '#1890ff' }} />
            <span>传输任务状态分布</span>
          </Space>
        }
        style={{ borderRadius: 12, marginBottom: 16, border: '1px solid #f0f0f8' }}
        styles={{ body: { padding: '16px 20px' }, header: { borderBottom: '1px solid #f0f0f0', padding: '12px 20px' } }}
      >
        <Row gutter={[14, 14]}>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, borderLeft: '4px solid #1890ff', transition: 'all 0.25s ease' }} styles={{ body: { padding: '16px 18px' } }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ width: 36, height: 36, borderRadius: 10, background: '#e6f7ff', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                    <RocketOutlined style={{ color: '#1890ff', fontSize: 17 }} />
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 700, color: '#1890ff', lineHeight: 1.1 }}>{ready}</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>就绪</div>
                </div>
                {total > 0 && <Tag color="#1890ff" style={{ borderRadius: 10, fontSize: 12, fontWeight: 600 }}>{Math.round((ready / total) * 100)}%</Tag>}
              </div>
            </Card>
          </Col>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, borderLeft: '4px solid #52c41a', transition: 'all 0.25s ease' }} styles={{ body: { padding: '16px 18px' } }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ width: 36, height: 36, borderRadius: 10, background: '#f6ffed', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                    <LoadingOutlined style={{ color: '#52c41a', fontSize: 17 }} />
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 700, color: '#52c41a', lineHeight: 1.1 }}>{running}</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>运行中</div>
                </div>
                {activeTotal > 0 && <Progress type="circle" percent={Math.round((running / activeTotal) * 100)} width={40} strokeColor="#52c41a" style={{ marginTop: -8 }} />}
              </div>
            </Card>
          </Col>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, borderLeft: '4px solid #faad14', transition: 'all 0.25s ease' }} styles={{ body: { padding: '16px 18px' } }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ width: 36, height: 36, borderRadius: 10, background: '#fffbe6', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                    <PauseCircleOutlined style={{ color: '#faad14', fontSize: 17 }} />
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 700, color: '#faad14', lineHeight: 1.1 }}>{paused}</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>已暂停</div>
                </div>
                {total > 0 && <Tag color="#faad14" style={{ borderRadius: 10, fontSize: 12, fontWeight: 600 }}>{Math.round((paused / total) * 100)}%</Tag>}
              </div>
            </Card>
          </Col>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, borderLeft: '4px solid #722ed1', transition: 'all 0.25s ease' }} styles={{ body: { padding: '16px 18px' } }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ width: 36, height: 36, borderRadius: 10, background: '#f9f0ff', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                    <CalendarOutlined style={{ color: '#722ed1', fontSize: 17 }} />
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 700, color: '#722ed1', lineHeight: 1.1 }}>{taskSummary.todayCount || 0}</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>今日新增</div>
                </div>
              </div>
            </Card>
          </Col>
        </Row>

        {/* 任务状态标签 */}
        {taskSummary.statusDistribution && Object.keys(taskSummary.statusDistribution).length > 0 && (
          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid #f0f0f0' }}>
            <span style={{ fontSize: 12, color: '#8c8c8c', marginRight: 12 }}>状态分布：</span>
            <Space size={[8, 8]} wrap>
              {Object.entries(taskSummary.statusDistribution).map(([status, count]) => {
                const config = taskStatusMap[status];
                return config ? (
                  <Tag key={status} color={config.color} style={{ borderRadius: 16, padding: '3px 12px', fontSize: 12 }}>
                    {config.text}: {count}
                  </Tag>
                ) : null;
              })}
            </Space>
          </div>
        )}
      </Card>

      {/* 传输文件明细统计 */}
      <Card
        title={
          <Space>
            <FileOutlined style={{ color: '#52c41a' }} />
            <span>传输文件明细统计</span>
          </Space>
        }
        style={{ borderRadius: 12, marginBottom: 16, border: '1px solid #f0f0f8' }}
        styles={{ body: { padding: '16px 20px' }, header: { borderBottom: '1px solid #f0f0f0', padding: '12px 20px' } }}
      >
        {/* 文件数量统计 */}
        <Row gutter={[14, 14]} style={{ marginBottom: 16 }}>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, borderLeft: '4px solid #1890ff', transition: 'all 0.25s ease' }} styles={{ body: { padding: '16px 18px' } }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ width: 36, height: 36, borderRadius: 10, background: '#e6f7ff', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                    <FileOutlined style={{ color: '#1890ff', fontSize: 17 }} />
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 700, color: '#1890ff', lineHeight: 1.1 }}>{subtaskSummary.totalCount || 0}</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>总文件数</div>
                </div>
              </div>
            </Card>
          </Col>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, borderLeft: '4px solid #52c41a', transition: 'all 0.25s ease' }} styles={{ body: { padding: '16px 18px' } }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ width: 36, height: 36, borderRadius: 10, background: '#f6ffed', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                    <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 17 }} />
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 700, color: '#52c41a', lineHeight: 1.1 }}>{subtaskSummary.completedCount || 0}</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>已完成</div>
                </div>
                {(subtaskSummary.totalCount > 0) && <Progress type="circle" percent={subtaskSummary.progressPercent ? Math.min(subtaskSummary.progressPercent.toNumber(), 100) : 0} width={40} strokeColor="#52c41a" style={{ marginTop: -8 }} />}
              </div>
            </Card>
          </Col>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, borderLeft: '4px solid #fa8c16', transition: 'all 0.25s ease' }} styles={{ body: { padding: '16px 18px' } }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ width: 36, height: 36, borderRadius: 10, background: '#fff7e6', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                    <LoadingOutlined style={{ color: '#fa8c16', fontSize: 17 }} />
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 700, color: '#fa8c16', lineHeight: 1.1 }}>{(subtaskSummary.sendingCount || 0) + (subtaskSummary.retryingCount || 0)}</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>传输中</div>
                </div>
              </div>
            </Card>
          </Col>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, borderLeft: '4px solid #ff4d4f', transition: 'all 0.25s ease' }} styles={{ body: { padding: '16px 18px' } }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ width: 36, height: 36, borderRadius: 10, background: '#fff2f0', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                    <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 17 }} />
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 700, color: '#ff4d4f', lineHeight: 1.1 }}>{subtaskSummary.failedCount || 0}</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>失败</div>
                </div>
              </div>
            </Card>
          </Col>
        </Row>

        {/* 文件大小和速度统计 */}
        <Row gutter={[14, 14]} style={{ marginBottom: 16 }}>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, background: '#fafafa', border: '1px solid #f0f0f0' }} styles={{ body: { padding: '14px 16px' } }}>
              <Statistic
                title={<span style={{ fontSize: 12, color: '#8c8c8c' }}><HddOutlined style={{ marginRight: 4 }} />总大小</span>}
                value={formatBytes(subtaskSummary.totalSizeBytes)}
                valueStyle={{ fontSize: 18, fontWeight: 600, color: '#262626' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, background: '#fafafa', border: '1px solid #f0f0f0' }} styles={{ body: { padding: '14px 16px' } }}>
              <Statistic
                title={<span style={{ fontSize: 12, color: '#8c8c8c' }}><CheckCircleOutlined style={{ marginRight: 4, color: '#52c41a' }} />已完成大小</span>}
                value={formatBytes(subtaskSummary.completedSizeBytes)}
                valueStyle={{ fontSize: 18, fontWeight: 600, color: '#52c41a' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, background: '#fafafa', border: '1px solid #f0f0f0' }} styles={{ body: { padding: '14px 16px' } }}>
              <Statistic
                title={<span style={{ fontSize: 12, color: '#8c8c8c' }}><ThunderboltOutlined style={{ marginRight: 4, color: '#722ed1' }} />平均速度</span>}
                value={formatSpeed(subtaskSummary.avgSpeedBytesPerSec)}
                valueStyle={{ fontSize: 18, fontWeight: 600, color: '#722ed1' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card hoverable style={{ borderRadius: 10, background: '#fafafa', border: '1px solid #f0f0f0' }} styles={{ body: { padding: '14px 16px' } }}>
              <Statistic
                title={<span style={{ fontSize: 12, color: '#8c8c8c' }}><CalendarOutlined style={{ marginRight: 4, color: '#722ed1' }} />今日新增</span>}
                value={subtaskSummary.todayCount || 0}
                valueStyle={{ fontSize: 18, fontWeight: 600, color: '#722ed1' }}
              />
            </Card>
          </Col>
        </Row>

        {/* 总体进度条 */}
        {(subtaskSummary.totalSizeBytes && subtaskSummary.totalSizeBytes > 0) && (
          <div style={{ padding: '16px 20px', background: '#fafafa', borderRadius: 8, border: '1px solid #f0f0f0' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
              <span style={{ fontSize: 13, color: '#666', fontWeight: 500 }}>总体传输进度</span>
              <span style={{ fontSize: 14, fontWeight: 700, color: '#1890ff' }}>
                {subtaskSummary.progressPercent ? subtaskSummary.progressPercent.toNumber().toFixed(2) : '0.00'}%
              </span>
            </div>
            <Progress
              percent={subtaskSummary.progressPercent ? Math.min(subtaskSummary.progressPercent.toNumber(), 100) : 0}
              strokeColor={{ '0%': '#108ee9', '100%': '#87d068' }}
              strokeWidth={12}
              status="active"
            />
          </div>
        )}

        {/* 文件状态分布标签 */}
        {subtaskSummary.statusDistribution && Object.keys(subtaskSummary.statusDistribution).length > 0 && (
          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid #f0f0f0' }}>
            <span style={{ fontSize: 12, color: '#8c8c8c', marginRight: 12 }}>状态分布：</span>
            <Space size={[8, 8]} wrap>
              {Object.entries(subtaskSummary.statusDistribution).map(([status, count]) => {
                const config = subtaskStatusMap[status];
                return config ? (
                  <Tag key={status} color={config.color} style={{ borderRadius: 16, padding: '3px 12px', fontSize: 12 }}>
                    {config.icon} {config.text}: {count}
                  </Tag>
                ) : null;
              })}
            </Space>
          </div>
        )}
      </Card>

      {/* 底部信息栏 */}
      <Card
        size="small"
        style={{
          borderRadius: 10,
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
