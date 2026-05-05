package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BatchTaskStatus {

    DRAFT("DRAFT", "草稿"),
    RUNNING("RUNNING", "运行中"),
    PAUSED("PAUSED", "已暂停"),
    STOPPED("STOPPED", "已停止");

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

    public boolean isStartable() {
        return this == DRAFT || this == PAUSED;
    }

    public boolean isPausable() {
        return this == RUNNING;
    }

    public boolean isResumable() {
        return this == PAUSED;
    }

    public boolean isStoppable() {
        return this == RUNNING || this == PAUSED || this == DRAFT;
    }

    public boolean isDeletable() {
        return this == STOPPED || this == DRAFT;
    }
}
