package com.cq.proxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 子任务创建成功响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubTaskCreateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建后的子任务ID（数据库主键） */
    private Long subtaskId;
}
