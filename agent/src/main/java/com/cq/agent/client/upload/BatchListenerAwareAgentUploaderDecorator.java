package com.cq.agent.client.upload;

import com.cq.agent.batch.scheduler.BatchUploadListener;
import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Objects;

/**
 * 批量任务监听器感知的上传装饰者（基于接口）
 * 
 * 使用装饰者模式为 UploadService 添加监听器管理能力：
 * - 监听器的反射创建与缓存
 * - 重启恢复场景的状态重建
 * - ProgressReporter 集成
 * 
 * 设计原则：
 * - 实现 UploadService 接口（与核心类相同的契约）
 * - 通过组合包装原始 AgentUploader
 * - 单一职责：只关注监听器生命周期管理
 */
public class BatchListenerAwareAgentUploaderDecorator implements UploadService {

    private static final Logger logger = LoggerFactory.getLogger(
        BatchListenerAwareAgentUploaderDecorator.class);

    /** 委托对象（必须实现 UploadService 接口） */
    private final UploadService delegate;
    
    /** 全局ProgressReporter实例 */
    private ProgressReporter globalProgressReporter;

    /**
     * 构造函数
     * @param delegate 被装饰的上传服务实例（通常是 AgentUploader）
     */
    public BatchListenerAwareAgentUploaderDecorator(UploadService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为null");
        logger.info("✅ 创建BatchListenerAwareAgentUploaderDecorator");
    }

    // ==================== UploadService 接口实现 ====================

    @Override
    public boolean uploadFile(String localFilePath, String remoteTargetInfo, UploadListener listener) {
        boolean result = delegate.uploadFile(localFilePath, remoteTargetInfo, listener);
        
        if (result && listener != null) {
            populateRestoreFieldsFromListener(listener);
        }
        
        return result;
    }

    @Override
    public AgentConfig getAgentConfig() {
        return delegate.getAgentConfig();
    }

    // ==================== 监听器增强功能 ====================

    /**
     * 设置全局ProgressReporter（用于重启恢复场景）
     */
    public void setGlobalProgressReporter(ProgressReporter progressReporter) {
        this.globalProgressReporter = progressReporter;
        logger.info("✅ 已设置全局ProgressReporter: {}", 
            progressReporter != null ? "已配置" : "null");
    }

    /**
     * 获取全局ProgressReporter
     */
    public ProgressReporter getGlobalProgressReporter() {
        return globalProgressReporter;
    }

    /**
     * 处理带监听器的任务（从processTask中抽离）
     */
    public void handleListenerForTask(UploadTask task, String taskKey) {
        if (task == null || taskKey == null) return;
        
        String listenerClassName = task.getListenerClassName();
        if (listenerClassName == null) return;
        
        try {
            UploadListener listener = createListenerInstance(listenerClassName, UploadListener.class);
            if (listener != null) {
                restoreListenerStateIfNeeded(listener, task);
                cacheListener(taskKey, listener);
            }
        } catch (Exception e) {
            logger.error("❌ 创建或恢复监听器失败: className={}", listenerClassName, e);
        }
    }

    /**
     * 恢复监听器状态（重启恢复场景）
     */
    public void restoreListenerStateIfNeeded(UploadListener listener, UploadTask task) {
        if (listener == null || task == null) return;
        
        if (!(listener instanceof BatchUploadListener batchListener)) return;
        if (!batchListener.isRestored()) return;  // 有参构造创建的跳过
        
        Long subtaskId = task.getSubtaskId();
        if (subtaskId == null) {
            logger.warn("⚠️ subtaskId为null，无法恢复监听器状态: file={}", task.getFileName());
            return;
        }
        
        try {
            ProgressReporter reporter = getOrCreateGlobalProgressReporter();
            
            batchListener.restoreState(
                task.getTaskId(),
                subtaskId,
                task.getLocalFilePath(),
                task.getFileName(),
                task.getFileSize(),
                reporter
            );
            
            logger.info("✅ 上传监听器状态已恢复: subtaskId={}, file={}", 
                subtaskId, task.getFileName());
                
        } catch (Exception e) {
            logger.error("❌ 恢复监听器状态失败: subtaskId={}, error={}",
                subtaskId, e.getMessage());
        }
    }

    /**
     * 从监听器提取恢复字段到任务对象
     */
    public void populateRestoreFieldsFromListener(UploadListener listener) {
        if (!(listener instanceof BatchUploadListener batchListener)) return;
        
        try {
            logger.debug("✅ 从监听器提取恢复字段: taskId={}, subtaskId={}, fileName={}",
                batchListener.getTaskId(), 
                batchListener.getSubtaskId(),
                batchListener.getFileName());
        } catch (Exception e) {
            logger.warn("⚠️ 提取恢复字段失败: {}", e.getMessage());
        }
    }

    // ==================== 委托访问 ====================

    /**
     * 获取委托对象（用于需要访问非接口方法时）
     */
    public UploadService getDelegate() {
        return delegate;
    }

    // ==================== 私有辅助方法 ====================

    private ProgressReporter getOrCreateGlobalProgressReporter() {
        if (globalProgressReporter != null) {
            return globalProgressReporter;
        }
        
        logger.warn("⚠️ globalProgressReporter未设置，将创建临时实例");
        return createTemporaryProgressReporter();
    }

    private ProgressReporter createTemporaryProgressReporter() {
        String proxyUrl = getRegistryServerUrl();
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return new ProgressReporter(null);
        }
        return new ProgressReporter(proxyUrl);
    }

    private String getRegistryServerUrl() {
        AgentConfig config = delegate.getAgentConfig();
        if (config == null) return null;
        
        List<String> urls = config.getRegistryServerUrls();
        return (urls != null && !urls.isEmpty()) ? urls.get(0) : null;
    }

    @SuppressWarnings("unchecked")
    private <T> T createListenerInstance(String className, Class<T> type) throws Exception {
        Class<?> clazz = Class.forName(className);
        return type.cast(clazz.getDeclaredConstructor().newInstance());
    }

    @SuppressWarnings("unchecked")
    private void cacheListener(String taskKey, UploadListener listener) {
        try {
            java.lang.reflect.Field field = findClass("com.cq.agent.client.BaseAgentClient")
                .getDeclaredField("listenerCache");
            field.setAccessible(true);
            java.util.Map<String, Object> cache = (java.util.Map<String, Object>) field.get(delegate);
            cache.put(taskKey, listener);
        } catch (Exception e) {
            logger.warn("无法缓存监听器: {}", e.getMessage());
        }
    }

    private Class<?> findClass(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }
}
