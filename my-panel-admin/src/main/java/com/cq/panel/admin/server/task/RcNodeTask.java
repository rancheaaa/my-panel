package com.cq.panel.admin.server.task;

import com.cq.panel.admin.server.common.utils.MyDateUtils;
import com.cq.panel.admin.server.repository.domain.RcNode;
import com.cq.panel.admin.server.repository.mapper.RcNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.util.List;

/**
 * 注册中心节点任务
 *
 * @author cq
 */
@Component("rcNodeTask")
public class RcNodeTask {
    private static final Logger log = LoggerFactory.getLogger(RcNodeTask.class);

    private final RcNodeMapper rcNodeMapper;

    public RcNodeTask(RcNodeMapper rcNodeMapper) {
        this.rcNodeMapper = rcNodeMapper;
    }

    /**
     * 扫描并下线超时节点
     *
     * @param timeoutSeconds 超时时间（秒）
     */
    public void scanOfflineNodes(Integer timeoutSeconds) {
        log.info("开始扫描超时节点，超时时间设置：{}秒", timeoutSeconds);

        Date now = MyDateUtils.getNowDate();
        long timeoutMillis = timeoutSeconds * 1000L;

        List<RcNode> onlineNodes = rcNodeMapper.selectOnlineNodes();
        if (onlineNodes == null || onlineNodes.isEmpty()) {
            log.info("没有在线节点需要检查");
            return;
        }

        int offlineCount = 0;
        for (RcNode node : onlineNodes) {
            Date lastRefreshTime = node.getLastRefreshTime();
            if (lastRefreshTime == null) {
                log.warn("节点 {}:{} 的最后刷新时间为空，标记为离线", node.getNodeIp(), node.getNodePort());
                markNodeOffline(node);
                offlineCount++;
                continue;
            }

            long timeDiff = now.getTime() - lastRefreshTime.getTime();
            if (timeDiff > timeoutMillis) {
                log.info("节点 {}:{} 超时，最后刷新时间：{}，超时：{}秒",
                        node.getNodeIp(), node.getNodePort(),
                        MyDateUtils.parseDateToStr(MyDateUtils.YYYY_MM_DD_HH_MM_SS, lastRefreshTime),
                        timeDiff / 1000);
                markNodeOffline(node);
                offlineCount++;
            }
        }

        if (offlineCount > 0) {
            log.info("扫描完成，成功下线 {} 个超时节点", offlineCount);
        } else {
            log.info("扫描完成，没有发现超时节点");
        }
    }

    /**
     * 标记节点为离线状态
     *
     * @param node 节点信息
     */
    private void markNodeOffline(RcNode node) {
        node.setStatus("1");
        node.setUpdateTime(MyDateUtils.getNowDate());
        rcNodeMapper.updateRcNode(node);
    }
}