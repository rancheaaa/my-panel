import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Space, Spin, message, Card, Row, Col } from 'antd';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { listSubtasks, retrySubtask, getBatchTaskDetail } from '../../../api/batch/task';
import SubtaskTable from '../../../components/batch/SubtaskTable';

const SubtaskDetail = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [taskId] = useState(searchParams.get('taskId'));
  const [task, setTask] = useState(null);
  const [subtasks, setSubtasks] = useState([]);
  const [enrichedInfo, setEnrichedInfo] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchData = async () => {
    if (!taskId) return;
    setLoading(true);
    try {
      const taskRes = await getBatchTaskDetail(taskId);
      if (taskRes.code === 200) setTask(taskRes.data);

      const subtaskRes = await listSubtasks(taskId);
      if (subtaskRes.code === 200) {
        const data = subtaskRes.data;
        if (data && data.subtasks) {
          setSubtasks(data.subtasks);
          setEnrichedInfo({
            sourceDir: data.sourceDir,
            sourceNodeName: data.sourceNodeName,
            targetAgentInfoMap: data.targetAgentInfoMap || {}
          });
        } else {
          setSubtasks(data?.rows || data || []);
          setEnrichedInfo(null);
        }
      }
    } catch (e) {
      message.error('加载失败');
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchData();
  }, [taskId]);

  const handleRetry = async (tid, sid) => {
    try {
      await retrySubtask(tid, sid);
      message.success('已重试');
      fetchData();
    } catch (e) {
      message.error('重试失败');
    }
  };

  if (loading && !task) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (!taskId) return <Card><div style={{ textAlign: 'center', padding: 40 }}>任务ID缺失</div></Card>;

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>返回</Button>
            <span style={{ fontSize: 16, fontWeight: 'bold', marginLeft: 8 }}>
              子任务明细 - {task?.taskName || taskId}
            </span>
          </Space>
        </Col>
        <Col>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>
        </Col>
      </Row>

      <Card size="small">
        <SubtaskTable
          subtasks={subtasks}
          loading={loading}
          onRetry={handleRetry}
          enrichedInfo={enrichedInfo}
        />
      </Card>
    </div>
  );
};

export default SubtaskDetail;
