import React, { useState, useEffect } from 'react';
import { Row, Col, Select, Card, Spin } from 'antd';
import { getQueueStatus, getQueueTrend } from '../../../api/batch/monitor';
import { listAgentRegistry } from '../../../api/agent';
import QueueStatusCard from '../../../components/batch/QueueStatusCard';
import QueueTrendChart from '../../../components/batch/QueueTrendChart';
import MitigationSuggestion from '../../../components/batch/MitigationSuggestion';

const agentLabel = (a) => `${a.appId || 'unknown'}@${a.agentIp}`;

const agentFilterOption = (input, option) => {
  const label = option.label || '';
  const lower = input.toLowerCase();
  return label.toLowerCase().includes(lower);
};

const QueueMonitor = () => {
  const [agents, setAgents] = useState([]);
  const [selectedAgent, setSelectedAgent] = useState(null);
  const [queueStatus, setQueueStatus] = useState(null);
  const [trendData, setTrendData] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchAgents = async () => {
      try {
        const res = await listAgentRegistry({ pageNum: 1, pageSize: 200 });
        if (res.code === 200) {
          const list = res.data?.rows || [];
          setAgents(list);
          if (list.length > 0 && !selectedAgent) setSelectedAgent(list[0].id);
        }
      } catch (e) { /* ignore */ }
    };
    fetchAgents();
  }, []);

  useEffect(() => {
    if (!selectedAgent) return;
    setLoading(true);
    const fetchStatus = async () => {
      try {
        const res = await getQueueStatus(selectedAgent);
        if (res.code === 200) setQueueStatus(res.data);
      } catch (e) { /* ignore */ }
    };
    const fetchTrend = async () => {
      try {
        const res = await getQueueTrend(selectedAgent);
        if (res.code === 200) setTrendData(res.data || []);
      } catch (e) { /* ignore */ }
    };
    Promise.all([fetchStatus(), fetchTrend()]).finally(() => setLoading(false));
  }, [selectedAgent]);

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Select showSearch filterOption={agentFilterOption} style={{ width: 300 }}
          placeholder="输入appId或IP模糊搜索" value={selectedAgent} onChange={setSelectedAgent}>
          {agents.map(a => (
            <Select.Option key={a.id} value={a.id} label={agentLabel(a)}>
              {agentLabel(a)}
            </Select.Option>
          ))}
        </Select>
      </Card>
      <Spin spinning={loading}>
        <QueueStatusCard agentId={selectedAgent} status={queueStatus} loading={loading} />
        {queueStatus && queueStatus.congestionLevel !== 'NORMAL' && (
          <MitigationSuggestion
            congestionLevel={queueStatus.congestionLevel}
            congestionReason={queueStatus.congestionReason}
            sendQueueDepth={queueStatus.sendQueueDepth}
            processingRatePerSec={queueStatus.processingRatePerSec}
            avgWaitMs={queueStatus.sendQueueAvgWaitMs}
          />
        )}
        <Row gutter={16}>
          <Col span={24}><QueueTrendChart data={trendData} title={`${selectedAgent || 'Agent'} 队列深度趋势`} /></Col>
        </Row>
      </Spin>
    </div>
  );
};

export default QueueMonitor;
