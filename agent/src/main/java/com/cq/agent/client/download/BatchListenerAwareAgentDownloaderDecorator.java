package com.cq.agent.client.download;

import com.cq.agent.batch.report.ProgressReporter;
import com.cq.agent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Objects;

/**
 * 批量任务监听器感知的下载装饰者（基于接口）
 * 
 * 使用装饰者模式为 DownloadService 添加监听器管理能力：
 * - 监听器的反射创建与缓存
 * - 重启恢复场景的状态重建
 * - ProgressReporter 集成
 */
public class BatchListenerAwareAgentDownloaderDecorator implements DownloadService {

    private static final Logger logger = LoggerFactory.getLogger(
        BatchListenerAwareAgentDownloaderDecorator.class);

    /** 委托对象（必须实现 DownloadService 接口） */
    private final DownloadService delegate;
    
    /** 全局ProgressReporter实例 */
    private ProgressReporter globalProgressReporter;

    /**
     * 构造函数
     * @param delegate 被装饰的下载服务实例（通常是 AgentDownloader）
     */
    public BatchListenerAwareAgentDownloaderDecorator(DownloadService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为null");
        logger.info("✅ 创建BatchListenerAwareAgentDownloaderDecorator");
    }

    // ==================== DownloadService 接口实现 ====================

    @Override
    public boolean downloadFile(String remoteFileInfo, String localFilePath, DownloadListener listener) {
        return delegate.downloadFile(remoteFileInfo, localFilePath, listener);
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

    // ==================== 委托访问 ====================

    /**
     * 获取委托对象
     */
    public DownloadService getDelegate() {
        return delegate;
    }
}
