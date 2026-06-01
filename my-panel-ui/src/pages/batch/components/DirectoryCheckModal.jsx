import React, { useState, useEffect } from 'react';
import { Modal, Tag, Button, Spin, Descriptions, Progress, Tooltip } from 'antd';
import {
  FolderOpenOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  CloudServerOutlined
} from '@ant-design/icons';
import { batchApi } from '../../../api/batch';
import './directory-check.scss';

const formatMB = (mb) => {
  if (mb == null) return '-';
  if (mb >= 1024) return (mb / 1024).toFixed(1) + ' GB';
  return mb.toLocaleString() + ' MB';
};

export const BoolTag = ({ value, trueText, falseText, trueColor, falseColor }) => {
  if (value == null) return <Tag color="#d9d9d9">未知</Tag>;
  return value
    ? <Tag color={trueColor || 'success'} icon={<CheckCircleOutlined />}>{trueText || '是'}</Tag>
    : <Tag color={falseColor || 'error'} icon={<CloseCircleOutlined />}>{falseText || '否'}</Tag>;
};

const DirCheckCard = ({ data, title, iconColor }) => {
  if (!data) return null;

  const hasError = data.errorMessage;
  const isOffline = data.agentOnline === false;
  const notExists = data.exists === false;

  return (
    <div className={`dir-check-card ${hasError || isOffline || notExists ? 'has-error' : 'ok'}`}>
      <div className="dir-check-card-header">
        <CloudServerOutlined style={{ color: iconColor, fontSize: 16, marginRight: 8 }} />
        <span className="dir-check-card-title">{title}</span>
        {data.agentName && <span className="dir-check-card-agent">{data.agentName}</span>}
        {data.agentIp && data.agentPort && (
          <span className="dir-check-card-addr">{data.agentIp}:{data.agentPort}</span>
        )}
        {isOffline && <Tag color="error" style={{ marginLeft: 8 }}>离线</Tag>}
      </div>

      <div className="dir-check-card-path">
        <FolderOpenOutlined style={{ marginRight: 6, color: '#fa8c16' }} />
        <span className="dir-check-path-text">{data.dirPath || '-'}</span>
      </div>

      {hasError && !isOffline ? (
        <div className="dir-check-card-error">
          <ExclamationCircleOutlined style={{ marginRight: 6 }} />
          {data.errorMessage}
        </div>
      ) : isOffline ? (
        <div className="dir-check-card-error">
          <ExclamationCircleOutlined style={{ marginRight: 6 }} />
          节点离线，无法检测
        </div>
      ) : (
        <>
          <div className="dir-check-card-section">
            <div className="dir-check-section-label">目录状态</div>
            <div className="dir-check-section-content">
              <div className="dir-check-item">
                <span className="dir-check-item-label">是否存在</span>
                <BoolTag value={data.exists} trueText="存在" falseText="不存在" />
              </div>
              {data.exists && (
                <div className="dir-check-item">
                  <span className="dir-check-item-label">是否为目录</span>
                  <BoolTag value={data.isDirectory} trueText="是" falseText="否" falseColor="warning" />
                </div>
              )}
            </div>
          </div>

          {data.exists && (
            <>
              <div className="dir-check-card-section">
                <div className="dir-check-section-label">访问权限</div>
                <div className="dir-check-section-content">
                  <div className="dir-check-item">
                    <span className="dir-check-item-label">读取</span>
                    <BoolTag value={data.canRead} trueText="可读" falseText="不可读" />
                  </div>
                  <div className="dir-check-item">
                    <span className="dir-check-item-label">写入</span>
                    <BoolTag value={data.canWrite} trueText="可写" falseText="不可写" />
                  </div>
                  <div className="dir-check-item">
                    <span className="dir-check-item-label">执行</span>
                    <BoolTag value={data.canExecute} trueText="可执行" falseText="不可执行" />
                  </div>
                  {data.posixPermissions && (
                    <div className="dir-check-item">
                      <span className="dir-check-item-label">权限码</span>
                      <code className="dir-check-perm-code">{data.posixPermissions}</code>
                    </div>
                  )}
                </div>
              </div>

              <div className="dir-check-card-section">
                <div className="dir-check-section-label">磁盘空间</div>
                <div className="dir-check-section-content">
                  <div className="dir-check-item">
                    <span className="dir-check-item-label">总空间</span>
                    <span className="dir-check-item-value">{formatMB(data.diskTotalMB)}</span>
                  </div>
                  <div className="dir-check-item">
                    <span className="dir-check-item-label">可用空间</span>
                    <span className="dir-check-item-value">{formatMB(data.diskUsableMB)}</span>
                  </div>
                  <div className="dir-check-item">
                    <span className="dir-check-item-label">剩余空间</span>
                    <span className="dir-check-item-value">{formatMB(data.diskFreeMB)}</span>
                  </div>
                  <div className="dir-check-item">
                    <span className="dir-check-item-label">空间充足</span>
                    <BoolTag value={data.diskSufficient} trueText="充足" falseText="不足" />
                  </div>
                  {data.diskTotalMB > 0 && (
                    <div className="dir-check-disk-bar">
                      <Progress
                        percent={Math.round(((data.diskTotalMB - data.diskUsableMB) / data.diskTotalMB) * 100)}
                        size="small"
                        strokeColor={data.diskUsableMB < 100 ? '#ff4d4f' : '#52c41a'}
                        format={() => `已用 ${formatMB(data.diskTotalMB - data.diskUsableMB)}`}
                      />
                    </div>
                  )}
                </div>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
};

export default function DirectoryCheckModal({ open, onClose, task }) {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);

  const runCheck = async () => {
    if (!task?.id) return;
    setLoading(true);
    setResult(null);
    try {
      const res = await batchApi.checkDirectories(task.id);
      if (res.code === 200) {
        setResult(res.data);
      }
    } catch (e) {
      console.error('目录检测失败', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open && task?.id) {
      runCheck();
    }
    if (!open) {
      setResult(null);
    }
  }, [open, task?.id]);

  const taskName = task?.taskName || task?.name || '';

  return (
    <Modal
      title={null}
      open={open}
      onCancel={onClose}
      width={900}
      footer={null}
      destroyOnClose
      className="dir-check-modal"
      closable
      style={{ top: 30 }}
    >
      <div className="dir-check-container">
        <div className="dir-check-toolbar">
          <div className="dir-check-toolbar-left">
            <FolderOpenOutlined style={{ fontSize: 18, color: '#fa8c16' }} />
            <span className="dir-check-toolbar-title">目录检测</span>
            {taskName && <span className="dir-check-toolbar-task">{taskName}</span>}
          </div>
          <div className="dir-check-toolbar-right">
            <Button icon={<ReloadOutlined />} onClick={runCheck} loading={loading} size="small">
              重新检测
            </Button>
          </div>
        </div>

        <div className="dir-check-body">
          {loading && !result && (
            <div className="dir-check-loading">
              <Spin size="large" tip="正在检测目录..." />
            </div>
          )}

          {result && (
            <div className="dir-check-results">
              <div className="dir-check-section-title">
                <span className="dir-check-section-dot source" />
                发送目录
              </div>
              <DirCheckCard data={result.source} title="发送节点" iconColor="#1890ff" />

              {result.targets && result.targets.length > 0 && (
                <>
                  <div className="dir-check-section-title" style={{ marginTop: 20 }}>
                    <span className="dir-check-section-dot target" />
                    接收目录
                  </div>
                  {result.targets.map((t, idx) => (
                    <DirCheckCard
                      key={idx}
                      data={t}
                      title={`接收节点 ${idx + 1}`}
                      iconColor="#722ed1"
                    />
                  ))}
                </>
              )}
            </div>
          )}

          {!loading && !result && (
            <div className="dir-check-empty">点击"重新检测"开始检测</div>
          )}
        </div>
      </div>
    </Modal>
  );
}
