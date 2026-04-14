package com.cq.panel.admin.server.web.domain.page;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 表格分页数据对象
 * 
 * @author cq
 */
@Data
public class TableDataInfo implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 列表数据 */
    private List<?> rows;

    /** 消息状态码 */
    private int code;

    /** 消息内容 */
    private String msg;
}
