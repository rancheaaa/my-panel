package com.cq.panel.admin.server.web.domain.vo.batch;

import lombok.Data;
import java.util.List;

/**
 * 配置校验结果
 * 按 taskId 展示字段级差异对比
 */
@Data
public class ConfigVerifyResultVO {
    private String sourceAgentId;
    private String sourceAgentName;
    private boolean allMatch;
    private List<TaskVerifyResult> tasks;

    @Data
    public static class TaskVerifyResult {
        /** 任务ID */
        private Long taskId;
        private String taskName;
        /** 是否存在 */
        private boolean exists;
        /** 是否完全一致 */
        private boolean match;
        /** 字段差异列表 */
        private List<FieldDiff> diffs;
    }

    @Data
    public static class FieldDiff {
        /** 字段路径，如 transferConfig.postTransferAction */
        private String fieldPath;
        /** 服务端值 */
        private String serverValue;
        /** Agent端值 */
        private String agentValue;
    }
}
