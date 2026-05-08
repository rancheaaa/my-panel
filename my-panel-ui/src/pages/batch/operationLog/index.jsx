import React, { useState, useEffect } from 'react';
import { Card, Table, Input, Select, Row, Col, Button, Tag, Space, Tooltip, Modal } from 'antd';
import { SearchOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import { getOperationLogs } from '../../../api/batch/monitor';
import { useNavigate } from 'react-router-dom';

const operationTypeLabel = {
  CREATE: '创建', START: '启动', PAUSE: '暂停', RESUME: '恢复', CANCEL: '取消',
  CONFIG_UPDATE: '配置修改', MANUAL_RETRY: '手动重试', DELETE: '删除'
};

const operationTypeColor = {
  CREATE: 'blue', START: 'green', PAUSE: 'orange', RESUME: 'green', CANCEL: 'default',
  CONFIG_UPDATE: 'purple', MANUAL_RETRY: 'gold', DELETE: 'red'
};

const OperationLogPage = () => {
  const navigate = useNavigate();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [queryParams, setQueryParams] = useState({ pageNum: 1, pageSize: 20 });
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailRecord, setDetailRecord] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getOperationLogs(queryParams);
      if (res.code === 200) {
        setData(res.data?.rows || []);
        setTotal(res.data?.total || 0);
      }
    } catch (e) { /* ignore */ }
    setLoading(false);
  };

  useEffect(() => { fetchData(); }, [queryParams.pageNum, queryParams.pageSize]);

  const showDetail = (record) => {
    setDetailRecord(record);
    setDetailVisible(true);
  };

  const columns = [
    {
      title: '任务ID', dataIndex: 'taskId', key: 'taskId', width: 80,
      render: v => v ? <Button type="link" size="small" onClick={() => navigate(`/batch/taskDetail?id=${v}`)}>#{v}</Button> : '-'
    },
    {
      title: '操作类型', dataIndex: 'operationType', key: 'operationType', width: 100,
      render: v => <Tag color={operationTypeColor[v] || 'default'}>{operationTypeLabel[v] || v}</Tag>
    },
    { title: '操作人', dataIndex: 'operatorName', key: 'operatorName', width: 100, render: v => v || '-' },
    { title: '操作时间', dataIndex: 'operationTime', key: 'operationTime', width: 170 },
    {
      title: '配置变更', key: 'configChange', width: 120,
      render: (_, r) => {
        if (!r.oldConfig && !r.newConfig) return '-';
        return <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => showDetail(r)}>查看详情</Button>;
      }
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', width: 200, ellipsis: true, render: v => v || '-' },
  ];

  return (
    <Card>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col><Input placeholder="任务ID" allowClear style={{ width: 120 }}
          onChange={e => setQueryParams(p => ({ ...p, taskId: e.target.value ? Number(e.target.value) : undefined, pageNum: 1 }))} /></Col>
        <Col><Select placeholder="操作类型" allowClear style={{ width: 130 }}
          onChange={v => setQueryParams(p => ({ ...p, operationType: v, pageNum: 1 }))}>
          {Object.entries(operationTypeLabel).map(([k, label]) => <Select.Option key={k} value={k}>{label}</Select.Option>)}
        </Select></Col>
        <Col><Input placeholder="操作人" allowClear style={{ width: 120 }}
          onChange={e => setQueryParams(p => ({ ...p, operatorName: e.target.value || undefined, pageNum: 1 }))} /></Col>
        <Col><Button icon={<SearchOutlined />} type="primary" onClick={fetchData}>搜索</Button></Col>
        <Col><Button icon={<ReloadOutlined />} onClick={() => { setQueryParams({ pageNum: 1, pageSize: 20 }); }}>重置</Button></Col>
      </Row>
      <Table rowKey="id" columns={columns} dataSource={data} loading={loading}
        pagination={{
          current: queryParams.pageNum, pageSize: queryParams.pageSize, total, showTotal: t => `共 ${t} 条`,
          onChange: (p, s) => setQueryParams(prev => ({ ...prev, pageNum: p, pageSize: s }))
        }}
      />

      <Modal title="配置变更详情" open={detailVisible} onCancel={() => setDetailVisible(false)}
        footer={<Button onClick={() => setDetailVisible(false)}>关闭</Button>} width={700}>
        {detailRecord && (
          <div>
            <Row gutter={16}>
              <Col span={12}>
                <Card title="变更前配置" size="small" style={{ background: '#fff2f0' }}>
                  <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontSize: 12, maxHeight: 300, overflow: 'auto' }}>
                    {detailRecord.oldConfig ? tryFormatJson(detailRecord.oldConfig) : '(无)'}
                  </pre>
                </Card>
              </Col>
              <Col span={12}>
                <Card title="变更后配置" size="small" style={{ background: '#f6ffed' }}>
                  <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontSize: 12, maxHeight: 300, overflow: 'auto' }}>
                    {detailRecord.newConfig ? tryFormatJson(detailRecord.newConfig) : '(无)'}
                  </pre>
                </Card>
              </Col>
            </Row>
          </div>
        )}
      </Modal>
    </Card>
  );
};

function tryFormatJson(str) {
  try {
    return JSON.stringify(JSON.parse(str), null, 2);
  } catch {
    return str;
  }
}

export default OperationLogPage;
