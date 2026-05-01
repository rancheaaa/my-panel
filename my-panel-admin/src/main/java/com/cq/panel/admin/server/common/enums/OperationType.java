package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OperationType {

    CREATE("CREATE", "创建任务"),
    START("START", "启动任务"),
    PAUSE("PAUSE", "暂停任务"),
    RESUME("RESUME", "恢复任务"),
    CANCEL("CANCEL", "取消任务"),
    CONFIG_UPDATE("CONFIG_UPDATE", "配置参数调整"),
    MANUAL_RETRY("MANUAL_RETRY", "手动重试子任务"),
    DELETE("DELETE", "删除任务");

    private final String code;
    private final String description;

    public static OperationType fromCode(String code) {
        for (OperationType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OperationType code: " + code);
    }
}
