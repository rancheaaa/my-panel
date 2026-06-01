package com.cq.proxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 子任务状态响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubTaskStatusResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前状态 */
    private String status;
}
