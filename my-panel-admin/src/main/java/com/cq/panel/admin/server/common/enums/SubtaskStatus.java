package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SubtaskStatus {

    QUEUED("QUEUED", "排队中"),
    SENDING("SENDING", "传输中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "失败"),
    RETRYING("RETRYING", "重试中"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;

    public static SubtaskStatus fromCode(String code) {
        for (SubtaskStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown SubtaskStatus code: " + code);
    }
}
