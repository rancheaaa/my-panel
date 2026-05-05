import React, { useState } from 'react';
import { Input, Modal, Radio, InputNumber, Select, Button, Space, Card } from 'antd';
import { SettingOutlined } from '@ant-design/icons';

const CRON_PRESETS = [
  { label: '每5分钟', value: '0 */5 * * * ?' },
  { label: '每15分钟', value: '0 */15 * * * ?' },
  { label: '每30分钟', value: '0 */30 * * * ?' },
  { label: '每小时', value: '0 0 * * * ?' },
  { label: '每天凌晨0点', value: '0 0 0 * * ?' },
  { label: '每周一0点', value: '0 0 0 ? * MON' },
  { label: '每月1号0点', value: '0 0 0 1 * ?' },
];

const CronExpressionInput = ({ value, onChange }) => {
  const [open, setOpen] = useState(false);
  const [secondType, setSecondType] = useState('all');
  const [minuteType, setMinuteType] = useState('all');
  const [hourType, setHourType] = useState('all');
  const [dayType, setDayType] = useState('all');
  const [monthType, setMonthType] = useState('all');
  const [weekType, setWeekType] = useState('none');

  const buildCron = () => {
    return `${secondType === 'all' ? '0' : '*'} ${minuteType === 'all' ? '*' : '0'} ${hourType === 'all' ? '*' : '0'} ${dayType === 'all' ? '?' : '*'} ${monthType === 'all' ? '*' : '?'} ${weekType === 'none' ? '?' : '*'}`;
  };

  const handlePresetSelect = (v) => {
    onChange?.(v);
    setOpen(false);
  };

  const handleManualConfirm = () => {
    onChange?.(buildCron());
    setOpen(false);
  };

  return (
    <Space.Compact style={{ width: '100%' }}>
      <Input
        value={value}
        onChange={e => onChange?.(e.target.value)}
        placeholder="如 0 */5 * * * ? (留空则使用扫描间隔)"
        style={{ flex: 1 }}
      />
      <Button icon={<SettingOutlined />} onClick={() => setOpen(true)}>Cron</Button>
      <Modal title="Cron表达式生成器" open={open} onCancel={() => setOpen(false)} onOk={handleManualConfirm} width={700}>
        <div style={{ marginBottom: 16 }}>
          <strong>快捷选择：</strong>
          <Select style={{ width: 200, marginLeft: 8 }} placeholder="选择预设" onSelect={handlePresetSelect} allowClear>
            {CRON_PRESETS.map(p => <Select.Option key={p.value} value={p.value}>{p.label}: {p.value}</Select.Option>)}
          </Select>
        </div>
        <div style={{ marginBottom: 16 }}>
          <strong>当前值：</strong><code>{value || '(空=使用轮询间隔)'}</code>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
          {[
            { label: '秒', type: secondType, setType: setSecondType },
            { label: '分', type: minuteType, setType: setMinuteType },
            { label: '时', type: hourType, setType: setHourType },
            { label: '日', type: dayType, setType: setDayType },
            { label: '月', type: monthType, setType: setMonthType },
            { label: '周', type: weekType, setType: setWeekType },
          ].map(({ label, type, setType }) => (
            <Card key={label} size="small" title={label}><Radio.Group value={type} onChange={e => setType(e.target.value)} size="small">
              <Radio.Button value="all">全选(*)</Radio.Button>
              <Radio.Button value="none">不设(?)</Radio.Button>
              <Radio.Button value="range">范围</Radio.Button>
              <Radio.Button value="step">步长</Radio.Button>
            </Radio.Group></Card>
          ))}
        </div>
        <div style={{ marginTop: 12, color: '#999' }}>
          常用示例：0 */5 * * * ? = 每5分钟 | 0 0 2 * * ? = 每天凌晨2点 | 0 30 8-18 * * ? = 每天8:00-18:30
        </div>
      </Modal>
    </Space.Compact>
  );
};

export default CronExpressionInput;
