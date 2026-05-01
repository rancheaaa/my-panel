import React, { useState, useEffect } from 'react';
import { Card, Input, Select, Button, Table, Space, Form, message } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';

const RegionConfigEditor = ({ value, onChange }) => {
  const [rules, setRules] = useState([]);
  const [defaultRegion, setDefaultRegion] = useState('');
  const [newPattern, setNewPattern] = useState('');
  const [newRegion, setNewRegion] = useState('');

  useEffect(() => {
    if (value && typeof value === 'string') {
      try {
        const config = JSON.parse(value);
        setRules(config.rules || []);
        setDefaultRegion(config.defaultRegion || '');
      } catch (e) { /* invalid JSON, ignore */ }
    }
  }, []);

  const triggerChange = (newRules, newDefaultRegion) => {
    const config = { rules: newRules, defaultRegion: newDefaultRegion };
    if (onChange) {
      onChange(JSON.stringify(config));
    }
  };

  const handleAddRule = () => {
    if (!newPattern.trim() || !newRegion.trim()) {
      message.warning('请输入文件模式和区域');
      return;
    }
    const newRules = [...rules, { pattern: newPattern.trim(), region: newRegion.trim() }];
    setRules(newRules);
    setNewPattern('');
    setNewRegion('');
    triggerChange(newRules, defaultRegion);
  };

  const handleDeleteRule = (index) => {
    const newRules = rules.filter((_, i) => i !== index);
    setRules(newRules);
    triggerChange(newRules, defaultRegion);
  };

  const handleDefaultRegionChange = (val) => {
    setDefaultRegion(val);
    triggerChange(rules, val);
  };

  const columns = [
    { title: '文件模式(Glob)', dataIndex: 'pattern', key: 'pattern' },
    { title: '目标区域', dataIndex: 'region', key: 'region' },
    { title: '操作', key: 'action', width: 60, render: (_, __, index) => (
      <Button type="link" danger size="small" icon={<DeleteOutlined />} onClick={() => handleDeleteRule(index)} />
    )},
  ];

  return (
    <Card size="small" title="区域路由配置" style={{ marginTop: 8 }}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space>
          <Input placeholder="文件模式, 如 logs/**/*.log" value={newPattern}
            onChange={e => setNewPattern(e.target.value)} style={{ width: 250 }} />
          <Input placeholder="区域, 如 cn-east" value={newRegion}
            onChange={e => setNewRegion(e.target.value)} style={{ width: 150 }} />
          <Button icon={<PlusOutlined />} onClick={handleAddRule}>添加规则</Button>
        </Space>
        <Table rowKey={(_, i) => i} columns={columns} dataSource={rules} pagination={false} size="small" />
        <Space>
          <span>默认区域:</span>
          <Input placeholder="默认区域, 如 cn-east" value={defaultRegion}
            onChange={e => handleDefaultRegionChange(e.target.value)} style={{ width: 200 }} />
        </Space>
      </Space>
    </Card>
  );
};

export default RegionConfigEditor;
