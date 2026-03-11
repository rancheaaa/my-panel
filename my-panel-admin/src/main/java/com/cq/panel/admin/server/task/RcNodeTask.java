package com.cq.panel.admin.server.task;

import com.cq.panel.admin.server.repository.mapper.RcNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 注册中心节点任务
 * 
 * @author cq
 */
@Component("rcNodeTask")
public class RcNodeTask
{
    private static final Logger log = LoggerFactory.getLogger(RcNodeTask.class);

    private final RcNodeMapper rcNodeMapper;

    public RcNodeTask(RcNodeMapper rcNodeMapper)
    {
        this.rcNodeMapper = rcNodeMapper;
    }

    /**
     * 扫描并下线超时节点
     * 
     * @param timeoutSeconds 超时时间（秒）
     */
    public void scanOfflineNodes(Integer timeoutSeconds)
    {
        log.info("开始扫描超时节点，超时时间设置：{}秒", timeoutSeconds);
        int count = rcNodeMapper.updateNodeOfflineByTimeout(timeoutSeconds);
        if (count > 0)
        {
            log.info("扫描完成，成功下线 {} 个超时节点", count);
        }
    }
}
