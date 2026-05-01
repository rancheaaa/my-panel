package com.cq.panel.admin.server.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RoutingStrategyType {

    BROADCAST("BROADCAST", "广播"),
    SINGLE("SINGLE", "单机粘性"),
    ROUND_ROBIN("ROUND_ROBIN", "轮询"),
    REGION_BASED("REGION_BASED", "区域路由"),
    RANDOM("RANDOM", "随机");

    private final String code;
    private final String description;

    public static RoutingStrategyType fromCode(String code) {
        for (RoutingStrategyType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RoutingStrategyType code: " + code);
    }
}
