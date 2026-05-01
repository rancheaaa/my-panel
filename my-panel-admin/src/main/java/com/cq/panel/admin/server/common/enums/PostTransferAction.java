package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PostTransferAction {

    NONE("NONE", "无操作"),
    DELETE("DELETE", "删除源文件"),
    BACKUP("BACKUP", "备份到指定目录");

    private final String code;
    private final String description;

    public static PostTransferAction fromCode(String code) {
        for (PostTransferAction action : values()) {
            if (action.getCode().equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown PostTransferAction code: " + code);
    }
}
