package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BackupMode {

    COPY("COPY", "复制"),
    MOVE("MOVE", "移动");

    private final String code;
    private final String description;

    public static BackupMode fromCode(String code) {
        for (BackupMode mode : values()) {
            if (mode.getCode().equals(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown BackupMode code: " + code);
    }
}
