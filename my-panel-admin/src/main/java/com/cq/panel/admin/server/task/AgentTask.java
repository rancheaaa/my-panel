package com.cq.panel.admin.server.task;

import com.cq.panel.admin.server.repository.service.IAgentRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent节点任务
 * 
 * @author cq
 */
@Component("agentTask")
public class AgentTask
{
    private static final Logger log = LoggerFactory.getLogger(AgentTask.class);

    private final IAgentRegistryService agentRegistryService;

    public AgentTask(IAgentRegistryService agentRegistryService)
    {
        this.agentRegistryService = agentRegistryService;
    }

    /**
     * 扫描agent节点并下线超时节点
     * 
     * @param timeoutSeconds 超时时间（秒）
     */
    public void scanOfflineAgents(Integer timeoutSeconds)
    {
        log.info("开始扫描Agent超时节点，超时时间设置：{}秒", timeoutSeconds);
        int count = agentRegistryService.offlineTimeoutNodes(timeoutSeconds);
        if (count > 0)
        {
            log.info("扫描完成，成功下线 {} 个Agent超时节点", count);
        }
        else
        {
            log.info("扫描完成，没有发现Agent超时节点");
        }
    }
}