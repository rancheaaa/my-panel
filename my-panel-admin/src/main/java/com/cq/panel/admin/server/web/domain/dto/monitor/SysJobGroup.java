package com.cq.panel.admin.server.web.domain.dto.monitor;

/**
 * 定时任务校验分组
 * 
 * @author cq
 */
public interface SysJobGroup {
    /**
     * 内置方法分组
     */
    interface InternalGroup {}

    /**
     * HTTP接口分组
     */
    interface HttpGroup {}
}
