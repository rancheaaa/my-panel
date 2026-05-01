package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BatchTaskStatus {

    PENDING("PENDING", "待启动"),
    SCANNING("SCANNING", "扫描中"),
    TRANSFERRING("TRANSFERRING", "传输中"),
    PAUSED("PAUSED", "已暂停"),
    POST_PROCESSING("POST_PROCESSING", "后处理中"),
    COMPLETED("COMPLETED", "已完成"),
    PARTIAL_FAILED("PARTIAL_FAILED", "部分失败"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消"),
    EXPIRED("EXPIRED", "已过期");

    private final String code;
    private final String description;

    public static BatchTaskStatus fromCode(String code) {
        for (BatchTaskStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown BatchTaskStatus code: " + code);
    }
}
