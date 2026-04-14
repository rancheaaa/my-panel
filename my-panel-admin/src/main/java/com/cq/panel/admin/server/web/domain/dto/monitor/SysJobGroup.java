package com.cq.panel.admin.server.web.domain.dto.monitor;

import jakarta.validation.groups.Default;

/**
 * 定时任务验证分组
 * 
 * @author cq
 */
public interface SysJobGroup {
    
    interface InternalGroup extends Default {
    }
    
    interface HttpGroup extends Default {
    }
    
    interface ScriptGroup extends Default {
    }
}