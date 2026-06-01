package com.cq.agent.client.download;

import com.cq.agent.config.AgentConfig;

/**
 * 下载服务接口（精简版）
 * 只包含核心契约，遵循接口隔离原则（ISP）：
 * - downloadFile(): 核心业务方法
 * - getAgentConfig(): 装饰者依赖的基础设施
 * 其他方法（任务管理、队列查询、重试等）属于实现细节，
 * 保留在 AgentDownloader 具体类中。
 */
public interface DownloadService {

    /**
     * 从远程Agent下载文件（核心契约）
     *
     * @param remoteFileInfo 远程文件信息 (格式: ip:port@username:remotePath)
     * @param localFilePath 本地保存路径
     * @param listener 下载监听器（可为null）
     * @return 是否成功提交到工作队列
     */
    boolean downloadFile(String remoteFileInfo, String localFilePath, DownloadListener listener);

    /**
     * 获取Agent配置信息（基础设施）
     * 装饰者通过此方法获取注册中心URL等信息，
     * 用于创建 ProgressReporter 等依赖项。
     *
     * @return 配置对象
     */
    AgentConfig getAgentConfig();
}
