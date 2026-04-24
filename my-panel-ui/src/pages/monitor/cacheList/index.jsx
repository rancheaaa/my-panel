import React, { useState, useEffect } from 'react';
import { Card, Table, Button, Space, Row, Col, message, Popconfirm, Input, Form, Descriptions, Spin } from 'antd';
import { 
  ReloadOutlined, 
  DeleteOutlined, 
  EyeOutlined, 
  SearchOutlined, 
  ClearOutlined,
  AppstoreOutlined,
  KeyOutlined,
  FileTextOutlined,
  DesktopOutlined
} from '@ant-design/icons';
import { listCacheName, listCacheKey, getCacheValue, clearCacheName, clearCacheKey, clearCacheAll } from '../../../api/monitor/cache';
import './index.scss';

const CacheList = () => {
  const [cacheNames, setCacheNames] = useState([]);
  const [cacheKeys, setCacheKeys] = useState([]);
  const [loadingNames, setLoadingNames] = useState(false);
  const [loadingKeys, setLoadingKeys] = useState(false);
  const [currentCacheName, setCurrentCacheName] = useState('');
  
  // Filter states
  const [nameFilter, setNameFilter] = useState('');
  const [keyFilter, setKeyFilter] = useState('');

  // Cache Content
  const [cacheValueData, setCacheValueData] = useState({ cacheName: '', cacheKey: '', cacheValue: '', ttl: '' });
  const [loadingValue, setLoadingValue] = useState(false);

  useEffect(() => {
    fetchCacheNames();
  }, []);

  const fetchCacheNames = async () => {
    setLoadingNames(true);
    try {
      const res = await listCacheName();
      if (res.code === 200) {
        setCacheNames(res.data);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setLoadingNames(false);
    }
  };

  const fetchCacheKeys = async (cacheKey, isRefresh = false) => {
    setLoadingKeys(true);
    setCurrentCacheName(cacheKey);
    if (!isRefresh) {
      setCacheKeys([]); // Clear previous keys
      setCacheValueData({ cacheName: '', cacheKey: '', cacheValue: '', ttl: '' }); // Clear content
    }
    try {
      const res = await listCacheKey(cacheKey);
      if (res.code === 200) {
        setCacheKeys(res.data);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setLoadingKeys(false);
    }
  };

  const handleClearCacheName = async (cacheKey) => {
      try {
          await clearCacheName(cacheKey);
          message.success('清理成功');
          fetchCacheNames();
          if (currentCacheName === cacheKey) {
              setCacheKeys([]);
              setCurrentCacheName('');
              setCacheValueData({ cacheName: '', cacheKey: '', cacheValue: '', ttl: '' });
          }
      } catch (error) {
          message.error('清理失败');
      }
  };
  
  const handleClearCacheKey = async (key) => {
      try {
          await clearCacheKey(key);
          message.success('清理成功');
          fetchCacheKeys(currentCacheName);
          if (cacheValueData.cacheKey === key) {
              setCacheValueData({ cacheName: '', cacheKey: '', cacheValue: '', ttl: '' });
          }
      } catch (error) {
          message.error('清理失败');
      }
  };

  const handleClearAll = async () => {
      try {
          await clearCacheAll();
          message.success('清理全部成功');
          fetchCacheNames();
          setCacheKeys([]);
          setCurrentCacheName('');
          setCacheValueData({ cacheName: '', cacheKey: '', cacheValue: '', ttl: '' });
      } catch (error) {
          message.error('清理失败');
      }
  };

  const handleViewValue = async (record) => {
      setLoadingValue(true);
      const key = record.cacheKey;
      const ttl = record.ttl;
      try {
          const res = await getCacheValue(key);
          if (res.code === 200) {
              let formattedValue = res.data.cacheValue;
              try {
                  if (typeof formattedValue === 'object' && formattedValue !== null) {
                      formattedValue = JSON.stringify(formattedValue, null, 2);
                  } else if (typeof formattedValue === 'string') {
                      // 尝试解析 JSON 字符串
                      try {
                          const parsed = JSON.parse(formattedValue);
                          formattedValue = JSON.stringify(parsed, null, 2);
                      } catch (e) {
                          // 如果解析失败，说明不是 JSON 字符串，保持原样
                      }
                      
                      // 某些情况下可能是双重序列化的 JSON，尝试再次解析
                      if (typeof formattedValue === 'string' && (formattedValue.startsWith('{') || formattedValue.startsWith('['))) {
                         try {
                              const parsedAgain = JSON.parse(formattedValue);
                              // 如果再次解析成功且是对象/数组，说明原字符串是 JSON 格式的字符串
                              if (typeof parsedAgain === 'object' && parsedAgain !== null) {
                                  formattedValue = JSON.stringify(parsedAgain, null, 2);
                              }
                          } catch (e) {
                              // 忽略二次解析错误
                          }
                      }
                  }
              } catch (e) {
                  // Keep as is
              }
              setCacheValueData({ 
                  cacheName: record.cacheName,
                  cacheKey: key, 
                  cacheValue: formattedValue !== undefined && formattedValue !== null ? formattedValue : '', 
                  ttl: (res.data.ttl !== undefined && res.data.ttl !== null && res.data.ttl !== '') ? res.data.ttl : ttl 
              });
          }
      } catch (error) {
          message.error('获取缓存内容失败');
      } finally {
          setLoadingValue(false);
      }
  };

  const handleRefreshAll = () => {
    // 刷新缓存名称列表
    fetchCacheNames();
    
    // 如果有选中的缓存名称，刷新键名列表（保留内容）
    if (currentCacheName) {
      fetchCacheKeys(currentCacheName, true);
    }
    
    // 如果有选中的缓存键，刷新缓存内容
    if (cacheValueData.cacheKey) {
      handleViewValue({ cacheKey: cacheValueData.cacheKey, ttl: cacheValueData.ttl });
    }
  };


  const filteredCacheNames = cacheNames.filter(item =>
      (item.cacheKey && item.cacheKey.toLowerCase().includes(nameFilter.toLowerCase())) ||
      (item.cacheName && item.cacheName.toLowerCase().includes(nameFilter.toLowerCase()))
  );

  const filteredCacheKeys = cacheKeys.filter(item => 
      item.cacheKey.toLowerCase().includes(keyFilter.toLowerCase())
  );

  const columnsName = [
    { title: '序号', render: (text, record, index) => index + 1, width: 60, align: 'center' },
    { title: '缓存键名前缀', dataIndex: 'cacheKey', key: 'cacheKey', width: 200, ellipsis: true },
    { title: '缓存名称', dataIndex: 'cacheName', key: 'cacheName', width: 150, ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 60,
      align: 'center',
      fixed: 'right',
      render: (_, record) => (
        <Popconfirm title="确定要删除此缓存吗？" onConfirm={(e) => { e.stopPropagation(); handleClearCacheName(record.cacheKey); }} okText="确定" cancelText="取消">
          <Button type="link" icon={<DeleteOutlined />} danger />
        </Popconfirm>
      ),
    },
  ];

  const columnsKey = [
    { title: '序号', render: (text, record, index) => index + 1, width: 60, align: 'center' },
    { title: '缓存键名', dataIndex: 'cacheKey', key: 'cacheKey', width: 200, ellipsis: true },
    {
        title: '操作',
        key: 'action',
        width: 60,
        align: 'center',
        fixed: 'right',
        render: (_, record) => (
          <Popconfirm title="确定要删除此缓存键吗？" onConfirm={(e) => { e.stopPropagation(); handleClearCacheKey(record.cacheKey); }} okText="确定" cancelText="取消">
            <Button type="link" icon={<DeleteOutlined />} danger />
          </Popconfirm>
        ),
      },
  ];

  return (
    <div className="app-container">
      <Row gutter={16}>
        <Col span={8}>
          <Card 
            title={<span><DesktopOutlined /> 缓存列表</span>}
            bordered={false} 
            extra={<Button icon={<ReloadOutlined />} onClick={fetchCacheNames} type="link" />}
            style={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column' }}
            bodyStyle={{ flex: 1, padding: 0, overflow: 'hidden' }}
          >
            <div style={{ padding: '8px 16px' }}>
                <Input 
                    placeholder="请输入缓存名称" 
                    prefix={<SearchOutlined />} 
                    value={nameFilter}
                    onChange={e => setNameFilter(e.target.value)}
                    allowClear
                />
            </div>
            <Table
              className="cache-list-table"
              columns={columnsName}
              dataSource={filteredCacheNames}
              rowKey="cacheName"
              loading={loadingNames}
              pagination={false}
              onRow={(record) => ({
                onClick: () => fetchCacheKeys(record.cacheKey),
                style: { cursor: 'pointer', backgroundColor: currentCacheName === record.cacheKey ? '#e6f7ff' : '' }
              })}
              scroll={{ x: 420, y: 'calc(100vh - 280px)' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card 
            title={<span><KeyOutlined /> 键名列表</span>}
            bordered={false}
            extra={<Button icon={<ReloadOutlined />} onClick={() => currentCacheName && fetchCacheKeys(currentCacheName)} disabled={!currentCacheName} type="link" />}
            style={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column' }}
            bodyStyle={{ flex: 1, padding: 0, overflow: 'hidden' }}
          >
             <div style={{ padding: '8px 16px' }}>
                <Input 
                    placeholder="请输入缓存键名" 
                    prefix={<SearchOutlined />} 
                    value={keyFilter}
                    onChange={e => setKeyFilter(e.target.value)}
                    allowClear
                    disabled={!currentCacheName}
                />
            </div>
            <Table
              className="cache-list-table"
              columns={columnsKey}
              dataSource={filteredCacheKeys}
              rowKey="cacheKey"
              loading={loadingKeys}
              pagination={false}
              onRow={(record) => ({
                onClick: () => handleViewValue(record),
                style: { cursor: 'pointer', backgroundColor: cacheValueData.cacheKey === record.cacheKey ? '#e6f7ff' : '' }
              })}
              scroll={{ x: 320, y: 'calc(100vh - 280px)' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card 
            title={<span><FileTextOutlined /> 缓存内容</span>}
            bordered={false}
            extra={
                <Space>
                    <Popconfirm title="确定要清理全部缓存吗？" onConfirm={handleClearAll}>
                        <Button icon={<ClearOutlined />} type="link" danger>清理全部</Button>
                    </Popconfirm>
                    <Button icon={<ReloadOutlined />} type="link" onClick={handleRefreshAll} disabled={!cacheValueData.cacheKey} />
                </Space>
            }
            style={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column' }}
            bodyStyle={{ flex: 1, padding: '16px', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
          >
             <Spin spinning={loadingValue} wrapperClassName="cache-content-spin" style={{ height: '100%' }}>
                <Form layout="vertical" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                    <Form.Item label="缓存名称">
                        <Input value={cacheValueData.cacheName} readOnly />
                    </Form.Item>
                    <Form.Item label="缓存键名">
                        <Input value={cacheValueData.cacheKey} readOnly />
                    </Form.Item>
                    <Form.Item label="TTL (秒)">
                        <Input value={cacheValueData.ttl} readOnly />
                    </Form.Item>
                    <Form.Item label="缓存内容" className="flex-1-form-item" style={{ marginBottom: 0 }}>
                        <Input.TextArea value={cacheValueData.cacheValue} style={{ height: '100%', resize: 'none' }} rows={12} readOnly />
                    </Form.Item>
                </Form>
             </Spin>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default CacheList;
