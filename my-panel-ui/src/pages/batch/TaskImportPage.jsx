import React, { useRef } from 'react';
import { Tag, Upload, Button, Popconfirm, Tooltip, Badge, Empty, Dropdown, Table, Input, message } from 'antd';
import {
  DownloadOutlined,
  ImportOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  RollbackOutlined,
  DeleteOutlined,
  EyeOutlined,
  InboxOutlined,
  CloudUploadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SwapOutlined,
  DownOutlined,
  SafetyCertificateOutlined,
  UnlockOutlined,
  ClockCircleOutlined,
  SearchOutlined
} from '@ant-design/icons';
import { useTaskImport } from './hooks/useTaskImport';
import { batchApi } from '../../api/batch';
import './index.scss';

const importStatusMap = {
  PENDING: { color: '#1890ff', text: '待导入', dot: 'processing' },
  IMPORTED: { color: '#52c41a', text: '已导入', dot: 'success' },
  ROLLBACK: { color: '#faad14', text: '已回退', dot: 'warning' },
  PARTIAL: { color: '#ff4d4f', text: '部分失败', dot: 'error' }
};

const TaskImportPage = () => {
  const {
    batches,
    currentBatch,
    previewData,
    uploading,
    operating,
    handleUpload,
    handleCommit,
    handleRollback,
    handleBatchStart,
    handleBatchPause,
    handleDeleteBatch,
    selectBatch,
    searchKeyword,
    handleSearchChange
  } = useTaskImport();

  const CHINESE_REGEX = /[\u4e00-\u9fa5]/;
  const uploadRef = useRef(null);

  const handleDownloadTemplate = async () => {
    try {
      const res = await batchApi.downloadTemplate();
      const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `batch_task_import_template_${new Date().toISOString().replace(/[-:T.]/g, '').slice(0, 17)}.xlsx`;
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('下载模板失败:', error);
    }
  };

  const uploadProps = {
    name: 'file',
    multiple: false,
    accept: '.xlsx,.xls',
    showUploadList: false,
    beforeUpload: (file) => {
      if (CHINESE_REGEX.test(file.name)) {
        message.error('文件名不能包含中文');
        return Upload.LIST_IGNORE;
      }
      return true;
    },
    customRequest: ({ file, onSuccess, onError }) => {
      handleUpload(file)
        .then(() => onSuccess())
        .catch(() => onError(new Error('上传失败')));
    }
  };

  const currentBatchInfo = batches.find(b => b.batchNo === currentBatch);
  const currentImportStatus = currentBatchInfo?.importStatus;
  const isRollback = currentImportStatus === 'ROLLBACK';
  const isPending = currentImportStatus === 'PENDING';
  const notImported = isPending || isRollback;

  const passCount = previewData?.passCount ?? 0;
  const failCount = previewData?.failCount ?? 0;
  const totalCount = previewData?.total ?? 0;

  const boolRender = (val) => val === 1 ? '是' : val === 0 ? '否' : '-';
  const enumRender = (val, map) => map[val] || val || '-';

  const transferModeMap = { ONE_TO_ONE: '一对一', ONE_TO_MANY: '一对多' };
  const backoffTypeMap = { LINEAR: '线性', EXPONENTIAL: '指数' };
  const postActionMap = { NONE: '无操作', DELETE: '删除源文件', BACKUP: '备份' };
  const backupModeMap = { COPY: '复制', MOVE: '移动' };
  const routingMap = { BROADCAST: '广播', ROUND_ROBIN: '轮询', RANDOM: '随机', REGION_BASED: '区域' };

  const previewColumns = [
    { title: '#', dataIndex: 'rowNum', key: 'rowNum', width: 48, align: 'center', fixed: 'left',
      render: (val) => <span className="row-num">{val}</span> },
    { title: '校验', dataIndex: 'validateStatus', key: 'validateStatus', width: 56, align: 'center', fixed: 'left',
      render: (status) => status === 'PASS'
        ? <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />
        : <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 16 }} /> },
    { title: '传输模式', dataIndex: 'transferMode', key: 'transferMode', width: 90, align: 'center',
      render: (v) => enumRender(v, transferModeMap) },
    { title: '任务名称', dataIndex: 'taskName', key: 'taskName', width: 150, ellipsis: true },
    { title: '任务描述', dataIndex: 'taskDescription', key: 'taskDescription', width: 180, ellipsis: true,
      render: (v) => v || '-' },
    { title: '源节点名称', dataIndex: 'sourceAgentName', key: 'sourceAgentName', width: 160, ellipsis: true,
      render: (v) => v || '-' },
    { title: '源目录', dataIndex: 'sourceDir', key: 'sourceDir', width: 160, ellipsis: true,
      render: (v) => <span className="dir-cell">{v || '-'}</span> },
    { title: '目标节点名称', dataIndex: 'targetAgentNames', key: 'targetAgentNames', width: 180, ellipsis: true,
      render: (v) => v || '-' },
    { title: '目标目录', dataIndex: 'targetDirs', key: 'targetDirs', width: 160, ellipsis: true,
      render: (v) => <span className="dir-cell">{v || '-'}</span> },
    { title: '包含模式', dataIndex: 'includePatterns', key: 'includePatterns', width: 130, ellipsis: true,
      render: (v) => v || '-' },
    { title: '排除模式', dataIndex: 'excludePatterns', key: 'excludePatterns', width: 130, ellipsis: true,
      render: (v) => v || '-' },
    { title: '执行频率', dataIndex: 'scanCronExpression', key: 'scanCronExpression', width: 130, ellipsis: true,
      render: (v) => v || '-' },
    { title: '最大扫描文件数', dataIndex: 'maxScanFiles', key: 'maxScanFiles', width: 100, align: 'center',
      render: (v) => v ?? '-' },
    { title: '启用重试', dataIndex: 'retryEnabled', key: 'retryEnabled', width: 80, align: 'center',
      render: (v) => boolRender(v) },
    { title: '重试保留天数', dataIndex: 'retryMaxDays', key: 'retryMaxDays', width: 100, align: 'center',
      render: (v) => v ?? '-' },
    { title: '重试间隔(分钟)', dataIndex: 'retryIntervalMin', key: 'retryIntervalMin', width: 110, align: 'center',
      render: (v) => v ?? '-' },
    { title: '最大重试次数', dataIndex: 'maxRetryCount', key: 'maxRetryCount', width: 100, align: 'center',
      render: (v) => v ?? '-' },
    { title: '重试退避策略', dataIndex: 'retryBackoffType', key: 'retryBackoffType', width: 100, align: 'center',
      render: (v) => enumRender(v, backoffTypeMap) },
    { title: '传输后操作', dataIndex: 'postTransferAction', key: 'postTransferAction', width: 100, align: 'center',
      render: (v) => enumRender(v, postActionMap) },
    { title: '备份目录', dataIndex: 'backupDir', key: 'backupDir', width: 130, ellipsis: true,
      render: (v) => v || '-' },
    { title: '备份模式', dataIndex: 'backupMode', key: 'backupMode', width: 80, align: 'center',
      render: (v) => enumRender(v, backupModeMap) },
    { title: '保持目录结构', dataIndex: 'preserveDirStructure', key: 'preserveDirStructure', width: 100, align: 'center',
      render: (v) => boolRender(v) },
    { title: '路由策略', dataIndex: 'routingStrategy', key: 'routingStrategy', width: 80, align: 'center',
      render: (v) => enumRender(v, routingMap) },
    { title: '路由配置', dataIndex: 'routingConfig', key: 'routingConfig', width: 100, ellipsis: true,
      render: (v) => v || '-' },
    { title: '定时传输', dataIndex: 'scheduledEnabled', key: 'scheduledEnabled', width: 80, align: 'center',
      render: (v) => boolRender(v) },
    { title: '定时开始时间', dataIndex: 'scheduledStartTime', key: 'scheduledStartTime', width: 100, align: 'center',
      render: (v) => v || '-' },
    { title: '定时结束时间', dataIndex: 'scheduledEndTime', key: 'scheduledEndTime', width: 100, align: 'center',
      render: (v) => v || '-' },
    { title: '优先级', dataIndex: 'taskPriority', key: 'taskPriority', width: 70, align: 'center',
      render: (v) => v ?? '-' },
    { title: '备注', dataIndex: 'remark', key: 'remark', width: 150, ellipsis: true,
      render: (v) => v || '-' },
    { title: '校验信息', dataIndex: 'validateMessage', key: 'validateMessage', width: 260, ellipsis: true, fixed: 'right',
      render: (msg, record) => {
        if (!msg) return '-';
        if (record.validateStatus === 'PASS') return <span style={{ color: '#52c41a' }}>{msg}</span>;
        return (
          <Tooltip title={msg}>
            <span className="error-msg">
              {msg.length > 50 ? msg.substring(0, 50) + '...' : msg}
            </span>
          </Tooltip>
        );
      }
    }
  ];

  return (
    <div className="task-import-page">
      <div className="import-layout">
        <div className="import-header">
          <div className="header-left">
            <div className="header-title">
              <SwapOutlined className="header-icon" />
              <span>批量任务导入</span>
            </div>
          </div>
          <div className="header-actions">
            <Upload {...uploadProps} ref={uploadRef}>
              <Button
                icon={<CloudUploadOutlined />}
                loading={uploading}
                className="btn-upload"
              >
                上传任务
              </Button>
            </Upload>
            <Button
              icon={<DownloadOutlined />}
              onClick={handleDownloadTemplate}
              className="btn-download"
            >
              下载模板
            </Button>
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'STRICT',
                    icon: <SafetyCertificateOutlined />,
                    label: '严格导入 — 仅导入校验通过的行',
                    onClick: () => handleCommit('STRICT')
                  },
                  {
                    key: 'LOOSE',
                    icon: <UnlockOutlined />,
                    label: '松散导入 — 导入能写入任务表的行',
                    onClick: () => handleCommit('LOOSE')
                  }
                ]
              }}
              trigger={['click']}
            >
              <Button
                type="primary"
                icon={<ImportOutlined />}
                loading={operating}
                disabled={!currentBatch || isRollback}
                className="btn-commit"
              >
                批导入 <DownOutlined />
              </Button>
            </Dropdown>
            <Popconfirm title="确定批量启动该批次任务？" onConfirm={handleBatchStart}>
              <Button
                icon={<PlayCircleOutlined />}
                loading={operating}
                disabled={!currentBatch || notImported}
                className="btn-action"
              >
                启用
              </Button>
            </Popconfirm>
            <Popconfirm title="确定批量暂停该批次任务？" onConfirm={handleBatchPause}>
              <Button
                icon={<PauseCircleOutlined />}
                loading={operating}
                disabled={!currentBatch || notImported}
                className="btn-action"
              >
                暂停
              </Button>
            </Popconfirm>
            <Popconfirm title="确定回退该批次已导入的任务？" onConfirm={handleRollback}>
              <Button
                icon={<RollbackOutlined />}
                loading={operating}
                disabled={!currentBatch || notImported}
                className="btn-action"
              >
                回退
              </Button>
            </Popconfirm>
            <Popconfirm title="确定删除该批次？" onConfirm={() => handleDeleteBatch(currentBatch)}>
              <Button
                danger
                icon={<DeleteOutlined />}
                disabled={!currentBatch}
                className="btn-action btn-danger"
              >
                删除
              </Button>
            </Popconfirm>
          </div>
        </div>

        <div className="import-body">
          <div className="import-sidebar">
            <div className="batch-list-section">
              <div className="section-header">
                <span className="section-title">历史批次</span>
                <div className="section-right">
                  <Input
                    placeholder="搜索文件名..."
                    prefix={<SearchOutlined />}
                    value={searchKeyword}
                    onChange={(e) => handleSearchChange(e.target.value)}
                    allowClear
                    size="small"
                    className="search-input"
                  />
                  <Badge count={batches.length} style={{ backgroundColor: '#1890ff' }} />
                </div>
              </div>
              <div className="batch-cards">
                {batches.length === 0 ? (
                  <Empty description="暂无批次" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  batches.map((batch) => {
                    const status = importStatusMap[batch.importStatus] || { color: '#d9d9d9', text: '-', dot: 'default' };
                    const isActive = currentBatch === batch.batchNo;
                    const displayName = batch.fileName
                      ? batch.fileName.replace('batch_task_import_template_', '').replace('.xlsx', '')
                      : batch.batchNo.slice(0, 8);
                    return (
                      <div
                        key={batch.batchNo}
                        className={`batch-card ${isActive ? 'active' : ''}`}
                        onClick={() => selectBatch(batch.batchNo)}
                      >
                        <Badge status={status.dot} className="card-dot" />
                        <span className="card-name" title={batch.fileName || batch.batchNo}>{displayName}</span>
                        <span className="card-time">
                          <ClockCircleOutlined /> {batch.createTime ? batch.createTime.replace('T', ' ').slice(5, 16) : '-'}
                        </span>
                        <span className="card-tags">
                          <span className="tag-pass">{batch.passCount ?? 0}</span>
                          <span className="tag-sep">/</span>
                          <span className="tag-fail">{batch.failCount ?? 0}</span>
                        </span>
                        <span className={`card-status-text status-${batch.importStatus?.toLowerCase()}`}>{status.text}</span>
                        <div className="card-actions">
                          <Tooltip title="预览">
                            <Button type="text" size="small" icon={<EyeOutlined />} onClick={(e) => { e.stopPropagation(); selectBatch(batch.batchNo); }} />
                          </Tooltip>
                          <Popconfirm title="确定删除？" onConfirm={(e) => { e?.stopPropagation?.(); handleDeleteBatch(batch.batchNo); }}>
                            <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={(e) => e.stopPropagation()} />
                          </Popconfirm>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          </div>

          <div className="import-main">
            {previewData ? (
              <>
                <div className="preview-header">
                  <div className="preview-title">
                    <span>数据预览</span>
                    <Tag className="batch-tag">{currentBatch?.slice(0, 8)}</Tag>
                  </div>
                  <div className="preview-stats">
                    <div className="stat-item stat-total">
                      <span className="stat-value">{totalCount}</span>
                      <span className="stat-label">总计</span>
                    </div>
                    <div className="stat-divider" />
                    <div className="stat-item stat-pass">
                      <span className="stat-value">{passCount}</span>
                      <span className="stat-label">通过</span>
                    </div>
                    <div className="stat-divider" />
                    <div className="stat-item stat-fail">
                      <span className="stat-value">{failCount}</span>
                      <span className="stat-label">失败</span>
                    </div>
                  </div>
                </div>
                <div className="preview-table-wrap">
                  <Table
                    columns={previewColumns}
                    dataSource={previewData?.rows || []}
                    rowKey="rowNum"
                    size="middle"
                    pagination={false}
                    scroll={{ x: 3200 }}
                    rowClassName={(record) => record.validateStatus === 'PASS' ? 'row-valid' : 'row-invalid'}
                  />
                </div>
              </>
            ) : (
              <div className="preview-empty">
                <div className="empty-illustration">
                  <InboxOutlined className="empty-icon" />
                </div>
                <p className="empty-title">上传 Excel 文件开始导入</p>
                <p className="empty-desc">支持批量导入传输任务，自动校验数据完整性</p>
                <div className="empty-steps">
                  <div className="step"><span className="step-num">1</span> 下载模板</div>
                  <div className="step-arrow" />
                  <div className="step"><span className="step-num">2</span> 填写数据</div>
                  <div className="step-arrow" />
                  <div className="step"><span className="step-num">3</span> 上传导入</div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default TaskImportPage;
