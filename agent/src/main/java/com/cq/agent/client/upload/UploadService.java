package com.cq.agent.client.upload;

import com.cq.agent.config.AgentConfig;

/**
 * 上传服务接口（精简版）
 * 只包含核心契约，遵循接口隔离原则（ISP）：
 * - uploadFile(): 核心业务方法
 * - getAgentConfig(): 装饰者依赖的基础设施
 * 其他方法（任务管理、队列查询、重试等）属于实现细节，
 * 保留在 AgentUploader 具体类中。
 */
public interface UploadService {

    /**
     * 上传文件到远程Agent（核心契约）
     *
     * @param localFilePath 本地文件绝对路径
     * @param remoteTargetInfo 远程目标信息 (格式: ip:port@username:destPath)
     * @param listener 上传监听器（可为null）
     * @return 是否成功提交到工作队列
     */
    boolean uploadFile(String localFilePath, String remoteTargetInfo, UploadListener listener);

    /**
     * 获取Agent配置信息（基础设施）
     * 装饰者通过此方法获取注册中心URL等信息，
     * 用于创建 ProgressReporter 等依赖项。
     *
     * @return 配置对象
     */
    AgentConfig getAgentConfig();
}
