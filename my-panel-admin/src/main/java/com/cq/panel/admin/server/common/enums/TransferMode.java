package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransferMode {

    ONE_TO_ONE("ONE_TO_ONE", "一对一"),
    ONE_TO_MANY("ONE_TO_MANY", "一对多");

    private final String code;
    private final String description;

    public static TransferMode fromCode(String code) {
        for (TransferMode mode : values()) {
            if (mode.getCode().equals(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown TransferMode code: " + code);
    }
}
