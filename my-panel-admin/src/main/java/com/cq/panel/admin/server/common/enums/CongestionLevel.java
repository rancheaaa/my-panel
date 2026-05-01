package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CongestionLevel {

    NORMAL("NORMAL", "正常"),
    WARNING("WARNING", "警告"),
    CRITICAL("CRITICAL", "严重");

    private final String code;
    private final String description;

    public static CongestionLevel fromCode(String code) {
        for (CongestionLevel level : values()) {
            if (level.getCode().equals(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown CongestionLevel code: " + code);
    }
}
