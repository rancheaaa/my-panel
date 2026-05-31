import { useState, useCallback, useEffect, useRef } from 'react';
import { message } from 'antd';
import { batchApi } from '../../../api/batch';

export function useTaskImport() {
  const [batches, setBatches] = useState([]);
  const [currentBatch, setCurrentBatch] = useState(null);
  const [previewData, setPreviewData] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [operating, setOperating] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const searchRef = useRef('');

  const fetchBatches = useCallback(async (keyword) => {
    try {
      const k = keyword !== undefined ? keyword : searchRef.current;
      const res = await batchApi.listImportBatches(k || '');
      if (res.code === 200) {
        setBatches(res.data || []);
      }
    } catch (error) {
      console.error('加载批次列表失败:', error);
    }
  }, []);

  const handleSearchChange = useCallback((value) => {
    searchRef.current = value;
    setSearchKeyword(value);
    fetchBatches(value);
  }, [fetchBatches]);

  const handleUpload = useCallback(async (file) => {
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await batchApi.uploadTaskImport(formData);
      if (res.code === 200 && res.data) {
        setPreviewData(res.data);
        setCurrentBatch(res.data.batchNo);
        message.success(`上传成功: 共${res.data.total}条, 通过${res.data.passCount}条, 失败${res.data.failCount}条`);
        fetchBatches();
      } else {
        message.error(res.msg || '上传失败');
      }
    } catch (error) {
      console.error('上传失败:', error);
      message.error('上传失败');
    } finally {
      setUploading(false);
    }
  }, [fetchBatches]);

  const handlePreview = useCallback(async (batchNo) => {
    try {
      const res = await batchApi.previewImport(batchNo);
      if (res.code === 200) {
        setPreviewData(res.data);
        setCurrentBatch(batchNo);
      }
    } catch (error) {
      console.error('预览失败:', error);
    }
  }, []);

  const handleCommit = useCallback(async (mode = 'STRICT') => {
    if (!currentBatch) return;
    setOperating(true);
    try {
      const res = await batchApi.commitImport(currentBatch, mode);
      if (res.code === 200) {
        message.success(`成功导入 ${res.data} 个任务`);
        fetchBatches();
        handlePreview(currentBatch);
      } else {
        message.error(res.msg || '导入失败');
      }
    } catch (error) {
      console.error('导入失败:', error);
      message.error('导入失败');
    } finally {
      setOperating(false);
    }
  }, [currentBatch, fetchBatches, handlePreview]);

  const handleRollback = useCallback(async () => {
    if (!currentBatch) return;
    setOperating(true);
    try {
      const res = await batchApi.rollbackImport(currentBatch);
      if (res.code === 200) {
        message.success(`成功回退 ${res.data} 个任务`);
        fetchBatches();
        handlePreview(currentBatch);
      } else {
        message.error(res.msg || '回退失败');
      }
    } catch (error) {
      console.error('回退失败:', error);
      message.error('回退失败');
    } finally {
      setOperating(false);
    }
  }, [currentBatch, fetchBatches, handlePreview]);

  const handleBatchStart = useCallback(async () => {
    if (!currentBatch) return;
    setOperating(true);
    try {
      const res = await batchApi.batchStartImport(currentBatch);
      if (res.code === 200) {
        message.success(`成功启动 ${res.data} 个任务`);
        handlePreview(currentBatch);
      } else {
        message.error(res.msg || '启动失败');
      }
    } catch (error) {
      console.error('启动失败:', error);
      message.error('启动失败');
    } finally {
      setOperating(false);
    }
  }, [currentBatch, handlePreview]);

  const handleBatchPause = useCallback(async () => {
    if (!currentBatch) return;
    setOperating(true);
    try {
      const res = await batchApi.batchPauseImport(currentBatch);
      if (res.code === 200) {
        message.success(`成功暂停 ${res.data} 个任务`);
        handlePreview(currentBatch);
      } else {
        message.error(res.msg || '暂停失败');
      }
    } catch (error) {
      console.error('暂停失败:', error);
      message.error('暂停失败');
    } finally {
      setOperating(false);
    }
  }, [currentBatch, handlePreview]);

  const handleDeleteBatch = useCallback(async (batchNo) => {
    try {
      const res = await batchApi.deleteImportBatch(batchNo);
      if (res.code === 200) {
        message.success('删除成功');
        if (currentBatch === batchNo) {
          setCurrentBatch(null);
          setPreviewData(null);
        }
        fetchBatches();
      } else {
        message.error(res.msg || '删除失败');
      }
    } catch (error) {
      console.error('删除失败:', error);
      message.error('删除失败');
    }
  }, [currentBatch, fetchBatches]);

  const selectBatch = useCallback((batchNo) => {
    setCurrentBatch(batchNo);
    handlePreview(batchNo);
  }, [handlePreview]);

  useEffect(() => {
    fetchBatches();
  }, [fetchBatches]);

  return {
    batches,
    currentBatch,
    previewData,
    uploading,
    operating,
    searchKeyword,
    handleSearchChange,
    fetchBatches,
    handleUpload,
    handleCommit,
    handleRollback,
    handleBatchStart,
    handleBatchPause,
    handleDeleteBatch,
    selectBatch
  };
}
