package com.cq.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * 操作消息提醒
 *
 * @author cq
 */

@Data
public class Result<T> implements Serializable {
    private final int code;
    private final String msg;
    private final T data;
}

