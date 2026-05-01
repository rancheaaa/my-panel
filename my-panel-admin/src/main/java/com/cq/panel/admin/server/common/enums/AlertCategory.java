package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AlertCategory {

    QUEUE_CONGESTION("QUEUE_CONGESTION", "队列堵塞"),
    TASK_FAILURE("TASK_FAILURE", "任务失败"),
    TASK_PARTIAL_FAIL("TASK_PARTIAL_FAIL", "部分失败"),
    BANDWIDTH_EXCEEDED("BANDWIDTH_EXCEEDED", "带宽超限"),
    AGENT_OFFLINE("AGENT_OFFLINE", "Agent离线"),
    SCAN_ERROR("SCAN_ERROR", "扫描错误"),
    RETRY_EXHAUSTED("RETRY_EXHAUSTED", "重试耗尽"),
    POST_TRANSFER_PARTIAL_FAIL("POST_TRANSFER_PARTIAL_FAIL", "后处理部分失败");

    private final String code;
    private final String description;

    public static AlertCategory fromCode(String code) {
        for (AlertCategory category : values()) {
            if (category.getCode().equals(code)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown AlertCategory code: " + code);
    }
}
