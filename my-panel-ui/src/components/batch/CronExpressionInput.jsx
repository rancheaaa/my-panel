import React, { useState, useMemo } from 'react';
import { Input, Modal, Select, Button, Space, Tag, InputNumber } from 'antd';
import { SettingOutlined } from '@ant-design/icons';

const CRON_PRESETS = [
  { label: '每分钟', value: '0 * * * * ?' },
  { label: '每5分钟', value: '0 */5 * * * ?' },
  { label: '每15分钟', value: '0 */15 * * * ?' },
  { label: '每30分钟', value: '0 */30 * * * ?' },
  { label: '每小时', value: '0 0 * * * ?' },
  { label: '每天凌晨0点', value: '0 0 0 * * ?' },
  { label: '每周一0点', value: '0 0 0 ? * MON' },
  { label: '每月1号0点', value: '0 0 0 1 * ?' },
];

const WEEK_OPTIONS = [
  { label: 'MON', value: 'MON' }, { label: 'TUE', value: 'TUE' },
  { label: 'WED', value: 'WED' }, { label: 'THU', value: 'THU' },
  { label: 'FRI', value: 'FRI' }, { label: 'SAT', value: 'SAT' },
  { label: 'SUN', value: 'SUN' },
];

const CronField = ({ label, value, onChange, min, max, showWeek }) => {
  const type = value === '*' ? 'every' : value === '?' ? 'none' :
    value.includes('/') ? 'step' : value.includes('-') ? 'range' : 'fixed';

  const parseStep = (v) => {
    if (v && v.includes('/')) return v.split('/');
    return ['', ''];
  };
  const parseRange = (v) => {
    if (v && v.includes('-')) return v.split('-');
    return ['', ''];
  };

  const handleTypeChange = (t) => {
    if (t === 'every') onChange('*');
    else if (t === 'none') onChange('?');
    else if (t === 'fixed') onChange(showWeek ? 'MON' : String(min));
    else if (t === 'step') onChange(`*/${min === 0 ? 1 : min}`);
    else if (t === 'range') onChange(`${min}-${min + 1}`);
  };

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
      <span style={{ width: 32, textAlign: 'right', fontSize: 13, color: '#666' }}>{label}</span>
      <Select value={type} onChange={handleTypeChange} style={{ width: 88 }} size="small"
        options={[
          { value: 'every', label: '每*' },
          ...(showWeek ? [{ value: 'none', label: '不指定?' }] : [{ value: 'none', label: '?' }]),
          { value: 'fixed', label: '指定' },
          { value: 'range', label: '范围' },
          { value: 'step', label: '步长' },
        ]}
      />
      {type === 'fixed' && (
        showWeek ? (
          <Select value={value === '?' ? undefined : value} onChange={v => onChange(v)}
            style={{ width: 80 }} size="small" placeholder="选择"
            options={WEEK_OPTIONS} />
        ) : (
          <InputNumber value={Number(value)} onChange={v => onChange(String(v))}
            min={min} max={max} style={{ width: 72 }} size="small" />
        )
      )}
      {type === 'range' && (
        <Space size={2}>
          <InputNumber value={Number(parseRange(value)[0])} min={min} max={max} size="small" style={{ width: 56 }}
            onChange={v => { const p = parseRange(value); onChange(`${v}-${p[1]}`); }} />
          <span style={{ color: '#999' }}>-</span>
          <InputNumber value={Number(parseRange(value)[1])} min={min} max={max} size="small" style={{ width: 56 }}
            onChange={v => { const p = parseRange(value); onChange(`${p[0]}-${v}`); }} />
        </Space>
      )}
      {type === 'step' && (
        <Space size={2}>
          <span style={{ color: '#999', fontSize: 12 }}>*</span>
          <span style={{ color: '#999' }}>/</span>
          <InputNumber value={Number(parseStep(value)[1])} min={1} max={max} size="small" style={{ width: 56 }}
            onChange={v => onChange(`*/${v}`)} />
        </Space>
      )}
    </div>
  );
};

const CronExpressionInput = ({ value, onChange }) => {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('0 */5 * * * ?');

  const parseToFields = (expr) => {
    if (!expr) return { sec: '0', min: '*/5', hour: '*', day: '*', month: '*', week: '?' };
    const parts = expr.trim().split(/\s+/);
    while (parts.length < 6) parts.push('*');
    return { sec: parts[0], min: parts[1], hour: parts[2], day: parts[3], month: parts[4], week: parts[5] };
  };

  const fields = useMemo(() => parseToFields(draft), [draft]);

  const updateField = (key, val) => {
    const f = { ...fields, [key]: val };
    // 日和周不能同时为 ?，互斥：一个设 ? 时另一个自动变 *
    if (key === 'day' && val === '?' && f.week === '?') f.week = '*';
    if (key === 'week' && val === '?' && f.day === '?') f.day = '*';
    setDraft(`${f.sec} ${f.min} ${f.hour} ${f.day} ${f.month} ${f.week}`);
  };

  const handleOpen = () => {
    setDraft(value || '0 */5 * * * ?');
    setOpen(true);
  };

  const handleConfirm = () => {
    onChange?.(draft);
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
      <Button icon={<SettingOutlined />} onClick={handleOpen}>Cron</Button>
      <Modal title="Cron 表达式生成器" open={open} onCancel={() => setOpen(false)}
        onOk={handleConfirm} width={480} destroyOnClose>
        <div style={{ marginBottom: 12 }}>
          <span style={{ fontSize: 13, color: '#666', marginRight: 8 }}>快捷预设</span>
          <Space size={[4, 4]} wrap>
            {CRON_PRESETS.map(p => (
              <Tag key={p.value} color={draft === p.value ? 'blue' : undefined}
                style={{ cursor: 'pointer' }} onClick={() => setDraft(p.value)}>
                {p.label}
              </Tag>
            ))}
          </Space>
        </div>
        <div style={{ background: '#fafafa', borderRadius: 6, padding: '12px 16px', marginBottom: 12 }}>
          <CronField label="秒" value={fields.sec} onChange={v => updateField('sec', v)} min={0} max={59} />
          <CronField label="分" value={fields.min} onChange={v => updateField('min', v)} min={0} max={59} />
          <CronField label="时" value={fields.hour} onChange={v => updateField('hour', v)} min={0} max={23} />
          <CronField label="日" value={fields.day} onChange={v => updateField('day', v)} min={1} max={31} />
          <CronField label="月" value={fields.month} onChange={v => updateField('month', v)} min={1} max={12} />
          <CronField label="周" value={fields.week} onChange={v => updateField('week', v)} min={1} max={7} showWeek />
        </div>
        <div style={{ textAlign: 'center', fontSize: 16, fontFamily: 'monospace', color: '#1677ff', fontWeight: 600 }}>
          {draft}
        </div>
      </Modal>
    </Space.Compact>
  );
};

export default CronExpressionInput;
